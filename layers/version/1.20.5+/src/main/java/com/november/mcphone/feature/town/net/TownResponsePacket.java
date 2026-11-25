package com.november.mcphone.feature.town.net;
import com.november.mcphone.MCphone;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** 포키 마을 앱 패킷 포장 */
public record TownResponsePacket(TownWire.Reply value) implements CustomPacketPayload {
    public static final Type<TownResponsePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MCphone.MODID, "poki_town_response_v1"));
    public static final StreamCodec<ByteBuf, TownResponsePacket> STREAM_CODEC = new StreamCodec<>() {
        public TownResponsePacket decode(ByteBuf b) { return new TownResponsePacket(TownWire.readReply(new FriendlyByteBuf(b))); }
        public void encode(ByteBuf b, TownResponsePacket v) { TownWire.writeReply(v.value(), new FriendlyByteBuf(b)); }
    };
    public Type<TownResponsePacket> type() { return TYPE; }
}
