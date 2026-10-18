package com.matsuri.auto.clicker.shortcut;

/**
 * Windows GetLastError 常见错误码说明（用于钩子安装失败提示）。
 */
final class WinErrorMessages {

    private WinErrorMessages() {
    }

    static String describe(int code) {
        return switch (code) {
            case 5 -> "拒绝访问，可能被安全软件拦截（错误码 5）";
            case 87 -> "参数无效（错误码 87）";
            case 1428 -> "安装钩子的线程缺少消息窗口，请重启程序（错误码 1428）";
            default -> "系统错误码 " + code;
        };
    }
}
