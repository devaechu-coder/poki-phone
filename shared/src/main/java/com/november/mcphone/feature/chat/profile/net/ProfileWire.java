package com.november.mcphone.feature.chat.profile.net;

import com.november.mcphone.feature.chat.profile.ProfileBook;
import net.minecraft.network.FriendlyByteBuf;
import java.util.Objects;
import java.util.UUID;

/** 小型、限长的资料协议；空介绍也是合法修改，用独立标志区分读取请求。 */
public final class ProfileWire {
    private ProfileWire() {}
    public enum Status {
        OK(0), FORBIDDEN(1), INVALID(2), BUSY(3), UNAVAILABLE(4);
        private final int wireId;
        Status(int wireId) { this.wireId = wireId; }
        public int wireId() { return wireId; }
        public static Status fromWire(int id) {
            for (var status : values()) if (status.wireId == id) return status;
            throw new io.netty.handler.codec.DecoderException("非法资料状态 " + id);
        }
    }
    public static final int NAME_LENGTH = 64;
    public record Query(UUID request, UUID target, boolean update, String introduction) {
        public Query { Objects.requireNonNull(request); Objects.requireNonNull(target); Objects.requireNonNull(introduction);
            if (introduction.length() > ProfileBook.MAX_INTRO || !update && !introduction.isEmpty()) throw new IllegalArgumentException("无效资料请求"); }
    }
    public record Reply(UUID request, UUID target, Status status, String name, String introduction) {
        public Reply { Objects.requireNonNull(request); Objects.requireNonNull(target); Objects.requireNonNull(status);
            Objects.requireNonNull(name); Objects.requireNonNull(introduction);
            if (name.length() > NAME_LENGTH || introduction.length() > ProfileBook.MAX_INTRO) throw new IllegalArgumentException("资料超限"); }
    }
    public static void writeQuery(Query q, FriendlyByteBuf b) {
        b.writeUUID(q.request()); b.writeUUID(q.target()); b.writeBoolean(q.update()); b.writeUtf(q.introduction(), ProfileBook.MAX_INTRO);
    }
    public static Query readQuery(FriendlyByteBuf b) { return new Query(b.readUUID(), b.readUUID(), b.readBoolean(), b.readUtf(ProfileBook.MAX_INTRO)); }
    public static void writeReply(Reply r, FriendlyByteBuf b) {
        b.writeUUID(r.request()); b.writeUUID(r.target()); b.writeVarInt(r.status().wireId()); b.writeUtf(r.name(), NAME_LENGTH); b.writeUtf(r.introduction(), ProfileBook.MAX_INTRO);
    }
    public static Reply readReply(FriendlyByteBuf b) { return new Reply(b.readUUID(), b.readUUID(), Status.fromWire(b.readVarInt()), b.readUtf(NAME_LENGTH), b.readUtf(ProfileBook.MAX_INTRO)); }
}
