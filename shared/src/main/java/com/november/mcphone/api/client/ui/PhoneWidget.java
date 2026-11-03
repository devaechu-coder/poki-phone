package com.november.mcphone.api.client.ui;

/** PhoneWidgetPage 的可选子控件；可包装原版或第三方控件，所有坐标沿用页面坐标系。 */
public interface PhoneWidget extends IPhonePage {
    boolean contains(double mouseX, double mouseY);
    default boolean focusable() { return true; }
    default void onFocusChanged(boolean focused) {}
}
