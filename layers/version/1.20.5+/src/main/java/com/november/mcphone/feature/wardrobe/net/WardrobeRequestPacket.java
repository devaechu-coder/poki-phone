package com.november.mcphone.feature.wardrobe.net;
import com.november.mcphone.MCphone;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** 포키 옷장 패킷 포장 */
public record WardrobeRequestPacket(WardrobeWire.Query value) implements CustomPacketPayload {
    public static final Type<WardrobeRequestPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MCphone.MODID, "poki_wardrobe_request_v1"));
    public static final StreamCodec<ByteBuf, WardrobeRequestPacket> STREAM_CODEC = new StreamCodec<>() {
        public WardrobeRequestPacket decode(ByteBuf b) { return new WardrobeRequestPacket(WardrobeWire.readQuery(new FriendlyByteBuf(b))); }
        public void encode(ByteBuf b, WardrobeRequestPacket v) { WardrobeWire.writeQuery(v.value(), new FriendlyByteBuf(b)); }
    };
    public Type<WardrobeRequestPacket> type() { return TYPE; }
}
