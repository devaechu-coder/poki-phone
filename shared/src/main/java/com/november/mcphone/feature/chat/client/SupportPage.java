package com.november.mcphone.feature.chat.client;

import com.november.mcphone.core.client.FontPalette;
import com.november.mcphone.core.client.GuiUtil;
import com.november.mcphone.core.net.MCphoneNetwork;
import com.november.mcphone.feature.support.net.SupportRequestPacket;
import com.november.mcphone.feature.support.net.SupportWire;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 포키 고객센터. 문의·신고 보내기, 내 문의 내역(운영진 답변), 운영진 문의함.
 * 실제 문의는 PokiHelp 플러그인에 저장 — /문의, /문의함 과 같은 목록.
 */
public final class SupportPage {
    private enum Page { HOME, ASK, REPORT, PICK, MINE, BOX, DETAIL }

    private static final String WIKI = "https://poki.devaechu.com";
    private static final String[] ASK_KINDS = {"BUG", "QUESTION", "OTHER"};
    private static final String[] REASONS = {"abuse", "manner", "spam", "scam", "exploit", "other"};
    private static final int RED = 0xFFFF5A6E, GREEN = 0xFF7BE0A6, YELLOW = 0xFFFFD36B, GRAY = 0xFFA9A4CF;

    private static Page page = Page.HOME;
    private static final List<Page> stack = new ArrayList<>();
    private static UUID request;
    private static String notice = "";
    private static boolean noticeBad;
    private static boolean staff;
    private static int unseen, open;
    private static List<SupportWire.Ticket> mine = List.of(), box = List.of();
    private static List<SupportWire.PlayerRow> players = List.of();
    private static boolean boxDone;
    private static int detailId = -1;

    private static String askKind = "QUESTION";
    private static int reason = -1;
    private static SupportWire.PlayerRow target;
    private static final ChatContactSearch askBox = new ChatContactSearch("mcphone.support.ask_hint", 200);
    private static final ChatContactSearch reportBox = new ChatContactSearch("mcphone.support.report_hint", 200);
    private static final ChatContactSearch replyBox = new ChatContactSearch("mcphone.support.reply_hint", 220);

    private static double scroll;
    private static int contentH, viewH;
    private record Hit(ChatLayout.Rect r, Runnable run) {}
    private static final List<Hit> HITS = new ArrayList<>();

    private SupportPage() {}

    private static String tr(String k, Object... a) { return Component.translatable("mcphone.support." + k, a).getString(); }

    // ───────── 열기·닫기·통신 ─────────

    public static void open() {
        page = Page.HOME; stack.clear(); scroll = 0; notice = ""; target = null; reason = -1;
        askBox.reset(); reportBox.reset(); replyBox.reset();
        send(SupportWire.Query.of(SupportWire.Kind.STATE));
    }

    public static void close() {
        request = null; HITS.clear();
        askBox.close(); reportBox.close(); replyBox.close();
    }

    private static void send(SupportWire.Query q) {
        request = q.request();
        try { MCphoneNetwork.sendToServer(new SupportRequestPacket(q)); }
        catch (RuntimeException e) { request = null; say(tr("status.error"), true); }
    }

    private static void say(String s, boolean bad) { notice = s; noticeBad = bad; }

    public static void accept(SupportWire.Reply r) {
        if (request == null || !request.equals(r.request())) return;
        request = null;
        if (r.status() != SupportWire.Status.OK) {
            String m = r.message().isEmpty() ? tr("status." + r.status().name().toLowerCase(Locale.ROOT)) : r.message();
            say(m, true);
            return;
        }
        staff = r.staff(); unseen = r.unseen(); open = r.open();
        switch (r.kind()) {
            case SUBMIT -> {
                say(tr("sent", r.message()), false);
                askBox.reset(); reportBox.reset(); target = null; reason = -1;
                go(Page.MINE, false);
                send(SupportWire.Query.of(SupportWire.Kind.MINE));
            }
            case MINE -> {
                mine = r.tickets();
                if (unseen > 0) send(SupportWire.Query.of(SupportWire.Kind.SEEN));
            }
            case BOX -> box = r.tickets();
            case ACT -> { box = r.tickets(); say(tr("done"), false); replyBox.reset(); }
            case PLAYERS -> players = r.players();
            default -> { }
        }
    }

    private static void go(Page p, boolean push) {
        if (push) stack.add(page);
        page = p; scroll = 0;
        askBox.close(); reportBox.close(); replyBox.close();
    }

