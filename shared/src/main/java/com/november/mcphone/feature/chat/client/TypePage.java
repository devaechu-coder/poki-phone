package com.november.mcphone.feature.chat.client;

import com.november.mcphone.core.client.FontPalette;
import com.november.mcphone.core.client.GuiUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 타입 상성표 (6세대 이후 표 = 코블몬과 같음).
 * 공격: 기술 타입 하나 → 누구한테 잘 듣나 / 방어: 포켓몬 타입 1~2개 → 뭐에 약하고 강한가.
 */
public final class TypePage {
    static final String[] KO = {"노말", "불꽃", "물", "풀", "전기", "얼음", "격투", "독", "땅", "비행", "에스퍼", "벌레", "바위", "고스트", "드래곤", "악", "강철", "페어리"};
    static final int[] COLOR = {0xA8A77A, 0xEE8130, 0x6390F0, 0x7AC74C, 0xF7D02C, 0x96D9D6, 0xC22E28, 0xA33EA1, 0xE2BF65,
            0xA98FF3, 0xF95587, 0xA6B91A, 0xB6A136, 0x735797, 0x6F35FC, 0x705746, 0xB7B7CE, 0xD685AD};
    private static final int NOR = 0, FIR = 1, WAT = 2, GRA = 3, ELE = 4, ICE = 5, FIG = 6, POI = 7, GRO = 8,
            FLY = 9, PSY = 10, BUG = 11, ROC = 12, GHO = 13, DRA = 14, DAR = 15, STE = 16, FAI = 17;
    /** M[공격][방어] = 배율×2 (0, 1=½, 2=1, 4=2) */
    static final int[][] M = new int[18][18];
    static {
        for (int[] r : M) java.util.Arrays.fill(r, 2);
        set(NOR, 1, ROC, STE); set(NOR, 0, GHO);
        set(FIR, 4, GRA, ICE, BUG, STE); set(FIR, 1, FIR, WAT, ROC, DRA);
        set(WAT, 4, FIR, GRO, ROC); set(WAT, 1, WAT, GRA, DRA);
        set(GRA, 4, WAT, GRO, ROC); set(GRA, 1, FIR, GRA, POI, FLY, BUG, DRA, STE);
        set(ELE, 4, WAT, FLY); set(ELE, 1, ELE, GRA, DRA); set(ELE, 0, GRO);
        set(ICE, 4, GRA, GRO, FLY, DRA); set(ICE, 1, FIR, WAT, ICE, STE);
        set(FIG, 4, NOR, ICE, ROC, DAR, STE); set(FIG, 1, POI, FLY, PSY, BUG, FAI); set(FIG, 0, GHO);
        set(POI, 4, GRA, FAI); set(POI, 1, POI, GRO, ROC, GHO); set(POI, 0, STE);
        set(GRO, 4, FIR, ELE, POI, ROC, STE); set(GRO, 1, GRA, BUG); set(GRO, 0, FLY);
        set(FLY, 4, GRA, FIG, BUG); set(FLY, 1, ELE, ROC, STE);
        set(PSY, 4, FIG, POI); set(PSY, 1, PSY, STE); set(PSY, 0, DAR);
        set(BUG, 4, GRA, PSY, DAR); set(BUG, 1, FIR, FIG, POI, FLY, GHO, STE, FAI);
        set(ROC, 4, FIR, ICE, FLY, BUG); set(ROC, 1, FIG, GRO, STE);
        set(GHO, 4, PSY, GHO); set(GHO, 1, DAR); set(GHO, 0, NOR);
        set(DRA, 4, DRA); set(DRA, 1, STE); set(DRA, 0, FAI);
        set(DAR, 4, PSY, GHO); set(DAR, 1, FIG, DAR, FAI);
        set(STE, 4, ICE, ROC, FAI); set(STE, 1, FIR, WAT, ELE, STE);
        set(FAI, 4, FIG, DRA, DAR); set(FAI, 1, FIR, POI, STE);
    }
    private static void set(int atk, int v, int... defs) { for (int d : defs) M[atk][d] = v; }

    private static boolean defend;
    /** 공격 모드는 하나, 방어 모드는 최대 둘 */
    private static final List<Integer> PICK = new ArrayList<>();
    private static double scroll;
    private static int contentH, viewH;
    private record Hit(ChatLayout.Rect r, Runnable run) {}
    private static final List<Hit> HITS = new ArrayList<>();

    private static String tr(String k) { return Component.translatable("mcphone.types." + k).getString(); }

    public static void open() { scroll = 0; }
    public static void close() { HITS.clear(); }

    private static void pick(int t) {
        scroll = 0;
        if (PICK.contains(t)) { PICK.remove((Integer) t); return; }
        if (!defend) PICK.clear();
        else if (PICK.size() >= 2) PICK.remove(0);
        PICK.add(t);
    }

