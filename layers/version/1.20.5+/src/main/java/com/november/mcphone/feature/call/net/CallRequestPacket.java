package com.november.mcphone.feature.call.net;
import com.november.mcphone.MCphone;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** 포키폰 통화 패킷 포장 */
public record CallRequestPacket(CallWire.Query value) implements CustomPacketPayload {
    public static final Type<CallRequestPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MCphone.MODID, "poki_call_request_v1"));
    public static final StreamCodec<ByteBuf, CallRequestPacket> STREAM_CODEC = new StreamCodec<>() {
        public CallRequestPacket decode(ByteBuf b) { return new CallRequestPacket(CallWire.readQuery(new FriendlyByteBuf(b))); }
        public void encode(ByteBuf b, CallRequestPacket v) { CallWire.writeQuery(v.value(), new FriendlyByteBuf(b)); }
    };
    public Type<CallRequestPacket> type() { return TYPE; }
}
