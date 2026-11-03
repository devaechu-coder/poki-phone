package com.november.mcphone.feature.sns.net;
import com.november.mcphone.MCphone;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** 포키 SNS 패킷 포장 */
public record SnsResponsePacket(SnsWire.Reply value) implements CustomPacketPayload {
    public static final Type<SnsResponsePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MCphone.MODID, "poki_sns_response_v1"));
    public static final StreamCodec<ByteBuf, SnsResponsePacket> STREAM_CODEC = new StreamCodec<>() {
        public SnsResponsePacket decode(ByteBuf b) { return new SnsResponsePacket(SnsWire.readReply(new FriendlyByteBuf(b))); }
        public void encode(ByteBuf b, SnsResponsePacket v) { SnsWire.writeReply(v.value(), new FriendlyByteBuf(b)); }
    };
    public Type<SnsResponsePacket> type() { return TYPE; }
}
