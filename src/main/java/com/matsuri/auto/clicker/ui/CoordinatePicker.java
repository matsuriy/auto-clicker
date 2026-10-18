package com.matsuri.auto.clicker.ui;

import javax.swing.JWindow;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;

/**
 * 全屏坐标拾取：半透明遮罩覆盖所有显示器，十字光标单击选择位置，Esc 或右键取消。
 * 同一时刻只允许一个拾取会话。
 */
final class CoordinatePicker {

    interface Listener {
        void onPicked(Point point);

        void onCancelled();
    }

    private static CoordinatePicker active;

    private final JWindow overlay;
    private final Listener listener;
    private JPanel veil;

    private CoordinatePicker(Listener listener) {
        this.listener = listener;
        this.overlay = createOverlay();
    }

    /** 开始拾取；已有拾取会话时忽略。 */
    static void pick(Listener listener) {
        if (active != null) {
            return;
        }
        active = new CoordinatePicker(listener);
        active.overlay.setVisible(true);
        active.overlay.requestFocus();
    }

    private JWindow createOverlay() {
        Rectangle bounds = new Rectangle();
        for (GraphicsDevice graphicsDevice : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            for (GraphicsConfiguration graphicsConfiguration : graphicsDevice.getConfigurations()) {
                bounds = bounds.union(graphicsConfiguration.getBounds());
            }
        }

        JWindow window = new JWindow();
        window.setAlwaysOnTop(true);
        window.setBounds(bounds);
        window.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

        veil = new JPanel(new BorderLayout());
        try {
            window.setBackground(new Color(0, 0, 0, 0)); // 逐像素半透明，遮罩呈淡暗效果
            veil.setBackground(new Color(0, 0, 0, 40));
        } catch (UnsupportedOperationException ex) {
            veil.setBackground(new Color(230, 230, 230)); // 不支持半透明时退回不透明浅色
        }

        JLabel hint = new JLabel("单击选择点击坐标，Esc 或右键取消", SwingConstants.CENTER);
        hint.setFont(hint.getFont().deriveFont(Font.BOLD, 18f));
        hint.setOpaque(true);
        hint.setBackground(new Color(0, 0, 0, 160));
        hint.setForeground(Color.WHITE);
        veil.add(hint, BorderLayout.NORTH);
        window.setContentPane(veil);

        veil.addMouseListener(new CoordinatePickMouseListener(this));
        veil.setFocusable(true);
        veil.addKeyListener(new CoordinatePickKeyListener(this));
        window.addWindowFocusListener(new CoordinatePickFocusListener(this));
        return window;
    }

    /** 遮罩窗口获得焦点后把焦点交给遮罩层，保证 Esc 监听生效。 */
    void focusOverlay() {
        veil.requestFocusInWindow();
    }

    void finish(Point point) {
        close();
        listener.onPicked(point);
    }

    void cancel() {
        close();
        listener.onCancelled();
    }

    private void close() {
        overlay.dispose();
        active = null;
    }
}
