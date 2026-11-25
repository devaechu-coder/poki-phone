package com.november.mcphone.feature.town.net;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 포키 마을 앱 프로토콜. 요청 하나 → 답 하나. 실제 마을은 Towny, 중간은 PokiTown 플러그인(PokiTownApi).
 * 답의 lines 는 PokiTown 이 준 탭(\t) 구분 줄 그대로 (+ 서버가 붙인 한글닉 칸).
 */
public final class TownWire {
    private TownWire() {}

    public static final int MAX_TEXT = 64;
    public static final int MAX_LINES = 500;
    public static final int MAX_LINE = 4096;

    /**
     * ME 내 상태(+내 마을 상세) · TOWNS 전체 마을 · TOWN 마을 상세(a=마을UUID)
     * ACT 행동(a=행동, b=대상) · SWITCH 전체 마을 목록 공개(flag)
     */
    public enum Kind { ME, TOWNS, TOWN, ACT, SWITCH }
    public enum Status { OK, ERROR, NO_PLUGIN, FORBIDDEN, BUSY }

    public record Query(UUID request, Kind kind, boolean flag, String a, String b) {
        public Query {
            Objects.requireNonNull(request); Objects.requireNonNull(kind);
            a = cut(a); b = cut(b);
        }
        public static Query of(Kind k) { return new Query(UUID.randomUUID(), k, false, "", ""); }
        public static Query of(Kind k, String a, String b) { return new Query(UUID.randomUUID(), k, false, a, b); }
    }

    public record Reply(UUID request, Kind kind, Status status, String message, List<String> lines) {
        public Reply {
            message = message == null ? "" : message;
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
        public static Reply of(Query q, Status s, String msg) { return new Reply(q.request(), q.kind(), s, msg, null); }
    }

    /** 서버 → 클라 알림 (포키폰 팝업) */
    public record Notice(String text) {}

    static String cut(String s) { s = s == null ? "" : s; return s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT) : s; }

    public static void writeQuery(Query q, FriendlyByteBuf b) {
        b.writeUUID(q.request()); b.writeVarInt(q.kind().ordinal()); b.writeBoolean(q.flag());
        b.writeUtf(q.a(), MAX_TEXT * 4); b.writeUtf(q.b(), MAX_TEXT * 4);
    }

    public static Query readQuery(FriendlyByteBuf b) {
        UUID r = b.readUUID(); Kind k = en(Kind.values(), b.readVarInt()); boolean f = b.readBoolean();
        return new Query(r, k, f, b.readUtf(MAX_TEXT * 4), b.readUtf(MAX_TEXT * 4));
    }

    public static void writeReply(Reply r, FriendlyByteBuf b) {
        b.writeUUID(r.request()); b.writeVarInt(r.kind().ordinal()); b.writeVarInt(r.status().ordinal());
        b.writeUtf(r.message(), 1024);
        int n = Math.min(r.lines().size(), MAX_LINES);
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            String l = r.lines().get(i);
            b.writeUtf(l.length() > MAX_LINE ? l.substring(0, MAX_LINE) : l, MAX_LINE * 4);
        }
    }

    public static Reply readReply(FriendlyByteBuf b) {
        UUID req = b.readUUID(); Kind k = en(Kind.values(), b.readVarInt()); Status st = en(Status.values(), b.readVarInt());
        String msg = b.readUtf(1024);
        int n = b.readVarInt();
        if (n < 0 || n > MAX_LINES) throw new io.netty.handler.codec.DecoderException("마을 줄 수 오류 " + n);
        List<String> ls = new ArrayList<>(n);
        for (int i = 0; i < n; i++) ls.add(b.readUtf(MAX_LINE * 4));
        return new Reply(req, k, st, msg, ls);
    }

    public static void writeNotice(Notice n, FriendlyByteBuf b) { b.writeUtf(n.text(), 256); }

    public static Notice readNotice(FriendlyByteBuf b) { return new Notice(b.readUtf(256)); }

    private static <E> E en(E[] v, int i) {
        if (i < 0 || i >= v.length) throw new io.netty.handler.codec.DecoderException("마을 값 오류 " + i);
        return v[i];
    }
}
