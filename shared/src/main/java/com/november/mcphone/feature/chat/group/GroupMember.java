package com.november.mcphone.feature.chat.group;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import java.util.UUID;

/** 名称由服务端已知玩家资料产生，离线成员仍可显示。 */
public record GroupMember(UUID id, String name) {
    public GroupMember {
        java.util.Objects.requireNonNull(id);
        if (name == null || name.isBlank() || name.length() > GroupLimits.NAME_LENGTH) throw new GroupFault(GroupStatus.INVALID);
    }
    public static final Codec<GroupMember> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(GroupMember::id),
            Codec.STRING.fieldOf("name").forGetter(GroupMember::name)).apply(i, GroupMember::new));
}
