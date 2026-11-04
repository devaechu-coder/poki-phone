package com.november.mcphone.feature.chat.client;

import com.november.mcphone.core.client.FontPalette;
import com.november.mcphone.core.client.GuiUtil;
import com.november.mcphone.core.client.PlayerAvatar;
import com.november.mcphone.feature.camera.client.CameraPhotos;
import com.november.mcphone.feature.sns.client.SnsClientCache;
import com.november.mcphone.feature.sns.net.SnsWire;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 포키 SNS — 채팅 앱 「둘러보기」 탭.
 *
 * 피드(전체 / 내 글) → 글 자세히(좋아요·댓글·신고·차단) → 새 글(글 + 카메라 사진 1장).
 * 클릭 영역은 그릴 때마다 {@link #hits} 에 다시 모은다: 목록이 스크롤되고 높이가 글마다 달라서
 * 미리 계산해 두는 것보다 그린 자리 그대로 쓰는 쪽이 어긋날 일이 없다.
 */
public final class ChatFeed {
    public enum Action { CHATS, CONTACTS, PROFILE, PICK_PHOTO }
    private enum Page { FEED, DETAIL, COMPOSE, BLOCKS }

    private static final int CARD_GAP = 4, PAD = 3;
    private static final int FEED_LINES = 3;
    private static final long STATUS_MS = 4_000L, CONFIRM_MS = 3_000L;

    private final ChatNavigationBar navigation = new ChatNavigationBar();
    private final ChatContactSearch postBox = new ChatContactSearch("mcphone.sns.write_hint", SnsWire.MAX_TEXT);
    private final ChatContactSearch commentBox = new ChatContactSearch("mcphone.sns.comment_hint", SnsWire.MAX_COMMENT);

    private boolean opened, mineOnly;
    private Page page = Page.FEED;
    private final List<SnsWire.Post> posts = new ArrayList<>();
    private int loadedPage = -1;
    private boolean more = true;
    private UUID feedReq, detailReq, actionReq, postReq, blocksReq;
    private final List<SnsWire.Post> blockedUsers = new ArrayList<>();
    private boolean uploading;
    private SnsWire.Post detail;
    private Path photo;
    private String status = "";
    private long statusUntil;
    private String confirm = "";
    private long confirmUntil;
    private double scroll;
    private int contentH, viewH;
    private Action pending;

    private record Hit(ChatLayout.Rect rect, Runnable run) {}
    private final List<Hit> hits = new ArrayList<>();
    /** 사진 누르면 크게 보기 (포키톡 대화방과 같은 것) */
    private final ChatImageViewer viewer = new ChatImageViewer();
    private ChatLayout.Rect view;

    // ── 바깥(PhoneScreen)과 주고받는 것 ─────────────────────

    /** 다른 탭의 아래 바에서 「둘러보기」를 눌렀나 */
    public static boolean consumeDiscover() {
        boolean b = ChatNavigationBar.discoverClicked;
        ChatNavigationBar.discoverClicked = false;
        return b;
    }

    public Action consumeAction() { var a = pending; pending = null; return a; }

    public void open() {
        ChatNavigationBar.discoverClicked = false;
        if (opened) return;
        opened = true; page = Page.FEED; mineOnly = false;
        refresh();
    }

    public void close() {
        SnsClientCache.cancel(feedReq); SnsClientCache.cancel(detailReq); SnsClientCache.cancel(actionReq);
        // 올리는 중인 글의 티켓은 놔둔다: 사진 업로드 정리는 SnsClientCache 가 따로 함
        feedReq = detailReq = actionReq = postReq = null;
        uploading = false;
        SnsClientCache.cancel(blocksReq); blocksReq = null; blockedUsers.clear();
        posts.clear(); detail = null; photo = null; loadedPage = -1; more = true;
        postBox.reset(); commentBox.reset(); navigation.clear(); hits.clear(); viewer.dismiss();
        status = ""; confirm = ""; scroll = 0; page = Page.FEED; opened = false; pending = null;
    }

    /** 카메라 사진 고르기 화면에서 돌아옴 */
    public void setPhoto(Path picked) {
        if (picked == null) return;
        if (!CameraPhotos.isCamera(picked)) { say("camera_only"); return; }
        photo = picked;
    }

    /** 뒤로. false 면 채팅 첫 화면으로 */
    public boolean back() {
        if (viewer.dismiss()) return true;
        if (page == Page.FEED) return false;
        if (page == Page.COMPOSE && uploading) return true;
        go(Page.FEED);
        return true;
    }

    // ── 요청 ─────────────────────

    private static String tr(String key) { return Component.translatable("mcphone.sns." + key).getString(); }

    private void say(String key) { status = tr(key); statusUntil = System.currentTimeMillis() + STATUS_MS; }

    private void sayStatus(SnsWire.Status s) {
        status = Component.translatable(s.key()).getString();
        statusUntil = System.currentTimeMillis() + STATUS_MS;
    }

    private static SnsWire.Query query(SnsWire.Kind kind, UUID post, UUID target, String text, boolean flag, int page) {
        return new SnsWire.Query(UUID.randomUUID(), kind, post, target, text, flag, page);
    }

    private void refresh() {
        SnsClientCache.cancel(feedReq); feedReq = null;
        loadedPage = -1; more = true; scroll = 0;
        loadMore();
    }

    private void loadMore() {
        if (feedReq != null || !more) return;
        feedReq = SnsClientCache.send(query(mineOnly ? SnsWire.Kind.MINE : SnsWire.Kind.FEED, null, null, "", false, loadedPage + 1));
    }

    private void openBlocks() {
        go(Page.BLOCKS);
        SnsClientCache.cancel(blocksReq);
        blocksReq = SnsClientCache.send(query(SnsWire.Kind.BLOCKS, null, null, "", false, 0));
    }

    private void openDetail(SnsWire.Post p) {
        detail = p; go(Page.DETAIL);
        SnsClientCache.cancel(detailReq);
        detailReq = SnsClientCache.send(query(SnsWire.Kind.DETAIL, p.id(), null, "", false, 0));
    }

    private void action(SnsWire.Kind kind, UUID post, UUID target, String text) {
        if (actionReq != null) return;
        actionReq = SnsClientCache.send(query(kind, post, target, text, false, 0));
        if (actionReq == null) say("unavailable");
    }

    private boolean confirmed(String key) {
        long now = System.currentTimeMillis();
        if (key.equals(confirm) && now < confirmUntil) { confirm = ""; return true; }
        confirm = key; confirmUntil = now + CONFIRM_MS;
        return false;
    }

    private void submitPost() {
        if (postReq != null) return;
        String text = postBox.query().trim();
        if (text.isEmpty() && photo == null) { say("empty"); return; }
        if (photo != null && !CameraPhotos.isCamera(photo)) { say("camera_only"); photo = null; return; }
        postReq = SnsClientCache.send(query(SnsWire.Kind.POST, null, null, text, photo != null, 0));
        if (postReq == null) say("unavailable");
    }

    private void submitComment() {
        if (detail == null) return;
        String text = commentBox.query().trim();
        if (text.isEmpty()) return;
        action(SnsWire.Kind.COMMENT, detail.id(), null, text);
    }

    private void go(Page next) {
        page = next; scroll = 0; confirm = "";
        postBox.close(); commentBox.close(); navigation.clear(); hits.clear();
        if (next != Page.DETAIL) { SnsClientCache.cancel(detailReq); detailReq = null; }
    }

    private void replace(SnsWire.Post p) {
        for (int i = 0; i < posts.size(); i++) if (posts.get(i).id().equals(p.id())) {
            // 피드 쪽엔 댓글 목록을 들고 있지 않는다
            posts.set(i, p.withComments(List.of()));
        }
        if (detail != null && detail.id().equals(p.id()))
            detail = p.comments().isEmpty() && !detail.comments().isEmpty() && p.commentCount() > 0
                    ? p.withComments(detail.comments())
                    : p;
    }

    private void remove(UUID id) {
        posts.removeIf(p -> p.id().equals(id));
        if (detail != null && detail.id().equals(id)) { detail = null; if (page == Page.DETAIL) go(Page.FEED); }
    }

    private void poll() {
        var r = SnsClientCache.consume(feedReq);
        if (r != null) {
            feedReq = null;
            if (r.status() == SnsWire.Status.OK) {
                if (r.page() == 0) posts.clear();
                posts.addAll(r.posts());
                loadedPage = r.page(); more = r.more();
            } else { more = false; if (r.status() != SnsWire.Status.BUSY) sayStatus(r.status()); else more = true; }
        } else if (feedReq != null && !SnsClientCache.waiting(feedReq)) { feedReq = null; more = false; say("timeout"); }

        r = SnsClientCache.consume(detailReq);
        if (r != null) {
            detailReq = null;
            if (r.status() == SnsWire.Status.OK && !r.posts().isEmpty()) { detail = r.posts().get(0); replace(detail); }
            else if (r.status() == SnsWire.Status.GONE) { say("gone"); remove(detail == null ? null : detail.id()); }
            else sayStatus(r.status());
        } else if (detailReq != null && !SnsClientCache.waiting(detailReq)) detailReq = null;

        r = SnsClientCache.consume(actionReq);
        if (r != null) {
            actionReq = null;
            if (r.status() == SnsWire.Status.GONE) { say("gone"); if (detail != null) remove(detail.id()); }
            else if (r.status() != SnsWire.Status.OK) sayStatus(r.status());
            else switch (r.kind()) {
                case LIKE, DELETE_COMMENT -> { if (!r.posts().isEmpty()) replace(r.posts().get(0)); }
                case COMMENT -> { if (!r.posts().isEmpty()) replace(r.posts().get(0)); commentBox.reset(); scroll = 1e9; }
                case DELETE_POST -> { say("deleted"); if (detail != null) remove(detail.id()); }
                case REPORT -> say("reported");
                case BLOCK -> { say("blocked"); go(Page.FEED); refresh(); }
                case UNBLOCK -> { say("unblocked"); openBlocks(); refresh(); }
                default -> {}
            }
        } else if (actionReq != null && !SnsClientCache.waiting(actionReq)) { actionReq = null; say("timeout"); }

        r = SnsClientCache.consume(blocksReq);
        if (r != null) {
            blocksReq = null;
            if (r.status() == SnsWire.Status.OK) { blockedUsers.clear(); blockedUsers.addAll(r.posts()); }
            else sayStatus(r.status());
        } else if (blocksReq != null && !SnsClientCache.waiting(blocksReq)) { blocksReq = null; say("timeout"); }

        r = SnsClientCache.consume(postReq);
        if (r != null) {
            if (r.status() == SnsWire.Status.PENDING) {
                if (photo != null && !uploading) { uploading = true; ChatImageSender.sendSns(photo); }
            } else {
                postReq = null; uploading = false;
                if (r.status() == SnsWire.Status.OK) {
                    say("posted");
                    postBox.reset(); photo = null;
                    if (!r.posts().isEmpty()) posts.add(0, r.posts().get(0));
                    go(Page.FEED);
                } else sayStatus(r.status());
            }
        } else if (postReq != null && !SnsClientCache.waiting(postReq)) { postReq = null; uploading = false; say("timeout"); }
    }

    // ── 그리기 ─────────────────────

    private float partial;

    public void render(GuiGraphics g, int left, int top, int sw, int sh, int statusH, int navH, int mx, int my, Font font, float tick) {
        if (!opened) return;
        partial = tick;
        poll();
        hits.clear();
        ChatImageCache.beginFrame(SnsWire.PEER);
        int x = left + 4, w = sw - 8, y = top + statusH + ChatLayout.TOP_GAP;
        int bottom = top + sh - navH - (page == Page.FEED ? ChatLayout.TAB_HEIGHT : 2);

        // 머리줄
        String title = switch (page) { case FEED -> tr("title"); case DETAIL -> tr("detail"); case COMPOSE -> tr("compose"); case BLOCKS -> tr("blocks"); };
        if (page != Page.FEED) {
            var back = new ChatLayout.Rect(x, y, 12, ChatLayout.HEADER_HEIGHT);
            ChatUi.sizedIcon(g, ChatUi.Icon.BACK, x + 2.5f, y + 2.5f, 7, back.contains(mx, my) ? ChatUi.accent() : FontPalette.body());
            hits.add(new Hit(back, () -> back()));
        } else {
            var write = new ChatLayout.Rect(x + w - 12, y, 12, ChatLayout.HEADER_HEIGHT);
            ChatUi.sizedIcon(g, ChatUi.Icon.PLUS, x + w - 9.5f, y + 2.5f, 7, write.contains(mx, my) ? ChatUi.accent() : FontPalette.body());
            hits.add(new Hit(write, () -> go(Page.COMPOSE)));
        }
        ChatUi.buttonText(g, font, title, x + w / 2f, y + ChatLayout.HEADER_HEIGHT / 2f, ChatLayout.TEXT_SCALE, FontPalette.title());
        y += ChatLayout.HEADER_HEIGHT + 3;

        // 상태 한 줄
        boolean showStatus = !status.isEmpty() && System.currentTimeMillis() < statusUntil;
        int statusLine = showStatus ? 9 : 0;

        switch (page) {
            case FEED -> feed(g, font, x, y, w, bottom - statusLine, mx, my);
            case DETAIL -> detailPage(g, font, x, y, w, bottom - statusLine, mx, my, tick);
            case COMPOSE -> compose(g, font, x, y, w, bottom - statusLine, mx, my, tick);
            case BLOCKS -> blocks(g, font, x, y, w, bottom - statusLine, mx, my);
        }
        if (showStatus)
            ChatUi.buttonText(g, font, ChatUi.truncate(font, status, w - 4, ChatLayout.META_SCALE), x + w / 2f, bottom - 4.5f, ChatLayout.META_SCALE, ChatUi.accent());
        if (page == Page.FEED) navigation.render(g, font, x, bottom, w, ChatNavigationBar.Page.DISCOVER);
        if (viewer.isOpen()) {
            hits.clear();
            ChatImageCache.beginFrame(SnsWire.PEER);
            viewer.render(g, font, left, top + statusH, sw, sh - statusH - navH, mx, my);
        }
        ChatImageCache.flushRequests(SnsWire.PEER);
    }

    private static String ago(long time) {
        long s = Math.max(0, (System.currentTimeMillis() - time) / 1000);
        if (s < 60) return tr("ago.now");
        if (s < 3600) return Component.translatable("mcphone.sns.ago.min", s / 60).getString();
        if (s < 86400) return Component.translatable("mcphone.sns.ago.hour", s / 3600).getString();
        return Component.translatable("mcphone.sns.ago.day", s / 86400).getString();
    }

    private static List<FormattedCharSequence> wrap(Font font, String text, int w, float scale) {
        return font.split(Component.literal(text), Math.max(1, ChatLayout.unscaledWidth(w, scale)));
    }

    /** 사진 상자 크기: 가로 w 안, 세로 maxH 안, 비율 유지 */
    private static int[] fit(SnsWire.Post p, int w, int maxH) {
        int pw = Math.max(1, p.width()), ph = Math.max(1, p.height());
        int bw = w, bh = Math.max(8, bw * ph / pw);
        if (bh > maxH) { bh = maxH; bw = Math.max(8, bh * pw / ph); }
        return new int[]{bw, bh};
    }

    // ── 이로치 자랑 카드 ─────────────────────

    private static final int CARD_H = 98, ICON = 40;
    private static final int GOLD = 0xFFE8B323, GOLD_BG = 0xFFFFF6D6, GOLD_TEXT = 0xFFC8820A, PINK = 0xFFE2588F, CARD_INK = 0xFF2B2533, CARD_MUTE = 0xFF7A7286;

    private static String trKey(String key, String fallback) {
        return net.minecraft.client.resources.language.I18n.exists(key) ? Component.translatable(key).getString() : fallback;
    }

    private static String path(String id) { int i = id.indexOf(':'); return i < 0 ? id : id.substring(i + 1); }

    private static String speciesName(String id) {
        String p = path(id);
        return trKey("cobblemon.species." + p + ".name", p.isEmpty() ? "?" : Character.toUpperCase(p.charAt(0)) + p.substring(1));
    }

    private void shinyCard(GuiGraphics g, Font font, String raw, int x, int y, int w) {
        if (view != null && (y + CARD_H < view.y() || y > view.y() + view.height())) return;
        var c = com.november.mcphone.feature.sns.SnsShiny.parse(raw);
        String sp = c.getOrDefault("species", "");
        ChatUi.roundedFill(g, x, y, w, CARD_H, GOLD);
        ChatUi.roundedFill(g, x + 1, y + 1, w - 2, CARD_H - 2, GOLD_BG);
        float cx = x + w / 2f;
        ChatUi.buttonText(g, font, "★ " + tr("shiny_title") + " ★", cx, y + 7, ChatLayout.META_SCALE, GOLD_TEXT);
        int ix = Math.round(cx - ICON / 2f), iy = y + 13;
        g.fill(ix, iy, ix + ICON, iy + ICON, 0x40FFFFFF);
        if (!CobblemonIcon.draw(g, sp, !"0".equals(c.get("shiny")), ix, iy, ICON, partial))
            ChatUi.buttonText(g, font, ChatUi.truncate(font, speciesName(sp), ICON + 20, ChatLayout.TEXT_SCALE), cx, iy + ICON / 2f, ChatLayout.TEXT_SCALE, CARD_MUTE);
        int ty = iy + ICON + 6;
        String name = speciesName(sp) + (c.containsKey("lv") ? "  Lv." + c.get("lv") : "");
        ChatUi.buttonText(g, font, ChatUi.truncate(font, name, w - 6, ChatLayout.TEXT_SCALE), cx, ty, ChatLayout.TEXT_SCALE, CARD_INK);
        ty += 9;
        StringBuilder info = new StringBuilder();
        String gender = c.getOrDefault("gender", "");
        if (gender.equals("MALE")) info.append("♂"); else if (gender.equals("FEMALE")) info.append("♀");
        if (c.containsKey("nature")) { if (info.length() > 0) info.append(" · "); info.append(trKey("cobblemon.nature." + path(c.get("nature")), path(c.get("nature")))); }
        if (c.containsKey("ability")) { if (info.length() > 0) info.append(" · "); info.append(trKey("cobblemon.ability." + path(c.get("ability")), path(c.get("ability")))); }
        ChatUi.buttonText(g, font, ChatUi.truncate(font, info.toString(), w - 6, ChatLayout.META_SCALE), cx, ty, ChatLayout.META_SCALE, CARD_MUTE);
        ty += 8;
        if (c.containsKey("iv")) {
            int iv = 0; try { iv = Integer.parseInt(c.get("iv")); } catch (NumberFormatException ignored) {}
            ChatUi.buttonText(g, font, Component.translatable("mcphone.sns.shiny_iv", iv, Math.round(iv * 100f / 186)).getString(), cx, ty, ChatLayout.META_SCALE, PINK);
            ty += 8;
        }
        StringBuilder where = new StringBuilder();
        if (c.containsKey("ball")) {
            var rl = ResourceLocation.tryParse(c.get("ball"));
            if (rl != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(rl))
                where.append(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl).getDescription().getString());
        }
        if (c.containsKey("biome")) {
            var rl = ResourceLocation.tryParse(c.get("biome"));
            if (rl != null) { if (where.length() > 0) where.append(" · "); where.append(trKey("biome." + rl.getNamespace() + "." + rl.getPath(), rl.getPath())); }
        }
        if (where.length() > 0)
            ChatUi.buttonText(g, font, ChatUi.truncate(font, where.toString(), w - 6, ChatLayout.META_SCALE), cx, ty, ChatLayout.META_SCALE, CARD_INK);
    }

    private void image(GuiGraphics g, Font font, UUID id, int x, int y, int w, int h) {
        if (view != null && (y + h < view.y() || y > view.y() + view.height())) return;
        ChatImageCache.declare(id, 1, 0);
        var tex = ChatImageCache.get(id);
        if (tex != null) {
            ChatImageViewer.drawImage(g, tex, id, x, y, w, h);
            hitIn(new ChatLayout.Rect(x, y, w, h), () -> viewer.open(id));
            return;
        }
        g.fill(x, y, x + w, y + h, 0x30000000);
        String hint = ChatImageCache.status(id) == ChatImageCache.Status.GONE ? tr("image_gone") : "…";
        ChatUi.buttonText(g, font, hint, x + w / 2f, y + h / 2f, ChatLayout.META_SCALE, FontPalette.subtle());
    }

    private void beginScroll(GuiGraphics g, int x, int y, int w, int h) {
        view = new ChatLayout.Rect(x, y, w, Math.max(0, h));
        viewH = view.height();
        double max = Math.max(0, contentH - viewH);
        if (scroll > max) scroll = max;
        if (scroll < 0) scroll = 0;
        GuiUtil.enableScissor(g, x, y, x + w, y + view.height());
    }

    private void endScroll(GuiGraphics g, int used) {
        GuiUtil.disableScissor(g);
        contentH = used;
    }

    /** 스크롤 안쪽 클릭 영역: 보이는 부분만 */
    private void hitIn(ChatLayout.Rect r, Runnable run) {
        if (view == null) { hits.add(new Hit(r, run)); return; }
        int top = Math.max(r.y(), view.y()), bot = Math.min(r.y() + r.height(), view.y() + view.height());
        if (bot > top) hits.add(new Hit(new ChatLayout.Rect(r.x(), top, r.width(), bot - top), run));
    }

    private void feed(GuiGraphics g, Font font, int x, int y, int w, int bottom, int mx, int my) {
        // 전체 / 내 글
        int half = (w - 2) / 2;
        for (int i = 0; i < 2; i++) {
            var chip = new ChatLayout.Rect(x + i * (half + 2), y, half, 11);
            boolean on = (i == 1) == mineOnly;
            ChatUi.glass(g, ChatGlass.Surface.BUTTON, chip.x(), chip.y(), chip.width(), chip.height(), chip.contains(mx, my));
            ChatUi.buttonText(g, font, tr(i == 0 ? "tab.all" : "tab.mine"), chip.x() + chip.width() / 2f, chip.y() + 5.5f,
                    ChatLayout.META_SCALE, on ? ChatUi.accent() : FontPalette.subtle());
            boolean mine = i == 1;
            hits.add(new Hit(chip, () -> { if (mineOnly != mine) { mineOnly = mine; posts.clear(); refresh(); } }));
        }
        y += 14;
        if (mineOnly) {
            var manage = new ChatLayout.Rect(x, y, w, 12);
            ChatProfileUi.row(g, font, manage, tr("blocks"), null, mx, my);
            hits.add(new Hit(manage, this::openBlocks));
            y += 15;
        }

        beginScroll(g, x, y, w, bottom - y - 1);
        int cy = (int) (y - scroll);
        int lh = ChatUi.lineHeight(font, ChatLayout.TEXT_SCALE) + 1;
        for (SnsWire.Post p : posts) {
            var lines = p.text().isEmpty() ? List.<FormattedCharSequence>of() : wrap(font, p.text(), w - PAD * 2, ChatLayout.TEXT_SCALE);
            int shown = Math.min(FEED_LINES, lines.size());
            int[] box = p.hasImage() ? fit(p, w - PAD * 2, 84) : null;
            int cardH = p.hasCard() ? CARD_H + 3 : 0;
            int h = PAD + 13 + shown * lh + (lines.size() > shown ? lh : 0) + (box != null ? box[1] + 3 : 0) + cardH + 11;
            var card = new ChatLayout.Rect(x, cy, w, h);
            if (cy + h >= y && cy <= y + viewH) {
                hitIn(card, () -> openDetail(p));
                ChatUi.glass(g, ChatGlass.Surface.CARD, x, cy, w, h, card.contains(mx, my) && view.contains(mx, my));
                int iy = cy + PAD;
                PlayerAvatar.draw(g, p.author(), x + PAD, iy, 10);
                String when = ago(p.time());
                int whenW = ChatUi.width(font, when, ChatLayout.META_SCALE);
                ChatUi.label(g, font, ChatUi.truncate(font, p.name(), w - PAD * 2 - 16 - whenW, ChatLayout.TEXT_SCALE), x + PAD + 13, iy + 5, ChatLayout.TEXT_SCALE, FontPalette.title());
                ChatUi.label(g, font, when, x + w - PAD - whenW, iy + 5, ChatLayout.META_SCALE, FontPalette.timestamp());
                iy += 13;
                for (int i = 0; i < shown; i++) { ChatUi.text(g, font, lines.get(i), x + PAD, iy, ChatLayout.TEXT_SCALE, FontPalette.body()); iy += lh; }
                if (lines.size() > shown) { ChatUi.text(g, font, tr("more_text"), x + PAD, iy, ChatLayout.META_SCALE, FontPalette.subtle()); iy += lh; }
                if (box != null) { image(g, font, p.image(), x + (w - box[0]) / 2, iy, box[0], box[1]); iy += box[1] + 3; }
                if (p.hasCard()) { shinyCard(g, font, p.card(), x + PAD, iy, w - PAD * 2); iy += cardH; }
                // 아래줄: ♥ n   댓글 n
                String heart = (p.liked() ? "♥ " : "♡ ") + p.likes();
                var like = new ChatLayout.Rect(x + PAD - 1, iy - 1, ChatUi.width(font, heart, ChatLayout.META_SCALE) + 4, 10);
                ChatUi.label(g, font, heart, x + PAD, iy + 4, ChatLayout.META_SCALE, p.liked() ? 0xFFE8608A : FontPalette.subtle());
                ChatUi.label(g, font, Component.translatable("mcphone.sns.comments_n", p.commentCount()).getString(),
                        like.x() + like.width() + 6, iy + 4, ChatLayout.META_SCALE, FontPalette.subtle());
                hitIn(like, () -> action(SnsWire.Kind.LIKE, p.id(), null, ""));
            }
            cy += h + CARD_GAP;
        }
        if (posts.isEmpty() && feedReq == null) {
            for (var line : wrap(font, tr(mineOnly ? "empty_mine" : "empty_feed"), w - 10, ChatLayout.META_SCALE)) {
                ChatUi.text(g, font, line, x + 5, cy + 6, ChatLayout.META_SCALE, FontPalette.subtle());
                cy += ChatUi.lineHeight(font, ChatLayout.META_SCALE) + 2;
            }
        } else if (feedReq != null || more) {
            ChatUi.buttonText(g, font, tr("loading"), x + w / 2f, cy + 5, ChatLayout.META_SCALE, FontPalette.subtle());
            cy += 12;
        }
        endScroll(g, (int) (cy + scroll - y));
        // 끝까지 내려오면 다음 쪽
        if (more && feedReq == null && contentH - scroll - viewH < 30) loadMore();
    }

    private void detailPage(GuiGraphics g, Font font, int x, int y, int w, int bottom, int mx, int my, float tick) {
        SnsWire.Post p = detail;
        if (p == null) { go(Page.FEED); return; }
        int inputY = bottom - 15;
        beginScroll(g, x, y, w, inputY - y - 2);
        int cy = (int) (y - scroll);
        int lh = ChatUi.lineHeight(font, ChatLayout.TEXT_SCALE) + 1;
        int mh = ChatUi.lineHeight(font, ChatLayout.META_SCALE) + 1;

        PlayerAvatar.draw(g, p.author(), x + 2, cy, 12);
        ChatUi.label(g, font, ChatUi.truncate(font, p.name(), w - 20, ChatLayout.TEXT_SCALE), x + 17, cy + 3, ChatLayout.TEXT_SCALE, FontPalette.title());
        ChatUi.label(g, font, ago(p.time()), x + 17, cy + 10, ChatLayout.META_SCALE, FontPalette.timestamp());
        cy += 16;
        for (var line : wrap(font, p.text(), w - 4, ChatLayout.TEXT_SCALE)) { ChatUi.text(g, font, line, x + 2, cy, ChatLayout.TEXT_SCALE, FontPalette.body()); cy += lh; }
        if (p.hasCard()) { cy += 2; shinyCard(g, font, p.card(), x, cy, w); cy += CARD_H + 2; }
        if (p.hasImage()) {
            int[] box = fit(p, w, 130);
            cy += 2;
            image(g, font, p.image(), x + (w - box[0]) / 2, cy, box[0], box[1]);
            cy += box[1] + 2;
        }
        cy += 2;

        // 버튼줄
        List<String> labels = new ArrayList<>();
        List<Runnable> runs = new ArrayList<>();
        labels.add((p.liked() ? "♥ " : "♡ ") + p.likes());
        runs.add(() -> action(SnsWire.Kind.LIKE, p.id(), null, ""));
        if (p.mine()) {
            labels.add(confirmLabel("delete"));
            runs.add(() -> { if (confirmed("delete")) action(SnsWire.Kind.DELETE_POST, p.id(), null, ""); });
        }
        if (!isSelf(p.author())) {
            labels.add(confirmLabel("report"));
            runs.add(() -> { if (confirmed("report")) action(SnsWire.Kind.REPORT, p.id(), null, ""); });
            labels.add(confirmLabel("block"));
            runs.add(() -> { if (confirmed("block")) action(SnsWire.Kind.BLOCK, null, p.author(), ""); });
        }
        int bw = (w - (labels.size() - 1) * 2) / labels.size();
        for (int i = 0; i < labels.size(); i++) {
            var b = new ChatLayout.Rect(x + i * (bw + 2), cy, bw, 12);
            if (cy + 12 >= y && cy <= y + viewH) {
                int color = i == 0 && p.liked() ? 0xFFE8608A : labels.get(i).equals(tr("confirm")) ? ChatUi.accent() : FontPalette.body();
                ChatUi.glass(g, ChatGlass.Surface.BUTTON, b.x(), b.y(), b.width(), b.height(), b.contains(mx, my) && view.contains(mx, my));
                ChatUi.buttonText(g, font, ChatUi.truncate(font, labels.get(i), bw - 3, ChatLayout.META_SCALE), b.x() + bw / 2f, b.y() + 6, ChatLayout.META_SCALE, color);
            }
            hitIn(b, runs.get(i));
        }
        cy += 16;

        ChatUi.text(g, font, Component.translatable("mcphone.sns.comments_n", p.commentCount()).getString(), x + 2, cy, ChatLayout.META_SCALE, FontPalette.subtle());
        cy += mh + 2;
        for (SnsWire.Comment c : p.comments()) {
            String head = c.name() + "  " + ago(c.time());
            ChatUi.text(g, font, ChatUi.truncate(font, head, w - 18, ChatLayout.META_SCALE), x + 2, cy, ChatLayout.META_SCALE, FontPalette.title());
            if (c.mine()) {
                String del = confirm.equals("c" + c.id()) && System.currentTimeMillis() < confirmUntil ? tr("confirm_short") : "×";
                int dw = ChatUi.width(font, del, ChatLayout.META_SCALE);
                var d = new ChatLayout.Rect(x + w - dw - 4, cy - 1, dw + 4, mh + 1);
                ChatUi.text(g, font, del, x + w - dw - 2, cy, ChatLayout.META_SCALE, d.contains(mx, my) ? ChatUi.accent() : FontPalette.subtle());
                UUID cid = c.id();
                hitIn(d, () -> { if (confirmed("c" + cid)) action(SnsWire.Kind.DELETE_COMMENT, p.id(), cid, ""); });
            }
            cy += mh;
            for (var line : wrap(font, c.text(), w - 6, ChatLayout.TEXT_SCALE)) { ChatUi.text(g, font, line, x + 4, cy, ChatLayout.TEXT_SCALE, FontPalette.body()); cy += lh; }
            cy += 3;
        }
        if (p.comments().isEmpty() && detailReq == null)
            { ChatUi.text(g, font, tr("no_comments"), x + 2, cy, ChatLayout.META_SCALE, FontPalette.dim()); cy += mh; }
        endScroll(g, (int) (cy + scroll - y) + 2);

        // 댓글 입력
        int sendW = 22;
        commentBox.render(g, font, new ChatLayout.Rect(x, inputY, w - sendW - 2, 14), mx, my, tick);
        var send = new ChatLayout.Rect(x + w - sendW, inputY, sendW, 14);
        ChatProfileUi.button(g, font, send, tr("send"), mx, my, actionReq == null ? ChatUi.accent() : FontPalette.dim());
        hits.add(new Hit(send, this::submitComment));
    }

    private void blocks(GuiGraphics g, Font font, int x, int y, int w, int bottom, int mx, int my) {
        beginScroll(g, x, y, w, bottom - y - 1);
        int cy = (int) (y - scroll);
        for (SnsWire.Post u : blockedUsers) {
            var row = new ChatLayout.Rect(x, cy, w, 16);
            if (cy + 16 >= y && cy <= y + viewH) {
                ChatUi.glass(g, ChatGlass.Surface.CARD, x, cy, w, 16, false);
                PlayerAvatar.draw(g, u.author(), x + 3, cy + 3, 10);
                ChatUi.label(g, font, ChatUi.truncate(font, u.name(), w - 48, ChatLayout.TEXT_SCALE), x + 16, cy + 8, ChatLayout.TEXT_SCALE, FontPalette.title());
                var b = new ChatLayout.Rect(x + w - 30, cy + 2, 28, 12);
                ChatProfileUi.button(g, font, b, tr("unblock"), mx, my, ChatUi.accent());
                UUID id = u.author();
                hitIn(b, () -> action(SnsWire.Kind.UNBLOCK, null, id, ""));
            }
            cy += 18;
        }
        if (blockedUsers.isEmpty())
            ChatUi.buttonText(g, font, tr(blocksReq != null ? "loading" : "no_blocks"), x + w / 2f, cy + 8, ChatLayout.META_SCALE, FontPalette.subtle());
        endScroll(g, (int) (cy + scroll - y) + 20);
    }

    private String confirmLabel(String key) {
        return key.equals(confirm) && System.currentTimeMillis() < confirmUntil ? tr("confirm") : tr(key);
    }

    private static boolean isSelf(UUID id) {
        var p = Minecraft.getInstance().player;
        return p != null && p.getUUID().equals(id);
    }

    private void compose(GuiGraphics g, Font font, int x, int y, int w, int bottom, int mx, int my, float tick) {
        view = null;
        postBox.render(g, font, new ChatLayout.Rect(x, y, w, 14), mx, my, tick);
        y += 16;
        String text = postBox.query();
        ChatUi.text(g, font, text.length() + " / " + SnsWire.MAX_TEXT, x + w - 2 - ChatUi.width(font, text.length() + " / " + SnsWire.MAX_TEXT, ChatLayout.META_SCALE), y, ChatLayout.META_SCALE, FontPalette.dim());
        y += 8;
        int lh = ChatUi.lineHeight(font, ChatLayout.TEXT_SCALE) + 1;
        int previewBottom = bottom - 50;
        GuiUtil.enableScissor(g, x, y, x + w, Math.max(y, previewBottom));
        try {
            int py = y;
            for (var line : wrap(font, text.isEmpty() ? tr("write_preview") : text, w - 4, ChatLayout.TEXT_SCALE)) {
                ChatUi.text(g, font, line, x + 2, py, ChatLayout.TEXT_SCALE, text.isEmpty() ? FontPalette.dim() : FontPalette.body());
                py += lh;
            }
        } finally { GuiUtil.disableScissor(g); }

        int py = previewBottom + 2;
        var pick = new ChatLayout.Rect(x, py, photo == null ? w : w - 26, 14);
        String pickLabel = photo == null ? tr("pick_photo") : tr("photo_chosen");
        ChatProfileUi.button(g, font, pick, pickLabel, mx, my, photo == null ? FontPalette.body() : ChatUi.accent());
        if (!uploading) hits.add(new Hit(pick, () -> pending = Action.PICK_PHOTO));
        if (photo != null) {
            var drop = new ChatLayout.Rect(x + w - 24, py, 24, 14);
            ChatProfileUi.button(g, font, drop, tr("remove_photo"), mx, my, FontPalette.subtle());
            if (!uploading) hits.add(new Hit(drop, () -> photo = null));
        }
        py += 16;
        for (var line : wrap(font, tr("camera_note"), w - 4, ChatLayout.META_SCALE)) {
            ChatUi.text(g, font, line, x + 2, py, ChatLayout.META_SCALE, FontPalette.dim());
            py += ChatUi.lineHeight(font, ChatLayout.META_SCALE) + 1;
        }
        var submit = new ChatLayout.Rect(x, bottom - 15, w, 14);
        ChatProfileUi.button(g, font, submit, tr(postReq != null ? (uploading ? "uploading" : "loading") : "submit"), mx, my,
                postReq == null ? ChatUi.accent() : FontPalette.dim());
        hits.add(new Hit(submit, this::submitPost));
    }

    // ── 입력 ─────────────────────

    public boolean mouseClicked(double mx, double my, int button) {
        if (!opened) return false;
        if (viewer.mouseClicked(button)) return true;
        if (page == Page.FEED && button == 0) {
            var nav = navigation.click(mx, my, button);
            if (nav != null) {
                ChatNavigationBar.discoverClicked = false;
                switch (nav) {
                    case CHATS -> pending = Action.CHATS;
                    case CONTACTS -> pending = Action.CONTACTS;
                    case PROFILE -> pending = Action.PROFILE;
                    case DISCOVER -> { scroll = 0; refresh(); }
                }
                return true;
            }
        }
        boolean box = page == Page.COMPOSE ? postBox.mouseClicked(mx, my, button)
                : page == Page.DETAIL && commentBox.mouseClicked(mx, my, button);
        if (box || button != 0) return true;
        // 나중에 그린 것(버튼)이 위에 있으니 뒤에서부터
        for (int i = hits.size() - 1; i >= 0; i--) {
            var h = hits.get(i);
            if (h.rect().contains(mx, my)) { h.run().run(); return true; }
        }
        return true;
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!opened || page == Page.COMPOSE) return false;
        if (viewer.isOpen()) return true;
        scroll -= amount * 14;
        if (scroll < 0) scroll = 0;
        return true;
    }

    public boolean keyPressed(int key, int scan, int modifiers) {
        if (!opened) return false;
        var box = page == Page.COMPOSE ? postBox : page == Page.DETAIL ? commentBox : null;
        if (box == null || !box.focused()) return false;
        if (key == 257 || key == 335) { // Enter
            if (page == Page.DETAIL) submitComment();
            return true;
        }
        return box.keyPressed(key, scan, modifiers);
    }

    public boolean charTyped(char c, int modifiers) {
        if (!opened) return false;
        if (page == Page.COMPOSE) return postBox.charTyped(c, modifiers);
        if (page == Page.DETAIL) return commentBox.charTyped(c, modifiers);
        return false;
    }
}