    public static void render(GuiGraphics g, int left, int top, int sw, int sh, int statusH, int navH, int mx, int my, Font font) {
        HITS.clear();
        int x = left + 4, w = sw - 8, y = top + statusH + ChatLayout.TOP_GAP;
        int bottom = top + sh - navH - 2;
        ChatUi.buttonText(g, font, tr("title"), x + w / 2f, y + ChatLayout.HEADER_HEIGHT / 2f, ChatLayout.TEXT_SCALE, FontPalette.title());
        y += ChatLayout.HEADER_HEIGHT + 3;

        int hw = w / 2;
        var a = new ChatLayout.Rect(x, y, hw - 1, 11);
        var b = new ChatLayout.Rect(x + hw, y, w - hw, 11);
        seg(g, font, a, tr("attack"), !defend, mx, my);
        seg(g, font, b, tr("defend"), defend, mx, my);
        HITS.add(new Hit(a, () -> { if (defend) { defend = false; if (PICK.size() > 1) PICK.subList(0, PICK.size() - 1).clear(); scroll = 0; } }));
        HITS.add(new Hit(b, () -> { if (!defend) { defend = true; scroll = 0; } }));
        y += 14;

        // 18타입 고르기 (6×3)
        int cols = 6, gap = 2, cw = (w - gap * (cols - 1)) / cols, ch = 11;
        for (int i = 0; i < 18; i++) {
            int cx = x + (i % cols) * (cw + gap), cy = y + (i / cols) * (ch + gap);
            var r = new ChatLayout.Rect(cx, cy, cw, ch);
            boolean on = PICK.contains(i);
            chip(g, font, i, cx, cy, cw, ch, on || PICK.isEmpty() || r.contains(mx, my) ? 1f : 0.45f, on);
            int t = i;
            HITS.add(new Hit(r, () -> pick(t)));
        }
        y += 3 * (ch + gap) + 3;

        int listTop = y;
        viewH = bottom - listTop;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - viewH)));
        GuiUtil.enableScissor(g, x, listTop, x + w, bottom);
        try {
            int cy = (int) (listTop - scroll);
            if (PICK.isEmpty()) {
                ChatUi.label(g, font, tr(defend ? "hint_defend" : "hint_attack"), x + 2, cy + 6, ChatLayout.META_SCALE, FontPalette.subtle());
                contentH = 12;
                return;
            }
            // 배율×4 단위로 모음 (방어 두 타입 곱: 0,1,2,4,8,16 → 0, ¼, ½, 1, 2, 4)
            int[] mult = new int[18];
            String[] heads; int[] keys;
            if (!defend) {
                int atk = PICK.get(0);
                for (int d = 0; d < 18; d++) mult[d] = M[atk][d];
                keys = new int[]{4, 1, 0};
                heads = new String[]{tr("atk_super"), tr("atk_weak"), tr("atk_none")};
            } else {
                for (int at = 0; at < 18; at++) {
                    int v = 1;
                    for (int d : PICK) v *= M[at][d];
                    mult[at] = PICK.size() == 1 ? v * 2 : v; // ×4 단위로 맞춤
                }
                keys = new int[]{16, 8, 2, 1, 0};
                heads = new String[]{tr("def_x4"), tr("def_x2"), tr("def_half"), tr("def_quarter"), tr("def_none")};
            }
            int start = cy;
            for (int k = 0; k < keys.length; k++) {
                List<Integer> list = new ArrayList<>();
                for (int t = 0; t < 18; t++) if (mult[t] == keys[k]) list.add(t);
                if (list.isEmpty()) continue;
                int color = keys[k] > (defend ? 4 : 2) ? 0xFFE0565B : keys[k] == 0 ? FontPalette.subtle() : 0xFF4FA8E0;
                if (!defend) color = keys[k] == 4 ? 0xFF4FB86A : keys[k] == 0 ? FontPalette.subtle() : 0xFFE0565B;
                ChatUi.label(g, font, heads[k], x + 2, cy + 5, ChatLayout.META_SCALE, color);
                cy += 11;
                for (int j = 0; j < list.size(); j++) {
                    int cx = x + (j % cols) * (cw + gap);
                    chip(g, font, list.get(j), cx, cy, cw, ch, 1f, false);
                    if (j % cols == cols - 1 && j != list.size() - 1) cy += ch + gap;
                }
                cy += ch + 5;
            }
            if (defend) {
                ChatUi.label(g, font, tr("def_note"), x + 2, cy + 4, ChatLayout.META_SCALE, FontPalette.subtle());
                cy += 10;
            }
            contentH = cy - start;
        } finally { GuiUtil.disableScissor(g); }
    }

    private static void chip(GuiGraphics g, Font font, int t, int x, int y, int w, int h, float alpha, boolean selected) {
        int rgb = COLOR[t];
        int a = (int) (alpha * 255) << 24;
        if (selected) ChatUi.roundedFill(g, x - 1, y - 1, w + 2, h + 2, 0xFFFFFFFF);
        ChatUi.roundedFill(g, x, y, w, h, a | rgb);
        int r = rgb >> 16 & 255, gg = rgb >> 8 & 255, b = rgb & 255;
        boolean light = (r * 299 + gg * 587 + b * 114) / 1000 > 170;
        int text = (light ? 0x2A2A2A : 0xFFFFFF) | (alpha < 1f ? 0xB0000000 : 0xFF000000);
        float scale = KO[t].length() >= 3 ? 0.5f : ChatLayout.META_SCALE;
        ChatUi.buttonText(g, font, KO[t], x + w / 2f, y + h / 2f, scale, text);
    }

    private static void seg(GuiGraphics g, Font font, ChatLayout.Rect r, String text, boolean on, int mx, int my) {
        if (on) ChatUi.roundedFill(g, r.x(), r.y(), r.width(), r.height(), ChatUi.accent());
        else ChatUi.glass(g, ChatGlass.Surface.CARD, r.x(), r.y(), r.width(), r.height(), r.contains(mx, my));
        ChatUi.buttonText(g, font, text, r.x() + r.width() / 2f, r.y() + r.height() / 2f, ChatLayout.META_SCALE, on ? 0xFFFFFFFF : FontPalette.body());
    }

    public static boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return true;
        for (int i = HITS.size() - 1; i >= 0; i--) if (HITS.get(i).r().contains(mx, my)) { HITS.get(i).run().run(); return true; }
        return true;
    }

    public static boolean mouseScrolled(double amount) {
        scroll -= amount * 14;
        if (scroll < 0) scroll = 0;
        return true;
    }
}
