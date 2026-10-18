package com.matsuri.auto.clicker.config;

import com.matsuri.auto.clicker.click.MouseButton;
import com.matsuri.auto.clicker.shortcut.KeyboardShortcutBinding;

/**
 * 连点器设置快照，用于退出时保存与启动时恢复，持久化到程序目录 auto-clicker-settings.properties。
 *
 * @param intervalMs    点击间隔（毫秒，最小 1）
 * @param maxClicks     连点次数上限（0 表示不限）
 * @param mouseButton   模拟点击的鼠标按键
 * @param coordinateX   点击坐标 X；null 表示跟随当前鼠标位置
 * @param coordinateY   点击坐标 Y；null 表示跟随当前鼠标位置
 * @param startShortcut 开始快捷键绑定
 * @param stopShortcut  结束快捷键绑定
 */
public record ClickerSettings(
        long intervalMs,
        long maxClicks,
        MouseButton mouseButton,
        Integer coordinateX,
        Integer coordinateY,
        KeyboardShortcutBinding startShortcut,
        KeyboardShortcutBinding stopShortcut) {
}
