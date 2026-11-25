package com.november.mcphone.feature.town.net;
import com.november.mcphone.MCphone;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** 포키 마을 앱 패킷 포장 */
public record TownRequestPacket(TownWire.Query value) implements CustomPacketPayload {
    public static final Type<TownRequestPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MCphone.MODID, "poki_town_request_v1"));
    public static final StreamCodec<ByteBuf, TownRequestPacket> STREAM_CODEC = new StreamCodec<>() {
        public TownRequestPacket decode(ByteBuf b) { return new TownRequestPacket(TownWire.readQuery(new FriendlyByteBuf(b))); }
        public void encode(ByteBuf b, TownRequestPacket v) { TownWire.writeQuery(v.value(), new FriendlyByteBuf(b)); }
    };
    public Type<TownRequestPacket> type() { return TYPE; }
}