    /** 뒤로: 앞 화면으로. false 면 홈으로 */
    public static boolean back() {
        if (stack.isEmpty()) return false;
        page = stack.remove(stack.size() - 1); scroll = 0; notice = "";
        askBox.close(); reportBox.close(); replyBox.close();
        return true;
    }

    // ───────── 그리기 ─────────

    public static void render(GuiGraphics g, int left, int top, int sw, int sh, int statusH, int navH, int mx, int my, Font font) {
        HITS.clear();
        int x = left + 4, w = sw - 8, y = top + statusH + ChatLayout.TOP_GAP;
        int bottom = top + sh - navH - 2;

        if (page != Page.HOME) {
            var back = new ChatLayout.Rect(x, y, 12, ChatLayout.HEADER_HEIGHT);
            ChatUi.sizedIcon(g, ChatUi.Icon.BACK, x + 2.5f, y + 2.5f, 7, back.contains(mx, my) ? ChatUi.accent() : FontPalette.body());
            HITS.add(new Hit(back, () -> { if (!back()) go(Page.HOME, false); }));
        }
        ChatUi.buttonText(g, font, tr("title." + page.name().toLowerCase(Locale.ROOT)), x + w / 2f, y + ChatLayout.HEADER_HEIGHT / 2f,
                ChatLayout.TEXT_SCALE, FontPalette.title());
        y += ChatLayout.HEADER_HEIGHT + 3;

        // 알림 줄
        if (!notice.isEmpty()) {
            for (FormattedCharSequence l : wrap(font, notice, w - 4, ChatLayout.META_SCALE)) {
                ChatUi.text(g, font, l, x + 2, y, ChatLayout.META_SCALE, noticeBad ? RED : GREEN);
                y += ChatUi.lineHeight(font, ChatLayout.META_SCALE) + 1;
            }
            y += 2;
        }

        switch (page) {
            case HOME -> home(g, font, x, y, w, bottom, mx, my);
            case ASK -> ask(g, font, x, y, w, bottom, mx, my);
            case REPORT -> report(g, font, x, y, w, bottom, mx, my);
            case PICK -> pick(g, font, x, y, w, bottom, mx, my);
            case MINE -> list(g, font, x, y, w, bottom, mx, my, mine, false);
            case BOX -> {
                int hw = w / 2;
                var a = new ChatLayout.Rect(x, y, hw - 1, 10); var b = new ChatLayout.Rect(x + hw, y, w - hw, 10);
                chip(g, font, a, tr("box.open", open), !boxDone, mx, my, () -> { boxDone = false; scroll = 0; send(boxQuery(false)); });
                chip(g, font, b, tr("box.done"), boxDone, mx, my, () -> { boxDone = true; scroll = 0; send(boxQuery(true)); });
                list(g, font, x, y + 13, w, bottom, mx, my, box, true);
            }
            case DETAIL -> detail(g, font, x, y, w, bottom, mx, my);
        }
    }

    private static SupportWire.Query boxQuery(boolean done) {
        return new SupportWire.Query(UUID.randomUUID(), SupportWire.Kind.BOX, 0, done, "", "", "", "");
    }

    private static void home(GuiGraphics g, Font font, int x, int y, int w, int bottom, int mx, int my) {
        int gap = 3, bw = (w - gap) / 2, bh = 26;
        String[][] items = {{"ask", "ASK"}, {"report", "REPORT"}, {"mine", "MINE"}, {"faq", "FAQ"}};
        for (int i = 0; i < 4; i++) {
            int bx = x + (i % 2) * (bw + gap), by = y + (i / 2) * (bh + gap);
            var r = new ChatLayout.Rect(bx, by, bw, bh);
            ChatUi.glass(g, ChatGlass.Surface.CARD, bx, by, bw, bh, r.contains(mx, my));
            ChatUi.buttonText(g, font, tr("home." + items[i][0]), bx + bw / 2f, by + bh / 2f, ChatLayout.TEXT_SCALE, FontPalette.body());
            if (i == 2 && unseen > 0) ChatUi.roundedFill(g, bx + bw - 6, by + 3, 3, 3, RED);
            final int idx = i;
            HITS.add(new Hit(r, () -> {
                notice = "";
                switch (idx) {
                    case 0 -> go(Page.ASK, true);
                    case 1 -> go(Page.REPORT, true);
                    case 2 -> { go(Page.MINE, true); send(SupportWire.Query.of(SupportWire.Kind.MINE)); }
                    default -> openWiki();
                }
            }));
        }
        y += 2 * (bh + gap) + 2;
        if (unseen > 0) {
            ChatUi.label(g, font, tr("home.unseen", unseen), x + 2, y + 3, ChatLayout.META_SCALE, ChatUi.accent());
            y += 9;
        }
        if (staff) {
            var r = new ChatLayout.Rect(x, y, w, 14);
            ChatUi.glass(g, ChatGlass.Surface.CARD, x, y, w, 14, r.contains(mx, my));
            ChatUi.buttonText(g, font, tr("home.box", open), x + w / 2f, y + 7, ChatLayout.TEXT_SCALE, open > 0 ? YELLOW : FontPalette.body());
            HITS.add(new Hit(r, () -> { notice = ""; boxDone = false; go(Page.BOX, true); send(boxQuery(false)); }));
        }
    }

