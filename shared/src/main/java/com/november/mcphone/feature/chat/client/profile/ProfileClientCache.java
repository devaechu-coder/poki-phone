package com.november.mcphone.feature.chat.client.profile;

import com.november.mcphone.feature.chat.profile.net.ProfileWire;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 客户端主线程的小型票据收件箱；关闭页和断线后不接受过期资料回声。 */
public final class ProfileClientCache {
    private ProfileClientCache() {}
    public static final long REQUEST_TIMEOUT_MS = 6_000L;
    public static final int MAX_PENDING = 8;
    private record Pending(UUID target, long deadline) {}
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, ProfileWire.Reply> RESULTS = new HashMap<>();
    public static UUID begin(UUID target, long now) {
        PENDING.entrySet().removeIf(e -> e.getValue().deadline() <= now);
        RESULTS.keySet().retainAll(PENDING.keySet());
        if (PENDING.size() >= MAX_PENDING) throw new IllegalStateException("资料请求过多");
        UUID id = UUID.randomUUID(); PENDING.put(id, new Pending(target, now + REQUEST_TIMEOUT_MS)); return id;
    }
    public static void accept(ProfileWire.Reply reply) { accept(reply, System.currentTimeMillis()); }
    public static void accept(ProfileWire.Reply reply, long now) {
        var p = PENDING.get(reply.request());
        if (p != null && p.deadline() > now && p.target().equals(reply.target())) RESULTS.putIfAbsent(reply.request(), reply);
    }
    public static ProfileWire.Reply consume(UUID id) {
        var r = RESULTS.remove(id); if (r != null) PENDING.remove(id); return r;
    }
    public static void cancel(UUID id) { PENDING.remove(id); RESULTS.remove(id); }
    public static void clear() { PENDING.clear(); RESULTS.clear(); }
}
