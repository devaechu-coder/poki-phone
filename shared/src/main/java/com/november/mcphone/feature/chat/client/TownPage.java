package com.november.mcphone.feature.chat.client;

import com.november.mcphone.core.PhoneItem;
import com.november.mcphone.core.client.FontPalette;
import com.november.mcphone.core.client.GuiUtil;
import com.november.mcphone.core.client.PhoneToast;
import com.november.mcphone.core.net.MCphoneNetwork;
import com.november.mcphone.feature.town.net.TownRequestPacket;
import com.november.mcphone.feature.town.net.TownWire;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 포키 마을 앱. 탭: 내 마을 / 전체 마을 (전체 마을은 운영진 + 서버 스위치가 켜져 있으면 유저도).
 * 실제 마을은 Towny — PokiTown 플러그인이 그 사람 본인 권한으로 Towny 명령어를 실행. 버튼은 실제 Towny 권한이 있을 때만 보임.
 */
public final class TownPage {
    private enum Page { HOME, MEMBERS, PERSON, ALL, DETAIL }

    private static final int RED = 0xFFFF5A6E, GREEN = 0xFF7BE0A6, YELLOW = 0xFFFFD36B, GRAY = 0xFFA9A4CF;
    private static final UUID TOAST_ID = new UUID(0x706f6b69746f776eL, 0x6e6f74696365L);

    record TownRow(String id, String name, String display, int color, String mayorName, String mayorNick, int res, int trusted,
                   int chunks, String bal, boolean ruined, String names, boolean hasSpawn) {}
    record Person(boolean trusted, String id, String name, String role, String online, long last, String nick) {}
    record Invite(String townId, String name, String display, String sender, String mayor, int res) {}

    private static Page page = Page.HOME;
    private static final List<Page> stack = new ArrayList<>();
    private static UUID request;
    private static TownWire.Kind requestKind;
    private static long deadline, refreshAt;
    private static String notice = "";
    private static boolean noticeBad;
    private static boolean loaded, noPlugin;

    // 내 상태
    private static String myTown = "", myRole = "";
    private static final Set<String> perms = new HashSet<>();
    private static boolean listPublic, op;
    private static List<Invite> invites = List.of();
    private static TownRow mine;
    private static List<Person> mineMembers = List.of();

    // 전체 마을
    private static List<TownRow> towns = List.of();
    private static TownRow other;
    private static List<Person> otherMembers = List.of();
    private static Person person;
    /** 확인 한 번 더: 문구 + 실행 */
    private static String confirmText;
    private static Runnable confirmRun;

    private static final ChatContactSearch nameBox = new ChatContactSearch("mcphone.town.name_hint", 20);
    private static final ChatContactSearch searchBox = new ChatContactSearch();

    private static double scroll;
    private record Hit(ChatLayout.Rect r, Runnable run) {}
    private static final List<Hit> HITS = new ArrayList<>();

    private TownPage() {}

    // ───────── 열기·닫기·통신 ─────────

    public static void open() {
        page = Page.HOME; stack.clear(); scroll = 0; notice = ""; person = null; other = null;
        confirmText = null; confirmRun = null; refreshAt = 0;
        nameBox.reset(); searchBox.reset();
        send(TownWire.Query.of(TownWire.Kind.ME));
    }

    public static void close() {
        request = null; HITS.clear(); refreshAt = 0;
        nameBox.close(); searchBox.close();
    }

    private static void send(TownWire.Query q) {
        request = q.request(); requestKind = q.kind();
        deadline = System.currentTimeMillis() + 6000;
        try { MCphoneNetwork.sendToServer(new TownRequestPacket(q)); }
        catch (RuntimeException e) { request = null; say("서버에 못 보냈어요", true); }
    }

    private static void say(String s, boolean bad) { notice = s; noticeBad = bad; }

