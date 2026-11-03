package com.november.mcphone.feature.chat.group.net;
import com.november.mcphone.MCphone;
import com.november.mcphone.feature.chat.ChatMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** 版本层仅适配正文 codec 和 payload，不承载群业务。 */
public record GroupRequestPacket(GroupCommand value) implements CustomPacketPayload {
    public static final Type<GroupRequestPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MCphone.MODID, "group_request_v5"));
    public static final StreamCodec<ByteBuf, GroupRequestPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override public GroupRequestPacket decode(ByteBuf b) {
            return new GroupRequestPacket(GroupWire.readCommand(new FriendlyByteBuf(b)));
        }
        @Override public void encode(ByteBuf b, GroupRequestPacket v) {
            GroupWire.writeCommand(v.value(), new FriendlyByteBuf(b));
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
