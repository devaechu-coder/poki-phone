package com.november.mcphone.feature.chat.group;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import java.util.UUID;

/** 待本人处理的入群邀请；唯一 ID 约束同意/拒绝，创建时不会取得成员资格。 */
public record GroupInvitation(UUID id, UUID group, GroupMember inviter, UUID recipient, long createdAt, long expiresAt) {
    public GroupInvitation {
        java.util.Objects.requireNonNull(id); java.util.Objects.requireNonNull(group);
        java.util.Objects.requireNonNull(inviter); java.util.Objects.requireNonNull(recipient);
        if (inviter.id().equals(recipient) || createdAt < 0 || expiresAt <= createdAt) throw new GroupFault(GroupStatus.INVALID);
    }
    public boolean expired(long now) { return now >= expiresAt; }
    public static final Codec<GroupInvitation> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(GroupInvitation::id),
            UUIDUtil.CODEC.fieldOf("group").forGetter(GroupInvitation::group),
            GroupMember.CODEC.fieldOf("inviter").forGetter(GroupInvitation::inviter),
            UUIDUtil.CODEC.fieldOf("recipient").forGetter(GroupInvitation::recipient),
            Codec.LONG.fieldOf("created_at").forGetter(GroupInvitation::createdAt),
            Codec.LONG.fieldOf("expires_at").forGetter(GroupInvitation::expiresAt)).apply(i, GroupInvitation::new));
}
