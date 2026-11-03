package com.november.mcphone.feature.chat.net;

import com.november.mcphone.MCphone;
import com.november.mcphone.feature.chat.TextBody;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** C2S：发一条消息。发件人与时间戳都不带：让客户端指定发件人等于允许冒充，时间戳一律以服务端为准。 */
public record SendChatMessagePacket(UUID target, String text, java.util.Optional<UUID> reply, java.util.Optional<UUID> ticket) implements CustomPacketPayload {
    public SendChatMessagePacket(UUID target, String text) { this(target,text,java.util.Optional.empty()); }
    public SendChatMessagePacket(UUID target, String text, java.util.Optional<UUID> reply) {
        this(target,text,reply,reply.map(ignored -> UUID.randomUUID()));
    }
    public SendChatMessagePacket {
        java.util.Objects.requireNonNull(target); java.util.Objects.requireNonNull(text);
        java.util.Objects.requireNonNull(reply); java.util.Objects.requireNonNull(ticket);
        if (reply.isPresent() != ticket.isPresent()) throw new IllegalArgumentException("引用和提交票据必须同时存在");
    }

    public static final CustomPacketPayload.Type<SendChatMessagePacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MCphone.MODID, "send_chat_message_reply_v2"));

    public static final StreamCodec<ByteBuf, SendChatMessagePacket> STREAM_CODEC =
            new StreamCodec<>() {
                public SendChatMessagePacket decode(ByteBuf raw) {
                    var b = new net.minecraft.network.FriendlyByteBuf(raw);
                    return new SendChatMessagePacket(b.readUUID(),b.readUtf(TextBody.MAX_LENGTH),ChatReplyWire.readId(b),ChatReplyWire.readId(b));
                }
                public void encode(ByteBuf raw, SendChatMessagePacket value) {
                    var b = new net.minecraft.network.FriendlyByteBuf(raw);
                    b.writeUUID(value.target()); b.writeUtf(value.text(),TextBody.MAX_LENGTH);
                    ChatReplyWire.writeId(b,value.reply()); ChatReplyWire.writeId(b,value.ticket());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
