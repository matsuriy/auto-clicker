package com.matsuri.auto.clicker.shortcut;

import java.awt.event.KeyEvent;

/**
 * 全局快捷键绑定（修饰键 + 主键）。键码统一使用 Win32 虚拟键码。
 *
 * @param modifiers 修饰键位掩码（KeyEvent 的 DOWN_MASK 组合，构造时裁剪掉不支持的位）
 * @param keyCode   主键的 Win32 虚拟键码
 */
public record KeyboardShortcutBinding(int modifiers, int keyCode) {

    /** 支持的修饰键掩码并集（Shift/Ctrl/Win/Alt） */
    public static final int MODIFIER_MASK = KeyEvent.CTRL_DOWN_MASK
            | KeyEvent.ALT_DOWN_MASK
            | KeyEvent.SHIFT_DOWN_MASK
            | KeyEvent.META_DOWN_MASK;

    /** 紧凑构造器：修饰键统一裁剪到支持的掩码内。 */
    public KeyboardShortcutBinding {
        modifiers &= MODIFIER_MASK;
    }

    /**
     * Java KeyEvent 键码 → Win32 虚拟键码（两者在回车与部分标点键上不一致，如回车 0x0A vs 0x0D）。
     */
    static int toWinVirtualKey(int javaKeyCode) {
        return switch (javaKeyCode) {
            case KeyEvent.VK_ENTER -> 0x0D;
            case KeyEvent.VK_SEMICOLON -> 0xBA;
            case KeyEvent.VK_EQUALS -> 0xBB;
            case KeyEvent.VK_COMMA -> 0xBC;
            case KeyEvent.VK_MINUS -> 0xBD;
            case KeyEvent.VK_PERIOD -> 0xBE;
            case KeyEvent.VK_SLASH -> 0xBF;
            case KeyEvent.VK_OPEN_BRACKET -> 0xDB;
            case KeyEvent.VK_BACK_SLASH -> 0xDC;
            case KeyEvent.VK_CLOSE_BRACKET -> 0xDD;
            case KeyEvent.VK_DELETE -> 0x2E;
            default -> javaKeyCode;
        };
    }

    /**
     * Win32 虚拟键码 → Java KeyEvent 键码（仅用于显示）。
     */
    private static int toJavaVirtualKey(int winVirtualKey) {
        return switch (winVirtualKey) {
            case 0x0D -> KeyEvent.VK_ENTER;
            case 0xBA -> KeyEvent.VK_SEMICOLON;
            case 0xBB -> KeyEvent.VK_EQUALS;
            case 0xBC -> KeyEvent.VK_COMMA;
            case 0xBD -> KeyEvent.VK_MINUS;
            case 0xBE -> KeyEvent.VK_PERIOD;
            case 0xBF -> KeyEvent.VK_SLASH;
            case 0xDB -> KeyEvent.VK_OPEN_BRACKET;
            case 0xDC -> KeyEvent.VK_BACK_SLASH;
            case 0xDD -> KeyEvent.VK_CLOSE_BRACKET;
            case 0x2E -> KeyEvent.VK_DELETE;
            default -> winVirtualKey;
        };
    }

    /** 从按键事件构造绑定：键码转为 Win32 虚拟键码，便于与钩子捕获值直接比对。 */
    public static KeyboardShortcutBinding fromKeyEvent(KeyEvent event) {
        return new KeyboardShortcutBinding(event.getModifiersEx(), toWinVirtualKey(event.getKeyCode()));
    }

    /** 判断钩子捕获的虚拟键码与修饰键按住状态是否命中本绑定。 */
    public boolean matchesVirtualKey(int virtualKeyCode, boolean ctrl, boolean alt, boolean shift, boolean meta) {
        if (virtualKeyCode != keyCode) {
            return false;
        }
        return ctrl == requiresCtrl()
                && alt == requiresAlt()
                && shift == requiresShift()
                && meta == requiresMeta();
    }

    private boolean requiresCtrl() {
        return (modifiers & KeyEvent.CTRL_DOWN_MASK) != 0;
    }

    private boolean requiresAlt() {
        return (modifiers & KeyEvent.ALT_DOWN_MASK) != 0;
    }

    private boolean requiresShift() {
        return (modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0;
    }

    private boolean requiresMeta() {
        return (modifiers & KeyEvent.META_DOWN_MASK) != 0;
    }

    /** 展示文本，如 Ctrl+F1。 */
    public String toDisplayText() {
        StringBuilder sb = new StringBuilder();
        if (requiresCtrl()) {
            sb.append("Ctrl+");
        }
        if (requiresAlt()) {
            sb.append("Alt+");
        }
        if (requiresShift()) {
            sb.append("Shift+");
        }
        if (requiresMeta()) {
            sb.append("Win+");
        }
        sb.append(KeyEvent.getKeyText(toJavaVirtualKey(keyCode)));
        return sb.toString();
    }

}
