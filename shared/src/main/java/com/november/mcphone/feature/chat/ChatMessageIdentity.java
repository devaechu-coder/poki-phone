package com.november.mcphone.feature.chat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** 旧记录在服务端装载时补 ID；同一旧存档重复装载得到相同结果，跨文件保存顺序也不会丢删除标记。 */
final class ChatMessageIdentity {
    public static final UUID MISSING_ID = new UUID(0, 0);
    private ChatMessageIdentity() {}
    public record Migration(List<ChatMessage> messages, boolean changed) {
        public Migration { messages = List.copyOf(messages); }
    }
    public static Migration normalize(ConversationKey conversation, List<ChatMessage> messages) {
        var used = new HashSet<UUID>();
        var result = new ArrayList<ChatMessage>();
        boolean changed = false;
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            UUID id = message.id();
            if (MISSING_ID.equals(id) || used.contains(id)) {
                String seed = "mcphone/message/" + conversation.toStorageKey() + "/" + i + "/"
                        + message.sender() + "/" + message.time() + "/" + legacyBody(message.body());
                int attempt = 0;
                do { id = UUID.nameUUIDFromBytes((seed + "/" + attempt++).getBytes(StandardCharsets.UTF_8)); }
                while (used.contains(id) || MISSING_ID.equals(id));
                message = new ChatMessage(id, message.sender(), message.time(), message.body());
                changed = true;
            }
            used.add(id);
            result.add(message);
        }
        return new Migration(result, changed);
    }

    /** 冻结 1.11.2 的迁移种子；record 新增字段不能改变尚未回存的旧消息身份。 */
    private static String legacyBody(MessageBody body) {
        if (body instanceof TextBody text) return "TextBody[text=" + text.text() + "]";
        if (body instanceof ImageBody image) return "ImageBody[image=" + image.image()
                + ", width=" + image.width() + ", height=" + image.height()
                + ", frames=" + image.frames() + ", frameMs=" + image.frameMs() + "]";
        throw new IllegalArgumentException("旧消息迁移不支持正文类型：" + body.kind());
    }
}
