package com.matsuri.auto.clicker.config;

import com.matsuri.auto.clicker.click.MouseButton;
import com.matsuri.auto.clicker.logging.OperateLogger;
import com.matsuri.auto.clicker.shortcut.KeyboardShortcutBinding;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 设置持久化：以 properties 形式存放在程序目录，任何一项损坏时回退到默认值。
 */
public final class ClickerSettingsStore {

    private static final String SETTINGS_FILE_NAME = "auto-clicker-settings.properties";

    private static final String KEY_INTERVAL_MS = "click.intervalMs";
    private static final String KEY_MAX_CLICKS = "click.maxClicks";
    private static final String KEY_MOUSE_BUTTON = "click.mouseButton";
    private static final String KEY_COORDINATE_X = "click.coordinateX";
    private static final String KEY_COORDINATE_Y = "click.coordinateY";
    private static final String KEY_START_SHORTCUT_MODIFIERS = "shortcut.start.modifiers";
    private static final String KEY_START_SHORTCUT_KEY = "shortcut.start.keyCode";
    private static final String KEY_STOP_SHORTCUT_MODIFIERS = "shortcut.stop.modifiers";
    private static final String KEY_STOP_SHORTCUT_KEY = "shortcut.stop.keyCode";

    /** 修饰键掩码说明：数值直接取自 KeyEvent 常量，保证注释与代码永远一致 */
    private static final String MODIFIER_MASK_COMMENT =
            "快捷键修饰键位掩码：Shift=" + KeyEvent.SHIFT_DOWN_MASK
                    + "，Ctrl=" + KeyEvent.CTRL_DOWN_MASK
                    + "，Win=" + KeyEvent.META_DOWN_MASK
                    + "，Alt=" + KeyEvent.ALT_DOWN_MASK
                    + "，可相加组合；0 表示无修饰键";

    private ClickerSettingsStore() {
    }

    /** 读取设置；文件不存在或某项非法时该项回退到 fallback。 */
    public static ClickerSettings load(ClickerSettings fallback) {
        Path settingsFile = settingsFile();
        if (!Files.isRegularFile(settingsFile)) {
            return fallback;
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(settingsFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            OperateLogger.warn("读取设置文件失败，使用默认设置：" + e.getMessage());
            return fallback;
        }
        return new ClickerSettings(
                readLong(properties, KEY_INTERVAL_MS, fallback.intervalMs(), 1, Long.MAX_VALUE),
                readLong(properties, KEY_MAX_CLICKS, fallback.maxClicks(), 0, Long.MAX_VALUE),
                readMouseButton(properties, KEY_MOUSE_BUTTON, fallback.mouseButton()),
                readCoordinate(properties, KEY_COORDINATE_X, fallback.coordinateX()),
                readCoordinate(properties, KEY_COORDINATE_Y, fallback.coordinateY()),
                readShortcut(properties, KEY_START_SHORTCUT_MODIFIERS, KEY_START_SHORTCUT_KEY, fallback.startShortcut()),
                readShortcut(properties, KEY_STOP_SHORTCUT_MODIFIERS, KEY_STOP_SHORTCUT_KEY, fallback.stopShortcut()));
    }

    /** 写入设置文件；每个键值对上方带一行 # 注释说明取值含义（Properties.store 会丢注释，故手动拼装）。 */
    public static void save(ClickerSettings settings) {
        StringBuilder content = new StringBuilder();
        content.append("# Auto Clicker 设置文件（程序自动读写）\n")
                .append("# 说明：某项缺失或取值非法时，程序自动回退该项默认值；运行期间修改会被自动保存覆盖\n");
        appendProperty(content, "点击间隔（毫秒，最小 1）", KEY_INTERVAL_MS, Long.toString(settings.intervalMs()));
        appendProperty(content, "连点次数上限（0 表示不限）", KEY_MAX_CLICKS, Long.toString(settings.maxClicks()));
        appendProperty(content, "鼠标按键：LEFT=左键，MIDDLE=中键，RIGHT=右键",
                KEY_MOUSE_BUTTON, settings.mouseButton().name());
        appendProperty(content, "固定点击坐标 X；留空=跟随当前鼠标位置，允许负值（多显示器）",
                KEY_COORDINATE_X, coordinateText(settings.coordinateX()));
        appendProperty(content, "固定点击坐标 Y；留空=跟随当前鼠标位置，允许负值（多显示器）",
                KEY_COORDINATE_Y, coordinateText(settings.coordinateY()));
        appendProperty(content, MODIFIER_MASK_COMMENT,
                KEY_START_SHORTCUT_MODIFIERS, Integer.toString(settings.startShortcut().modifiers()));
        appendProperty(content, "开始快捷键主键（Win32 虚拟键码，如 112=F1）",
                KEY_START_SHORTCUT_KEY, Integer.toString(settings.startShortcut().keyCode()));
        appendProperty(content, MODIFIER_MASK_COMMENT,
                KEY_STOP_SHORTCUT_MODIFIERS, Integer.toString(settings.stopShortcut().modifiers()));
        appendProperty(content, "结束快捷键主键（Win32 虚拟键码，如 113=F2）",
                KEY_STOP_SHORTCUT_KEY, Integer.toString(settings.stopShortcut().keyCode()));
        try (Writer writer = Files.newBufferedWriter(settingsFile(), StandardCharsets.UTF_8)) {
            writer.write(content.toString());
        } catch (IOException e) {
            OperateLogger.warn("保存设置文件失败：" + e.getMessage());
        }
    }

    private static Path settingsFile() {
        return Path.of(System.getProperty("user.dir"), SETTINGS_FILE_NAME);
    }

    /** 追加一行 # 注释 + 一个键值对。 */
    private static void appendProperty(StringBuilder content, String comment, String key, String value) {
        content.append('#').append(comment).append('\n')
                .append(key).append('=').append(value).append('\n');
    }

    /** 坐标分量转文本：null（跟随鼠标）写为空串。 */
    private static String coordinateText(Integer coordinate) {
        return coordinate == null ? "" : Integer.toString(coordinate);
    }

    /** 读取 [min, max] 范围内的整数，缺失或越界时返回 fallback。 */
    private static long readLong(Properties properties, String key, long fallback, long min, long max) {
        String text = properties.getProperty(key);
        if (text == null || text.isBlank()) {
            return fallback;
        }
        try {
            long value = Long.parseLong(text.trim());
            return value >= min && value <= max ? value : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 读取坐标分量：空白表示未设置（返回 null），非法时回退到 fallback。 */
    private static Integer readCoordinate(Properties properties, String key, Integer fallback) {
        String text = properties.getProperty(key);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static MouseButton readMouseButton(Properties properties, String key, MouseButton fallback) {
        String text = properties.getProperty(key);
        if (text == null || text.isBlank()) {
            return fallback;
        }
        try {
            return MouseButton.valueOf(text.trim());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static KeyboardShortcutBinding readShortcut(Properties properties,
                                                        String modifiersKey,
                                                        String keyCodeKey,
                                                        KeyboardShortcutBinding fallback) {
        String modifiersText = properties.getProperty(modifiersKey);
        String keyCodeText = properties.getProperty(keyCodeKey);
        if (isBlank(modifiersText) || isBlank(keyCodeText)) {
            return fallback;
        }
        try {
            int keyCode = Integer.parseInt(keyCodeText.trim());
            if (keyCode <= 0) {
                return fallback;
            }
            return new KeyboardShortcutBinding(Integer.parseInt(modifiersText.trim()), keyCode);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