    private static void ask(GuiGraphics g, Font font, int x, int y, int w, int bottom, int mx, int my) {
        ChatUi.label(g, font, tr("kind"), x + 2, y + 3, ChatLayout.META_SCALE, FontPalette.subtle());
        y += 8;
        int cw = w / ASK_KINDS.length;
        for (int i = 0; i < ASK_KINDS.length; i++) {
            String k = ASK_KINDS[i];
            chip(g, font, new ChatLayout.Rect(x + i * cw, y, cw - 2, 10), tr("kind." + k.toLowerCase(Locale.ROOT)), k.equals(askKind), mx, my, () -> askKind = k);
        }
        y += 14;
        ChatUi.label(g, font, tr("content"), x + 2, y + 3, ChatLayout.META_SCALE, FontPalette.subtle());
        y += 8;
        askBox.render(g, font, new ChatLayout.Rect(x, y, w, 14), mx, my, 0f);
        y += 18;
        ChatUi.label(g, font, tr("where_note"), x + 2, y + 2, ChatLayout.META_SCALE, FontPalette.subtle());
        y += 10;
        button(g, font, new ChatLayout.Rect(x, y, w, 14), tr("send"), mx, my, () -> {
            String t = askBox.query().trim();
            if (t.length() < 2) { say(tr("too_short"), true); return; }
            send(new SupportWire.Query(UUID.randomUUID(), SupportWire.Kind.SUBMIT, 0, false, askKind, t, "", ""));
        });
    }

    private static void report(GuiGraphics g, Font font, int x, int y, int w, int bottom, int mx, int my) {
        ChatUi.label(g, font, tr("target"), x + 2, y + 3, ChatLayout.META_SCALE, FontPalette.subtle());
        y += 8;
        var tr0 = new ChatLayout.Rect(x, y, w, 13);
        ChatUi.glass(g, ChatGlass.Surface.CARD, x, y, w, 13, tr0.contains(mx, my));
        ChatUi.label(g, font, target == null ? tr("target_pick") : target.display(), x + 4, y + 4, ChatLayout.TEXT_SCALE,
                target == null ? FontPalette.subtle() : FontPalette.body());
        HITS.add(new Hit(tr0, () -> { go(Page.PICK, true); send(SupportWire.Query.of(SupportWire.Kind.PLAYERS)); }));
        y += 16;
        ChatUi.label(g, font, tr("reason"), x + 2, y + 3, ChatLayout.META_SCALE, FontPalette.subtle());
        y += 8;
        int cw = w / 3;
        for (int i = 0; i < REASONS.length; i++) {
            int idx = i;
            chip(g, font, new ChatLayout.Rect(x + (i % 3) * cw, y + (i / 3) * 12, cw - 2, 10), tr("reason." + REASONS[i]), reason == i, mx, my, () -> reason = idx);
        }
        y += 26;
        ChatUi.label(g, font, tr("content_opt"), x + 2, y + 3, ChatLayout.META_SCALE, FontPalette.subtle());
        y += 8;
        reportBox.render(g, font, new ChatLayout.Rect(x, y, w, 14), mx, my, 0f);
        y += 18;
        ChatUi.label(g, font, tr("anon"), x + 2, y + 2, ChatLayout.META_SCALE, FontPalette.subtle());
        y += 10;
        button(g, font, new ChatLayout.Rect(x, y, w, 14), tr("report_send"), mx, my, () -> {
            if (target == null) { say(tr("need_target"), true); return; }
            if (reason < 0) { say(tr("need_reason"), true); return; }
            String reasonText = tr("reason." + REASONS[reason]);
            String desc = reportBox.query().trim();
            String msg = "[" + target.name() + "] " + reasonText + (desc.isEmpty() ? "" : " · " + desc);
            send(new SupportWire.Query(UUID.randomUUID(), SupportWire.Kind.SUBMIT, 0, false, "REPORT", msg, target.name(), reasonText));
        });
    }

