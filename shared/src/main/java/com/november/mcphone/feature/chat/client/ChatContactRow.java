package com.november.mcphone.feature.chat.client;

import com.november.mcphone.core.client.FontPalette;
import com.november.mcphone.core.client.PlayerAvatar;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 好友卡片的共用视图。普通联系人、建群和邀请使用同一布局，不持有选择或业务状态。 */
final class ChatContactRow {
    private ChatContactRow() {}
    record Layout(ChatLayout.Rect card, ChatLayout.Rect avatar, int textX, int textWidth,
                  float nameCenterY, float statusCenterY, ChatLayout.Rect statusDot, ChatLayout.Rect choice) {}

    static Layout layout(int x, int y, int width, int height, boolean selectable) {
        var card = new ChatLayout.Rect(x, y, width, height - 1);
        float centerY = y + card.height() / 2f;
        var avatar = new ChatLayout.Rect(x + 2, y + (card.height() - ChatLayout.AVATAR_SIZE) / 2,
                ChatLayout.AVATAR_SIZE, ChatLayout.AVATAR_SIZE);
        var choice = selectable ? new ChatLayout.Rect(x + width - 11, y + (card.height() - 8) / 2, 8, 8) : null;
        int textX = x + ChatLayout.AVATAR_SIZE + 5;
        int textEnd = choice == null ? x + width - 3 : choice.x() - 4;
        return new Layout(card, avatar, textX, Math.max(1, textEnd - textX), centerY - 3.5f,
                centerY + 4, new ChatLayout.Rect(textX, (int) (centerY + 3), 2, 2), choice);
    }

    static void render(GuiGraphics g, Font font, ChatContactDirectory.Contact contact,
                       int x, int y, int width, int height, boolean hovered, boolean selectable, boolean selected) {
        var row = layout(x, y, width, height, selectable);
        var card = row.card();
        ChatUi.glass(g, ChatGlass.Surface.CARD, card.x(), card.y(), card.width(), card.height(), hovered || selected);
        var avatar = row.avatar(); PlayerAvatar.draw(g, contact.id(), avatar.x(), avatar.y(), avatar.width());
        ChatUi.label(g, font, ChatUi.truncate(font, contact.name(), row.textWidth(), ChatLayout.TEXT_SCALE),
                row.textX(), row.nameCenterY(), ChatLayout.TEXT_SCALE, FontPalette.title());
        var dot = row.statusDot();
        g.fill(dot.x(), dot.y(), dot.x() + dot.width(), dot.y() + dot.height(),
                contact.online() ? PlayerAvatar.COLOR_ONLINE : PlayerAvatar.COLOR_OFFLINE);
        ChatUi.label(g, font, ChatUi.truncate(font, Component.translatable(contact.online()
                ? "mcphone.chat.contacts.online" : "mcphone.chat.contacts.offline").getString(),
                Math.max(1, row.textWidth() - 4), ChatLayout.META_SCALE),
                row.textX() + 4, row.statusCenterY(), ChatLayout.META_SCALE, FontPalette.subtle());
        if (selectable) {
            var choice = row.choice();
            ChatUi.glass(g, ChatGlass.Surface.BUTTON, choice.x(), choice.y(), choice.width(), choice.height(), selected);
            if (selected) ChatUi.sizedIcon(g, ChatUi.Icon.CHECK, choice.x() + 1, choice.y() + 1, 6, ChatUi.accent());
        }
    }
}
