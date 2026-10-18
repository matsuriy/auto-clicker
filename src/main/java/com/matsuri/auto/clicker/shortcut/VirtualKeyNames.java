package com.matsuri.auto.clicker.shortcut;

import java.awt.event.KeyEvent;

/**
 * 虚拟键码显示辅助。
 */
final class VirtualKeyNames {

    private VirtualKeyNames() {
    }

    static String format(int virtualKeyCode) {
        return switch (virtualKeyCode) {
            case ModifierKeys.VIRTUAL_KEY_LEFT_SHIFT -> "Left Shift(0xA0)";
            case ModifierKeys.VIRTUAL_KEY_RIGHT_SHIFT -> "Right Shift(0xA1)";
            case ModifierKeys.VIRTUAL_KEY_LEFT_CONTROL -> "Left Ctrl(0xA2)";
            case ModifierKeys.VIRTUAL_KEY_RIGHT_CONTROL -> "Right Ctrl(0xA3)";
            case ModifierKeys.VIRTUAL_KEY_LEFT_MENU -> "Left Alt(0xA4)";
            case ModifierKeys.VIRTUAL_KEY_RIGHT_MENU -> "Right Alt(0xA5)";
            case ModifierKeys.VIRTUAL_KEY_LEFT_WIN -> "Left Win(0x5B)";
            case ModifierKeys.VIRTUAL_KEY_RIGHT_WIN -> "Right Win(0x5C)";
            case ModifierKeys.VIRTUAL_KEY_SHIFT -> "Shift(0x10)";
            case ModifierKeys.VIRTUAL_KEY_CONTROL -> "Ctrl(0x11)";
            case ModifierKeys.VIRTUAL_KEY_MENU -> "Alt(0x12)";
            case 0x0D -> "Enter(0x0D)";
            case 0x2E -> "Delete(0x2E)";
            case 0xBA -> "\";\"(0xBA)";
            case 0xBB -> "\"=\"(0xBB)";
            case 0xBC -> "\",\"(0xBC)";
            case 0xBD -> "\"-\"(0xBD)";
            case 0xBE -> "\".\"(0xBE)";
            case 0xBF -> "\"/\"(0xBF)";
            case 0xDB -> "\"[\"(0xDB)";
            case 0xDC -> "\"\\\"(0xDC)";
            case 0xDD -> "\"]\"(0xDD)";
            default -> formatByKeyText(virtualKeyCode);
        };
    }

    /** 非修饰键与标点键：优先用 KeyEvent 名称，无名称时按十六进制码展示。 */
    private static String formatByKeyText(int virtualKeyCode) {
        String text = KeyEvent.getKeyText(virtualKeyCode);
        String hex = "(0x" + Integer.toHexString(virtualKeyCode).toUpperCase() + ")";
        if (text.isEmpty() || text.startsWith("Unknown")) {
            return "VirtualKey_0x" + Integer.toHexString(virtualKeyCode).toUpperCase();
        }
        return text + hex;
    }

    static String formatModifiers(boolean ctrl, boolean alt, boolean shift, boolean meta) {
        return "ctrl=" + ctrl + ", alt=" + alt + ", shift=" + shift + ", win=" + meta;
    }
}
