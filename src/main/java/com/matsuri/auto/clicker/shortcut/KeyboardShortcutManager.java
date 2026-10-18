package com.matsuri.auto.clicker.shortcut;

import com.matsuri.auto.clicker.logging.OperateLogger;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HINSTANCE;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser;

import javax.swing.SwingUtilities;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Win32 低级键盘钩子（WH_KEYBOARD_LL）的全局快捷键监听。
 * 绑定与回调在界面线程写入、钩子线程读取，均以 volatile 保证可见性。
 */
public class KeyboardShortcutManager {

    public static final int KEYBOARD_SHORTCUT_ID_START = 1;
    public static final int KEYBOARD_SHORTCUT_ID_STOP = 2;
    private static final int DEBOUNCE_MS = 300;

    private volatile Runnable onStart;
    private volatile Runnable onStop;
    private volatile KeyboardShortcutBinding startBinding;
    private volatile KeyboardShortcutBinding stopBinding;

    private volatile boolean running;
    private volatile String createErrorDetail = "";
    private Thread hookThread;
    private WinUser.HHOOK hook;
    private CountDownLatch hookReady = new CountDownLatch(1);

    private final ModifierKeys modifierKeys = new ModifierKeys();

    private volatile long lastStartMs;
    private volatile long lastStopMs;

    /** 启动钩子线程并等待就绪；已有钩子时直接复用，启动失败或超时抛 IllegalStateException。 */
    public synchronized void install(Runnable onStart, Runnable onStop) {
        this.onStart = onStart;
        this.onStop = onStop;
        OperateLogger.info("开始安装键盘钩子");
        if (hookThread == null) {
            running = true;
            hookReady = new CountDownLatch(1);
            createErrorDetail = "";
            hookThread = new Thread(this::runHookLoop, "keyboard-hook-loop");
            hookThread.setDaemon(true);
            hookThread.start();
        }
        waitUntilReady();
        OperateLogger.info("键盘钩子已就绪");
    }

