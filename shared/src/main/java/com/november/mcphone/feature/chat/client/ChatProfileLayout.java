package com.november.mcphone.feature.chat.client;

import java.util.List;

/** 个人卡片与四个入口的不可变排版；绘制和点击复用同一份边界。 */
final class ChatProfileLayout {
    record Home(ChatLayout.Rect card, ChatLayout.Rect avatar, List<ChatLayout.Rect> rows,
                float textX, float nameCenterY, float introCenterY) {}

    private ChatProfileLayout() {}

    static Home home(ChatLayout.Rect area) {
        var card = new ChatLayout.Rect(area.x(), area.y(), area.width(), 38);
        var avatar = new ChatLayout.Rect(area.x() + 5, area.y() + 7, 24, 24);
        var rows = java.util.stream.IntStream.range(0, 4)
                .mapToObj(i -> new ChatLayout.Rect(area.x(), area.y() + 44 + i * 20, area.width(), 18))
                .toList();
        float centerY = card.y() + card.height() / 2f;
        return new Home(card, avatar, rows, area.x() + 35, centerY - 5.5f, centerY + 5.5f);
    }
}
