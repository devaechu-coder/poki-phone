package com.november.mcphone.feature.sns.net;

import com.november.mcphone.MCphone;
import com.november.mcphone.feature.chat.ChatImageStore;
import com.november.mcphone.feature.chat.FriendGuard;
import com.november.mcphone.feature.sns.SnsData;
import com.november.mcphone.feature.sns.SnsFilter;
import com.november.mcphone.util.TextSanitizer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/** 서버 쪽 SNS 규칙. 네트워크 쪽이 이미 주 스레드로 옮겨 준 뒤에 불린다. */
public final class SnsServer {
    private SnsServer() {}

    /** 신고 몇 번이면 검토함으로 */
    public static final int REPORT_LIMIT = 3;
    private static final long POST_COOLDOWN_MS = 60_000L;
    private static final int POSTS_PER_DAY = 10;
    private static final long COMMENT_COOLDOWN_MS = 5_000L;
    private static final long ANY_COOLDOWN_MS = 150L;
    private static final long PENDING_MS = 30_000L;
    private static final int MAX_COMMENTS = 100;

    private static BiConsumer<ServerPlayer, SnsWire.Reply> reply;
    private record Pending(UUID request, String text, long time) {}
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Long> LAST_ANY = new HashMap<>(), LAST_POST = new HashMap<>(), LAST_COMMENT = new HashMap<>();
    private static final Map<UUID, String> LAST_TEXT = new HashMap<>();
    private static final Map<UUID, List<Long>> DAY = new HashMap<>();

    public static void install(BiConsumer<ServerPlayer, SnsWire.Reply> sender) { reply = sender; }

    public static void onLogout(UUID id) { PENDING.remove(id); LAST_ANY.remove(id); }

    private static boolean staff(ServerPlayer p) { return p.hasPermissions(2); }

    public static void handle(SnsWire.Query q, ServerPlayer player) {
        SnsWire.Reply out;
        try {
            out = run(q, player);
        } catch (SecurityException e) { out = SnsWire.Reply.of(q, SnsWire.Status.FORBIDDEN); }
        catch (IllegalArgumentException e) { out = SnsWire.Reply.of(q, SnsWire.Status.INVALID); }
        catch (RuntimeException e) { MCphone.LOGGER.error("포키 SNS 요청 처리 실패", e); out = SnsWire.Reply.of(q, SnsWire.Status.UNAVAILABLE); }
        if (out != null) reply.accept(player, out);
    }

    private static SnsWire.Reply run(SnsWire.Query q, ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (player.hasDisconnected() || server.getPlayerList().getPlayer(player.getUUID()) != player || !FriendGuard.carriesPhone(player))
            throw new SecurityException();
        UUID me = player.getUUID();
        long now = System.currentTimeMillis();
        if (now - LAST_ANY.getOrDefault(me, 0L) < ANY_COOLDOWN_MS) return SnsWire.Reply.of(q, SnsWire.Status.BUSY);
        LAST_ANY.put(me, now);

        SnsData data = SnsData.get(server);
        sweep(server, data, now);

        return switch (q.kind()) {
            case FEED, MINE -> feed(q, player, data);
            case DETAIL -> detail(q, player, data, SnsWire.Status.OK);
            case POST -> post(q, player, data, now);
            case LIKE -> {
                var p = visible(data, q.post(), me);
                if (p == null) yield SnsWire.Reply.of(q, SnsWire.Status.GONE);
                if (!p.likes.remove(me)) p.likes.add(me);
                data.setDirty();
                yield single(q, player, data, p, false);
            }
            case COMMENT -> comment(q, player, data, now);
            case DELETE_POST -> {
                var p = data.find(q.post());
                if (p == null) yield SnsWire.Reply.of(q, SnsWire.Status.GONE);
                if (!p.author.equals(me) && !staff(player)) throw new SecurityException();
                delete(server, data, p);
                yield SnsWire.Reply.of(q, SnsWire.Status.OK);
            }
            case DELETE_COMMENT -> {
                var p = data.find(q.post());
                if (p == null) yield SnsWire.Reply.of(q, SnsWire.Status.GONE);
                boolean own = p.author.equals(me) || staff(player);
                boolean removed = p.comments.removeIf(c -> c.id.equals(q.target()) && (own || c.author.equals(me)));
                if (removed) data.setDirty();
                yield detail(q, player, data, removed ? SnsWire.Status.OK : SnsWire.Status.FORBIDDEN);
            }
            case REPORT -> {
                var p = data.find(q.post());
                if (p == null) yield SnsWire.Reply.of(q, SnsWire.Status.GONE);
                if (p.author.equals(me)) throw new IllegalArgumentException();
                if (p.reports.add(me)) {
                    data.setDirty();
                    if (!p.held && p.reports.size() >= REPORT_LIMIT) {
                        p.held = true;
                        notifyStaff(server, p);
                    }
                }
                yield SnsWire.Reply.of(q, SnsWire.Status.OK);
            }
            case BLOCKS -> {
                // 차단 목록: 글 자리에 (id=author=차단한 사람, name) 만 채워서 보냄
                List<SnsWire.Post> out = new ArrayList<>();
                for (UUID id : data.blocked(me)) {
                    if (out.size() >= 60) break;
                    out.add(new SnsWire.Post(id, id, nameOf(server, data, id), 0L, "", null, 0, 0, 0, false, 0, false, List.of()));
                }
                yield new SnsWire.Reply(q.request(), q.kind(), SnsWire.Status.OK, 0, false, out);
            }
            case BLOCK, UNBLOCK -> {
                if (SnsWire.none(q.target()) || q.target().equals(me)) throw new IllegalArgumentException();
                data.block(me, q.target(), q.kind() == SnsWire.Kind.BLOCK);
                yield SnsWire.Reply.of(q, SnsWire.Status.OK);
            }
        };
    }

