package com.november.mcphone.feature.chat.group;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 只给本群成员的个人摘要，最后一条与未读数已应用本人的删除记录。 */
public record GroupSummary(UUID id, String name, UUID owner, int memberCount, List<UUID> portraits,
                           Optional<GroupMessage> last, int unread) {
    public GroupSummary {
        java.util.Objects.requireNonNull(id); java.util.Objects.requireNonNull(owner);
        portraits = List.copyOf(portraits); java.util.Objects.requireNonNull(last);
        if (name == null || name.isBlank() || name.length() > GroupLimits.NAME_LENGTH || memberCount < 1
                || memberCount > GroupLimits.MEMBERS || portraits.size() > 4 || unread < 0 || unread > GroupLimits.MESSAGES)
            throw new GroupFault(GroupStatus.INVALID);
    }
    public static GroupSummary of(GroupRoom room, UUID player) {
        var visible = room.visible(player);
        return new GroupSummary(room.id(), room.name(), room.owner(), room.members().size(),
                room.members().stream().limit(4).map(GroupMember::id).toList(),
                visible.isEmpty() ? Optional.empty() : Optional.of(visible.get(visible.size() - 1)), room.unread(player));
    }
    public long lastTime() { return last.map(m -> m.message().time()).orElse(0L); }
}
