package com.november.mcphone.feature.support.net;
import com.november.mcphone.MCphone;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** 포키 고객센터 패킷 포장 */
public record SupportResponsePacket(SupportWire.Reply value) implements CustomPacketPayload {
    public static final Type<SupportResponsePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MCphone.MODID, "poki_support_response_v1"));
    public static final StreamCodec<ByteBuf, SupportResponsePacket> STREAM_CODEC = new StreamCodec<>() {
        public SupportResponsePacket decode(ByteBuf b) { return new SupportResponsePacket(SupportWire.readReply(new FriendlyByteBuf(b))); }
        public void encode(ByteBuf b, SupportResponsePacket v) { SupportWire.writeReply(v.value(), new FriendlyByteBuf(b)); }
    };
    public Type<SupportResponsePacket> type() { return TYPE; }
}