    private static String nameOf(MinecraftServer server, SnsData data, UUID id) {
        var online = server.getPlayerList().getPlayer(id);
        if (online != null) return clean(online.getName().getString(), SnsWire.NAME_LENGTH);
        try {
            var cache = server.getProfileCache();
            if (cache != null) { var gp = cache.get(id); if (gp.isPresent()) return clean(gp.get().getName(), SnsWire.NAME_LENGTH); }
        } catch (RuntimeException ignored) {}
        for (var p : data.posts()) if (p.author.equals(id)) return p.name;
        return id.toString().substring(0, 8);
    }

    // ── 글 목록 ─────────────────────

    private static SnsData.Post visible(SnsData data, UUID id, UUID viewer) {
        var p = data.find(id);
        if (p == null || p.held && !p.author.equals(viewer) || data.blocked(viewer).contains(p.author)) return null;
        return p;
    }

    private static SnsWire.Reply feed(SnsWire.Query q, ServerPlayer player, SnsData data) {
        UUID me = player.getUUID();
        var blocked = data.blocked(me);
        boolean mine = q.kind() == SnsWire.Kind.MINE;
        List<SnsWire.Post> out = new ArrayList<>();
        int skip = q.page() * SnsWire.PAGE_SIZE, seen = 0;
        boolean more = false;
        for (var p : data.posts()) {
            if (mine ? !p.author.equals(me) : (p.held || blocked.contains(p.author))) continue;
            if (seen++ < skip) continue;
            if (out.size() >= SnsWire.PAGE_SIZE) { more = true; break; }
            out.add(view(p, me, blocked, staff(player), false));
        }
        return new SnsWire.Reply(q.request(), q.kind(), SnsWire.Status.OK, q.page(), more, out);
    }

    private static SnsWire.Reply detail(SnsWire.Query q, ServerPlayer player, SnsData data, SnsWire.Status status) {
        var p = visible(data, q.post(), player.getUUID());
        if (p == null) return SnsWire.Reply.of(q, SnsWire.Status.GONE);
        return new SnsWire.Reply(q.request(), q.kind(), status, 0, false,
                List.of(view(p, player.getUUID(), data.blocked(player.getUUID()), staff(player), true)));
    }

    private static SnsWire.Reply single(SnsWire.Query q, ServerPlayer player, SnsData data, SnsData.Post p, boolean comments) {
        return new SnsWire.Reply(q.request(), q.kind(), SnsWire.Status.OK, 0, false,
                List.of(view(p, player.getUUID(), data.blocked(player.getUUID()), staff(player), comments)));
    }

