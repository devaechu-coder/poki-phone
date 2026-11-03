package com.november.mcphone.feature.chat.group;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.november.mcphone.feature.chat.ChatMessage;

/** 群内递增序号与发送时的名称；成员退出后，旧消息仍有正确的头像 UUID 和名称。 */
public record GroupMessage(long sequence, String senderName, ChatMessage message) {
    public GroupMessage {
        java.util.Objects.requireNonNull(message);
        if (sequence <= 0 || senderName == null || senderName.isBlank()
                || senderName.length() > GroupLimits.NAME_LENGTH) throw new GroupFault(GroupStatus.INVALID);
    }
    public static final Codec<GroupMessage> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("sequence").forGetter(GroupMessage::sequence),
            Codec.STRING.fieldOf("sender_name").forGetter(GroupMessage::senderName),
            ChatMessage.CODEC.fieldOf("message").forGetter(GroupMessage::message)).apply(i, GroupMessage::new));
}
