package com.november.mcphone.feature.chat.client;

import com.november.mcphone.core.client.FontPalette;
import com.november.mcphone.core.client.GuiUtil;
import com.november.mcphone.core.net.MCphoneNetwork;
import com.november.mcphone.feature.gym.net.GymRequestPacket;
import com.november.mcphone.feature.gym.net.GymWire;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 포키 체육관 앱. 목록(첫 화면) → 체육관 상세.
 * 공략(추천 타입·관장 포켓몬)은 일부러 안 보여 준다. 규칙(레벨 제한)과 내 기록만.
 */
public final class GymPage {
    private static UUID request;
    private static long deadline;
    private static GymWire.Reply reply;
    private static String error = "";
    private static int detail = -1;
    private static double scroll;
    private static int contentH, viewH;
    private record Hit(ChatLayout.Rect r, Runnable run) {}
    private static final List<Hit> HITS = new ArrayList<>();

    private static final int GOLD = 0xFFE8B323, GREEN = 0xFF4F9A5A, ORANGE = 0xFFC8820A, LOCK = 0xFFAAA4B4;

    private static String tr(String k) { return Component.translatable("mcphone.gym." + k).getString(); }

    public static void open() {
        detail = -1; scroll = 0; error = ""; reply = null;
        refresh();
    }

    public static void close() { request = null; reply = null; detail = -1; HITS.clear(); }

    private static void refresh() {
        request = UUID.randomUUID();
        deadline = System.currentTimeMillis() + 6000;
        try { MCphoneNetwork.sendToServer(new GymRequestPacket(request)); }
        catch (RuntimeException e) { request = null; error = tr("error"); }
    }

    public static void accept(GymWire.Reply r) {
        if (request == null || !request.equals(r.request())) return;
        request = null;
        if (r.status() == GymWire.Status.OK) { reply = r; error = r.gyms().isEmpty() ? tr("empty") : ""; }
        else error = tr("status." + r.status().name().toLowerCase(java.util.Locale.ROOT));
    }

    /** 뒤로: 상세면 목록으로. false 면 홈으로 */
    public static boolean back() {
        if (detail >= 0) { detail = -1; scroll = 0; return true; }
        return false;
    }

    static int typeColor(String type) {
        return switch (type) {
            case "바위" -> 0xFFB8A06E; case "물" -> 0xFF5AA0F0; case "전기" -> 0xFFF5C828; case "풀" -> 0xFF6EBE5A;
            case "독" -> 0xFFAA64C8; case "에스퍼" -> 0xFFF56496; case "불꽃" -> 0xFFF0783C; case "땅" -> 0xFFC89650;
            case "얼음" -> 0xFF78C8E6; case "격투" -> 0xFFC0504A; case "비행" -> 0xFF96A8F0; case "벌레" -> 0xFFA0B432;
            case "고스트" -> 0xFF705898; case "드래곤" -> 0xFF7050F0; case "악" -> 0xFF705848; case "강철" -> 0xFFA8A8C0;
            case "페어리" -> 0xFFEE99AC; default -> 0xFFA8A0B0;
        };
    }

    private static void circle(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.floor(Math.sqrt((double) r * r - dy * dy) + 0.3);
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    /** 타입별 포키 배지 그림 (textures/gym/badge_N.png, 64×64). 모르는 타입이면 0 */
    private static int badgeIndex(String type) {
        return switch (type) {
            case "바위" -> 1; case "물" -> 2; case "전기" -> 3; case "풀" -> 4;
            case "독" -> 5; case "에스퍼" -> 6; case "불꽃" -> 7; case "땅" -> 8; default -> 0;
        };
    }

    /** 배지 그림이 있으면 그걸로 (안 깬 건 흐리게), 없으면 번호 동그라미 */
    private static void badge(GuiGraphics g, Font font, String type, int cx, int cy, int r, int color, boolean on, int num) {
        int idx = badgeIndex(type);
        if (idx == 0) { medal(g, font, cx, cy, r, color, on, num); return; }
        var tex = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.november.mcphone.MCphone.MODID, "textures/gym/badge_" + idx + ".png");
        int size = r * 2 + 2;
        if (!on) g.setColor(0.55f, 0.55f, 0.6f, 0.45f);
        try {
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            GuiUtil.drawTexture(g, tex, cx - size / 2, cy - size / 2, size, size, 64, 64);
        } finally { g.setColor(1f, 1f, 1f, 1f); }
    }

