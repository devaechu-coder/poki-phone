package com.november.mcphone.feature.chat.client;

import com.november.mcphone.core.client.FontPalette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 消息与联系人共用底栏。发现、个人页只预留，不产生导航。 */
final class ChatNavigationBar {
    enum Page { CHATS(0), CONTACTS(1), PROFILE(3);
        final int column; Page(int column) { this.column=column; }
    }
    private static final String[] KEYS = {"chats", "contacts", "discover", "profile"};
    private static final ChatUi.Icon[] ICONS = {
            ChatUi.Icon.CHAT, ChatUi.Icon.CONTACTS, ChatUi.Icon.DISCOVER, ChatUi.Icon.PROFILE};
    private ChatLayout.Rect bounds;
    void clear() { bounds = null; }
    Page click(double mx, double my, int button) {
        if (button != 0 || bounds == null || !bounds.contains(mx, my)) return null;
        int column = 0;
        while (column < 3 && mx >= bounds.x() + bounds.width() * (column + 1) / 4) column++;
        return switch(column) { case 0 -> Page.CHATS;case 1 -> Page.CONTACTS;case 3 -> Page.PROFILE;default -> null; };
    }
    void render(GuiGraphics g, Font font, int x, int y, int w, Page active) {
        bounds = new ChatLayout.Rect(x, y, w, ChatLayout.TAB_HEIGHT);
        ChatUi.glass(g, ChatGlass.Surface.NAVIGATION, x, y, w, ChatLayout.TAB_HEIGHT, false);
        for (int i = 0; i < KEYS.length; i++) {
            int left = x + w * i / 4, right = x + w * (i + 1) / 4;
            float centerX = (left + right) / 2f;
            int color = i == active.column ? ChatUi.accent()
                    : (i != 2 ? FontPalette.body() : FontPalette.dim());
            ChatUi.centeredIcon(g, ICONS[i], centerX, y + ChatLayout.TAB_ICON_TOP, color);
            String label = ChatUi.truncate(font,
                    Component.translatable("mcphone.chat.layout." + KEYS[i]).getString(),
                    right - left - 2, ChatLayout.TAB_LABEL_SCALE);
            ChatUi.buttonText(g, font, label, centerX, y + ChatLayout.TAB_LABEL_TOP + 2,
                    ChatLayout.TAB_LABEL_SCALE, color);
            if (i == active.column) {
                int width = Math.min(16, right - left - 4);
                int underlineX = Math.round(centerX - width / 2f);
                g.fill(underlineX, y + ChatLayout.TAB_HEIGHT - 1,
                        underlineX + width, y + ChatLayout.TAB_HEIGHT, color);
            }
        }
    }
}
