package com.november.mcphone.feature.call.net;
import com.november.mcphone.MCphone;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** 포키폰 통화 패킷 포장 */
public record CallEventPacket(CallWire.Event value) implements CustomPacketPayload {
    public static final Type<CallEventPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MCphone.MODID, "poki_call_event_v1"));
    public static final StreamCodec<ByteBuf, CallEventPacket> STREAM_CODEC = new StreamCodec<>() {
        public CallEventPacket decode(ByteBuf b) { return new CallEventPacket(CallWire.readEvent(new FriendlyByteBuf(b))); }
        public void encode(ByteBuf b, CallEventPacket v) { CallWire.writeEvent(v.value(), new FriendlyByteBuf(b)); }
    };
    public Type<CallEventPacket> type() { return TYPE; }
}
