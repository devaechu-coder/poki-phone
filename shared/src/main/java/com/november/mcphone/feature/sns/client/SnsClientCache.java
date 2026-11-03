package com.november.mcphone.feature.sns.client;

import com.november.mcphone.core.net.MCphoneNetwork;
import com.november.mcphone.feature.sns.net.SnsRequestPacket;
import com.november.mcphone.feature.sns.net.SnsWire;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 클라이언트 주 스레드 티켓 함. 화면을 닫거나 접속이 끊기면 지난 답은 버린다. */
public final class SnsClientCache {
    private SnsClientCache() {}
    public static final long TIMEOUT_MS = 8_000L;
    /** 사진 올리는 동안은 길게 기다림 */
    public static final long UPLOAD_TIMEOUT_MS = 45_000L;
    private static final Map<UUID, Long> PENDING = new HashMap<>();
    private static final Map<UUID, SnsWire.Reply> RESULTS = new HashMap<>();

    /** 보내고 티켓을 돌려준다. 못 보내면 null */
    public static UUID send(SnsWire.Query q) {
        long now = System.currentTimeMillis();
        PENDING.entrySet().removeIf(e -> e.getValue() <= now);
        RESULTS.keySet().retainAll(PENDING.keySet());
        if (PENDING.size() >= 16) return null;
        try {
            PENDING.put(q.request(), now + TIMEOUT_MS);
            MCphoneNetwork.sendToServer(new SnsRequestPacket(q));
            return q.request();
        } catch (RuntimeException e) {
            PENDING.remove(q.request());
            return null;
        }
    }

    public static void accept(SnsWire.Reply reply) {
        // 사진 업로드 쪽 정리는 화면이 열려 있든 말든 해야 함
        if (reply.kind() == SnsWire.Kind.POST && reply.status() != SnsWire.Status.PENDING) {
            UUID image = reply.status() == SnsWire.Status.OK && !reply.posts().isEmpty() && reply.posts().get(0).hasImage()
                    ? reply.posts().get(0).image() : null;
            com.november.mcphone.feature.chat.client.ChatImageSender.onSnsResult(image);
        }
        Long deadline = PENDING.get(reply.request());
        if (deadline == null || deadline <= System.currentTimeMillis()) return;
        if (reply.status() == SnsWire.Status.PENDING) PENDING.put(reply.request(), System.currentTimeMillis() + UPLOAD_TIMEOUT_MS);
        RESULTS.put(reply.request(), reply);
    }

    public static boolean waiting(UUID id) {
        Long d = id == null ? null : PENDING.get(id);
        return d != null && d > System.currentTimeMillis();
    }

    /** 답이 왔으면 꺼낸다. PENDING 답은 꺼내도 티켓은 계속 살아 있다 */
    public static SnsWire.Reply consume(UUID id) {
        if (id == null) return null;
        var r = RESULTS.remove(id);
        if (r != null && r.status() != SnsWire.Status.PENDING) PENDING.remove(id);
        return r;
    }

    public static void cancel(UUID id) { if (id != null) { PENDING.remove(id); RESULTS.remove(id); } }
    public static void clear() { PENDING.clear(); RESULTS.clear(); }
}
