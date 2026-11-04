package com.november.mcphone.feature.sns.net;
import com.november.mcphone.MCphone;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** 포키 SNS 패킷 포장 */
public record SnsRequestPacket(SnsWire.Query value) implements CustomPacketPayload {
    public static final Type<SnsRequestPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MCphone.MODID, "poki_sns_request_v2"));
    public static final StreamCodec<ByteBuf, SnsRequestPacket> STREAM_CODEC = new StreamCodec<>() {
        public SnsRequestPacket decode(ByteBuf b) { return new SnsRequestPacket(SnsWire.readQuery(new FriendlyByteBuf(b))); }
        public void encode(ByteBuf b, SnsRequestPacket v) { SnsWire.writeQuery(v.value(), new FriendlyByteBuf(b)); }
    };
    public Type<SnsRequestPacket> type() { return TYPE; }
}
