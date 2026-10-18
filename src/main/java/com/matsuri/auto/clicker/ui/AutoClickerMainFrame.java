package com.matsuri.auto.clicker.ui;

import com.github.lgooddatepicker.components.DatePicker;
import com.github.lgooddatepicker.components.DatePickerSettings;
import com.matsuri.auto.clicker.click.ClickService;
import com.matsuri.auto.clicker.click.MouseButton;
import com.matsuri.auto.clicker.config.ClickerSettings;
import com.matsuri.auto.clicker.config.ClickerSettingsStore;
import com.matsuri.auto.clicker.logging.OperateLogger;
import com.matsuri.auto.clicker.shortcut.KeyboardShortcutBinding;
import com.matsuri.auto.clicker.shortcut.KeyboardShortcutManager;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;

/**
 * 主窗口：设置连点参数、定时、显示统计与使用说明。
 * 设置在退出/应用/拾取坐标/恢复默认时自动持久化，下次启动恢复。
 */
public class AutoClickerMainFrame extends JFrame {

    private static final KeyboardShortcutBinding DEFAULT_START_SHORTCUT = new KeyboardShortcutBinding(0, KeyEvent.VK_F1);
    private static final KeyboardShortcutBinding DEFAULT_STOP_SHORTCUT = new KeyboardShortcutBinding(0, KeyEvent.VK_F2);
    private static final long DEFAULT_INTERVAL_MS = 10;
    private static final long DEFAULT_MAX_CLICKS = 10;
    private static final ClickerSettings DEFAULT_SETTINGS = new ClickerSettings(
            DEFAULT_INTERVAL_MS, DEFAULT_MAX_CLICKS, MouseButton.LEFT,
            null, null, DEFAULT_START_SHORTCUT, DEFAULT_STOP_SHORTCUT);
    private static final Point INVALID_COORDINATE = new Point(-1, -1);
    /** 倒计时小时输入上限 */
    private static final int MAX_COUNTDOWN_HOURS = 999;
    /** 定时状态刷新周期 */
    private static final int TIMER_TICK_MS = 250;
    private static final String CARD_COUNTDOWN = "countdown";
    private static final String CARD_DATETIME = "datetime";

    private final JTextField intervalField = new JTextField("10", 5);
    private final JTextField countField = new JTextField("10", 5);
    private final JComboBox<MouseButton> mouseButtonBox = new JComboBox<>(MouseButton.values());
    private final KeyboardShortcutField startKeyboardShortcutField = new KeyboardShortcutField("开始快捷键：", DEFAULT_START_SHORTCUT);
    private final KeyboardShortcutField stopKeyboardShortcutField = new KeyboardShortcutField("结束快捷键：", DEFAULT_STOP_SHORTCUT);
    private final JTextField coordinateXField = new JTextField("", 5);
    private final JTextField coordinateYField = new JTextField("", 5);
    private final JButton pickCoordinateButton = new JButton("拾取坐标");
    private boolean picking;

    private final JComboBox<TimerMode> timerModeBox = createTimerModeCombo();
    private final JTextField timerHoursField = new JTextField("0", 2);
    private final JTextField timerMinutesField = new JTextField("0", 2);
    private final JTextField timerSecondsField = new JTextField("30", 2);
    private final DatePicker timerDatePicker = new DatePicker(new DatePickerSettings(Locale.CHINA));
    private final JComboBox<Integer> timerHourCombo = createTimeCombo(24);
    private final JComboBox<Integer> timerMinuteCombo = createTimeCombo(60);
    private final JComboBox<Integer> timerSecondCombo = createTimeCombo(60);
    private final JPanel timerValueCards = new JPanel(new CardLayout());
    private final JButton timerButton = new JButton("启动定时");
    private Timer timerTick;
    private long timerFireAtMs;
    private String timerTargetText;

    private final JLabel statusLabel = new JLabel("状态：初始化中...");
    private final JLabel statsLabel = new JLabel("上次统计：尚未开始");
    private final JTextArea logArea = new JTextArea(8, 60);

