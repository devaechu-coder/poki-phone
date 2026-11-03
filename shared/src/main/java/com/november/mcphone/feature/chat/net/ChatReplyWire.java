package com.november.mcphone.feature.chat.net;

import com.november.mcphone.feature.chat.ChatQuote;
import net.minecraft.network.FriendlyByteBuf;
import java.util.Optional;
import java.util.UUID;

/** 两代正文 codec 共用的有界引用字段；请求只带 ID，绝不采信客户端快照。 */
public final class ChatReplyWire {
    private ChatReplyWire() {}
    public static void writeId(FriendlyByteBuf b, Optional<UUID> id) {
        b.writeBoolean(id.isPresent()); id.ifPresent(b::writeUUID);
    }
    public static Optional<UUID> readId(FriendlyByteBuf b) { return b.readBoolean() ? Optional.of(b.readUUID()) : Optional.empty(); }
    public static void write(FriendlyByteBuf b, Optional<ChatQuote> quote) {
        b.writeBoolean(quote.isPresent());
        quote.ifPresent(q -> { b.writeUUID(q.message()); b.writeUUID(q.sender());
            b.writeUtf(q.name(),ChatQuote.NAME_LIMIT); b.writeUtf(q.text(),ChatQuote.TEXT_LIMIT); b.writeBoolean(q.image()); });
    }
    public static Optional<ChatQuote> read(FriendlyByteBuf b) {
        return b.readBoolean() ? Optional.of(new ChatQuote(b.readUUID(),b.readUUID(),
            b.readUtf(ChatQuote.NAME_LIMIT),b.readUtf(ChatQuote.TEXT_LIMIT),b.readBoolean())) : Optional.empty();
    }
}
