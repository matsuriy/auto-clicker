package com.matsuri.auto.clicker.ui;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * 坐标拾取遮罩焦点监听：遮罩窗口获得焦点后把焦点交给遮罩层，保证 Esc 监听生效。
 */
public class CoordinatePickFocusListener extends WindowAdapter {

    private final CoordinatePicker coordinatePicker;

    public CoordinatePickFocusListener(CoordinatePicker coordinatePicker) {
        this.coordinatePicker = coordinatePicker;
    }

    @Override
    public void windowGainedFocus(WindowEvent event) {
        coordinatePicker.focusOverlay();
    }
}
