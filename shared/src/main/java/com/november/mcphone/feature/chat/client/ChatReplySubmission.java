package com.november.mcphone.feature.chat.client;

import com.november.mcphone.feature.chat.ChatMessage;
import com.november.mcphone.feature.chat.ChatReplyIdentity;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/** 会话内的一次引用提交。超时保留票据；迟到回声不能确认另一条相同正文的发送。 */
final class ChatReplySubmission {
    static final long TIMEOUT_MS = 6000;
    enum Result { WAITING, ACCEPTED, TIMEOUT }
    private final UUID ticket = UUID.randomUUID();
    private final UUID reply, message;
    private final ChatComposer.Draft draft;
    private long deadline;
    private boolean timedOut;

    ChatReplySubmission(UUID reply, ChatComposer.Draft draft, UUID actor, UUID target, boolean group, long now) {
        this.reply = reply;
        this.draft = draft;
        message = ChatReplyIdentity.messageId(actor, target, group, ticket);
        retry(now);
    }
    UUID ticket() { return ticket; }
    UUID reply() { return reply; }
    UUID message() { return message; }
    boolean timedOut() { return timedOut; }
    boolean matches(UUID original, ChatComposer.Draft current) { return reply.equals(original) && draft.equals(current); }
    void retry(long now) { deadline = now + TIMEOUT_MS; timedOut = false; }
    void rejected() { timedOut = true; }

    /** 只清理未发生过编辑的原始草稿；清洗后相同或编辑后撤销均不算原草稿。 */
    boolean complete(ChatReplyDraft current, Predicate<ChatComposer.Draft> clear) {
        if (current.id().isPresent() && !current.id().get().equals(reply)) return false;
        if (!clear.test(draft)) return false;
        current.clear();
        return true;
    }
    Result poll(List<ChatMessage> messages, UUID self, long now) {
        if (messages.stream().anyMatch(m -> m.id().equals(message) && m.sender().equals(self))) return Result.ACCEPTED;
        if (!timedOut && now >= deadline) { timedOut = true; return Result.TIMEOUT; }
        return Result.WAITING;
    }
}