    private final ClickService clickService = new ClickService();
    private KeyboardShortcutManager keyboardShortcutManager;

    public AutoClickerMainFrame() {
        super("Auto Clicker");
        clickService.setTargetReachedListener(() ->
                SwingUtilities.invokeLater(this::onTargetReached));
        clickService.setFailureListener(reason ->
                SwingUtilities.invokeLater(() -> onClickFailed(reason)));
        // 先恢复上次保存的设置再创建界面，保证输入框初始值即为上次状态
        applyLoadedSettings(ClickerSettingsStore.load(DEFAULT_SETTINGS));
        // 固定时间定时的日期默认为第二天，保证默认值总是将来的时间
        timerDatePicker.setDate(LocalDate.now().plusDays(1));
        initUi();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pack();
        setMinimumSize(new Dimension(520, 480));
        setLocationRelativeTo(null);

        OperateLogger.setUiListener(line -> OperateLogger.appendToUiArea(logArea, line));
        OperateLogger.info("主窗口已创建");

        addWindowListener(new MainFrameWindowListener(this));
    }

    /** 窗口关闭时保存设置并释放连点与快捷键资源。 */
    void onWindowClosing() {
        OperateLogger.info("程序退出");
        saveSettings();
        clickService.stop();
        if (keyboardShortcutManager != null) {
            keyboardShortcutManager.dispose();
        }
    }

    private void initUi() {
        JPanel mainTab = new JPanel(new GridBagLayout());
        mainTab.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        GridBagConstraints gridConstraints = new GridBagConstraints();
        gridConstraints.gridx = 0;
        gridConstraints.weightx = 1.0;
        gridConstraints.fill = GridBagConstraints.HORIZONTAL;
        gridConstraints.anchor = GridBagConstraints.NORTHWEST;
        gridConstraints.insets = new Insets(0, 0, 12, 0);
        mainTab.add(buildSettingsPanel(), gridConstraints);
        gridConstraints.gridy = 1;
        mainTab.add(buildGuidePanel(), gridConstraints);
        gridConstraints.gridy = 2;
        mainTab.add(buildStatsPanel(), gridConstraints);
        gridConstraints.gridy = 3;
        gridConstraints.weighty = 1.0;
        gridConstraints.insets = new Insets(0, 0, 0, 0);
        mainTab.add(new JPanel(), gridConstraints); // 占位撑满剩余高度，让上面的面板贴顶

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("主界面", mainTab);
        tabs.addTab("运行日志", buildLogPanel());
        setContentPane(tabs);
    }