    private static SnsWire.Post view(SnsData.Post p, UUID me, java.util.Set<UUID> blocked, boolean staff, boolean withComments) {
        List<SnsWire.Comment> cs = new ArrayList<>();
        int count = 0;
        for (var c : p.comments) {
            if (blocked.contains(c.author)) continue;
            count++;
            if (withComments) cs.add(new SnsWire.Comment(c.id, c.author, c.name, c.time, c.text, c.author.equals(me) || p.author.equals(me) || staff));
        }
        if (cs.size() > SnsWire.MAX_COMMENTS_SENT) cs = cs.subList(cs.size() - SnsWire.MAX_COMMENTS_SENT, cs.size());
        return new SnsWire.Post(p.id, p.author, p.name, p.time, p.text, p.image, p.width, p.height,
                p.likes.size(), p.likes.contains(me), count, p.author.equals(me) || staff, cs);
    }

    // ── 글쓰기 ─────────────────────

    private static String clean(String raw, int max) { return TextSanitizer.sanitize(raw, max); }

    private static SnsWire.Status checkText(UUID me, String text) {
        if (SnsFilter.profane(text)) return SnsWire.Status.PROFANITY;
        if (SnsFilter.spammy(text)) return SnsWire.Status.DUPLICATE;
        return null;
    }

    private static SnsWire.Reply post(SnsWire.Query q, ServerPlayer player, SnsData data, long now) {
        UUID me = player.getUUID();
        if (data.muted(me)) return SnsWire.Reply.of(q, SnsWire.Status.MUTED);
        String text = clean(q.text(), SnsWire.MAX_TEXT);
        if (text.isEmpty() && !q.flag()) throw new IllegalArgumentException();
        var bad = checkText(me, text);
        if (bad != null) return SnsWire.Reply.of(q, bad);
        if (!text.isEmpty() && text.equals(LAST_TEXT.get(me))) return SnsWire.Reply.of(q, SnsWire.Status.DUPLICATE);
        if (now - LAST_POST.getOrDefault(me, 0L) < POST_COOLDOWN_MS && !staff(player)) return SnsWire.Reply.of(q, SnsWire.Status.TOO_FAST);
        var day = DAY.computeIfAbsent(me, k -> new ArrayList<>());
        day.removeIf(t -> now - t > 24L * 60 * 60 * 1000);
        if (day.size() >= POSTS_PER_DAY && !staff(player)) return SnsWire.Reply.of(q, SnsWire.Status.TOO_FAST);
        if (q.flag()) {
            if (!com.november.mcphone.core.ServerConfig.allowChatImages()) return SnsWire.Reply.of(q, SnsWire.Status.NO_IMAGE);
            PENDING.put(me, new Pending(q.request(), text, now));
            return SnsWire.Reply.of(q, SnsWire.Status.PENDING);
        }
        return publish(player, data, q.request(), text, null, 0, 0, now);
    }

    private static SnsWire.Reply publish(ServerPlayer player, SnsData data, UUID request, String text, UUID image, int w, int h, long now) {
        UUID me = player.getUUID();
        var p = new SnsData.Post(UUID.randomUUID(), me, clean(player.getName().getString(), SnsWire.NAME_LENGTH), now, text);
        p.image = image; p.width = w; p.height = h;
        data.add(p);
        LAST_POST.put(me, now);
        if (!text.isEmpty()) LAST_TEXT.put(me, text);
        DAY.computeIfAbsent(me, k -> new ArrayList<>()).add(now);
        return new SnsWire.Reply(request, SnsWire.Kind.POST, SnsWire.Status.OK, 0, false,
                List.of(view(p, me, data.blocked(me), staff(player), false)));
    }

    private static SnsWire.Reply comment(SnsWire.Query q, ServerPlayer player, SnsData data, long now) {
        UUID me = player.getUUID();
        if (data.muted(me)) return SnsWire.Reply.of(q, SnsWire.Status.MUTED);
        var p = visible(data, q.post(), me);
        if (p == null) return SnsWire.Reply.of(q, SnsWire.Status.GONE);
        String text = clean(q.text(), SnsWire.MAX_COMMENT);
        if (text.isEmpty()) throw new IllegalArgumentException();
        var bad = checkText(me, text);
        if (bad != null) return SnsWire.Reply.of(q, bad);
        if (now - LAST_COMMENT.getOrDefault(me, 0L) < COMMENT_COOLDOWN_MS) return SnsWire.Reply.of(q, SnsWire.Status.TOO_FAST);
        if (p.comments.size() >= MAX_COMMENTS) return SnsWire.Reply.of(q, SnsWire.Status.INVALID);
        LAST_COMMENT.put(me, now);
        p.comments.add(new SnsData.Comment(UUID.randomUUID(), me, clean(player.getName().getString(), SnsWire.NAME_LENGTH), now, text));
        data.setDirty();
        return detail(q, player, data, SnsWire.Status.OK);
    }

