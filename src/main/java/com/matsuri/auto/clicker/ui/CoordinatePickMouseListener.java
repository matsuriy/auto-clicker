package com.matsuri.auto.clicker.ui;

import javax.swing.SwingUtilities;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 坐标拾取遮罩鼠标监听：左键选定坐标，其他按键取消拾取。
 */
public class CoordinatePickMouseListener extends MouseAdapter {

    private final CoordinatePicker coordinatePicker;

    public CoordinatePickMouseListener(CoordinatePicker coordinatePicker) {
        this.coordinatePicker = coordinatePicker;
    }

    @Override
    public void mouseClicked(MouseEvent event) {
        if (SwingUtilities.isLeftMouseButton(event)) {
            coordinatePicker.finish(event.getLocationOnScreen());
        } else {
            coordinatePicker.cancel();
        }
    }
}
