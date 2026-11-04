package com.november.mcphone.feature.gym.net;
import com.november.mcphone.MCphone;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** 포키 체육관 앱 패킷 포장 */
public record GymRequestPacket(java.util.UUID value) implements CustomPacketPayload {
    public static final Type<GymRequestPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MCphone.MODID, "poki_gym_request_v1"));
    public static final StreamCodec<ByteBuf, GymRequestPacket> STREAM_CODEC = new StreamCodec<>() {
        public GymRequestPacket decode(ByteBuf b) { return new GymRequestPacket(GymWire.readQuery(new FriendlyByteBuf(b))); }
        public void encode(ByteBuf b, GymRequestPacket v) { GymWire.writeQuery(v.value(), new FriendlyByteBuf(b)); }
    };
    public Type<GymRequestPacket> type() { return TYPE; }
}
