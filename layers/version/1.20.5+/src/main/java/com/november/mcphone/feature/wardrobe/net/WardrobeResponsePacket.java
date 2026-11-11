package com.november.mcphone.feature.wardrobe.net;
import com.november.mcphone.MCphone;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** 포키 옷장 패킷 포장 */
public record WardrobeResponsePacket(WardrobeWire.Reply value) implements CustomPacketPayload {
    public static final Type<WardrobeResponsePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MCphone.MODID, "poki_wardrobe_response_v1"));
    public static final StreamCodec<ByteBuf, WardrobeResponsePacket> STREAM_CODEC = new StreamCodec<>() {
        public WardrobeResponsePacket decode(ByteBuf b) { return new WardrobeResponsePacket(WardrobeWire.readReply(new FriendlyByteBuf(b))); }
        public void encode(ByteBuf b, WardrobeResponsePacket v) { WardrobeWire.writeReply(v.value(), new FriendlyByteBuf(b)); }
    };
    public Type<WardrobeResponsePacket> type() { return TYPE; }
}
