package com.matsuri.auto.clicker.click;

import java.awt.event.InputEvent;

/**
 * 可模拟点击的鼠标按键。
 */
public enum MouseButton {

    /** 鼠标左键 */
    LEFT("左键", InputEvent.BUTTON1_DOWN_MASK),
    /** 鼠标中键 */
    MIDDLE("中键", InputEvent.BUTTON2_DOWN_MASK),
    /** 鼠标右键 */
    RIGHT("右键", InputEvent.BUTTON3_DOWN_MASK);

    private final String displayName;
    private final int inputMask;

    MouseButton(String displayName, int inputMask) {
        this.displayName = displayName;
        this.inputMask = inputMask;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Robot.mousePress / mouseRelease 使用的按键掩码。 */
    public int getInputMask() {
        return inputMask;
    }

    /** JComboBox 直接显示中文名。 */
    @Override
    public String toString() {
        return displayName;
    }
}
