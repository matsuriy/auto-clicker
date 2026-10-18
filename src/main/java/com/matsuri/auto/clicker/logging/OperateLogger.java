package com.matsuri.auto.clicker.logging;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 简单异步文件日志，便于排查快捷键与连点问题。
 * 写日志只做入队（纳秒级），磁盘 I/O 与界面刷新由后台线程批量完成。
 * 键盘钩子回调等延迟敏感路径上不能做磁盘操作：回调一旦超时，Windows 会
 * 静默移除低级钩子，快捷键从此全局失效。
 */
public final class OperateLogger {

    private static final Object LOCK = new Object();
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final int MAX_UI_LINES = 200;
    private static final int MAX_BATCH_CHARS = 8192;
    private static final int MAX_BATCH_ENTRIES = 512;
    private static final BlockingQueue<LogEntry> QUEUE = new LinkedBlockingQueue<>(8192);

    private static volatile File logFile;
    private static volatile Consumer<String> uiListener;
    private static PrintWriter logWriter;

    /** 合并 UI 刷新：避免每条日志都往 EDT 灌一个 Runnable */
    private static final AtomicBoolean UI_PENDING = new AtomicBoolean(false);
    private static final StringBuilder UI_BUFFER = new StringBuilder();
    private static final Object UI_LOCK = new Object();

    /** 供 appendToUiArea 使用的行数计数（仅 EDT 访问） */
    private static final AtomicInteger UI_LINE_COUNT = new AtomicInteger();

    private record LogEntry(String level, String message) {
    }

    static {
        Thread writer = new Thread(OperateLogger::drainLoop, "app-logger");
        writer.setDaemon(true);
        writer.start();

        Runtime.getRuntime().addShutdownHook(new Thread(OperateLogger::flushOnShutdown, "app-logger-shutdown"));
    }

    private OperateLogger() {
    }

    public static File getLogFile() {
        ensureLogFile();
        return logFile;
    }

    public static void setUiListener(Consumer<String> listener) {
        uiListener = listener;
        info("日志文件：" + getLogFile().getAbsolutePath());
    }

    public static void info(String message) {
        enqueue("INFO", message);
    }

    public static void debug(String message) {
        enqueue("DEBUG", message);
    }

    public static void warn(String message) {
        enqueue("WARN", message);
    }

    public static void error(String message, Throwable throwable) {
        // 合并为一次入队，避免与其它线程日志交错
        String full = message;
        if (throwable != null) {
            full = message + "\n" + throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
        }
        enqueue("ERROR", full);
    }

    private static void enqueue(String level, String message) {
        if (!QUEUE.offer(new LogEntry(level, message))) {
            // 队列满时降级到 stderr，绝不阻塞钩子回调等调用方
            System.err.println("[" + level + "] " + message);
        }
    }

    private static void drainLoop() {
        while (true) {
            try {
                LogEntry first = QUEUE.take();
                StringBuilder batch = new StringBuilder(format(first));
                LogEntry more;
                int count = 1;
                while (batch.length() < MAX_BATCH_CHARS
                        && count < MAX_BATCH_ENTRIES
                        && (more = QUEUE.poll()) != null) {
                    batch.append('\n').append(format(more));
                    count++;
                }
                String text = batch.toString();
                writeBatch(text);
                notifyUi(text);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable t) {
                // 写盘等意外异常不能终止日志线程，否则所有日志静默丢失
                t.printStackTrace();
            }
        }
    }

    private static String format(LogEntry entry) {
        // 仅日志线程调用
        return "[" + TIME_FORMAT.format(LocalDateTime.now()) + "] [" + entry.level() + "] " + entry.message();
    }

    private static void writeBatch(String text) {
        synchronized (LOCK) {
            ensureLogFileLocked();
            if (logWriter == null) {
                logWriter = openWriter();
            }
            if (logWriter != null) {
                logWriter.print(text);
                logWriter.print('\n');
                logWriter.flush();
                if (logWriter.checkError()) {
                    // 写入失败（如文件被外部删除），下次写入时重开
                    logWriter.close();
                    logWriter = null;
                }
            }
            if (logWriter == null) {
                System.err.println(text);
            }
        }
    }

    private static PrintWriter openWriter() {
        try {
            return new PrintWriter(new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(logFile, true), StandardCharsets.UTF_8)));
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 合并 UI 通知：后台线程把文本追加到 UI_BUFFER，并确保只调度一次 EDT 任务。
     * 避免高频日志导致 EDT 事件队列膨胀。
     */
    private static void notifyUi(String text) {
        synchronized (UI_LOCK) {
            if (!UI_BUFFER.isEmpty()) {
                UI_BUFFER.append('\n');
            }
            UI_BUFFER.append(text);
        }
        if (UI_PENDING.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(() -> {
                String payload;
                synchronized (UI_LOCK) {
                    payload = UI_BUFFER.toString();
                    UI_BUFFER.setLength(0);
                    UI_PENDING.set(false);
                }
                Consumer<String> listener = uiListener;
                if (listener != null) {
                    listener.accept(payload);
                }
            });
        }
    }

    /**
     * 向 JTextArea 追加日志行（必须在 EDT 调用）。
     * 超过 MAX_UI_LINES 时清空，避免 UI 文本无限增长。
     */
    public static void appendToUiArea(JTextArea area, String line) {
        int lineCount = 1;
        for (int i = 0, n = line.length(); i < n; i++) {
            if (line.charAt(i) == '\n') {
                lineCount++;
            }
        }
        if (UI_LINE_COUNT.get() + lineCount > MAX_UI_LINES) {
            area.setText("");
            UI_LINE_COUNT.set(0);
        }
        area.append(line);
        if (!line.endsWith("\n")) {
            area.append("\n");
        }
        UI_LINE_COUNT.addAndGet(lineCount);
    }

    private static void ensureLogFile() {
        if (logFile != null) {
            return;
        }
        synchronized (LOCK) {
            ensureLogFileLocked();
        }
    }

    private static void ensureLogFileLocked() {
        if (logFile == null) {
            String baseDir = System.getProperty("user.dir");
            logFile = new File(baseDir, "auto-clicker.log");
        }
    }

    /** JVM 退出时尽量刷完队列 */
    private static void flushOnShutdown() {
        try {
            LogEntry entry;
            StringBuilder batch = new StringBuilder();
            while ((entry = QUEUE.poll()) != null) {
                if (!batch.isEmpty()) {
                    batch.append('\n');
                }
                batch.append(format(entry));
            }
            if (!batch.isEmpty()) {
                writeBatch(batch.toString());
            }
        } catch (Throwable ignored) {
            // shutdown hook 中不再抛异常
        }
    }
}