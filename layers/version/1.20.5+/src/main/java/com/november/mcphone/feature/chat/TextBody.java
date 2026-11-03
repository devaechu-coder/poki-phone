package com.november.mcphone.feature.chat;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** 玩家打的一段字。text 存原样，清洗在收包时做（见 ChatService.sendMessage）。 */
public record TextBody(String text, java.util.Optional<ChatQuote> quote) implements MessageBody {
    public TextBody(String text) { this(text, java.util.Optional.empty()); }
    public TextBody { java.util.Objects.requireNonNull(text); java.util.Objects.requireNonNull(quote); }

    /** 单条消息长度上限，在编解码器层面封死 */
    public static final int MAX_LENGTH = 256;

    public static final MapCodec<TextBody> MAP_CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    com.mojang.serialization.Codec.STRING.fieldOf("text").forGetter(TextBody::text),
                    ChatQuote.OPTIONAL_FIELD.forGetter(TextBody::quote)
            ).apply(instance, TextBody::new)
    );

    /** 超长文本在解码阶段就被拒收，轮不到业务层 */
    public static final StreamCodec<ByteBuf, TextBody> STREAM_CODEC =
            new StreamCodec<>() {
                public TextBody decode(ByteBuf raw) {
                    var b = new net.minecraft.network.FriendlyByteBuf(raw);
                    return new TextBody(b.readUtf(MAX_LENGTH), com.november.mcphone.feature.chat.net.ChatReplyWire.read(b));
                }
                public void encode(ByteBuf raw, TextBody value) {
                    var b = new net.minecraft.network.FriendlyByteBuf(raw);
                    b.writeUtf(value.text(), MAX_LENGTH);
                    com.november.mcphone.feature.chat.net.ChatReplyWire.write(b,value.quote());
                }
            };

    @Override
    public MessageKind kind() {
        return MessageKind.TEXT;
    }

    @Override
    public Component preview() {
        return Component.literal(text);
    }
}