    private static void medal(GuiGraphics g, Font font, int cx, int cy, int r, int color, boolean on, int num) {
        circle(g, cx, cy, r, on ? 0xFFFFFFFF : 0xFFD7D2DE);
        circle(g, cx, cy, r - 1, on ? color : 0xFFE8E4EE);
        ChatUi.buttonText(g, font, on ? String.valueOf(num) : "?", cx + 0.5f, cy + 0.5f, ChatLayout.META_SCALE, on ? 0xFFFFFFFF : LOCK);
    }

    /** 다음에 도전할 체육관 번호 (모두 깼으면 -1) */
    private static int next(List<GymWire.Gym> gyms) {
        for (int i = 0; i < gyms.size(); i++) if (!gyms.get(i).cleared()) return i;
        return -1;
    }

    public static void render(GuiGraphics g, int left, int top, int sw, int sh, int statusH, int navH, int mx, int my, Font font) {
        HITS.clear();
        if (request != null && System.currentTimeMillis() > deadline) { request = null; error = tr("timeout"); }
        int x = left + 4, w = sw - 8, y = top + statusH + ChatLayout.TOP_GAP;
        int bottom = top + sh - navH - 2;
        List<GymWire.Gym> gyms = reply == null ? List.of() : reply.gyms();
        boolean inDetail = detail >= 0 && detail < gyms.size();

        if (inDetail) {
            var back = new ChatLayout.Rect(x, y, 12, ChatLayout.HEADER_HEIGHT);
            ChatUi.sizedIcon(g, ChatUi.Icon.BACK, x + 2.5f, y + 2.5f, 7, back.contains(mx, my) ? ChatUi.accent() : FontPalette.body());
            HITS.add(new Hit(back, GymPage::back));
        }
        String title = inDetail ? Component.translatable("mcphone.gym.detail_title", gyms.get(detail).name()).getString() : tr("title");
        ChatUi.buttonText(g, font, title, x + w / 2f, y + ChatLayout.HEADER_HEIGHT / 2f, ChatLayout.TEXT_SCALE, FontPalette.title());
        if (!inDetail && !gyms.isEmpty()) {
            long got = gyms.stream().filter(GymWire.Gym::cleared).count();
            String badge = Component.translatable("mcphone.gym.badges", got, gyms.size()).getString();
            int bw = ChatUi.width(font, badge, ChatLayout.META_SCALE);
            ChatUi.label(g, font, badge, x + w - bw - 2, y + ChatLayout.HEADER_HEIGHT / 2f, ChatLayout.META_SCALE, ChatUi.accent());
        }
        y += ChatLayout.HEADER_HEIGHT + 4;

        if (gyms.isEmpty()) {
            String msg = request != null ? tr("loading") : error.isEmpty() ? tr("empty") : error;
            int ly = y + 10;
            for (var line : font.split(Component.literal(msg), ChatLayout.unscaledWidth(w - 8, ChatLayout.META_SCALE))) {
                ChatUi.text(g, font, line, x + 4, ly, ChatLayout.META_SCALE, FontPalette.subtle()); ly += 8;
            }
            return;
        }
        viewH = bottom - y;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - viewH)));
        GuiUtil.enableScissor(g, x, y, x + w, bottom);
        try {
            int used = inDetail ? detail(g, font, gyms, x, (int) (y - scroll), w) - (int) (y - scroll)
                    : list(g, font, gyms, x, (int) (y - scroll), w, y, bottom, mx, my) - (int) (y - scroll);
            contentH = used;
        } finally { GuiUtil.disableScissor(g); }
    }

    private static int list(GuiGraphics g, Font font, List<GymWire.Gym> gyms, int x, int y, int w, int top, int bottom, int mx, int my) {
        int nx = next(gyms);
        for (int i = 0; i < gyms.size(); i++) {
            var gym = gyms.get(i);
            boolean open = gym.cleared() || i == nx;
            var row = new ChatLayout.Rect(x, y, w, 22);
            if (y + 22 >= top && y <= bottom) {
                boolean hover = row.contains(mx, my) && my >= top && my < bottom;
                if (i == nx) ChatUi.roundedFill(g, x, y, w, 22, GOLD);
                ChatUi.glass(g, ChatGlass.Surface.CARD, x + (i == nx ? 1 : 0), y + (i == nx ? 1 : 0), w - (i == nx ? 2 : 0), 22 - (i == nx ? 2 : 0), hover);
                int col = typeColor(gym.type());
                badge(g, font, gym.type(), x + 11, y + 11, 8, col, gym.cleared(), i + 1);
                ChatUi.label(g, font, ChatUi.truncate(font, gym.name(), w - 70, ChatLayout.TEXT_SCALE), x + 22, y + 7, ChatLayout.TEXT_SCALE,
                        open ? FontPalette.title() : FontPalette.subtle());
                String meta = (gym.type().isEmpty() ? "" : gym.type() + " · ") + "Lv." + gym.cap();
                ChatUi.label(g, font, meta, x + 22, y + 16, ChatLayout.META_SCALE, open ? col : LOCK);
                String st = gym.cleared() ? tr("cleared") : i == nx ? tr("next") : tr("locked");
                int sc = gym.cleared() ? GREEN : i == nx ? ORANGE : LOCK;
                int stw = ChatUi.width(font, st, ChatLayout.META_SCALE);
                ChatUi.label(g, font, st, x + w - stw - 4, y + 11, ChatLayout.META_SCALE, sc);
                if (my >= top && my < bottom) { int idx = i; HITS.add(new Hit(row, () -> { detail = idx; scroll = 0; })); }
            }
            y += 25;
        }
        return y;
    }

    private static int detail(GuiGraphics g, Font font, List<GymWire.Gym> gyms, int x, int y, int w) {
        var gym = gyms.get(detail);
        int nx = next(gyms);
        int col = typeColor(gym.type());
        ChatUi.roundedFill(g, x, y, w, 36, col);
        circle(g, x + 17, y + 18, 13, 0xFFFFFFFF);
        if (badgeIndex(gym.type()) > 0) badge(g, font, gym.type(), x + 17, y + 18, 11, col, gym.cleared(), detail + 1);
        else ChatUi.buttonText(g, font, String.valueOf(detail + 1), x + 17.5f, y + 18.5f, ChatLayout.TEXT_SCALE, col);
        ChatUi.label(g, font, ChatUi.truncate(font, Component.translatable("mcphone.gym.leader", gym.name()).getString(), w - 40, ChatLayout.TEXT_SCALE),
                x + 34, y + 9, ChatLayout.TEXT_SCALE, 0xFFFFFFFF);
        String sub = (gym.type().isEmpty() ? "" : Component.translatable("mcphone.gym.type", gym.type()).getString() + " · ")
                + Component.translatable("mcphone.gym.order", detail + 1).getString();
        ChatUi.label(g, font, ChatUi.truncate(font, sub, w - 40, ChatLayout.META_SCALE), x + 34, y + 19, ChatLayout.META_SCALE, 0xFFFFFFFF);
        String st = gym.cleared() ? "✓ " + tr("cleared") + (gym.firstWin() > 0 ? " · " + new SimpleDateFormat("MM/dd").format(new Date(gym.firstWin())) : "")
                : detail == nx ? tr("next") : tr("locked");
        ChatUi.label(g, font, st, x + 34, y + 28, ChatLayout.META_SCALE, 0xFFFFFFFF);
        y += 42;
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{tr("cap"), "Lv." + gym.cap()});
        if (gym.attempts() > 0)
            rows.add(new String[]{tr("record"), Component.translatable("mcphone.gym.record_value", gym.attempts(), gym.wins(), gym.losses()).getString()});
        if (gym.bestMs() > 0) {
            long s = gym.bestMs() / 1000;
            rows.add(new String[]{tr("best"), Component.translatable("mcphone.gym.time", s / 60, s % 60).getString()});
        }
        for (String[] r : rows) {
            ChatUi.label(g, font, r[0], x + 3, y + 5, ChatLayout.META_SCALE, FontPalette.subtle());
            int vw = ChatUi.width(font, r[1], ChatLayout.TEXT_SCALE);
            ChatUi.label(g, font, r[1], x + w - vw - 3, y + 5, ChatLayout.TEXT_SCALE, FontPalette.title());
            g.fill(x + 2, y + 11, x + w - 2, y + 12, 0x22000000);
            y += 14;
        }
        y += 4;
        var lines = font.split(Component.literal(tr("rule")), ChatLayout.unscaledWidth(w - 10, ChatLayout.META_SCALE));
        int boxH = 12 + lines.size() * 8;
        ChatUi.glass(g, ChatGlass.Surface.CARD, x, y, w, boxH, false);
        ChatUi.text(g, font, tr("rule_title"), x + 5, y + 3, ChatLayout.META_SCALE, ChatUi.accent());
        int ly = y + 11;
        for (var line : lines) { ChatUi.text(g, font, line, x + 5, ly, ChatLayout.META_SCALE, FontPalette.body()); ly += 8; }
        return y + boxH + 4;
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
