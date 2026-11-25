package com.november.mcphone.feature.town.net;
import com.november.mcphone.MCphone;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** 포키 마을 앱 패킷 포장 */
public record TownNoticePacket(TownWire.Notice value) implements CustomPacketPayload {
    public static final Type<TownNoticePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MCphone.MODID, "poki_town_notice_v1"));
    public static final StreamCodec<ByteBuf, TownNoticePacket> STREAM_CODEC = new StreamCodec<>() {
        public TownNoticePacket decode(ByteBuf b) { return new TownNoticePacket(TownWire.readNotice(new FriendlyByteBuf(b))); }
        public void encode(ByteBuf b, TownNoticePacket v) { TownWire.writeNotice(v.value(), new FriendlyByteBuf(b)); }
    };
    public Type<TownNoticePacket> type() { return TYPE; }
}
