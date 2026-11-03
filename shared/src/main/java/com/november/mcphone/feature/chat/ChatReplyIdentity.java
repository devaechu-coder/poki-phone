package com.november.mcphone.feature.chat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** 引用提交的身份协议。服务端绑定真实作者和会话；重试沿用票据，新的发送使用新票据。 */
public final class ChatReplyIdentity {
    private ChatReplyIdentity() {}

    public static UUID messageId(UUID actor, UUID target, boolean group, UUID ticket) {
        String seed = "mcphone/reply/v1/" + (group ? "group/" : "direct/")
                + actor + "/" + target + "/" + ticket;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    /** 同一票据不能被用来改写已经存下的正文或引用目标。 */
    public static boolean matches(ChatMessage message, UUID actor, UUID original, String text) {
        return message.sender().equals(actor) && message.body() instanceof TextBody body
                && body.text().equals(text) && body.quote().map(q -> q.message().equals(original)).orElse(false);
    }
}
