package com.november.mcphone.feature.chat.profile.net;
import com.november.mcphone.MCphone;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** 现代版本的个人资料载荷包装。 */
public record ProfileResponsePacket(ProfileWire.Reply value) implements CustomPacketPayload {
    public static final Type<ProfileResponsePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MCphone.MODID, "profile_response_v1"));
    public static final StreamCodec<ByteBuf, ProfileResponsePacket> STREAM_CODEC = new StreamCodec<>() {
        public ProfileResponsePacket decode(ByteBuf b) { return new ProfileResponsePacket(ProfileWire.readReply(new FriendlyByteBuf(b))); }
        public void encode(ByteBuf b, ProfileResponsePacket v) { ProfileWire.writeReply(v.value(), new FriendlyByteBuf(b)); }
    };
    public Type<ProfileResponsePacket> type() { return TYPE; }
}