    private static void pick(GuiGraphics g, Font font, int x, int y, int w, int bottom, int mx, int my) {
        if (players.isEmpty()) {
            ChatUi.label(g, font, request != null ? tr("loading") : tr("no_players"), x + 4, y + 6, ChatLayout.META_SCALE, FontPalette.subtle());
            return;
        }
        int rowH = 13;
        scrollList(g, x, y, w, bottom, players.size() * (rowH + 2), (top) -> {
            for (int i = 0; i < players.size(); i++) {
                var p = players.get(i);
                int ry = top + i * (rowH + 2);
                if (ry + rowH < y || ry > bottom) continue;
                var r = new ChatLayout.Rect(x, ry, w, rowH);
                boolean in = my >= y && my < bottom && r.contains(mx, my);
                ChatUi.glass(g, ChatGlass.Surface.CARD, x, ry, w, rowH, in);
                ChatUi.label(g, font, ChatUi.truncate(font, p.display(), w - 24, ChatLayout.TEXT_SCALE), x + 4, ry + 4, ChatLayout.TEXT_SCALE, FontPalette.body());
                if (p.near()) ChatUi.label(g, font, tr("near"), x + w - 16, ry + 4, ChatLayout.META_SCALE, ChatUi.accent());
                if (in || my < y || my >= bottom) HITS.add(new Hit(clip(r, y, bottom), () -> { target = p; back(); }));
            }
        });
    }

    private static void list(GuiGraphics g, Font font, int x, int y, int w, int bottom, int mx, int my,
                             List<SupportWire.Ticket> items, boolean staffView) {
        if (items.isEmpty()) {
            ChatUi.label(g, font, request != null ? tr("loading") : tr(staffView ? "box.empty" : "mine.empty"), x + 4, y + 6, ChatLayout.META_SCALE, FontPalette.subtle());
            return;
        }
        float ms = ChatLayout.META_SCALE; int lh = ChatUi.lineHeight(font, ms) + 1;
        // 카드 높이 먼저 계산
        List<Integer> hs = new ArrayList<>();
        int total = 0;
        for (var t : items) {
            int h = 4 + lh + Math.min(3, wrap(font, t.message(), w - 8, ms).size()) * lh + 3;
            if (!t.reply().isEmpty() && !staffView) h += 4 + lh + wrap(font, t.reply(), w - 14, ms).size() * lh + 3;
            if (staffView) h += lh;
            hs.add(h); total += h + 3;
        }
        scrollList(g, x, y, w, bottom, total, (top) -> {
            int cy = top;
            for (int i = 0; i < items.size(); i++) {
                var t = items.get(i); int h = hs.get(i);
                if (cy + h >= y && cy <= bottom) {
                    var r = new ChatLayout.Rect(x, cy, w, h);
                    boolean in = staffView && my >= y && my < bottom && r.contains(mx, my);
                    ChatUi.glass(g, ChatGlass.Surface.CARD, x, cy, w, h, in);
                    int ty = cy + 3;
                    String head = "[" + tr("kind." + t.kind().toLowerCase(Locale.ROOT)) + "]" + (staffView ? " #" + t.id() + " " + t.who() : "");
                    ChatUi.text(g, font, ChatUi.truncate(font, head, w - 34, ms), x + 4, ty, ms, kindColor(t.kind()));
                    String st = tr("state." + t.state().toLowerCase(Locale.ROOT));
                    ChatUi.text(g, font, st, x + w - 4 - ChatUi.width(font, st, ms), ty, ms, stateColor(t.state()));
                    ty += lh;
                    List<FormattedCharSequence> ls = wrap(font, t.message(), w - 8, ms);
                    for (int k = 0; k < Math.min(3, ls.size()); k++) { ChatUi.text(g, font, ls.get(k), x + 4, ty, ms, FontPalette.body()); ty += lh; }
                    if (staffView) {
                        String meta = time(t.created()) + (t.handler().isEmpty() ? "" : " · " + tr("handler", t.handler()));
                        ChatUi.text(g, font, ChatUi.truncate(font, meta, w - 8, ms), x + 4, ty, ms, FontPalette.subtle()); ty += lh;
                    }
                    if (!t.reply().isEmpty() && !staffView) {
                        ty += 1;
                        List<FormattedCharSequence> rs = wrap(font, t.reply(), w - 14, ms);
                        ChatUi.roundedFill(g, x + 3, ty, w - 6, 3 + lh + rs.size() * lh, 0x33FFFFFF);
                        ChatUi.text(g, font, tr("reply_by", t.replyBy()), x + 6, ty + 2, ms, ChatUi.accent());
                        int ry = ty + 2 + lh;
                        for (var l : rs) { ChatUi.text(g, font, l, x + 6, ry, ms, FontPalette.body()); ry += lh; }
                    }
                    if (in) { int id = t.id(); HITS.add(new Hit(clip(r, y, bottom), () -> { detailId = id; replyBox.reset(); go(Page.DETAIL, true); })); }
                }
                cy += h + 3;
            }
        });
    }

