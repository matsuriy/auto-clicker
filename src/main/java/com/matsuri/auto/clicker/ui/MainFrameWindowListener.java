package com.matsuri.auto.clicker.ui;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * 主窗口生命周期监听：打开时初始化快捷键，关闭时释放资源。
 */
public class MainFrameWindowListener extends WindowAdapter {

    private final AutoClickerMainFrame mainFrame;

    public MainFrameWindowListener(AutoClickerMainFrame mainFrame) {
        this.mainFrame = mainFrame;
    }

    @Override
    public void windowOpened(WindowEvent event) {
        mainFrame.initKeyboardShortcuts();
    }

    @Override
    public void windowClosing(WindowEvent event) {
        mainFrame.onWindowClosing();
    }
}