    private JPanel buildSettingsPanel() {
        JPanel settingsPanel = new JPanel();
        settingsPanel.setLayout(new BoxLayout(settingsPanel, BoxLayout.Y_AXIS));
        settingsPanel.setBorder(BorderFactory.createTitledBorder("设置"));

        JPanel intervalCountRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        intervalCountRow.add(new JLabel("点击间隔（毫秒）："));
        intervalCountRow.add(intervalField);
        intervalCountRow.add(Box.createHorizontalStrut(16));
        intervalCountRow.add(new JLabel("连点次数（0=不限）："));
        intervalCountRow.add(countField);
        intervalCountRow.add(Box.createHorizontalStrut(16));
        intervalCountRow.add(new JLabel("鼠标按键："));
        intervalCountRow.add(mouseButtonBox);
        intervalCountRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel coordinateRow = new JPanel(new BorderLayout(8, 0));
        coordinateRow.add(new JLabel("点击坐标（留空=当前鼠标位置）："), BorderLayout.WEST);
        JPanel coordinateInputs = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        coordinateInputs.add(new JLabel("X："));
        coordinateInputs.add(coordinateXField);
        coordinateInputs.add(new JLabel("Y："));
        coordinateInputs.add(coordinateYField);
        coordinateInputs.add(Box.createHorizontalStrut(6));
        coordinateInputs.add(pickCoordinateButton);
        pickCoordinateButton.addActionListener(e -> beginPickCoordinate());
        coordinateRow.add(coordinateInputs, BorderLayout.CENTER);
        coordinateRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel countdownPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        countdownPanel.add(new JLabel("时"));
        countdownPanel.add(timerHoursField);
        countdownPanel.add(new JLabel("分"));
        countdownPanel.add(timerMinutesField);
        countdownPanel.add(new JLabel("秒"));
        countdownPanel.add(timerSecondsField);

        JPanel datetimePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        datetimePanel.add(timerDatePicker);
        datetimePanel.add(new JLabel("时"));
        datetimePanel.add(timerHourCombo);
        datetimePanel.add(new JLabel("分"));
        datetimePanel.add(timerMinuteCombo);
        datetimePanel.add(new JLabel("秒"));
        datetimePanel.add(timerSecondCombo);

        timerValueCards.add(countdownPanel, CARD_COUNTDOWN);
        timerValueCards.add(datetimePanel, CARD_DATETIME);

        JPanel timerRow = new JPanel(new BorderLayout(8, 0));
        timerRow.add(new JLabel("定时开始："), BorderLayout.WEST);
        JPanel timerInputs = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        timerInputs.add(timerModeBox);
        timerInputs.add(timerValueCards);
        timerInputs.add(timerButton);
        timerModeBox.addActionListener(e -> updateTimerCards());
        timerButton.addActionListener(e -> onTimerButton());
        timerRow.add(timerInputs, BorderLayout.CENTER);
        timerRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel keyboardShortcutRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        keyboardShortcutRow.add(startKeyboardShortcutField);
        keyboardShortcutRow.add(Box.createHorizontalStrut(16));
        keyboardShortcutRow.add(stopKeyboardShortcutField);
        keyboardShortcutRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton applyButton = new JButton("应用快捷键");
        applyButton.addActionListener(e -> applyKeyboardShortcuts());
        JButton resetButton = new JButton("恢复默认设置");
        resetButton.addActionListener(e -> resetToDefaults());
        JPanel settingButtonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        settingButtonRow.add(applyButton);
        settingButtonRow.add(Box.createHorizontalStrut(8));
        settingButtonRow.add(resetButton);
        settingButtonRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JButton startButton = new JButton("开始连点");
        JButton stopButton = new JButton("停止连点");
        startButton.addActionListener(e -> onStartKeyboardShortcut());
        stopButton.addActionListener(e -> onStopKeyboardShortcut());
        actionRow.add(startButton);
        actionRow.add(Box.createHorizontalStrut(8));
        actionRow.add(stopButton);
        actionRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        settingsPanel.add(intervalCountRow);
        settingsPanel.add(Box.createVerticalStrut(8));
        settingsPanel.add(coordinateRow);
        settingsPanel.add(Box.createVerticalStrut(8));
        settingsPanel.add(timerRow);
        settingsPanel.add(Box.createVerticalStrut(8));
        settingsPanel.add(keyboardShortcutRow);
        settingsPanel.add(Box.createVerticalStrut(10));
        settingsPanel.add(settingButtonRow);
        settingsPanel.add(Box.createVerticalStrut(10));
        settingsPanel.add(actionRow);
        return settingsPanel;
    }

    private JPanel buildGuidePanel() {
        JPanel guidePanel = new JPanel();
        guidePanel.setLayout(new BoxLayout(guidePanel, BoxLayout.Y_AXIS));
        guidePanel.setBorder(BorderFactory.createTitledBorder("使用步骤"));
        String[] steps = {
                "1. 按开始快捷键（默认 F1），或点击「开始连点」",
                "2. 按结束快捷键（默认 F2），或点击「停止连点」",
                "3. 可选：设置连点次数，达到次数后自动停止",
                "4. 可选：选择鼠标按键（左/中/右），默认左键",
                "5. 可选：点「拾取坐标」后在屏幕上单击选择位置，连点固定点击该处",
                "6. 可选：设置定时（倒计时或固定日期时间），到点后按当前设置自动开始连点",
                "7. 设置会在退出时自动保存，下次启动自动恢复",
                "8. 注意：目标程序若以管理员身份运行，本程序也需以管理员身份运行，否则快捷键和模拟点击对它无效",
        };
        for (String step : steps) {
            JLabel stepLabel = new JLabel(step);
            stepLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            guidePanel.add(stepLabel);
        }
        return guidePanel;
    }

