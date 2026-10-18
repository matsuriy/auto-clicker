package com.matsuri.auto.clicker;

import com.matsuri.auto.clicker.logging.OperateLogger;
import com.matsuri.auto.clicker.ui.AutoClickerMainFrame;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * 程序入口：设置系统外观、记录启动环境，并兜底捕获未处理异常到日志。
 */
public final class AutoClickerMain {

    private AutoClickerMain() {
    }

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler(
                (thread, throwable) ->
                        OperateLogger.error("未捕获异常（线程 " + thread.getName() + "）", throwable));
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // 使用默认外观
            }
            OperateLogger.info("程序启动：Java " + System.getProperty("java.version")
                    + "，" + System.getProperty("os.name"));
            try {
                new AutoClickerMainFrame().setVisible(true);
            } catch (Throwable t) {
                OperateLogger.error("程序启动失败", t);
                JOptionPane.showMessageDialog(
                        null,
                        describeStartupFailure(t),
                        "Auto Clicker",
                        JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }

    /** 组装启动失败提示；getMessage() 可能为 null（如 NPE），此时退回异常类名。 */
    private static String describeStartupFailure(Throwable throwable) {
        String detail = throwable.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = throwable.getClass().getName();
        }
        return "程序启动失败：\n" + detail
                + "\n\n请确认已安装 Java 17 或更高版本，"
                + "或尝试双击 run.bat 启动。";
    }
}
