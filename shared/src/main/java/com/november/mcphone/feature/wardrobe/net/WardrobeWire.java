package com.november.mcphone.feature.wardrobe.net;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 포키 옷장 프로토콜. 요청 하나 → 답 하나 (답에는 항상 옷장 목록이 같이 옴). 실제 저장은 PokiCosmetics 모드. */
public final class WardrobeWire {
    private WardrobeWire() {}

    /** LIST 옷장 목록 · WEAR 입기(slot, cmd) · OFF 벗기(slot) */
    public enum Kind { LIST, WEAR, OFF }
    public enum Status { OK, ERROR, NO_MOD, FORBIDDEN, BUSY }

    public record Query(UUID request, Kind kind, String slot, int cmd) {
        public Query {
            Objects.requireNonNull(request); Objects.requireNonNull(kind);
            slot = slot == null ? "" : slot.length() > 16 ? slot.substring(0, 16) : slot;
        }
        public static Query of(Kind k, String slot, int cmd) { return new Query(UUID.randomUUID(), k, slot, cmd); }
    }

    /** owned: "hat:29" 같은 키 */
    public record Reply(UUID request, Kind kind, Status status, String message, List<String> owned) {
        public Reply {
            message = message == null ? "" : message;
            owned = owned == null ? List.of() : List.copyOf(owned);
        }
        public static Reply of(Query q, Status s, String msg) { return new Reply(q.request(), q.kind(), s, msg, null); }
    }

    public static void writeQuery(Query q, FriendlyByteBuf b) {
        b.writeUUID(q.request()); b.writeVarInt(q.kind().ordinal()); b.writeUtf(q.slot(), 64); b.writeVarInt(q.cmd());
    }

    public static Query readQuery(FriendlyByteBuf b) {
        return new Query(b.readUUID(), en(Kind.values(), b.readVarInt()), b.readUtf(64), b.readVarInt());
    }

    public static void writeReply(Reply r, FriendlyByteBuf b) {
        b.writeUUID(r.request()); b.writeVarInt(r.kind().ordinal()); b.writeVarInt(r.status().ordinal());
        b.writeUtf(r.message(), 512);
        b.writeVarInt(r.owned().size());
        for (String k : r.owned()) b.writeUtf(k, 64);
    }

    public static Reply readReply(FriendlyByteBuf b) {
        UUID req = b.readUUID(); Kind k = en(Kind.values(), b.readVarInt()); Status st = en(Status.values(), b.readVarInt());
        String msg = b.readUtf(512);
        int n = b.readVarInt();
        if (n < 0 || n > 4096) throw new io.netty.handler.codec.DecoderException("옷장 개수 오류 " + n);
        List<String> owned = new ArrayList<>(n);
        for (int i = 0; i < n; i++) owned.add(b.readUtf(64));
        return new Reply(req, k, st, msg, owned);
    }

    private static <E> E en(E[] values, int i) {
        if (i < 0 || i >= values.length) throw new io.netty.handler.codec.DecoderException("잘못된 값 " + i);
        return values[i];
    }
}
