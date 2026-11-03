package com.november.mcphone.feature.chat.client.contextmenu;

import com.november.mcphone.core.client.FontPalette;
import com.november.mcphone.core.client.GuiUtil;
import com.november.mcphone.feature.chat.client.ChatUi;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.List;

/** 聊天首页、消息和输入框共用的菜单视图；只负责显示与命中，不持有业务数据或网络。 */
public final class ChatContextMenuView {
    public enum Icon { COPY, DELETE, PASTE, CREATE_GROUP, ADD_CONTACT, FAVORITE, REPLY }
    public record Entry(String labelKey, Icon icon, boolean enabled) {}
    @FunctionalInterface public interface PanelPainter { void paint(GuiGraphics g, int x, int y, int width, int height); }
    private static final float SCALE = .75f;
    // 深色菜单叠约 18% 灰白；浅色菜单轻微压暗，保留原面板的透明层次。
    private static final int HOVER_FILL = 0x2EFFFFFF;
    private static final int HOVER_FILL_ON_LIGHT = 0x18000000;
    private boolean open;
    private double anchorX, anchorY;
    private ChatContextMenuLayout.Layout layout;
    private List<Entry> entries = List.of();

    public boolean isOpen() { return open; }
    public void open(double x, double y) { open = true; anchorX = x; anchorY = y; layout = null; entries = List.of(); }
    public boolean dismiss() { boolean wasOpen = open; open = false; layout = null; entries = List.of(); return wasOpen; }
    public int hitIndex(double x, double y) {
        int row = !open || layout == null ? -1 : layout.rowAt(x, y);
        return row < 0 || !entries.get(row).enabled() ? -1 : row;
    }
    void arrange(ChatContextMenuLayout.Bounds viewport, int labelWidth, int textHeight, List<Entry> entries) {
        this.entries = List.copyOf(entries);
        layout = ChatContextMenuLayout.place(anchorX, anchorY, viewport, labelWidth, textHeight, entries.size());
    }
    public void render(GuiGraphics g, Font font, int x, int y, int width, int height, int mx, int my,
                       List<Entry> entries, PanelPainter painter) {
        if (!open || entries.isEmpty()) return;
        int labelWidth = 0;
        for (var entry : entries) labelWidth = Math.max(labelWidth,
                (int) Math.ceil(font.width(Component.translatable(entry.labelKey())) * SCALE));
        arrange(new ChatContextMenuLayout.Bounds(x,y,width,height), labelWidth,
                (int) Math.ceil(font.lineHeight * SCALE), entries);
        var bounds = layout.bounds();
        painter.paint(g, bounds.x(), bounds.y(), bounds.width(), bounds.height());
        float inkHeight = Math.max(1, font.lineHeight - 2) * SCALE;
        int hoveredIndex = hitIndex(mx, my);
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            int rowY = bounds.y() + ChatContextMenuLayout.PAD + i * layout.rowHeight();
            boolean hovered = hoveredIndex == i;
            if (hovered) {
                int fill = FontPalette.current().darkText() ? HOVER_FILL_ON_LIGHT : HOVER_FILL;
                ChatUi.roundedFill(g,bounds.x()+2,rowY,bounds.width()-4,layout.rowHeight(),fill);
            }
            int color = !entry.enabled() ? FontPalette.subtle()
                    : entry.icon() == Icon.DELETE ? FontPalette.danger()
                    : hovered ? FontPalette.title() : FontPalette.body();
            float centerY = rowY + layout.rowHeight() / 2f;
            if (entry.icon() != null) ChatContextMenuIcon.draw(g,entry.icon(),layout.iconX(),centerY,color);
            int available = bounds.x()+bounds.width()-ChatContextMenuLayout.PAD-layout.textX();
            String label = GuiUtil.truncate(font,Component.translatable(entry.labelKey()).getString(),
                    Math.max(0,(int)(available/SCALE)));
            g.pose().pushPose();
            try {
                // 与图标共用行中心；字体底部两像素空白不参与视觉居中。
                g.pose().translate(layout.textX(),centerY-inkHeight/2f,0);
                g.pose().scale(SCALE,SCALE,1);
                g.drawString(font,label,0,0,color,false);
            } finally { g.pose().popPose(); }
        }
    }
}
