package com.november.mcphone.feature.chat.group;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** 成员详情单独请求，不把整张成员表塞进每一帧的会话摘要。 */
public record GroupInfo(UUID id, String name, UUID owner, List<GroupMember> members) {
    public GroupInfo {
        java.util.Objects.requireNonNull(id); java.util.Objects.requireNonNull(owner); members = List.copyOf(members);
        if (name == null || name.isBlank() || name.length() > GroupLimits.NAME_LENGTH
                || members.isEmpty() || members.size() > GroupLimits.MEMBERS) throw new GroupFault(GroupStatus.INVALID);
        var unique = new HashSet<UUID>();
        for (var member : members) if (!unique.add(member.id())) throw new GroupFault(GroupStatus.INVALID);
        if (!unique.contains(owner)) throw new GroupFault(GroupStatus.INVALID);
    }
    public static GroupInfo of(GroupRoom room) { return new GroupInfo(room.id(), room.name(), room.owner(), room.members()); }
}