    private static void detail(GuiGraphics g, Font font, int x, int y, int w, int bottom, int mx, int my) {
        SupportWire.Ticket t = null;
        for (var b : box) if (b.id() == detailId) t = b;
        if (t == null) { ChatUi.label(g, font, tr("gone"), x + 4, y + 6, ChatLayout.META_SCALE, FontPalette.subtle()); return; }
        float ms = ChatLayout.META_SCALE; int lh = ChatUi.lineHeight(font, ms) + 1;
        final SupportWire.Ticket tk = t;
        // 위: 내용 (스크롤), 아래: 버튼·답변 칸 고정
        int fixedH = 14 + 3 + 14 + 3 + 13;
        int listBottom = bottom - fixedH;
        List<String> lines = new ArrayList<>();
        lines.add("#" + t.id() + " [" + tr("kind." + t.kind().toLowerCase(Locale.ROOT)) + "] " + tr("state." + t.state().toLowerCase(Locale.ROOT)));
        lines.add(tr("from", t.who()) + " · " + time(t.created()));
        if (!t.target().isEmpty()) lines.add(tr("target_is", t.target(), t.reason()));
        lines.add(tr("where", t.where()));
        if (!t.handler().isEmpty()) lines.add(tr("handler", t.handler()));
        List<FormattedCharSequence> body = new ArrayList<>();
        for (String s : lines) body.addAll(wrap(font, s, w - 8, ms));
        int metaN = body.size();
        body.addAll(wrap(font, t.message(), w - 8, ms));
        int replyStart = body.size();
        if (!t.reply().isEmpty()) {
            body.addAll(wrap(font, tr("reply_by", t.replyBy()), w - 8, ms));
            body.addAll(wrap(font, t.reply(), w - 8, ms));
        }
        int total = body.size() * lh + 8;
        scrollList(g, x, y, w, listBottom, total, (top) -> {
            ChatUi.glass(g, ChatGlass.Surface.CARD, x, top, w, total - 2, false);
            int ty = top + 3;
            for (int i = 0; i < body.size(); i++) {
                int c = i < metaN ? FontPalette.subtle() : i >= replyStart ? (i == replyStart ? ChatUi.accent() : GREEN) : FontPalette.body();
                ChatUi.text(g, font, body.get(i), x + 4, ty, ms, c);
                ty += lh;
            }
        });
        int by = listBottom + 3, bw = (w - 6) / 4;
        String[][] acts = {{"claim", "claim"}, {"tp", "tp"}, {"resolve", tk.state().equals("DONE") || tk.state().equals("ANSWERED") ? "reopen" : "resolve"}, {"x", ""}};
        for (int i = 0; i < 3; i++) {
            String act = acts[i][1];
            button(g, font, new ChatLayout.Rect(x + i * (bw + 2), by, bw, 14), tr("act." + act), mx, my,
                    () -> send(new SupportWire.Query(UUID.randomUUID(), SupportWire.Kind.ACT, tk.id(), false, act, "", "", "")));
        }
        by += 17;
        replyBox.render(g, font, new ChatLayout.Rect(x, by, w - 26, 14), mx, my, 0f);
        button(g, font, new ChatLayout.Rect(x + w - 24, by, 24, 14), tr("act.reply"), mx, my, () -> {
            String r = replyBox.query().trim();
            if (r.isEmpty()) { say(tr("need_reply"), true); return; }
            send(new SupportWire.Query(UUID.randomUUID(), SupportWire.Kind.ACT, tk.id(), false, "reply", r, "", ""));
        });
    }

