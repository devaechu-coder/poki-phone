package com.november.mcphone.feature.support.net;
import com.november.mcphone.MCphone;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** 포키 고객센터 패킷 포장 */
public record SupportRequestPacket(SupportWire.Query value) implements CustomPacketPayload {
    public static final Type<SupportRequestPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MCphone.MODID, "poki_support_request_v1"));
    public static final StreamCodec<ByteBuf, SupportRequestPacket> STREAM_CODEC = new StreamCodec<>() {
        public SupportRequestPacket decode(ByteBuf b) { return new SupportRequestPacket(SupportWire.readQuery(new FriendlyByteBuf(b))); }
        public void encode(ByteBuf b, SupportRequestPacket v) { SupportWire.writeQuery(v.value(), new FriendlyByteBuf(b)); }
    };
    public Type<SupportRequestPacket> type() { return TYPE; }
}
