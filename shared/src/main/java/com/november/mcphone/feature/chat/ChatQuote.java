package com.november.mcphone.feature.chat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import java.util.UUID;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/** 服务端生成的单层引用快照；不递归保留原消息的引用，也不保存图片像素。 */
public record ChatQuote(UUID message, UUID sender, String name, String text, boolean image) {
    public static final int NAME_LIMIT = 64, TEXT_LIMIT = TextBody.MAX_LENGTH;
    public ChatQuote {
        Objects.requireNonNull(message); Objects.requireNonNull(sender);
        if (name == null || name.length() > NAME_LIMIT || text == null || text.length() > TEXT_LIMIT)
            throw new IllegalArgumentException("引用快照超限");
    }
    public static ChatQuote from(ChatMessage original, String name) {
        String safeName = com.november.mcphone.util.TextSanitizer.sanitize(name, NAME_LIMIT);
        String text = original.body() instanceof TextBody t ? t.text() : "";
        text = com.november.mcphone.util.TextSanitizer.sanitize(text.replace('\n', ' ').replace('\r', ' '), TEXT_LIMIT);
        return new ChatQuote(original.id(), original.sender(), safeName, text, original.body() instanceof ImageBody);
    }
    public static final Codec<ChatQuote> CODEC = RecordCodecBuilder.create(i -> i.group(
        UUIDUtil.CODEC.fieldOf("message").forGetter(ChatQuote::message),
        UUIDUtil.CODEC.fieldOf("sender").forGetter(ChatQuote::sender),
        bounded(NAME_LIMIT).fieldOf("name").forGetter(ChatQuote::name),
        bounded(TEXT_LIMIT).fieldOf("text").forGetter(ChatQuote::text),
        Codec.BOOL.fieldOf("image").forGetter(ChatQuote::image)).apply(i, ChatQuote::new));

    /** DFU 6 的 optionalFieldOf 会吞掉坏字段；四个平台都明确区分“缺失”和“损坏”。 */
    public static final MapCodec<Optional<ChatQuote>> OPTIONAL_FIELD = new MapCodec<>() {
        public <T> DataResult<Optional<ChatQuote>> decode(DynamicOps<T> ops, MapLike<T> input) {
            T value = input.get("quote");
            return value == null ? DataResult.success(Optional.empty()) : CODEC.parse(ops,value).map(Optional::of);
        }
        public <T> RecordBuilder<T> encode(Optional<ChatQuote> value, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            return value.isPresent() ? prefix.add("quote",CODEC.encodeStart(ops,value.get())) : prefix;
        }
        public <T> Stream<T> keys(DynamicOps<T> ops) { return Stream.of(ops.createString("quote")); }
    };

    private static Codec<String> bounded(int limit) {
        // DFU 6（Forge 1.20.1）没有 Codec.validate，使用各目标共通的双向校验入口。
        java.util.function.Function<String,DataResult<String>> check = value -> value.length() <= limit
                ? DataResult.success(value) : DataResult.error(() -> "引用字段超过 " + limit + " 字符");
        return Codec.STRING.flatXmap(check,check);
    }
}
