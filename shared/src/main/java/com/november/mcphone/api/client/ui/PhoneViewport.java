package com.november.mcphone.api.client.ui;

/** 可保存的内容区布局；坐标与 PhoneCanvas 一致，scale 是手机到窗口 GUI 坐标的倍率。 */
public record PhoneViewport(int x, int y, int width, int height, double scale) {
    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }
}
