package com.november.mcphone.feature.chat.group.net;
import com.november.mcphone.MCphone;
import com.november.mcphone.feature.chat.ChatMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** 版本层仅适配正文 codec 和 payload，不承载群业务。 */
public record GroupResponsePacket(GroupReply value) implements CustomPacketPayload {
    public static final Type<GroupResponsePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MCphone.MODID, "group_response_v5"));
    public static final StreamCodec<ByteBuf, GroupResponsePacket> STREAM_CODEC = new StreamCodec<>() {
        @Override public GroupResponsePacket decode(ByteBuf b) {
            return new GroupResponsePacket(GroupWire.readReply(new FriendlyByteBuf(b), ChatMessage.STREAM_CODEC::decode));
        }
        @Override public void encode(ByteBuf b, GroupResponsePacket v) {
            GroupWire.writeReply(v.value(), new FriendlyByteBuf(b), (message, buf) -> ChatMessage.STREAM_CODEC.encode(buf, message));
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
