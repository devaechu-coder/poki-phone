package com.november.mcphone.feature.chat.client;

import com.november.mcphone.feature.chat.ChatQuote;
import com.november.mcphone.core.client.FontPalette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 预览和消息气泡共用引用的两行摘要，绝不修改可复制/可选择的正文。 */
final class ChatReplyView {
    static final int HEIGHT = 18, DRAFT_HEIGHT = 21;
    private ChatReplyView() {}
    static String summary(ChatQuote quote) {
        return quote.image() ? Component.translatable("mcphone.chat.reply.image").getString()
                : quote.text().replace('\n',' ').replace('\r',' ');
    }
    static void draw(GuiGraphics g, Font font, ChatQuote quote, int x, int y, int width) {
        if (width < 8) return;
        g.fill(x,y+2,x+1,y+HEIGHT-2,ChatUi.accent());
        ChatUi.label(g,font,ChatUi.truncate(font,quote.name(),width-5,ChatLayout.GROUP_NAME_SCALE),
                x+4,y+5,ChatLayout.GROUP_NAME_SCALE,FontPalette.subtle());
        ChatUi.label(g,font,ChatUi.truncate(font,summary(quote),width-5,ChatLayout.META_SCALE),
                x+4,y+12,ChatLayout.META_SCALE,FontPalette.subtle());
    }
}
