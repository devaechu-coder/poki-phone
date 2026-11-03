package com.november.mcphone.feature.chat.group;

import java.util.UUID;

/** 仅发给被邀请者的通知投影，不携带成员清单、消息或图片身份。群名取当前服务器资料。 */
public record GroupInviteNotice(UUID id, UUID group, String name, GroupMember inviter, long createdAt, long expiresAt) {
    public GroupInviteNotice {
        java.util.Objects.requireNonNull(id); java.util.Objects.requireNonNull(group); java.util.Objects.requireNonNull(inviter);
        if (name == null || name.isBlank() || name.length() > GroupLimits.NAME_LENGTH || createdAt < 0 || expiresAt <= createdAt)
            throw new GroupFault(GroupStatus.INVALID);
    }
    static GroupInviteNotice of(GroupInvitation invitation, GroupRoom room) {
        return new GroupInviteNotice(invitation.id(), room.id(), room.name(), invitation.inviter(), invitation.createdAt(), invitation.expiresAt());
    }
}
