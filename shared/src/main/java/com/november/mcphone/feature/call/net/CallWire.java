package com.november.mcphone.feature.call.net;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 포키폰 통화 프로토콜 */
public final class CallWire {
    private CallWire() {}
    private static final UUID NONE = new UUID(0, 0);

    public enum Action { START, ACCEPT, DECLINE, HANGUP, JOIN }
    public enum Kind { RESULT, INCOMING, STATE, ENDED, GROUP }
    public enum Status { OK, NO_VOICE, NOT_CONNECTED, PEER_NOT_CONNECTED, BUSY, OFFLINE, FORBIDDEN, DECLINED, MISSED, ENDED, FAILED, NO_ONE }

    /** target: 1:1 이면 상대, 그룹이면 그룹방 id, ACCEPT/DECLINE/JOIN 이면 통화 id */
    public record Query(Action action, UUID target, boolean group) {
        public Query { target = target == null ? NONE : target; }
    }

    public record Member(UUID id, String name, boolean ringing) {}

    public record Event(Kind kind, Status status, UUID call, UUID from, String title, boolean group, UUID groupChat,
                        long started, int count, List<Member> members) {
        public Event {
            call = call == null ? NONE : call; from = from == null ? NONE : from; groupChat = groupChat == null ? NONE : groupChat;
            title = title == null ? "" : title; members = members == null ? List.of() : List.copyOf(members);
        }
        public static Event result(Status s) { return new Event(Kind.RESULT, s, null, null, "", false, null, 0, 0, null); }
    }

    public static boolean none(UUID id) { return id == null || id.equals(NONE); }

    public static void writeQuery(Query q, FriendlyByteBuf b) { b.writeVarInt(q.action().ordinal()); b.writeUUID(q.target()); b.writeBoolean(q.group()); }
    public static Query readQuery(FriendlyByteBuf b) {
        int a = b.readVarInt();
        if (a < 0 || a >= Action.values().length) throw new io.netty.handler.codec.DecoderException("통화 동작 오류");
        return new Query(Action.values()[a], b.readUUID(), b.readBoolean());
    }

    public static void writeEvent(Event e, FriendlyByteBuf b) {
        b.writeVarInt(e.kind().ordinal()); b.writeVarInt(e.status().ordinal()); b.writeUUID(e.call()); b.writeUUID(e.from());
        b.writeUtf(e.title(), 128); b.writeBoolean(e.group()); b.writeUUID(e.groupChat()); b.writeLong(e.started()); b.writeVarInt(e.count());
        b.writeVarInt(e.members().size());
        for (Member m : e.members()) { b.writeUUID(m.id()); b.writeUtf(m.name(), 64); b.writeBoolean(m.ringing()); }
    }
    public static Event readEvent(FriendlyByteBuf b) {
        int k = b.readVarInt(), s = b.readVarInt();
        if (k < 0 || k >= Kind.values().length || s < 0 || s >= Status.values().length) throw new io.netty.handler.codec.DecoderException("통화 이벤트 오류");
        UUID call = b.readUUID(), from = b.readUUID(); String title = b.readUtf(128); boolean group = b.readBoolean();
        UUID gc = b.readUUID(); long started = b.readLong(); int count = b.readVarInt();
        int n = b.readVarInt();
        if (n < 0 || n > 64) throw new io.netty.handler.codec.DecoderException("통화 인원 오류");
        List<Member> ms = new ArrayList<>(n);
        for (int i = 0; i < n; i++) ms.add(new Member(b.readUUID(), b.readUtf(64), b.readBoolean()));
        return new Event(Kind.values()[k], Status.values()[s], call, from, title, group, gc, started, count, ms);
    }
}