    private JPanel buildStatsPanel() {
        JPanel statsPanel = new JPanel();
        statsPanel.setLayout(new BoxLayout(statsPanel, BoxLayout.Y_AXIS));
        statsPanel.setBorder(BorderFactory.createTitledBorder("运行统计"));
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statsPanel.add(statusLabel);
        statsPanel.add(Box.createVerticalStrut(6));
        statsPanel.add(statsLabel);
        return statsPanel;
    }

    private JPanel buildLogPanel() {
        JPanel logPanel = new JPanel(new BorderLayout(0, 6));
        logPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        JLabel logFileHint = new JLabel("日志同时写入程序目录 auto-clicker.log");
        logFileHint.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        logPanel.add(logFileHint, BorderLayout.NORTH);
        logPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        JButton openLogButton = new JButton("打开日志文件位置");
        openLogButton.addActionListener(e -> openLogDirectory());
        logPanel.add(openLogButton, BorderLayout.SOUTH);
        return logPanel;
    }

    private void openLogDirectory() {
        try {
            Desktop.getDesktop().open(OperateLogger.getLogFile().getParentFile());
        } catch (IOException ex) {
            OperateLogger.error("打开日志目录失败", ex);
            JOptionPane.showMessageDialog(this,
                    "日志文件路径：\n" + OperateLogger.getLogFile().getAbsolutePath(),
                    "日志位置",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /** 惰性创建快捷键管理器并（重新）安装钩子。 */
    private void ensureKeyboardShortcutManager() {
        if (keyboardShortcutManager == null) {
            keyboardShortcutManager = new KeyboardShortcutManager();
        }
        keyboardShortcutManager.install(this::onStartKeyboardShortcut, this::onStopKeyboardShortcut);
    }

    /** 初始化快捷键（由 MainFrameWindowListener 在窗口打开时调用）。 */
    void initKeyboardShortcuts() {
        OperateLogger.info("初始化快捷键");
        try {
            ensureKeyboardShortcutManager();
            applyKeyboardShortcuts();
        } catch (RuntimeException e) {
            OperateLogger.error("快捷键初始化失败", e);
            statusLabel.setText("状态：快捷键启动失败");
            JOptionPane.showMessageDialog(this, e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void applyKeyboardShortcuts() {
        OperateLogger.info("应用快捷键设置");
        try {
            ensureKeyboardShortcutManager();
        } catch (RuntimeException e) {
            OperateLogger.error("快捷键管理器启动失败", e);
            statusLabel.setText("状态：快捷键启动失败");
            JOptionPane.showMessageDialog(this, e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        KeyboardShortcutBinding start = startKeyboardShortcutField.getBinding();
        KeyboardShortcutBinding stop = stopKeyboardShortcutField.getBinding();
        if (start.equals(stop)) {
            JOptionPane.showMessageDialog(this, "开始与结束快捷键不能相同。", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        keyboardShortcutManager.unregisterAll();
        KeyboardShortcutManager.RegisterResult startResult =
                keyboardShortcutManager.register(KeyboardShortcutManager.KEYBOARD_SHORTCUT_ID_START, start);
        KeyboardShortcutManager.RegisterResult stopResult =
                keyboardShortcutManager.register(KeyboardShortcutManager.KEYBOARD_SHORTCUT_ID_STOP, stop);
        if (!startResult.isSuccess() || !stopResult.isSuccess()) {
            StringBuilder detail = new StringBuilder("快捷键注册失败：\n");
            if (!startResult.isSuccess()) {
                detail.append("开始键 ").append(start.toDisplayText())
                        .append(" - ").append(startResult.message()).append('\n');
            }
            if (!stopResult.isSuccess()) {
                detail.append("结束键 ").append(stop.toDisplayText())
                        .append(" - ").append(stopResult.message());
            }
            statusLabel.setText("状态：快捷键未生效");
            OperateLogger.warn("快捷键注册失败：" + detail.toString().trim().replace('\n', ' '));
            JOptionPane.showMessageDialog(this, detail.toString().trim(), "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        saveSettings();
        statusLabel.setText("状态：待命（快捷键已生效：" + start.toDisplayText() + " / " + stop.toDisplayText() + "）");
        OperateLogger.info("快捷键应用成功");
    }

    private void onStartKeyboardShortcut() {
        startClicking(true);
    }

    /** 开始连点；interactive=false 时（定时触发）不弹窗仅记日志，避免无人值守时被模态框卡住。 */
    private void startClicking(boolean interactive) {
        OperateLogger.info(interactive ? "收到开始连点请求" : "定时时间到，自动开始连点");
        if (clickService.isRunning()) {
            OperateLogger.debug("连点已在运行，忽略开始请求");
            return;
        }
        long interval = parseInterval();
        if (interval < 1) {
            failToStart(interactive, "请输入有效的点击间隔（毫秒）。", "点击间隔无效");
            return;
        }
        long maxClicks = parseMaxClicks();
        if (maxClicks < 0) {
            failToStart(interactive, "请输入有效的连点次数（0 表示不限）。", "连点次数无效");
            return;
        }
        Point target = parseCoordinate();
        if (target == INVALID_COORDINATE) {
            failToStart(interactive,
                    "请输入有效的点击坐标（X、Y 为整数，或全部留空表示点击当前鼠标位置）。",
                    "点击坐标无效");
            return;
        }
        MouseButton mouseButton = Objects.requireNonNullElse(
                (MouseButton) mouseButtonBox.getSelectedItem(), MouseButton.LEFT);
        clickService.start(interval, maxClicks, mouseButton, target);
        OperateLogger.info("连点已启动，间隔=" + interval + "ms，次数=" + (maxClicks > 0 ? maxClicks + " 次" : "不限")
                + "，按键=" + mouseButton.getDisplayName()
                + (target != null ? "，坐标=" + target.x + "," + target.y : "，坐标=当前鼠标位置"));
        statusLabel.setText("状态：连点中...");
        setExtendedState(Frame.ICONIFIED);
    }

    private void failToStart(boolean interactive, String dialogMessage, String briefReason) {
        OperateLogger.warn("连点未启动：" + briefReason);
        if (interactive) {
            restoreWindow();
            JOptionPane.showMessageDialog(this, dialogMessage, "错误", JOptionPane.ERROR_MESSAGE);
        } else {
            statusLabel.setText("状态：定时启动失败（" + briefReason + "）");
        }
    }

    private void onStopKeyboardShortcut() {
        OperateLogger.info("收到停止连点请求");
        if (!clickService.isRunning()) {
            OperateLogger.debug("当前未在连点，忽略停止请求");
            return;
        }
        clickService.stop();
        showStopSummary("状态：已停止（快捷键仍生效）", "上次统计：共点击 %d 次，持续 %d 秒");
    }

    /** 达到设定次数后自动停止（回调已切回 EDT）。 */
    private void onTargetReached() {
        showStopSummary("状态：已达到设定次数，自动停止（快捷键仍生效）",
                "上次统计：共点击 %d 次，持续 %d 秒（已达到设定次数）");
    }

    /** 连点线程异常退出后恢复界面（回调已切回 EDT）。 */
    private void onClickFailed(String reason) {
        if (clickService.isRunning()) {
            return; // 已重新开始连点，忽略过期的失败回调
        }
        OperateLogger.warn("连点异常结束：" + reason);
        statusLabel.setText("状态：连点失败（" + reason + "）");
        statsLabel.setText("上次统计：共点击 " + clickService.getClickCount() + " 次");
        restoreWindow();
    }

    /** 停止后统一刷新统计展示并恢复窗口。 */
    private void showStopSummary(String statusText, String statsText) {
        long clicks = clickService.getClickCount();
        long seconds = clickService.getDurationSeconds();
        OperateLogger.info("连点已停止，共点击 " + clicks + " 次，持续 " + seconds + " 秒");
        statusLabel.setText(statusText);
        statsLabel.setText(String.format(statsText, clicks, seconds));
        restoreWindow();
    }

    /** 进入坐标拾取模式：隐藏主窗口，屏幕上单击选择位置，Esc 或右键取消。 */
    private void beginPickCoordinate() {
        if (picking) {
            return;
        }
        picking = true;
        OperateLogger.info("进入坐标拾取模式：在屏幕上单击选择位置，Esc 或右键取消");
        statusLabel.setText("状态：坐标拾取中（单击屏幕选择位置，Esc 取消）...");
        setVisible(false); // 拾取期间隐藏主窗口，避免遮挡目标位置
        CoordinatePicker.pick(new CoordinatePickListener(this));
    }

    /** 拾取完成：回填坐标并恢复窗口。 */
    void onCoordinatePicked(Point point) {
        picking = false;
        coordinateXField.setText(String.valueOf(point.x));
        coordinateYField.setText(String.valueOf(point.y));
        OperateLogger.info("已拾取点击坐标：" + point.x + "," + point.y);
        statusLabel.setText("状态：已拾取坐标 " + point.x + "," + point.y + "（待命）");
        saveSettings();
        restoreWindow();
    }

    /** 拾取被取消：恢复窗口。 */
    void onCoordinatePickCancelled() {
        picking = false;
        OperateLogger.info("已取消坐标拾取");
        statusLabel.setText("状态：已取消坐标拾取（快捷键仍生效）");
        restoreWindow();
    }

    /** 按模式切换输入区：倒计时=时/分/秒三框，固定时间=日历+时/分/秒下拉。 */
    private void updateTimerCards() {
        CardLayout cards = (CardLayout) timerValueCards.getLayout();
        if (timerModeBox.getSelectedItem() == TimerMode.FIXED_TIME) {
            cards.show(timerValueCards, CARD_DATETIME);
        } else {
            cards.show(timerValueCards, CARD_COUNTDOWN);
        }
    }

    /** 启动/取消定时（按钮切换）。 */
    private void onTimerButton() {
        if (timerTick != null) {
            stopTimerTick();
            OperateLogger.info("定时已取消");
            statusLabel.setText("状态：待命（定时已取消）");
            return;
        }
        TimerMode mode = Objects.requireNonNullElse(
                (TimerMode) timerModeBox.getSelectedItem(), TimerMode.NONE);
        if (mode == TimerMode.NONE) {
            JOptionPane.showMessageDialog(this, "请先选择定时模式。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        long delayMs;
        if (mode == TimerMode.COUNTDOWN) {
            delayMs = resolveCountdownDelayMs();
            if (delayMs <= 0) {
                return;
            }
            timerTargetText = formatClockText(delayMs / 1000) + " 倒计时";
        } else {
            delayMs = resolveFixedTimeDelayMs();
            if (delayMs <= 0) {
                return;
            }
            timerTargetText = timerDatePicker.getDate() + " " + String.format("%02d:%02d:%02d",
                    selectedComboValue(timerHourCombo),
                    selectedComboValue(timerMinuteCombo),
                    selectedComboValue(timerSecondCombo));
        }
        timerFireAtMs = System.currentTimeMillis() + delayMs;
        timerTick = new Timer(TIMER_TICK_MS, e -> tickTimer());
        timerTick.setRepeats(true);
        timerTick.start();
        timerButton.setText("取消定时");
        OperateLogger.info("定时已启动：" + timerTargetText + "，到点自动开始连点");
        tickTimer();
    }

    /** 解析倒计时输入，非法时弹窗提示并返回 -1。 */
    private long resolveCountdownDelayMs() {
        Integer hours = parseBoundedInt(timerHoursField, 0, MAX_COUNTDOWN_HOURS);
        Integer minutes = parseBoundedInt(timerMinutesField, 0, 59);
        Integer seconds = parseBoundedInt(timerSecondsField, 0, 59);
        if (hours == null || minutes == null || seconds == null) {
            JOptionPane.showMessageDialog(this, "时/分/秒 需为有效数字（时 ≥ 0，分、秒 0-59）。",
                    "错误", JOptionPane.ERROR_MESSAGE);
            return -1;
        }
        long totalSeconds = hours * 3600L + minutes * 60L + seconds;
        if (totalSeconds <= 0) {
            JOptionPane.showMessageDialog(this, "倒计时必须大于 0。", "错误", JOptionPane.ERROR_MESSAGE);
            return -1;
        }
        return totalSeconds * 1000L;
    }

    /** 解析固定时间点输入，非法或已过期时弹窗提示并返回 -1。 */
    private long resolveFixedTimeDelayMs() {
        LocalDate date = timerDatePicker.getDate();
        if (date == null) {
            JOptionPane.showMessageDialog(this, "请选择目标日期。", "错误", JOptionPane.ERROR_MESSAGE);
            return -1;
        }
        LocalTime time = LocalTime.of(selectedComboValue(timerHourCombo),
                selectedComboValue(timerMinuteCombo),
                selectedComboValue(timerSecondCombo));
        long delayMs = LocalDateTime.of(date, time)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                - System.currentTimeMillis();
        if (delayMs <= 0) {
            JOptionPane.showMessageDialog(this, "设定时间已过去，请选择将来的时间点。",
                    "错误", JOptionPane.ERROR_MESSAGE);
            return -1;
        }
        return delayMs;
    }

    private void tickTimer() {
        long remainMs = timerFireAtMs - System.currentTimeMillis();
        if (remainMs <= 0) {
            stopTimerTick();
            startClicking(false);
            return;
        }
        if (!clickService.isRunning()) {
            statusLabel.setText("状态：定时中，剩余 " + formatRemaining(remainMs) + "（" + timerTargetText + "）...");
        }
    }

    private void stopTimerTick() {
        if (timerTick != null) {
            timerTick.stop();
            timerTick = null;
        }
        timerButton.setText("启动定时");
    }

    private static String formatRemaining(long remainMs) {
        long totalSeconds = (remainMs + 999) / 1000;
        if (totalSeconds / 3600 > 0) {
            return formatClockText(totalSeconds);
        }
        return String.format("%d:%02d", totalSeconds % 3600 / 60, totalSeconds % 60);
    }

    /** 把总秒数格式化为 h:mm:ss。 */
    private static String formatClockText(long totalSeconds) {
        return String.format("%d:%02d:%02d", totalSeconds / 3600, totalSeconds % 3600 / 60, totalSeconds % 60);
    }

    /** 解析单个时间输入框，超出 [min, max] 或非法返回 null。 */
    private static Integer parseBoundedInt(JTextField field, int min, int max) {
        try {
            int value = Integer.parseInt(field.getText().trim());
            return value >= min && value <= max ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 生成 0..size-1 的下拉框（时/分/秒选择用）。 */
    private static JComboBox<Integer> createTimeCombo(int size) {
        JComboBox<Integer> combo = new JComboBox<>();
        for (int i = 0; i < size; i++) {
            combo.addItem(i);
        }
        combo.setSelectedIndex(0);
        return combo;
    }

    private static JComboBox<TimerMode> createTimerModeCombo() {
        JComboBox<TimerMode> combo = new JComboBox<>();
        for (TimerMode mode : TimerMode.values()) {
            combo.addItem(mode);
        }
        combo.setSelectedItem(TimerMode.NONE);
        return combo;
    }

    private static int selectedComboValue(JComboBox<Integer> combo) {
        Object value = combo.getSelectedItem();
        return value instanceof Integer selected ? selected : 0;
    }

    /** 恢复全部设置为默认值：间隔、次数、按键、坐标、定时与快捷键。 */
    private void resetToDefaults() {
        applyLoadedSettings(DEFAULT_SETTINGS);
        if (timerTick != null) {
            stopTimerTick();
        }
        timerModeBox.setSelectedItem(TimerMode.NONE);
        timerHoursField.setText("0");
        timerMinutesField.setText("0");
        timerSecondsField.setText("30");
        timerDatePicker.setDate(LocalDate.now().plusDays(1));
        timerHourCombo.setSelectedIndex(0);
        timerMinuteCombo.setSelectedIndex(0);
        timerSecondCombo.setSelectedIndex(0);
        updateTimerCards();
        OperateLogger.info("已恢复默认设置");
        saveSettings();
        applyKeyboardShortcuts();
    }

    /** 从最小化/隐藏状态恢复窗口并置前。 */
    private void restoreWindow() {
        setExtendedState(Frame.NORMAL);
        setVisible(true);
        toFront();
        requestFocus();
    }

    /** 解析间隔输入（毫秒），非法返回 -1。 */
    private long parseInterval() {
        try {
            return Long.parseLong(intervalField.getText().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 解析连点次数：空或 0 表示不限，返回 -1 表示输入无效。 */
    private long parseMaxClicks() {
        String text = countField.getText().trim();
        if (text.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 解析点击坐标：全部留空返回 null（跟随当前鼠标），格式错误返回 INVALID_COORD。允许负值（多显示器场景）。 */
    private Point parseCoordinate() {
        String xText = coordinateXField.getText().trim();
        String yText = coordinateYField.getText().trim();
        if (xText.isEmpty() && yText.isEmpty()) {
            return null;
        }
        try {
            return new Point(Integer.parseInt(xText), Integer.parseInt(yText));
        } catch (NumberFormatException e) {
            return INVALID_COORDINATE;
        }
    }

    /** 把已保存的设置回填到界面控件。 */
    private void applyLoadedSettings(ClickerSettings settings) {
        intervalField.setText(Long.toString(settings.intervalMs()));
        countField.setText(Long.toString(settings.maxClicks()));
        mouseButtonBox.setSelectedItem(settings.mouseButton());
        coordinateXField.setText(settings.coordinateX() == null ? "" : Integer.toString(settings.coordinateX()));
        coordinateYField.setText(settings.coordinateY() == null ? "" : Integer.toString(settings.coordinateY()));
        startKeyboardShortcutField.setBinding(settings.startShortcut());
        stopKeyboardShortcutField.setBinding(settings.stopShortcut());
    }

    /** 从界面收集当前设置并持久化（非法输入按默认值落盘）。 */
    private void saveSettings() {
        long intervalMs = parseInterval();
        if (intervalMs < 1) {
            intervalMs = DEFAULT_INTERVAL_MS;
        }
        long maxClicks = parseMaxClicks();
        if (maxClicks < 0) {
            maxClicks = DEFAULT_MAX_CLICKS;
        }
        MouseButton mouseButton = Objects.requireNonNullElse(
                (MouseButton) mouseButtonBox.getSelectedItem(), MouseButton.LEFT);
        Point coordinate = parseCoordinate();
        boolean coordinateValid = coordinate != null && coordinate != INVALID_COORDINATE;
        ClickerSettings settings = new ClickerSettings(
                intervalMs,
                maxClicks,
                mouseButton,
                coordinateValid ? coordinate.x : null,
                coordinateValid ? coordinate.y : null,
                startKeyboardShortcutField.getBinding(),
                stopKeyboardShortcutField.getBinding());
        ClickerSettingsStore.save(settings);
    }

    /** 定时模式：下拉框选项顺序即展示顺序。 */
    private enum TimerMode {
        /** 不启用定时 */
        NONE("不启用"),
        /** 倒计时结束后开始连点 */
        COUNTDOWN("倒计时后开始"),
        /** 到达固定日期时间点后开始连点 */
        FIXED_TIME("固定时间点开始");

        private final String displayName;

        TimerMode(String displayName) {
            this.displayName = displayName;
        }

        /** JComboBox 直接显示中文名。 */
        @Override
        public String toString() {
            return displayName;
        }
    }
}