    // ───────── 작은 도구 ─────────

    private interface Body { void draw(int top); }

    private static void scrollList(GuiGraphics g, int x, int y, int w, int bottom, int total, Body body) {
        viewH = bottom - y; contentH = total;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - viewH)));
        GuiUtil.enableScissor(g, x, y, x + w, bottom);
        try { body.draw((int) (y - scroll)); }
        finally { GuiUtil.disableScissor(g); }
    }

    private static ChatLayout.Rect clip(ChatLayout.Rect r, int top, int bottom) {
        int y0 = Math.max(r.y(), top), y1 = Math.min(r.y() + r.height(), bottom);
        return new ChatLayout.Rect(r.x(), y0, r.width(), Math.max(0, y1 - y0));
    }

    private static void chip(GuiGraphics g, Font font, ChatLayout.Rect r, String text, boolean on, int mx, int my, Runnable run) {
        if (on) ChatUi.roundedFill(g, r.x(), r.y(), r.width(), r.height(), ChatUi.accent());
        else ChatUi.glass(g, ChatGlass.Surface.CARD, r.x(), r.y(), r.width(), r.height(), r.contains(mx, my));
        ChatUi.buttonText(g, font, ChatUi.truncate(font, text, r.width() - 2, ChatLayout.META_SCALE), r.x() + r.width() / 2f, r.y() + r.height() / 2f,
                ChatLayout.META_SCALE, on ? 0xFFFFFFFF : FontPalette.body());
        HITS.add(new Hit(r, run));
    }

    private static void button(GuiGraphics g, Font font, ChatLayout.Rect r, String text, int mx, int my, Runnable run) {
        boolean hot = r.contains(mx, my);
        ChatUi.roundedFill(g, r.x(), r.y(), r.width(), r.height(), hot ? ChatUi.accent() : 0xFF6A5AA8);
        ChatUi.buttonText(g, font, text, r.x() + r.width() / 2f, r.y() + r.height() / 2f, ChatLayout.META_SCALE, 0xFFFFFFFF);
        HITS.add(new Hit(r, () -> { if (request == null) run.run(); }));
    }

    private static List<FormattedCharSequence> wrap(Font font, String s, int width, float scale) {
        return font.split(Component.literal(s == null ? "" : s), Math.max(10, (int) (width / scale)));
    }

    private static int kindColor(String k) {
        return switch (k) { case "BUG" -> 0xFFFF8A8A; case "REPORT" -> 0xFFF4A7C4; case "QUESTION" -> 0xFF8CC6F2; default -> GRAY; };
    }

    private static int stateColor(String s) {
        return switch (s) { case "ANSWERED" -> GREEN; case "CHECKING" -> YELLOW; case "DONE" -> 0xFFB8D8C4; default -> GRAY; };
    }

    private static String time(long ms) {
        return ms <= 0 ? "-" : new SimpleDateFormat("MM/dd HH:mm").format(new Date(ms));
    }

    private static void openWiki() {
        Minecraft mc = Minecraft.getInstance();
        Screen prev = mc.screen;
        mc.setScreen(new ConfirmLinkScreen(ok -> {
            if (ok) Util.getPlatform().openUri(WIKI);
            mc.setScreen(prev);
        }, WIKI, true));
    }

    // ───────── 입력 ─────────

    public static boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return true;
        boolean box = askBox.mouseClicked(mx, my, button) | reportBox.mouseClicked(mx, my, button) | replyBox.mouseClicked(mx, my, button);
        if (box) return true;
        for (int i = HITS.size() - 1; i >= 0; i--) if (HITS.get(i).r().contains(mx, my)) { HITS.get(i).run().run(); return true; }
        return true;
    }

    public static boolean mouseScrolled(double amount) {
        scroll -= amount * 14;
        if (scroll < 0) scroll = 0;
        return true;
    }

    public static boolean keyPressed(int key, int scan, int modifiers) {
        return switch (page) {
            case ASK -> askBox.keyPressed(key, scan, modifiers);
            case REPORT -> reportBox.keyPressed(key, scan, modifiers);
            case DETAIL -> replyBox.keyPressed(key, scan, modifiers);
            default -> false;
        };
    }

    public static boolean charTyped(char c, int modifiers) {
        return switch (page) {
            case ASK -> askBox.charTyped(c, modifiers);
            case REPORT -> reportBox.charTyped(c, modifiers);
            case DETAIL -> replyBox.charTyped(c, modifiers);
            default -> false;
        };
    }
}
