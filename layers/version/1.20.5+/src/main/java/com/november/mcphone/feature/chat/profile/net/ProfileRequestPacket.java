package com.november.mcphone.feature.chat.profile.net;
import com.november.mcphone.MCphone;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** 现代版本的个人资料载荷包装。 */
public record ProfileRequestPacket(ProfileWire.Query value) implements CustomPacketPayload {
    public static final Type<ProfileRequestPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MCphone.MODID, "profile_request_v1"));
    public static final StreamCodec<ByteBuf, ProfileRequestPacket> STREAM_CODEC = new StreamCodec<>() {
        public ProfileRequestPacket decode(ByteBuf b) { return new ProfileRequestPacket(ProfileWire.readQuery(new FriendlyByteBuf(b))); }
        public void encode(ByteBuf b, ProfileRequestPacket v) { ProfileWire.writeQuery(v.value(), new FriendlyByteBuf(b)); }
    };
    public Type<ProfileRequestPacket> type() { return TYPE; }
}