    // ── 사진 업로드 (ChatNetworking 에서 불림) ─────────────────────

    private static Pending pending(ServerPlayer sender) {
        var p = PENDING.get(sender.getUUID());
        if (p == null) return null;
        if (System.currentTimeMillis() - p.time() > PENDING_MS) { PENDING.remove(sender.getUUID()); return null; }
        return p;
    }

    /** 이 사람이 지금 SNS 사진을 올려도 되나 */
    public static boolean maySendImage(ServerPlayer sender) {
        return com.november.mcphone.core.ServerConfig.allowChatImages() && FriendGuard.carriesPhone(sender) && pending(sender) != null;
    }

    /** 업로드가 중간에 막혔을 때: 기다리던 글을 버리고 폰에 알려 준다 */
    public static void failPending(ServerPlayer sender) {
        var p = PENDING.remove(sender.getUUID());
        if (p != null && reply != null)
            reply.accept(sender, new SnsWire.Reply(p.request(), SnsWire.Kind.POST, SnsWire.Status.NO_IMAGE, 0, false, List.of()));
    }

    /** 사진이 저장됐다 → 글 완성. 주 스레드에서 */
    public static void onImageStored(ServerPlayer sender, UUID imageId, int w, int h) {
        var p = PENDING.remove(sender.getUUID());
        MinecraftServer server = sender.level().getServer();
        if (p == null || sender.hasDisconnected() || !FriendGuard.carriesPhone(sender)) {
            if (imageId != null && !SnsData.get(server).referencedImages().contains(imageId)) ChatImageStore.delete(server, imageId);
            return;
        }
        if (imageId == null) {
            reply.accept(sender, new SnsWire.Reply(p.request(), SnsWire.Kind.POST, SnsWire.Status.NO_IMAGE, 0, false, List.of()));
            return;
        }
        reply.accept(sender, publish(sender, SnsData.get(server), p.request(), p.text(), imageId, w, h, System.currentTimeMillis()));
    }

    /** 이 사진을 이 사람에게 보여 줘도 되나 */
    public static boolean mayReadImage(ServerPlayer player, UUID imageId) {
        if (!FriendGuard.carriesPhone(player)) return false;
        var data = SnsData.get(player.level().getServer());
        var blocked = data.blocked(player.getUUID());
        for (var p : data.posts())
            if (imageId.equals(p.image)) return !blocked.contains(p.author) && (!p.held || p.author.equals(player.getUUID()) || staff(player));
        return false;
    }

    // ── 정리 ─────────────────────

    private static long lastSweep;

    private static void sweep(MinecraftServer server, SnsData data, long now) {
        if (now - lastSweep < 60_000L) return;
        lastSweep = now;
        for (var p : data.expire(now)) dropImage(server, data, p);
    }

    public static void delete(MinecraftServer server, SnsData data, SnsData.Post p) {
        if (data.remove(p)) dropImage(server, data, p);
    }

    private static void dropImage(MinecraftServer server, SnsData data, SnsData.Post p) {
        if (p.image == null || data.referencedImages().contains(p.image)) return;
        ChatImageStore.delete(server, p.image);
    }

    private static void notifyStaff(MinecraftServer server, SnsData.Post p) {
        String head = p.text.length() > 30 ? p.text.substring(0, 30) + "…" : p.text;
        var msg = Component.literal("§d[포키 SNS] §f" + p.name + " 님 글이 신고 " + REPORT_LIMIT + "회로 검토함에 들어감: §7" + head
                + " §8(" + p.id.toString().substring(0, 8) + ")");
        for (var op : server.getPlayerList().getPlayers()) if (op.hasPermissions(2)) op.sendSystemMessage(msg);
        MCphone.LOGGER.info("[포키 SNS] 신고 누적 검토함: {} {} {}", p.id, p.name, p.text);
    }
}