    /** 서버 알림 → 포키폰 팝업 (포키톡 팝업과 같은 모양) */
    public static void notice(TownWire.Notice n) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player == null || !PhoneItem.isCarriedBy(mc.player)) return;
            mc.getToasts().addToast(new PhoneToast(TOAST_ID, "마을", n.text()));
            // 마을 앱을 보고 있으면 바로 새로 고침
            if (mc.screen instanceof com.november.mcphone.core.client.PhoneScreen && request == null && loaded)
                refreshAt = System.currentTimeMillis() + 300;
        });
    }

    public static void accept(TownWire.Reply r) {
        if (request == null || !request.equals(r.request())) return;
        request = null;
        if (r.status() == TownWire.Status.BUSY) { refreshAt = System.currentTimeMillis() + 300; return; }
        if (r.status() == TownWire.Status.NO_PLUGIN) { noPlugin = true; loaded = true; return; }
        if (r.status() != TownWire.Status.OK) {
            String m = !r.message().isEmpty() ? r.message() : switch (r.status()) {
                case FORBIDDEN -> "포키폰을 들고 있어야 해요";
                default -> "잠시 뒤에 다시 해 주세요";
            };
            say(m, true);
            return;
        }
        noPlugin = false;
        switch (r.kind()) {
            case ME -> { parseMe(r.lines()); loaded = true; }
            case TOWNS -> towns = parseTowns(r.lines());
            case TOWN -> {
                List<Person> ps = new ArrayList<>();
                other = parseDetail(r.lines(), ps);
                otherMembers = ps;
            }
            case ACT -> {
                say("요청했어요 · 안 됐으면 채팅에서 이유를 확인해 주세요", false);
                refreshAt = System.currentTimeMillis() + 700;
            }
            case SWITCH -> {
                say("바꿨어요", false);
                refreshAt = System.currentTimeMillis() + 200;
            }
        }
    }

    private static void refresh() {
        refreshAt = 0;
        if (page == Page.ALL) send(TownWire.Query.of(TownWire.Kind.TOWNS));
        else if (page == Page.DETAIL && other != null) send(TownWire.Query.of(TownWire.Kind.TOWN, other.id(), ""));
        else send(TownWire.Query.of(TownWire.Kind.ME));
    }

    // ───────── 받은 줄 읽기 ─────────

    private static void parseMe(List<String> lines) {
        invites = new ArrayList<>(); perms.clear();
        myTown = ""; myRole = ""; mine = null;
        List<Invite> inv = new ArrayList<>();
        List<String> detail = new ArrayList<>();
        for (String l : lines) {
            String[] f = l.split("\t", -1);
            switch (f[0]) {
                case "ME" -> {
                    myTown = at(f, 1); myRole = at(f, 2);
                    for (String p : at(f, 3).split(",")) if (!p.isEmpty()) perms.add(p);
                    listPublic = at(f, 4).equals("1"); op = at(f, 5).equals("1");
                }
                case "INV" -> inv.add(new Invite(at(f, 1), at(f, 2), strip(at(f, 3)), at(f, 4), at(f, 5), num(at(f, 6))));
                default -> detail.add(l);
            }
        }
        invites = inv;
        List<Person> ps = new ArrayList<>();
        mine = myTown.isEmpty() ? null : parseDetail(detail, ps);
        mineMembers = ps;
        if (mine == null && (page == Page.MEMBERS || page == Page.PERSON)) { stack.clear(); page = Page.HOME; }
        if (!allTab() && (page == Page.ALL || page == Page.DETAIL)) { stack.clear(); page = Page.HOME; }
        // 사람 메뉴를 보고 있었으면 그 사람 최신 상태로
        if (person != null) {
            Person now = null;
            for (Person p : mineMembers) if (p.id().equals(person.id()) && p.trusted() == person.trusted()) now = p;
            person = now;
            if (now == null && page == Page.PERSON) back();
        }
    }

    /** T 줄 + R/A 줄 */
    private static TownRow parseDetail(List<String> lines, List<Person> out) {
        TownRow t = null;
        for (String l : lines) {
            String[] f = l.split("\t", -1);
            if (f[0].equals("T")) t = row(f, false);
            else if (f[0].equals("R") || f[0].equals("A"))
                out.add(new Person(f[0].equals("A"), at(f, 1), at(f, 2), at(f, 3), at(f, 4), lng(at(f, 5)), strip(at(f, 6))));
        }
        return t;
    }

    private static List<TownRow> parseTowns(List<String> lines) {
        List<TownRow> out = new ArrayList<>();
        for (String l : lines) {
            String[] f = l.split("\t", -1);
            if (f[0].equals("T")) out.add(row(f, true));
        }
        // 내 마을은 맨 위
        out.sort((a, b) -> Boolean.compare(!a.id().equals(myTown), !b.id().equals(myTown)));
        return out;
    }

    /** T 마을UUID 이름 표시 색 이장UUID 이장 주민 알바 청크 잔액 폐허 | (목록) 주민이름들 이장한글닉 | (상세) 스폰 이장한글닉 */
    private static TownRow row(String[] f, boolean list) {
        String mayorNick = strip(at(f, 13));
        if (mayorNick.isEmpty()) mayorNick = at(f, 6);
        return new TownRow(at(f, 1), at(f, 2), strip(at(f, 3)), color(at(f, 4)), at(f, 6), mayorNick,
                num(at(f, 7)), num(at(f, 8)), num(at(f, 9)), at(f, 10), at(f, 11).equals("1"),
                list ? at(f, 12) : "", !list && at(f, 12).equals("1"));
    }

    private static boolean allTab() { return op || listPublic; }

    // ───────── 이동 ─────────

    private static void go(Page p, boolean push) {
        if (push) stack.add(page);
        page = p; scroll = 0; confirmText = null; confirmRun = null;
        nameBox.close(); searchBox.close();
    }

    /** 뒤로: 앞 화면으로. false 면 홈 화면으로 나감 */
    public static boolean back() {
        if (confirmText != null) { confirmText = null; confirmRun = null; return true; }
        if (stack.isEmpty()) return false;
        page = stack.remove(stack.size() - 1); scroll = 0; notice = "";
        nameBox.close(); searchBox.close();
        return true;
    }

    // ───────── 그리기 ─────────

    public static void render(GuiGraphics g, int left, int top, int sw, int sh, int statusH, int navH, int mx, int my, Font font) {
        HITS.clear();
        long now = System.currentTimeMillis();
        if (request != null && now > deadline) { request = null; say("서버가 대답이 없어요 (서버 포키폰이 예전 버전일 수 있어요)", true); }
        if (refreshAt > 0 && request == null && now > refreshAt) refresh();
        int x = left + 4, w = sw - 8, y = top + statusH + ChatLayout.TOP_GAP;
        int bottom = top + sh - navH - 2;

        boolean root = page == Page.HOME || page == Page.ALL;
        if (!root) {
            var back = new ChatLayout.Rect(x, y, 12, ChatLayout.HEADER_HEIGHT);
            ChatUi.sizedIcon(g, ChatUi.Icon.BACK, x + 2.5f, y + 2.5f, 7, back.contains(mx, my) ? ChatUi.accent() : FontPalette.body());
            HITS.add(new Hit(back, TownPage::back));
        }
        String title = switch (page) {
            case HOME -> "내 마을";
            case ALL -> "전체 마을";
            case MEMBERS -> "주민";
            case PERSON -> person == null ? "" : person.nick();
            case DETAIL -> other == null ? "마을" : other.display();
        };
        ChatUi.buttonText(g, font, ChatUi.truncate(font, title, w - 30, ChatLayout.TEXT_SCALE), x + w / 2f, y + ChatLayout.HEADER_HEIGHT / 2f,
                ChatLayout.TEXT_SCALE, FontPalette.title());
        y += ChatLayout.HEADER_HEIGHT + 3;

        if (root && loaded && !noPlugin && allTab()) {
            int hw = w / 2;
            chip(g, font, new ChatLayout.Rect(x, y, hw - 1, 11), "내 마을", page == Page.HOME, mx, my, () -> { if (page != Page.HOME) { go(Page.HOME, false); send(TownWire.Query.of(TownWire.Kind.ME)); } });
            chip(g, font, new ChatLayout.Rect(x + hw, y, w - hw, 11), "전체 마을", page == Page.ALL, mx, my, () -> { if (page != Page.ALL) { go(Page.ALL, false); send(TownWire.Query.of(TownWire.Kind.TOWNS)); } });
            y += 14;
        }

        if (!notice.isEmpty()) {
            for (var l : font.split(net.minecraft.network.chat.Component.literal(notice), Math.max(10, (int) ((w - 4) / ChatLayout.META_SCALE)))) {
                ChatUi.text(g, font, l, x + 2, y, ChatLayout.META_SCALE, noticeBad ? RED : GREEN);
                y += ChatUi.lineHeight(font, ChatLayout.META_SCALE) + 1;
            }
            y += 2;
        }

        if (!loaded) { meta(g, font, request != null ? "불러오는 중…" : "", x + 4, y + 4, FontPalette.subtle()); return; }
        if (noPlugin) {
            meta(g, font, "마을 앱 준비 중이에요", x + 4, y + 4, FontPalette.body());
            meta(g, font, "마을은 /마을 명령어로 관리할 수 있어요", x + 4, y + 14, FontPalette.subtle());
            return;
        }
        if (confirmText != null) { confirm(g, font, x, y, w, mx, my); return; }

        switch (page) {
            case HOME -> home(g, font, x, y, w, bottom, mx, my);
            case MEMBERS -> members(g, font, x, y, w, bottom, mx, my);
            case PERSON -> person(g, font, x, y, w, mx, my);
            case ALL -> all(g, font, x, y, w, bottom, mx, my);
            case DETAIL -> detail(g, font, x, y, w, bottom, mx, my);
        }
    }

    private static void confirm(GuiGraphics g, Font font, int x, int y, int w, int mx, int my) {
        var lines = font.split(net.minecraft.network.chat.Component.literal(confirmText), Math.max(10, (int) ((w - 8) / ChatLayout.TEXT_SCALE)));
        int lh = ChatUi.lineHeight(font, ChatLayout.TEXT_SCALE) + 1;
        int h = 5 + lines.size() * lh + 4 + 14 + 4;
        ChatUi.glass(g, ChatGlass.Surface.CARD, x, y, w, h, false);
        int ty = y + 5;
        for (var l : lines) { ChatUi.text(g, font, l, x + 4, ty, ChatLayout.TEXT_SCALE, FontPalette.body()); ty += lh; }
        int by = y + h - 18, bw = (w - 11) / 2;
        Runnable run = confirmRun;
        button(g, font, new ChatLayout.Rect(x + 4, by, bw, 14), "확인", RED, mx, my, () -> { confirmText = null; confirmRun = null; if (run != null) run.run(); });
        button(g, font, new ChatLayout.Rect(x + 7 + bw, by, bw, 14), "취소", 0xFF6A5AA8, mx, my, () -> { confirmText = null; confirmRun = null; });
    }

    private static void ask(String text, Runnable run) { confirmText = text; confirmRun = run; }

    private static void act(String action, String arg) {
        send(new TownWire.Query(UUID.randomUUID(), TownWire.Kind.ACT, false, action, arg));
    }

    // 내 마을
    private static void home(GuiGraphics g, Font font, int x, int y, int w, int bottom, int mx, int my) {
        if (mine == null) { noTown(g, font, x, y, w, bottom, mx, my); return; }
        y = townCard(g, font, mine, x, y, w, true) + 4;
        if (perms.contains("spawn") && mine.hasSpawn()) {
            button(g, font, new ChatLayout.Rect(x, y, w, 14), "마을 스폰으로 이동", 0xFF6A5AA8, mx, my, () -> act("spawn", ""));
            y += 17;
        }
        button(g, font, new ChatLayout.Rect(x, y, w, 14), "주민 목록", 0xFF6A5AA8, mx, my, () -> { nameBox.reset(); go(Page.MEMBERS, true); });
        y += 17;
        if (perms.contains("leave")) {
            button(g, font, new ChatLayout.Rect(x, y, w, 14), "마을 탈퇴", 0xFF8A4A6A, mx, my, () -> {
                if (myRole.equals("mayor")) { say("이장은 탈퇴할 수 없어요 · 먼저 /마을 양도 로 이장을 넘겨 주세요", true); return; }
                ask(mine.display() + " 마을에서 정말 나갈까요?", () -> act("leave", ""));
            });
        }
    }

    private static void noTown(GuiGraphics g, Font font, int x, int y, int w, int bottom, int mx, int my) {
        if (invites.isEmpty()) {
            meta(g, font, request != null ? "불러오는 중…" : "아직 마을이 없어요", x + 4, y + 4, FontPalette.body());
            if (request == null) meta(g, font, "이장에게 초대를 부탁하세요", x + 4, y + 14, FontPalette.subtle());
            return;
        }
        meta(g, font, "받은 초대 " + invites.size(), x + 2, y + 2, FontPalette.subtle());
        y += 10;
        int h = 34;
        for (Invite i : invites) {
            if (y + h > bottom) break;
            ChatUi.glass(g, ChatGlass.Surface.CARD, x, y, w, h, false);
            ChatUi.label(g, font, ChatUi.truncate(font, i.display(), w - 8, ChatLayout.TEXT_SCALE), x + 4, y + 3, ChatLayout.TEXT_SCALE, FontPalette.body());
            meta(g, font, ChatUi.truncate(font, "이장 " + i.mayor() + " · 주민 " + i.res() + "명", w - 8, ChatLayout.META_SCALE), x + 4, y + 12, FontPalette.subtle());
            int bw = (w - 11) / 2;
            button(g, font, new ChatLayout.Rect(x + 4, y + h - 13, bw, 10), "수락", 0xFF4A9A6A, mx, my, () -> act("accept", i.name()));
            button(g, font, new ChatLayout.Rect(x + 7 + bw, y + h - 13, bw, 10), "거절", 0xFF6A5AA8, mx, my,
                    () -> ask(i.display() + " 마을 초대를 거절할까요?", () -> act("deny", i.name())));
            y += h + 3;
        }
    }

    /** 마을 카드. 끝난 y 를 돌려줌 */
    private static int townCard(GuiGraphics g, Font font, TownRow t, int x, int y, int w, boolean money) {
        float ms = ChatLayout.META_SCALE; int lh = ChatUi.lineHeight(font, ms) + 2;
        boolean bal = money && !t.bal().equals("-");
        int h = 4 + ChatUi.lineHeight(font, ChatLayout.TEXT_SCALE) + 2 + lh * (bal ? 3 : 2) + 2;
        ChatUi.glass(g, ChatGlass.Surface.CARD, x, y, w, h, false);
        int ty = y + 4;
        String name = t.display() + (t.ruined() ? " (폐허)" : "");
        ChatUi.label(g, font, ChatUi.truncate(font, name, w - 8, ChatLayout.TEXT_SCALE), x + 4, ty, ChatLayout.TEXT_SCALE, t.color());
        ty += ChatUi.lineHeight(font, ChatLayout.TEXT_SCALE) + 2;
        meta(g, font, ChatUi.truncate(font, "이장 " + t.mayorNick(), w - 8, ms), x + 4, ty, FontPalette.body()); ty += lh;
        meta(g, font, "주민 " + t.res() + "명 · 알바 " + t.trusted() + "명 · 땅 " + t.chunks() + "청크", x + 4, ty, FontPalette.subtle()); ty += lh;
        if (bal) meta(g, font, "마을 소지금 " + money(t.bal()), x + 4, ty, YELLOW);
        return y + h;
    }

    // 주민 목록 (내 마을)
    private static void members(GuiGraphics g, Font font, int x, int y, int w, int bottom, int mx, int my) {
        if (mine == null) return;
        boolean inv = perms.contains("invite"), tr = perms.contains("trust");
        if (inv || tr) {
            int bw = 26, n = (inv ? 1 : 0) + (tr ? 1 : 0);
            int boxW = w - n * (bw + 2);
            nameBox.render(g, font, new ChatLayout.Rect(x, y, boxW, 14), mx, my, 0f);
            int bx = x + boxW + 2;
            if (inv) {
                button(g, font, new ChatLayout.Rect(bx, y, bw, 14), "초대", 0xFF6A5AA8, mx, my, () -> withName(n2 -> act("invite", n2)));
                bx += bw + 2;
            }
            if (tr) button(g, font, new ChatLayout.Rect(bx, y, bw, 14), "알바", 0xFF6A5AA8, mx, my, () -> withName(n2 -> act("trust_add", n2)));
            y += 18;
        }
        people(g, font, x, y, w, bottom, mx, my, mineMembers, true, op);
    }

    private static void withName(java.util.function.Consumer<String> run) {
        String n = nameBox.query().trim();
        if (n.isEmpty() || n.length() > 20) { say("한글닉이나 마크닉을 적어 주세요", true); return; }
        nameBox.reset();
        run.accept(n);
    }

    /** 주민 + 알바 목록. clickable=true 면 눌러서 메뉴 */
    private static void people(GuiGraphics g, Font font, int x, int y, int w, int bottom, int mx, int my, List<Person> list, boolean clickable, boolean showLast) {
        List<Person> res = new ArrayList<>(), al = new ArrayList<>();
        for (Person p : list) (p.trusted() ? al : res).add(p);
        int rowH = 13, headH = 10;
        int total = headH + res.size() * (rowH + 2) + headH + Math.max(1, al.size()) * (rowH + 2);
        scrollList(g, x, y, w, bottom, total, top -> {
            int cy = top;
            meta(g, font, "주민 " + res.size(), x + 2, cy + 2, FontPalette.subtle());
            cy += headH;
            for (Person p : res) { personRow(g, font, p, x, cy, w, rowH, y, bottom, mx, my, clickable, showLast); cy += rowH + 2; }
            meta(g, font, "알바 " + al.size(), x + 2, cy + 2, FontPalette.subtle());
            cy += headH;
            if (al.isEmpty()) meta(g, font, "없음", x + 6, cy + 3, FontPalette.dim());
            for (Person p : al) { personRow(g, font, p, x, cy, w, rowH, y, bottom, mx, my, clickable, showLast); cy += rowH + 2; }
        });
    }

    private static void personRow(GuiGraphics g, Font font, Person p, int x, int ry, int w, int rowH, int top, int bottom,
                                  int mx, int my, boolean clickable, boolean showLast) {
        if (ry + rowH < top || ry > bottom) return;
        boolean can = clickable && !menu(p).isEmpty();
        var r = new ChatLayout.Rect(x, ry, w, rowH);
        boolean in = can && my >= top && my < bottom && r.contains(mx, my);
        ChatUi.glass(g, ChatGlass.Surface.CARD, x, ry, w, rowH, in);
        int tx = x + 4;
        if (!p.online().equals("-")) {
            ChatUi.roundedFill(g, tx, ry + 5, 3, 3, p.online().equals("1") ? GREEN : 0xFF6E6A8E);
            tx += 6;
        }
        String badge = switch (p.role()) { case "mayor" -> "이장"; case "assistant" -> "부이장"; default -> ""; };
        String right = !badge.isEmpty() ? badge
                : showLast && p.online().equals("0") && p.last() > 0 ? new SimpleDateFormat("MM/dd").format(new Date(p.last())) : "";
        int rw = right.isEmpty() ? 0 : ChatUi.width(font, right, ChatLayout.META_SCALE) + 6;
        String nm = p.nick().equals(p.name()) ? p.name() : p.nick() + " (" + p.name() + ")";
        ChatUi.label(g, font, ChatUi.truncate(font, nm, x + w - tx - rw - 4, ChatLayout.TEXT_SCALE), tx, ry + 4, ChatLayout.TEXT_SCALE, FontPalette.body());
        if (!right.isEmpty())
            meta(g, font, right, x + w - 4 - ChatUi.width(font, right, ChatLayout.META_SCALE), ry + 4, badge.isEmpty() ? FontPalette.dim() : badge.equals("이장") ? YELLOW : ChatUi.accent());
        if (in) HITS.add(new Hit(clip(r, top, bottom), () -> { person = p; go(Page.PERSON, true); }));
    }

    /** 그 사람에게 내가 할 수 있는 것 (권한 있는 것만) */
    private static List<String[]> menu(Person p) {
        List<String[]> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        boolean self = mc.player != null && mc.player.getUUID().toString().equals(p.id());
        if (!p.trusted()) {
            if (perms.contains("rank") && p.role().equals("resident")) out.add(new String[]{"assistant_add", "부이장으로 지정", ""});
            if (perms.contains("rank") && p.role().equals("assistant")) out.add(new String[]{"assistant_remove", "부이장 해제", "부이장에서 해제할까요?"});
            if (perms.contains("kick") && !p.role().equals("mayor") && !self) out.add(new String[]{"kick", "마을에서 추방", "마을에서 추방할까요?"});
        } else if (perms.contains("trust")) {
            out.add(new String[]{"trust_remove", "알바에서 빼기", "알바에서 뺄까요?"});
        }
        return out;
    }

    private static void person(GuiGraphics g, Font font, int x, int y, int w, int mx, int my) {
        if (person == null) return;
        Person p = person;
        ChatUi.glass(g, ChatGlass.Surface.CARD, x, y, w, 24, false);
        ChatUi.label(g, font, ChatUi.truncate(font, p.nick(), w - 8, ChatLayout.TEXT_SCALE), x + 4, y + 4, ChatLayout.TEXT_SCALE, FontPalette.body());
        String role = p.trusted() ? "알바" : switch (p.role()) { case "mayor" -> "이장"; case "assistant" -> "부이장"; default -> "주민"; };
        meta(g, font, p.name() + " · " + role + (p.online().equals("1") ? " · 접속 중" : ""), x + 4, y + 14, FontPalette.subtle());
        y += 28;
        for (String[] m : menu(p)) {
            boolean danger = !m[2].isEmpty();
            button(g, font, new ChatLayout.Rect(x, y, w, 14), m[1], danger ? 0xFF8A4A6A : 0xFF6A5AA8, mx, my, () -> {
                if (danger) ask(p.nick() + " — " + m[2], () -> { act(m[0], p.name()); back(); });
                else { act(m[0], p.name()); back(); }
            });
            y += 17;
        }
    }

    // 전체 마을
    private static void all(GuiGraphics g, Font font, int x, int y, int w, int bottom, int mx, int my) {
        if (op) {
            String s = "유저에게도 보이기: " + (listPublic ? "켜짐" : "꺼짐");
            chip(g, font, new ChatLayout.Rect(x, y, w, 11), s, listPublic, mx, my,
                    () -> ask(listPublic ? "전체 마을 목록을 유저에게 숨길까요? (운영진은 계속 보여요)" : "전체 마을 목록을 유저에게도 보일까요?",
                            () -> send(new TownWire.Query(UUID.randomUUID(), TownWire.Kind.SWITCH, !listPublic, "", ""))));
            y += 14;
        }
        searchBox.render(g, font, new ChatLayout.Rect(x, y, w, ChatLayout.SEARCH_HEIGHT), mx, my, 0f);
        y += ChatLayout.SEARCH_HEIGHT + 3;
        String q = searchBox.query().trim().toLowerCase(Locale.ROOT);
        List<TownRow> list = new ArrayList<>();
        for (TownRow t : towns) {
            if (q.isEmpty() || t.display().toLowerCase(Locale.ROOT).contains(q) || t.name().toLowerCase(Locale.ROOT).contains(q)
                    || (op && t.names().toLowerCase(Locale.ROOT).contains(q))) list.add(t);
        }
        if (list.isEmpty()) {
            meta(g, font, request != null ? "불러오는 중…" : towns.isEmpty() ? "마을이 없어요" : "찾는 마을이 없어요", x + 4, y + 4, FontPalette.subtle());
            return;
        }
        int h = 25;
        final int listTop = y;
        scrollList(g, x, y, w, bottom, list.size() * (h + 3), top -> {
            int cy = top;
            for (TownRow t : list) {
                if (cy + h >= listTop && cy <= bottom) {
                    var r = new ChatLayout.Rect(x, cy, w, h);
                    boolean in = my >= listTop && my < bottom && r.contains(mx, my);
                    ChatUi.glass(g, ChatGlass.Surface.CARD, x, cy, w, h, in);
                    boolean me = t.id().equals(myTown);
                    String tag = me ? "내 마을" : t.ruined() ? "폐허" : "";
                    int tw = tag.isEmpty() ? 0 : ChatUi.width(font, tag, ChatLayout.META_SCALE) + 6;
                    ChatUi.label(g, font, ChatUi.truncate(font, t.display(), w - 8 - tw, ChatLayout.TEXT_SCALE), x + 4, cy + 3, ChatLayout.TEXT_SCALE, t.color());
                    if (!tag.isEmpty()) meta(g, font, tag, x + w - 4 - ChatUi.width(font, tag, ChatLayout.META_SCALE), cy + 4, me ? ChatUi.accent() : FontPalette.dim());
                    String line = "이장 " + t.mayorNick() + " · 주민 " + t.res() + " · 알바 " + t.trusted() + " · " + t.chunks() + "청크";
                    meta(g, font, ChatUi.truncate(font, line, w - 8, ChatLayout.META_SCALE), x + 4, cy + 14, FontPalette.subtle());
                    if (in) HITS.add(new Hit(clip(r, listTop, bottom), () -> {
                        other = t; otherMembers = List.of();
                        go(Page.DETAIL, true);
                        send(TownWire.Query.of(TownWire.Kind.TOWN, t.id(), ""));
                    }));
                }
                cy += h + 3;
            }
        });
    }

    private static void detail(GuiGraphics g, Font font, int x, int y, int w, int bottom, int mx, int my) {
        if (other == null) return;
        y = townCard(g, font, other, x, y, w, true) + 4;
        if (otherMembers.isEmpty()) { meta(g, font, request != null ? "불러오는 중…" : "", x + 4, y + 4, FontPalette.subtle()); return; }
        people(g, font, x, y, w, bottom, mx, my, otherMembers, false, op);
    }

    // ───────── 작은 도구 ─────────

    private interface Body { void draw(int top); }

    private static void scrollList(GuiGraphics g, int x, int y, int w, int bottom, int total, Body body) {
        int viewH = bottom - y;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, total - viewH)));
        GuiUtil.enableScissor(g, x, y, x + w, bottom);
        try { body.draw((int) (y - scroll)); }
        finally { GuiUtil.disableScissor(g); }
    }

    private static ChatLayout.Rect clip(ChatLayout.Rect r, int top, int bottom) {
        int y0 = Math.max(r.y(), top), y1 = Math.min(r.y() + r.height(), bottom);
        return new ChatLayout.Rect(r.x(), y0, r.width(), Math.max(0, y1 - y0));
    }

    private static void meta(GuiGraphics g, Font font, String s, int x, int y, int color) {
        ChatUi.label(g, font, s, x, y, ChatLayout.META_SCALE, color);
    }

    private static void chip(GuiGraphics g, Font font, ChatLayout.Rect r, String text, boolean on, int mx, int my, Runnable run) {
        if (on) ChatUi.roundedFill(g, r.x(), r.y(), r.width(), r.height(), ChatUi.accent());
        else ChatUi.glass(g, ChatGlass.Surface.CARD, r.x(), r.y(), r.width(), r.height(), r.contains(mx, my));
        ChatUi.buttonText(g, font, ChatUi.truncate(font, text, r.width() - 2, ChatLayout.META_SCALE), r.x() + r.width() / 2f, r.y() + r.height() / 2f,
                ChatLayout.META_SCALE, on ? 0xFFFFFFFF : FontPalette.body());
        HITS.add(new Hit(r, () -> { if (request == null) run.run(); }));
    }

    private static void button(GuiGraphics g, Font font, ChatLayout.Rect r, String text, int base, int mx, int my, Runnable run) {
        boolean hot = r.contains(mx, my);
        ChatUi.roundedFill(g, r.x(), r.y(), r.width(), r.height(), hot ? ChatUi.accent() : base);
        ChatUi.buttonText(g, font, ChatUi.truncate(font, text, r.width() - 2, ChatLayout.META_SCALE), r.x() + r.width() / 2f, r.y() + r.height() / 2f,
                ChatLayout.META_SCALE, 0xFFFFFFFF);
        HITS.add(new Hit(r, () -> { if (request == null) { notice = ""; run.run(); } }));
    }

    private static String at(String[] f, int i) { return i < f.length ? f[i] : ""; }
    private static int num(String s) { try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; } }
    private static long lng(String s) { try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return 0; } }

    private static String money(String s) {
        try { return String.format(Locale.ROOT, "%,d", Long.parseLong(s.trim())); }
        catch (NumberFormatException e) { return s; }
    }

    /** &a · &#RRGGBB · § 색 코드 빼기 */
    static String strip(String s) {
        return s == null ? "" : s.replaceAll("(?i)[&§](#[0-9a-f]{6}|[0-9a-fk-or])", "");
    }

    /** 마을 색 코드 → 화면 색 (첫 번째 색만) */
    static int color(String code) {
        if (code != null) {
            var m = java.util.regex.Pattern.compile("(?i)&#([0-9a-f]{6})|&([0-9a-f])").matcher(code);
            if (m.find()) {
                if (m.group(1) != null) return 0xFF000000 | Integer.parseInt(m.group(1), 16);
                ChatFormatting f = ChatFormatting.getByCode(Character.toLowerCase(m.group(2).charAt(0)));
                if (f != null && f.getColor() != null) return 0xFF000000 | f.getColor();
            }
        }
        return 0xFF8FD3FF;
    }

    // ───────── 입력 ─────────

    public static boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return true;
        boolean box = nameBox.mouseClicked(mx, my, button) | searchBox.mouseClicked(mx, my, button);
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
            case MEMBERS -> nameBox.keyPressed(key, scan, modifiers);
            case ALL -> searchBox.keyPressed(key, scan, modifiers);
            default -> false;
        };
    }

    public static boolean charTyped(char c, int modifiers) {
        return switch (page) {
            case MEMBERS -> nameBox.charTyped(c, modifiers);
            case ALL -> searchBox.charTyped(c, modifiers);
            default -> false;
        };
    }
}
