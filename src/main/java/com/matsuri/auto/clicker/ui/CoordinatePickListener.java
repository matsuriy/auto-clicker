package com.matsuri.auto.clicker.ui;

import java.awt.Point;

/**
 * 坐标拾取结果监听：把拾取/取消结果回传给主窗口处理。
 */
public class CoordinatePickListener implements CoordinatePicker.Listener {

    private final AutoClickerMainFrame mainFrame;

    public CoordinatePickListener(AutoClickerMainFrame mainFrame) {
        this.mainFrame = mainFrame;
    }

    @Override
    public void onPicked(Point point) {
        mainFrame.onCoordinatePicked(point);
    }

    @Override
    public void onCancelled() {
        mainFrame.onCoordinatePickCancelled();
    }
}