    private void waitUntilReady() {
        try {
            if (!hookReady.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("键盘钩子启动超时");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("键盘钩子启动被中断", e);
        }
        if (hook == null) {
            hookThread = null;
            running = false;
            if (!createErrorDetail.isEmpty()) {
                throw new IllegalStateException(createErrorDetail);
            }
            throw new IllegalStateException("键盘钩子启动失败");
        }
    }

    private void runHookLoop() {
        HINSTANCE hInstance = Kernel32.INSTANCE.GetModuleHandle(null);
        WinUser.LowLevelKeyboardProc keyboardProc = (notificationCode, wParam, info) -> {
            if (notificationCode >= 0) {
                int message = wParam.intValue();
                int virtualKeyCode = info.vkCode;
                if (message == WinUser.WM_KEYDOWN || message == WinUser.WM_SYSKEYDOWN) {
                    handleHookKeyEvent(virtualKeyCode, true);
                } else if (message == WinUser.WM_KEYUP || message == WinUser.WM_SYSKEYUP) {
                    handleHookKeyEvent(virtualKeyCode, false);
                }
            }
            return User32.INSTANCE.CallNextHookEx(
                    hook, notificationCode, wParam, new LPARAM(Pointer.nativeValue(info.getPointer())));
        };

        hook = User32.INSTANCE.SetWindowsHookEx(
                WinUser.WH_KEYBOARD_LL,
                keyboardProc,
                hInstance,
                0);

        if (hook == null) {
            createErrorDetail = "安装键盘钩子失败：" + WinErrorMessages.describe(Kernel32.INSTANCE.GetLastError());
            OperateLogger.warn(createErrorDetail);
            hookReady.countDown();
            return;
        }

        OperateLogger.info("SetWindowsHookEx 成功，进入消息循环");
        hookReady.countDown();

        WinUser.MSG msg = new WinUser.MSG();
        while (running) {
            int result = User32.INSTANCE.GetMessage(msg, null, 0, 0);
            if (result == 0 || result == -1) {
                OperateLogger.warn("键盘钩子消息循环结束（GetMessage 返回值=" + result
                        + "），快捷键已失效，可点击「应用快捷键」重新安装");
                break;
            }
            User32.INSTANCE.TranslateMessage(msg);
            User32.INSTANCE.DispatchMessage(msg);
        }
        synchronized (this) {
            hook = null;
            hookThread = null; // 允许下次 install() 重新创建钩子线程
        }
    }

    private void handleHookKeyEvent(int virtualKeyCode, boolean keyDown) {
        if (modifierKeys.isModifier(virtualKeyCode)) {
            OperateLogger.debug((keyDown ? "修饰键按下：" : "修饰键释放：")
                    + VirtualKeyNames.format(virtualKeyCode) + " " + modifierKeys.snapshot());
            return;
        }
        if (!keyDown) {
            return;
        }
        handleKeyDown(virtualKeyCode);
    }

    private void handleKeyDown(int virtualKeyCode) {
        boolean ctrl = modifierKeys.isCtrlDown();
        boolean alt = modifierKeys.isAltDown();
        boolean shift = modifierKeys.isShiftDown();
        boolean meta = modifierKeys.isMetaDown();
        long now = System.currentTimeMillis();

        OperateLogger.debug("捕获按键 " + VirtualKeyNames.format(virtualKeyCode) + "，"
                + VirtualKeyNames.formatModifiers(ctrl, alt, shift, meta) + " " + modifierKeys.snapshot());

        if (startBinding == null && stopBinding == null) {
            OperateLogger.debug("当前未设置快捷键绑定，忽略");
            return;
        }

        if (startBinding != null
                && onStart != null
                && startBinding.matchesVirtualKey(virtualKeyCode, ctrl, alt, shift, meta)) {
            if (now - lastStartMs < DEBOUNCE_MS) {
                OperateLogger.debug("开始快捷键命中但在防抖时间内，忽略");
                return;
            }
            lastStartMs = now;
            OperateLogger.info("命中开始快捷键：" + startBinding.toDisplayText() + "，触发开始连点");
            SwingUtilities.invokeLater(onStart);
            return;
        }

        if (stopBinding != null
                && onStop != null
                && stopBinding.matchesVirtualKey(virtualKeyCode, ctrl, alt, shift, meta)) {
            if (now - lastStopMs < DEBOUNCE_MS) {
                OperateLogger.debug("结束快捷键命中但在防抖时间内，忽略");
                return;
            }
            lastStopMs = now;
            OperateLogger.info("命中结束快捷键：" + stopBinding.toDisplayText() + "，触发停止连点");
            SwingUtilities.invokeLater(onStop);
            return;
        }

        if (startBinding != null || stopBinding != null) {
            OperateLogger.debug("按键未匹配。期望开始=" + bindingText(startBinding)
                    + "，期望结束=" + bindingText(stopBinding));
        }
    }

    private static String bindingText(KeyboardShortcutBinding binding) {
        return binding == null ? "未设置" : binding.toDisplayText();
    }

    /** 注册快捷键绑定；钩子未就绪时返回失败结果。 */
    public RegisterResult register(int keyboardShortcutId, KeyboardShortcutBinding binding) {
        try {
            waitUntilReady();
        } catch (IllegalStateException e) {
            return RegisterResult.fail(e.getMessage());
        }
        if (keyboardShortcutId == KEYBOARD_SHORTCUT_ID_START) {
            startBinding = binding;
            OperateLogger.info("注册开始快捷键：" + binding.toDisplayText());
        } else if (keyboardShortcutId == KEYBOARD_SHORTCUT_ID_STOP) {
            stopBinding = binding;
            OperateLogger.info("注册结束快捷键：" + binding.toDisplayText());
        } else {
            OperateLogger.warn("未知快捷键编号：" + keyboardShortcutId);
            return RegisterResult.fail("未知快捷键编号");
        }
        return RegisterResult.ok();
    }

    /** 清空全部快捷键绑定（钩子保持安装，用于重新应用前重置）。 */
    public void unregisterAll() {
        OperateLogger.info("清除快捷键绑定");
        startBinding = null;
        stopBinding = null;
    }

    /** 卸载钩子并结束钩子线程，清空绑定；窗口关闭时调用。 */
    public synchronized void dispose() {
        OperateLogger.info("卸载键盘钩子");
        running = false;
        if (hook != null) {
            User32.INSTANCE.UnhookWindowsHookEx(hook);
            hook = null;
        }
        if (hookThread != null) {
            User32.INSTANCE.PostThreadMessage(
                    (int) hookThread.getId(),
                    WinUser.WM_QUIT,
                    new WPARAM(0),
                    new LPARAM(0));
            try {
                hookThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            hookThread = null;
        }
        startBinding = null;
        stopBinding = null;
    }

    /** 注册结果：success=false 时 message 为失败原因。 */
    public record RegisterResult(boolean success, String message) {

        public static RegisterResult ok() {
            return new RegisterResult(true, "");
        }

        public static RegisterResult fail(String message) {
            return new RegisterResult(false, message);
        }

        public boolean isSuccess() {
            return success;
        }
    }
}
