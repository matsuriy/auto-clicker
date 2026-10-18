package com.matsuri.auto.clicker.ui;

import com.matsuri.auto.clicker.shortcut.KeyboardShortcutBinding;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.KeyboardFocusManager;
import java.awt.KeyEventDispatcher;
import java.awt.event.KeyEvent;
import java.util.function.Predicate;

/**
 * 快捷键设置控件：点击按钮后按下组合键即可录入。
 *
 * <p>录入期间通过 {@link KeyboardFocusManager} 注册临时
 * {@link KeyEventDispatcher} 全局捕获按键，避免依赖组件焦点，
 * 也能获得完整的修饰键状态（{@link KeyEvent#getModifiersEx()}）。</p>
 */
public class KeyboardShortcutField extends JPanel {

    private static final String CAPTURE_PROMPT = "请按下快捷键（Esc 取消）";

    private final JTextField displayField = new JTextField(12);
    private final JButton captureButton = new JButton("设置");

    private KeyboardShortcutBinding binding;
    /** 正在使用的全局派发器；非 null 表示处于录入状态。 */
    private CaptureDispatcher dispatcher;

    public KeyboardShortcutField(String label, KeyboardShortcutBinding defaultBinding) {
        super(new BorderLayout(6, 0));
        this.binding = defaultBinding;

        displayField.setEditable(false);
        displayField.setFocusable(false);          // 避免抢焦点
        displayField.setText(binding.toDisplayText());
        displayField.setToolTipText(binding.toDisplayText());

        JLabel nameLabel = new JLabel(label);
        add(nameLabel, BorderLayout.WEST);
        add(displayField, BorderLayout.CENTER);
        add(captureButton, BorderLayout.EAST);

        captureButton.addActionListener(e -> beginCapture());

        // 窗口失去激活时自动取消录入，避免卡在「按键...」状态
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0
                    && !isShowing()) {
                cancelCapture();
            }
        });
    }

    // ------------------------------------------------------------------
    // 录入状态
    // ------------------------------------------------------------------

    private void beginCapture() {
        if (dispatcher != null) {           // 已在录入，忽略重复点击
            return;
        }
        dispatcher = new CaptureDispatcher(this::handleCapturedKey);
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(dispatcher);

        capturingState(true);
        displayField.setText(CAPTURE_PROMPT);
        requestFocusInWindow();
    }

    private void cancelCapture() {
        if (dispatcher == null) {
            return;
        }
        stopCapture();
        displayField.setText(binding.toDisplayText());
        displayField.setToolTipText(binding.toDisplayText());
    }

    private void finishCapture(KeyboardShortcutBinding newBinding) {
        if (dispatcher == null) {
            return;
        }
        stopCapture();
        binding = newBinding;
        displayField.setText(binding.toDisplayText());
        displayField.setToolTipText(binding.toDisplayText());
    }

    private void stopCapture() {
        if (dispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .removeKeyEventDispatcher(dispatcher);
            dispatcher = null;
        }
        capturingState(false);
    }

    private void capturingState(boolean capturing) {
        captureButton.setText(capturing ? "按键..." : "设置");
    }

    // ------------------------------------------------------------------
    // 按键处理
    // ------------------------------------------------------------------

    /** 由全局派发器在录入期间调用。返回 true 表示事件已被消费。 */
    private boolean handleCapturedKey(KeyEvent event) {
        if (event.getID() != KeyEvent.KEY_PRESSED) {
            // 按下事件决定结果；松开与敲击事件一并吞掉，避免漏到其他组件
            return true;
        }
        if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
            cancelCapture();
            return true;
        }
        if (isModifierOnly(event.getKeyCode())) {
            // 仅按下修饰键：继续等待主键，但吞掉事件避免副作用
            return true;
        }
        finishCapture(KeyboardShortcutBinding.fromKeyEvent(event));
        return true;
    }

    private static boolean isModifierOnly(int keyCode) {
        return switch (keyCode) {
            case KeyEvent.VK_CONTROL, KeyEvent.VK_ALT, KeyEvent.VK_SHIFT, KeyEvent.VK_META -> true;
            default -> false;
        };
    }

    // ------------------------------------------------------------------
    // 公开 API
    // ------------------------------------------------------------------

    public KeyboardShortcutBinding getBinding() {
        return binding;
    }

    public void setBinding(KeyboardShortcutBinding binding) {
        this.binding = binding;
        displayField.setText(binding.toDisplayText());
        displayField.setToolTipText(binding.toDisplayText());
    }

    // ------------------------------------------------------------------
    // 全局按键派发器
    // ------------------------------------------------------------------

    /**
     * 录入期间临时注册的 {@link KeyEventDispatcher}。
     * 因为它在 {@link KeyboardFocusManager} 链路中先于组件焦点链执行，
     * 所以能稳定拿到含完整修饰键状态的 KEY_PRESSED 事件。
     */
    private record CaptureDispatcher(Predicate<KeyEvent> handler) implements KeyEventDispatcher {

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            return handler.test(event);
        }
    }
}
