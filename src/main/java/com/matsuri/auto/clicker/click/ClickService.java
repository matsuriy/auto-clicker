package com.matsuri.auto.clicker.click;

import com.matsuri.auto.clicker.logging.OperateLogger;

import java.awt.AWTException;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Robot;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 在后台线程按设定按键执行连点（可指定固定点击坐标）。
 */
public class ClickService {

    /** 固定坐标模式下：移动后稍候再按下，给目标窗口处理悬停事件的时间 */
    private static final long MOVE_SETTLE_MS = 20;
    /** 固定坐标模式下：按下到抬起的间隔，过快的合成点击会被部分程序忽略 */
    private static final long PRESS_DURATION_MS = 10;

    private final AtomicLong clickCount = new AtomicLong(0);
    private volatile boolean running;
    private volatile long intervalMs = 100;
    private volatile long maxClicks = 0;
    private volatile MouseButton mouseButton = MouseButton.LEFT;
    private volatile Point targetPoint;
    private volatile long startTimeMs;
    private volatile long elapsedMs;
    private Thread workerThread;
    private volatile Runnable targetReachedListener;
    private volatile Consumer<String> failureListener;

    /**
     * @param target 固定点击坐标；null 表示点击当前鼠标位置
     */
    public synchronized void start(long intervalMs, long maxClicks, MouseButton mouseButton, Point target) {
        if (running) {
            return;
        }
        this.intervalMs = Math.max(1, intervalMs);
        this.maxClicks = Math.max(0, maxClicks);
        this.mouseButton = Objects.requireNonNull(mouseButton, "mouseButton");
        this.targetPoint = target;
        clickCount.set(0);
        startTimeMs = System.currentTimeMillis();
        elapsedMs = 0;
        running = true;
        workerThread = new Thread(new ClickTask(), "auto-clicker");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
            try {
                workerThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            workerThread = null;
        }
        elapsedMs = System.currentTimeMillis() - startTimeMs;
    }

    public boolean isRunning() {
        return running;
    }

    public long getClickCount() {
        return clickCount.get();
    }

    public long getDurationSeconds() {
        if (startTimeMs == 0) {
            return 0;
        }
        long ms = running ? (System.currentTimeMillis() - startTimeMs) : elapsedMs;
        return Math.max(0, ms / 1000);
    }

    /** 设置达到设定次数后的回调（在点击线程上执行，界面侧需自行切回 EDT）。 */
    public void setTargetReachedListener(Runnable listener) {
        targetReachedListener = listener;
    }

    /** 设置连点失败（如无法创建鼠标控制器）后的回调，参数为失败原因；需自行切回 EDT。 */
    public void setFailureListener(Consumer<String> listener) {
        failureListener = listener;
    }

    private class ClickTask implements Runnable {
        @Override
        public void run() {
            boolean reachedTarget = false;
            String failureReason = null;
            try {
                Robot clickRobot = new Robot();
                clickRobot.setAutoDelay(0);
                if (targetPoint != null) {
                    verifyTargetPosition(clickRobot);
                }
                while (running) {
                    clickOnce(clickRobot);
                    long count = clickCount.incrementAndGet();
                    if (maxClicks > 0 && count >= maxClicks) {
                        reachedTarget = true;
                        break;
                    }
                    Thread.sleep(intervalMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (AWTException e) {
                failureReason = "无法创建鼠标控制器：" + e.getMessage();
            }
            if (reachedTarget) {
                finishByTargetReached();
            } else if (failureReason != null) {
                finishByFailure(failureReason);
            }
        }

        /** 单次点击：固定坐标模式下先移动过去再按下。 */
        private void clickOnce(Robot clickRobot) throws InterruptedException {
            int inputMask = mouseButton.getInputMask();
            if (targetPoint != null) {
                clickRobot.mouseMove(targetPoint.x, targetPoint.y);
                Thread.sleep(MOVE_SETTLE_MS);
            }
            clickRobot.mousePress(inputMask);
            if (targetPoint != null) {
                Thread.sleep(PRESS_DURATION_MS);
            }
            clickRobot.mouseRelease(inputMask);
        }
    }

    /** 启动时先移动到目标坐标并校验实际落点，便于在日志中排查显示缩放/多显示器偏差。 */
    private void verifyTargetPosition(Robot clickRobot) throws InterruptedException {
        clickRobot.mouseMove(targetPoint.x, targetPoint.y);
        Thread.sleep(30);
        Point actual = MouseInfo.getPointerInfo().getLocation();
        if (actual.x != targetPoint.x || actual.y != targetPoint.y) {
            OperateLogger.warn("光标实际位置 " + actual.x + "," + actual.y + " 与目标坐标 "
                    + targetPoint.x + "," + targetPoint.y + " 不一致（可能受显示缩放/多显示器影响）");
        } else {
            OperateLogger.info("已定位到目标坐标 " + actual.x + "," + actual.y);
        }
    }

    /** 达到设定次数后由点击线程收尾并通知界面。 */
    private void finishByTargetReached() {
        synchronized (this) {
            if (!running) {
                return; // 几乎同时被手动停止，按手动停止处理
            }
            running = false;
            elapsedMs = System.currentTimeMillis() - startTimeMs;
            workerThread = null;
        }
        Runnable listener = targetReachedListener;
        if (listener != null) {
            listener.run();
        }
    }

    /** 启动失败时收尾并通知界面，避免界面停留在「连点中」。 */
    private void finishByFailure(String reason) {
        synchronized (this) {
            if (!running) {
                return; // 已被手动停止
            }
            running = false;
            elapsedMs = System.currentTimeMillis() - startTimeMs;
            workerThread = null;
        }
        OperateLogger.warn("连点已中止：" + reason);
        Consumer<String> listener = failureListener;
        if (listener != null) {
            listener.accept(reason);
        }
    }
}
