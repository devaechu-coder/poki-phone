package com.november.mcphone.feature.gym.net;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 포키 체육관 앱 프로토콜: 요청 하나 → 체육관 목록 하나 */
public final class GymWire {
    private GymWire() {}
    public enum Status { OK, NO_GYM, FORBIDDEN, BUSY }

    public record Gym(String id, String name, String type, int cap, boolean cleared, long firstWin,
                      int attempts, int wins, int losses, long bestMs) {}

    public record Reply(UUID request, Status status, List<Gym> gyms) {
        public Reply { gyms = gyms == null ? List.of() : List.copyOf(gyms); }
    }

    public static void writeQuery(UUID q, FriendlyByteBuf b) { b.writeUUID(q); }
    public static UUID readQuery(FriendlyByteBuf b) { return b.readUUID(); }

    public static void writeReply(Reply r, FriendlyByteBuf b) {
        b.writeUUID(r.request()); b.writeVarInt(r.status().ordinal()); b.writeVarInt(r.gyms().size());
        for (Gym g : r.gyms()) {
            b.writeUtf(g.id(), 64); b.writeUtf(g.name(), 64); b.writeUtf(g.type(), 32); b.writeVarInt(g.cap());
            b.writeBoolean(g.cleared()); b.writeLong(g.firstWin());
            b.writeVarInt(g.attempts()); b.writeVarInt(g.wins()); b.writeVarInt(g.losses()); b.writeLong(g.bestMs());
        }
    }

    public static Reply readReply(FriendlyByteBuf b) {
        UUID req = b.readUUID();
        int st = b.readVarInt();
        Status status = st >= 0 && st < Status.values().length ? Status.values()[st] : Status.NO_GYM;
        int n = b.readVarInt();
        if (n < 0 || n > 32) throw new io.netty.handler.codec.DecoderException("체육관 개수 오류 " + n);
        List<Gym> gyms = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
            gyms.add(new Gym(b.readUtf(64), b.readUtf(64), b.readUtf(32), b.readVarInt(), b.readBoolean(), b.readLong(),
                    b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readLong()));
        return new Reply(req, status, gyms);
    }
}
