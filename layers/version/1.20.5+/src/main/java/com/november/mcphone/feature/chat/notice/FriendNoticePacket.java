package com.november.mcphone.feature.chat.notice;
import com.november.mcphone.MCphone;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** 포키 친구 알림 패킷 포장 (서버 → 클라) */
public record FriendNoticePacket(FriendNotice.Notice value) implements CustomPacketPayload {
    public static final Type<FriendNoticePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MCphone.MODID, "poki_friend_notice_v1"));
    public static final StreamCodec<ByteBuf, FriendNoticePacket> STREAM_CODEC = new StreamCodec<>() {
        public FriendNoticePacket decode(ByteBuf b) { return new FriendNoticePacket(FriendNotice.read(new FriendlyByteBuf(b))); }
        public void encode(ByteBuf b, FriendNoticePacket v) { FriendNotice.write(v.value(), new FriendlyByteBuf(b)); }
    };
    public Type<FriendNoticePacket> type() { return TYPE; }
}
