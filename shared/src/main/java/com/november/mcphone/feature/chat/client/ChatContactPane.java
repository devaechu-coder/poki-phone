package com.november.mcphone.feature.chat.client;

import com.november.mcphone.core.client.FontPalette;
import com.november.mcphone.core.client.GuiUtil;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 方形头像、分组行和字母索引的视图。点击按实际坐标命中，不依赖上一帧悬停。 */
final class ChatContactPane {
    @FunctionalInterface interface RowPainter {
        void draw(GuiGraphics g, Font font, ChatContactDirectory.Contact contact, int x, int y, int width, int height, boolean hovered);
    }
    private final ChatContactScroll scroll = new ChatContactScroll();
    private ChatLayout.Rect bounds;
    private List<ChatContactDirectory.Section> sections = List.of();
    private int previousHeight = -1;
    void reset() { scroll.reset(); }
    void close() { bounds = null; sections = List.of(); scroll.clear(); previousHeight = -1; }

    void render(GuiGraphics g, Font font, List<ChatContactDirectory.Section> next,
                ChatLayout.Rect area, int mx, int my, String emptyKey) {
        render(g, font, next, area, mx, my, emptyKey, ChatContactPane::renderContact);
    }
    void render(GuiGraphics g, Font font, List<ChatContactDirectory.Section> next,
                ChatLayout.Rect area, int mx, int my, String emptyKey, RowPainter painter) {
        bounds = area;
        if (sections != next || previousHeight != area.height()) {
            sections = next; previousHeight = area.height(); scroll.update(next, area.height());
        }
        if (area.height() <= 0) return;
        if (next.isEmpty()) {
            int y = area.y() + 3;
            for (var line : font.split(Component.translatable(emptyKey),
                    ChatLayout.unscaledWidth(area.width() - 6, ChatLayout.TEXT_SCALE))) {
                if (y + ChatUi.lineHeight(font, ChatLayout.TEXT_SCALE) > area.y() + area.height()) break;
                ChatUi.text(g, font, line, area.x() + 3, y, ChatLayout.TEXT_SCALE, FontPalette.subtle());
                y += ChatUi.lineHeight(font, ChatLayout.TEXT_SCALE) + 1;
            }
            return;
        }
        int width = area.width() - 9;
        GuiUtil.enableScissor(g, area.x(), area.y(), area.x() + width, area.y() + area.height());
        try {
            for (var row : scroll.rows()) {
                int y = area.y() + row.top() - scroll.offset();
                if (y + row.height() <= area.y()) continue;
                if (y >= area.y() + area.height()) break;
                if (row.heading()) {
                    ChatUi.text(g, font, row.letter(), area.x() + 3, y + 1,
                            ChatLayout.META_SCALE, ChatUi.accent());
                } else {
                    painter.draw(g, font, row.contact(), area.x(), y, width, row.height(),
                            area.contains(mx, my) && mx < area.x() + width && my >= y && my < y + row.height());
                }
            }
        } finally { GuiUtil.disableScissor(g); }
        float step = area.height() / (float) ChatContactDirectory.LETTERS.length();
        float scale = Math.min(ChatLayout.TAB_LABEL_SCALE, step / font.lineHeight);
        for (int i = 0; i < ChatContactDirectory.LETTERS.length(); i++) {
            String letter = ChatContactDirectory.LETTERS.substring(i, i + 1);
            boolean present = next.stream().anyMatch(s -> s.letter().equals(letter));
            int color = !present ? FontPalette.dim()
                    : (letter.equals(scroll.firstLetter()) ? ChatUi.accent() : FontPalette.body());
            ChatUi.buttonText(g, font, letter, area.x() + area.width() - 4,
                    area.y() + (i + .5f) * step, scale, color);
        }
    }

    static void renderContact(GuiGraphics g, Font font, ChatContactDirectory.Contact contact,
                                      int x, int y, int width, int height, boolean hovered) {
        ChatContactRow.render(g, font, contact, x, y, width, height, hovered, false, false);
    }

    UUID click(double mx, double my, int button) {
        if (button != 0 || bounds == null || !bounds.contains(mx, my)) return null;
        if (mx >= bounds.x() + bounds.width() - 8) {
            int i = Math.min(ChatContactDirectory.LETTERS.length() - 1,
                    (int) ((my - bounds.y()) * ChatContactDirectory.LETTERS.length() / bounds.height()));
            scroll.jump(ChatContactDirectory.LETTERS.substring(i, i + 1), bounds.height());
            return null;
        }
        int contentY = (int) (my - bounds.y()) + scroll.offset();
        for (var row : scroll.rows()) {
            if (contentY >= row.top() && contentY < row.top() + row.height())
                return row.heading() ? null : row.contact().id();
        }
        return null;
    }
    UUID avatarAt(double mx,double my,int button) {
        if (button!=0 || bounds==null || !bounds.contains(mx,my)) return null;
        for (var row:scroll.rows()) {
            if (row.heading()) continue;
            int y=bounds.y()+row.top()-scroll.offset();
            var avatar=ChatContactRow.layout(bounds.x(),y,bounds.width()-9,row.height(),false).avatar();
            if (avatar.contains(mx,my)) return row.contact().id();
        }
        return null;
    }
    boolean mouseScrolled(double mx, double my, double amount) {
        return bounds != null && bounds.contains(mx, my) && scroll.scroll(amount, bounds.height());
    }
}
