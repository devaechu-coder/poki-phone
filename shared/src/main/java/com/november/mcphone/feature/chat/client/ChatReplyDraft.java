package com.november.mcphone.feature.chat.client;

import com.november.mcphone.feature.chat.ChatQuote;
import java.util.Optional;
import java.util.UUID;

/** 会话拥有的单个待引用目标；选区、网络、正文输入和静态缓存不进入这里。 */
final class ChatReplyDraft {
    private ChatQuote quote;
    ChatQuote quote() { return quote; }
    Optional<UUID> id() { return quote == null ? Optional.empty() : Optional.of(quote.message()); }
    void select(ChatQuote value) { quote = java.util.Objects.requireNonNull(value); }
    void clear() { quote = null; }
}
