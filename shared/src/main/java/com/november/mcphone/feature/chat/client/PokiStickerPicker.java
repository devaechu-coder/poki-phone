package com.november.mcphone.feature.chat.client;

import com.november.mcphone.core.client.FontPalette;
import com.november.mcphone.core.client.GuiUtil;
import com.november.mcphone.core.client.PhoneTheme;
import com.november.mcphone.feature.chat.PokiSticker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 포키냥 이모티콘 고르기 (4칸 격자, 스크롤) */
public final class PokiStickerPicker {
    private static final int PAD = 6, COLS = 4, GAP = 3;
    /** 고르기 칸 순서: 글자 있는 것 먼저 (1~8, 18~32), 그다음 그림만 (9~17) */
    private static final int[] ORDER = order();
    private static int[] order() {
        java.util.List<Integer> l = new java.util.ArrayList<>();
        for (int i = 1; i <= 8; i++) l.add(i);
        for (int i = 18; i <= PokiSticker.COUNT; i++) l.add(i);
        for (int i = 9; i <= 17; i++) l.add(i);
        return l.stream().mapToInt(Integer::intValue).toArray();
    }
    private int selected = -1, hovered = -1;
    private double scroll;
    private int contentH, viewH;

    public void open() { selected = -1; scroll = 0; }
    public void close() { selected = -1; hovered = -1; }

    /** 고른 번호 (1~), 없으면 -1 */
    public int consumeSelection() { int s = selected; selected = -1; return s; }

    public void render(GuiGraphics g, int phoneLeft, int phoneTop, int screenW, int screenH, int statusH, int navH,
                       int mx, int my, Font font) {
        int x = phoneLeft + PAD, y = phoneTop + statusH + 4, w = screenW - PAD * 2;
        g.drawString(font, GuiUtil.truncate(font, Component.translatable("mcphone.chat.poki_sticker_title").getString(), w), x, y, FontPalette.title(), true);
        y += font.lineHeight + 4;
        g.fill(x, y, x + w, y + 1, PhoneTheme.COLOR_DIVIDER);
        y += 4;
        int bottom = phoneTop + screenH - navH - 2;
        viewH = bottom - y;
        int cell = (w - GAP * (COLS - 1)) / COLS;
        int rows = (ORDER.length + COLS - 1) / COLS;
        contentH = rows * (cell + GAP);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - viewH)));
        hovered = -1;
        ChatImageCache.beginFrame(null);
        GuiUtil.enableScissor(g, x, y, x + w, bottom);
        try {
            for (int i = 0; i < ORDER.length; i++) {
                int n = ORDER[i];
                int cx = x + (i % COLS) * (cell + GAP);
                int cy = (int) (y + (i / COLS) * (cell + GAP) - scroll);
                if (cy + cell < y || cy > bottom) continue;
                boolean hov = mx >= cx && mx < cx + cell && my >= cy && my < cy + cell && my >= y && my < bottom;
                if (hov) { hovered = n; g.fill(cx, cy, cx + cell, cy + cell, 0x30FFFFFF); }
                var id = PokiStickers.id(n);
                var tex = ChatImageCache.get(id);
                if (tex != null) ChatImageViewer.drawImage(g, tex, 1, 0, cx + 1, cy + 1, cell - 2, cell - 2);
            }
        } finally { GuiUtil.disableScissor(g); }
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        if (hovered > 0) { selected = hovered; return true; }
        return false;
    }

    public boolean mouseScrolled(double scrollY) {
        scroll -= scrollY * 16;
        if (scroll < 0) scroll = 0;
        return true;
    }
}
