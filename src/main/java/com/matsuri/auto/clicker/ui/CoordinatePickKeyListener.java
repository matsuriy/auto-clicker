package com.matsuri.auto.clicker.ui;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * 坐标拾取遮罩按键监听：Esc 取消拾取。
 */
public class CoordinatePickKeyListener extends KeyAdapter {

    private final CoordinatePicker coordinatePicker;

    public CoordinatePickKeyListener(CoordinatePicker coordinatePicker) {
        this.coordinatePicker = coordinatePicker;
    }

    @Override
    public void keyPressed(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
            coordinatePicker.cancel();
        }
    }
}
