package com.november.mcphone.feature.gym.net;
import com.november.mcphone.MCphone;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** 포키 체육관 앱 패킷 포장 */
public record GymResponsePacket(GymWire.Reply value) implements CustomPacketPayload {
    public static final Type<GymResponsePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MCphone.MODID, "poki_gym_response_v1"));
    public static final StreamCodec<ByteBuf, GymResponsePacket> STREAM_CODEC = new StreamCodec<>() {
        public GymResponsePacket decode(ByteBuf b) { return new GymResponsePacket(GymWire.readReply(new FriendlyByteBuf(b))); }
        public void encode(ByteBuf b, GymResponsePacket v) { GymWire.writeReply(v.value(), new FriendlyByteBuf(b)); }
    };
    public Type<GymResponsePacket> type() { return TYPE; }
}
