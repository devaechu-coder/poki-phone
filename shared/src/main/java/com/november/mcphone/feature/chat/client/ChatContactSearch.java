package com.november.mcphone.feature.chat.client;

import com.november.mcphone.core.client.FontPalette;
import com.november.mcphone.core.client.GuiUtil;
import com.november.mcphone.core.client.PhoneSkin;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** 搜索、群名称与个人介绍复用的单行输入；没有业务数据、网络请求或页面跳转。 */
final class ChatContactSearch {
    private final String hint;
    private final int maxLength;
    ChatContactSearch() { this("mcphone.chat.layout.search", 64); }
    ChatContactSearch(String hint, int maxLength) { this.hint = hint; this.maxLength = maxLength; }
    private EditBox box;
    private String initialValue = "";
    private ChatLayout.Rect bounds;
    private float originY;
    private int originX;
    record Content(int textX, float inputY, float hintY, float iconY, int inputWidth) {}
    static Content layout(ChatLayout.Rect area, int lineHeight, int cursorWidth, boolean searchIcon) {
        int inset = searchIcon ? ChatLayout.SEARCH_TEXT_X : ChatLayout.SEARCH_PAD_X;
        float centerY = area.y() + area.height() / 2f;
        return new Content(area.x() + inset,
                ChatLayout.labelTop(lineHeight, ChatLayout.TEXT_SCALE, centerY),
                ChatLayout.labelTop(lineHeight, ChatLayout.META_SCALE, centerY),
                centerY - ChatLayout.SEARCH_ICON_SIZE / 2f,
                Math.max(1, ChatLayout.unscaledWidth(area.width() - inset - 3, ChatLayout.TEXT_SCALE) - cursorWidth));
    }
    String query() { return box == null ? initialValue : box.getValue(); }
    void value(String value) {
        initialValue = value.length() > maxLength ? value.substring(0, maxLength) : value;
        if (box != null) box.setValue(initialValue);
    }
    boolean focused() { return box != null && box.isFocused(); }
    void close() { bounds = null; if (box != null) box.setFocused(false); }
    void reset() { close(); value(""); }
    void render(GuiGraphics g, Font font, ChatLayout.Rect area, int mx, int my, float tick) {
        bounds = area;
        ChatUi.inputBar(g, area.x(), area.y(), area.width(), area.height());
        boolean searchIcon = hint.equals("mcphone.chat.layout.search");
        Content content = layout(area, font.lineHeight, font.width("_"), searchIcon);
        if (searchIcon) ChatUi.sizedIcon(g, ChatUi.Icon.SEARCH, area.x() + ChatLayout.SEARCH_PAD_X,
                content.iconY(), ChatLayout.SEARCH_ICON_SIZE, FontPalette.dim());
        originX = content.textX(); originY = content.inputY();
        int width = content.inputWidth();
        if (box == null) {
            box = new EditBox(font, 0, 0, width, font.lineHeight,
                    Component.translatable(hint));
            box.setBordered(false); box.setMaxLength(maxLength);
            box.setValue(initialValue);
        } else if (box.getWidth() != width) {
            box.setWidth(width); box.setCursorPosition(box.getCursorPosition());
        }
        int color = PhoneSkin.textColor(PhoneSkin.Element.CHAT_INPUT_BAR, 0xFFE0E0E0);
        box.setTextColor(color); box.setTextColorUneditable(color);
        GuiUtil.enableScissor(g, area.x(), area.y(), area.x() + area.width(), area.y() + area.height());
        g.pose().pushPose();
        try {
            g.pose().translate(originX, originY, 0);
            g.pose().scale(ChatLayout.TEXT_SCALE, ChatLayout.TEXT_SCALE, 1);
            box.render(g, (int) ((mx - originX) / ChatLayout.TEXT_SCALE),
                    (int) ((my - originY) / ChatLayout.TEXT_SCALE), tick);
        } finally { g.pose().popPose(); GuiUtil.disableScissor(g); }
        if (query().isEmpty() && !focused()) {
            ChatUi.text(g, font, ChatUi.truncate(font,
                    Component.translatable(hint).getString(), area.x() + area.width() - originX - 3,
                    ChatLayout.META_SCALE), originX,
                    content.hintY(),
                    ChatLayout.META_SCALE, FontPalette.dim());
        }
    }
    boolean mouseClicked(double mx, double my, int button) {
        if (box == null) return false;
        boolean inside = bounds != null && bounds.contains(mx, my);
        if (button == 0) {
            box.setFocused(inside);
            if (inside) box.mouseClicked((mx - originX) / ChatLayout.TEXT_SCALE,
                    Mth.clamp((my - originY) / ChatLayout.TEXT_SCALE, 0, box.getHeight() - 1), button);
        }
        return inside;
    }
    boolean keyPressed(int key, int scan, int modifiers) {
        if (!focused()) return false;
        box.keyPressed(key, scan, modifiers); return true;
    }
    boolean charTyped(char c, int modifiers) { return focused() && box.charTyped(c, modifiers); }
}
