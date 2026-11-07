package com.november.mcphone.feature.support.net;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 포키 고객센터 프로토콜. 요청 하나 → 답 하나. 실제 문의 저장은 PokiHelp 플러그인. */
public final class SupportWire {
    private SupportWire() {}

    public static final int MAX_TEXT = 220;

    /** STATE 첫 화면 숫자 · SUBMIT 보내기 · MINE 내 문의 · SEEN 답변 읽음 · PLAYERS 신고 대상 · BOX 문의함 · ACT 운영진 처리 */
    public enum Kind { STATE, SUBMIT, MINE, SEEN, PLAYERS, BOX, ACT }
    public enum Status { OK, ERROR, NO_PLUGIN, FORBIDDEN, BUSY }

    /** SUBMIT: a 종류 · b 내용 · c 대상 · d 사유 / ACT: a 동작 · b 답변, id / BOX: flag = 처리됨 */
    public record Query(UUID request, Kind kind, int id, boolean flag, String a, String b, String c, String d) {
        public Query {
            Objects.requireNonNull(request); Objects.requireNonNull(kind);
            a = cut(a); b = cut(b); c = cut(c); d = cut(d);
        }
        public static Query of(Kind k) { return new Query(UUID.randomUUID(), k, 0, false, "", "", "", ""); }
    }

    public record Ticket(int id, String kind, String state, String message, String target, String reason, long created,
                         String reply, String replyBy, long replyAt, String handler, String who, String where) {}

    public record PlayerRow(UUID id, String name, String display, boolean near) {}

    public record Reply(UUID request, Kind kind, Status status, String message, boolean staff, int unseen, int open,
                        List<Ticket> tickets, List<PlayerRow> players) {
        public Reply {
            message = message == null ? "" : message;
            tickets = tickets == null ? List.of() : List.copyOf(tickets);
            players = players == null ? List.of() : List.copyOf(players);
        }
        public static Reply of(Query q, Status s, String msg) { return new Reply(q.request(), q.kind(), s, msg, false, 0, 0, null, null); }
    }

    static String cut(String s) { s = s == null ? "" : s; return s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT) : s; }

    public static void writeQuery(Query q, FriendlyByteBuf b) {
        b.writeUUID(q.request()); b.writeVarInt(q.kind().ordinal()); b.writeVarInt(q.id()); b.writeBoolean(q.flag());
        for (String s : new String[]{q.a(), q.b(), q.c(), q.d()}) b.writeUtf(s, MAX_TEXT * 4);
    }

    public static Query readQuery(FriendlyByteBuf b) {
        UUID r = b.readUUID(); Kind k = en(Kind.values(), b.readVarInt()); int id = b.readVarInt(); boolean f = b.readBoolean();
        return new Query(r, k, id, f, b.readUtf(MAX_TEXT * 4), b.readUtf(MAX_TEXT * 4), b.readUtf(MAX_TEXT * 4), b.readUtf(MAX_TEXT * 4));
    }

    public static void writeReply(Reply r, FriendlyByteBuf b) {
        b.writeUUID(r.request()); b.writeVarInt(r.kind().ordinal()); b.writeVarInt(r.status().ordinal());
        b.writeUtf(r.message(), 1024); b.writeBoolean(r.staff()); b.writeVarInt(r.unseen()); b.writeVarInt(r.open());
        b.writeVarInt(r.tickets().size());
        for (Ticket t : r.tickets()) {
            b.writeVarInt(t.id()); b.writeUtf(t.kind(), 16); b.writeUtf(t.state(), 16); b.writeUtf(t.message(), 2048);
            b.writeUtf(t.target(), 64); b.writeUtf(t.reason(), 64); b.writeLong(t.created()); b.writeUtf(t.reply(), 2048);
            b.writeUtf(t.replyBy(), 64); b.writeLong(t.replyAt()); b.writeUtf(t.handler(), 64); b.writeUtf(t.who(), 96); b.writeUtf(t.where(), 96);
        }
        b.writeVarInt(r.players().size());
        for (PlayerRow p : r.players()) { b.writeUUID(p.id()); b.writeUtf(p.name(), 32); b.writeUtf(p.display(), 64); b.writeBoolean(p.near()); }
    }

    public static Reply readReply(FriendlyByteBuf b) {
        UUID req = b.readUUID(); Kind k = en(Kind.values(), b.readVarInt()); Status st = en(Status.values(), b.readVarInt());
        String msg = b.readUtf(1024); boolean staff = b.readBoolean(); int unseen = b.readVarInt(); int open = b.readVarInt();
        int n = b.readVarInt();
        if (n < 0 || n > 64) throw new io.netty.handler.codec.DecoderException("문의 개수 오류 " + n);
        List<Ticket> ts = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
            ts.add(new Ticket(b.readVarInt(), b.readUtf(16), b.readUtf(16), b.readUtf(2048), b.readUtf(64), b.readUtf(64), b.readLong(),
                    b.readUtf(2048), b.readUtf(64), b.readLong(), b.readUtf(64), b.readUtf(96), b.readUtf(96)));
        int m = b.readVarInt();
        if (m < 0 || m > 200) throw new io.netty.handler.codec.DecoderException("플레이어 수 오류 " + m);
        List<PlayerRow> ps = new ArrayList<>(m);
        for (int i = 0; i < m; i++) ps.add(new PlayerRow(b.readUUID(), b.readUtf(32), b.readUtf(64), b.readBoolean()));
        return new Reply(req, k, st, msg, staff, unseen, open, ts, ps);
    }

    private static <E> E en(E[] v, int i) {
        if (i < 0 || i >= v.length) throw new io.netty.handler.codec.DecoderException("고객센터 값 오류 " + i);
        return v[i];
    }
}
