package com.matsuri.auto.clicker.shortcut;

import com.sun.jna.platform.win32.User32;

/**
 * 修饰键识别与状态跟踪（支持左右 Ctrl/Alt/Shift）。
 * 状态直接读取 GetAsyncKeyState 的实时物理键态：按键自动重复产生的多次
 * keydown、偶发丢失的 keyup 都不会导致状态卡死（旧实现用计数器，自动重复
 * 会让计数只增不减，物理释放后仍误判为按住，快捷键从此失效）。
 */
final class ModifierKeys {

    /** Win32 虚拟键码：通用 Ctrl（左右 Ctrl 也会产生的公共码） */
    static final int VIRTUAL_KEY_CONTROL = 0x11;
    /** Win32 虚拟键码：通用 Alt */
    static final int VIRTUAL_KEY_MENU = 0x12;
    /** Win32 虚拟键码：通用 Shift */
    static final int VIRTUAL_KEY_SHIFT = 0x10;
    /** Win32 虚拟键码：左 Ctrl */
    static final int VIRTUAL_KEY_LEFT_CONTROL = 0xA2;
    /** Win32 虚拟键码：右 Ctrl */
    static final int VIRTUAL_KEY_RIGHT_CONTROL = 0xA3;
    /** Win32 虚拟键码：左 Alt */
    static final int VIRTUAL_KEY_LEFT_MENU = 0xA4;
    /** Win32 虚拟键码：右 Alt */
    static final int VIRTUAL_KEY_RIGHT_MENU = 0xA5;
    /** Win32 虚拟键码：左 Shift */
    static final int VIRTUAL_KEY_LEFT_SHIFT = 0xA0;
    /** Win32 虚拟键码：右 Shift */
    static final int VIRTUAL_KEY_RIGHT_SHIFT = 0xA1;
    /** Win32 虚拟键码：左 Win */
    static final int VIRTUAL_KEY_LEFT_WIN = 0x5B;
    /** Win32 虚拟键码：右 Win */
    static final int VIRTUAL_KEY_RIGHT_WIN = 0x5C;
    /** GetAsyncKeyState 返回值的最高位表示当前物理按下 */
    private static final int KEY_STATE_PRESSED_FLAG = 0x8000;

    boolean isCtrlDown() {
        return isPressed(VIRTUAL_KEY_CONTROL)
                || isPressed(VIRTUAL_KEY_LEFT_CONTROL)
                || isPressed(VIRTUAL_KEY_RIGHT_CONTROL);
    }

    boolean isAltDown() {
        return isPressed(VIRTUAL_KEY_MENU)
                || isPressed(VIRTUAL_KEY_LEFT_MENU)
                || isPressed(VIRTUAL_KEY_RIGHT_MENU);
    }

    boolean isShiftDown() {
        return isPressed(VIRTUAL_KEY_SHIFT)
                || isPressed(VIRTUAL_KEY_LEFT_SHIFT)
                || isPressed(VIRTUAL_KEY_RIGHT_SHIFT);
    }

    boolean isMetaDown() {
        return isPressed(VIRTUAL_KEY_LEFT_WIN) || isPressed(VIRTUAL_KEY_RIGHT_WIN);
    }

    boolean isModifier(int virtualKeyCode) {
        return isCtrl(virtualKeyCode) || isAlt(virtualKeyCode) || isShift(virtualKeyCode) || isMeta(virtualKeyCode);
    }

    String snapshot() {
        return "tracked[ctrl=" + isCtrlDown() + ", alt=" + isAltDown()
                + ", shift=" + isShiftDown() + ", win=" + isMetaDown() + "]";
    }

    static boolean isCtrl(int virtualKeyCode) {
        return virtualKeyCode == VIRTUAL_KEY_CONTROL
                || virtualKeyCode == VIRTUAL_KEY_LEFT_CONTROL
                || virtualKeyCode == VIRTUAL_KEY_RIGHT_CONTROL;
    }

    static boolean isAlt(int virtualKeyCode) {
        return virtualKeyCode == VIRTUAL_KEY_MENU
                || virtualKeyCode == VIRTUAL_KEY_LEFT_MENU
                || virtualKeyCode == VIRTUAL_KEY_RIGHT_MENU;
    }

    static boolean isShift(int virtualKeyCode) {
        return virtualKeyCode == VIRTUAL_KEY_SHIFT
                || virtualKeyCode == VIRTUAL_KEY_LEFT_SHIFT
                || virtualKeyCode == VIRTUAL_KEY_RIGHT_SHIFT;
    }

    static boolean isMeta(int virtualKeyCode) {
        return virtualKeyCode == VIRTUAL_KEY_LEFT_WIN || virtualKeyCode == VIRTUAL_KEY_RIGHT_WIN;
    }

    private static boolean isPressed(int virtualKey) {
        return (User32.INSTANCE.GetAsyncKeyState(virtualKey) & KEY_STATE_PRESSED_FLAG) != 0;
    }
}
