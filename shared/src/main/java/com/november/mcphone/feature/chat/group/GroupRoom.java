package com.november.mcphone.feature.chat.group;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** 完整且不可变的群状态；群 ID 独立于玩家 UUID，不借用双人 ConversationKey。 */
public record GroupRoom(UUID id, String name, UUID owner, List<GroupMember> members,
                        List<GroupMessage> messages, long sequence, long revision,
                        Map<UUID, Long> read, Map<UUID, List<UUID>> deleted, Map<UUID, Long> joinedAfter) {
    /** 原有群成员从建群开始可见；旧格式读取不改变已有成员的历史。 */
    public GroupRoom(UUID id, String name, UUID owner, List<GroupMember> members, List<GroupMessage> messages,
                     long sequence, long revision, Map<UUID, Long> read, Map<UUID, List<UUID>> deleted) {
        this(id, name, owner, members, messages, sequence, revision, read, deleted, Map.of());
    }
    public GroupRoom {
        java.util.Objects.requireNonNull(id); java.util.Objects.requireNonNull(owner);
        if (name == null || name.isBlank() || name.length() > GroupLimits.NAME_LENGTH
                || members.isEmpty() || members.size() > GroupLimits.MEMBERS
                || messages.size() > GroupLimits.MESSAGES || sequence < 0 || revision < 0)
            throw new GroupFault(GroupStatus.INVALID);
        members = List.copyOf(members); messages = List.copyOf(messages); read = Map.copyOf(read);
        joinedAfter = Map.copyOf(joinedAfter);
        deleted = deleted.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                e -> List.copyOf(e.getValue())));
        var ids = new HashSet<UUID>();
        for (var member : members) if (!ids.add(member.id())) throw new GroupFault(GroupStatus.INVALID);
        if (!ids.contains(owner) || !ids.containsAll(read.keySet()) || !ids.containsAll(deleted.keySet()) || !ids.containsAll(joinedAfter.keySet()))
            throw new GroupFault(GroupStatus.INVALID);
        long previous = 0;
        var messageIds = new HashSet<UUID>();
        for (var entry : messages) {
            if (entry.sequence() <= previous || entry.sequence() > sequence || !messageIds.add(entry.message().id()))
                throw new GroupFault(GroupStatus.INVALID);
            previous = entry.sequence();
        }
        for (long value : read.values()) if (value < 0 || value > sequence) throw new GroupFault(GroupStatus.INVALID);
        for (long value : joinedAfter.values()) if (value < 0 || value > sequence) throw new GroupFault(GroupStatus.INVALID);
        for (var hidden : deleted.values()) {
            if (hidden.size() > GroupLimits.MESSAGES || new HashSet<>(hidden).size() != hidden.size()
                    || !messageIds.containsAll(hidden)) throw new GroupFault(GroupStatus.INVALID);
        }
    }
    public boolean contains(UUID player) { return members.stream().anyMatch(m -> m.id().equals(player)); }
    public void requireMember(UUID player) { if (!contains(player)) throw new GroupFault(GroupStatus.NOT_MEMBER); }
    public List<GroupMessage> visible(UUID player) {
        requireMember(player);
        var hidden = new HashSet<>(deleted.getOrDefault(player, List.of()));
        long joined = joinedAfter.getOrDefault(player, 0L);
        return messages.stream().filter(m -> m.sequence() > joined && !hidden.contains(m.message().id())).toList();
    }
    public int unread(UUID player) {
        long seen = read.getOrDefault(player, 0L);
        return (int) visible(player).stream().filter(m -> m.sequence() > seen && !m.message().sender().equals(player)).count();
    }
    public static final Codec<GroupRoom> CODEC = codec(true);
    /** 仅格式 1 没有历史边界；不能把它的缺省语义继续用于格式 2/3。 */
    static final Codec<GroupRoom> LEGACY_CODEC = codec(false);
    private static Codec<GroupRoom> codec(boolean requireJoinedAfter) {
        var joined = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG);
        return RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(GroupRoom::id), Codec.STRING.fieldOf("name").forGetter(GroupRoom::name),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(GroupRoom::owner), GroupMember.CODEC.listOf().fieldOf("members").forGetter(GroupRoom::members),
            GroupMessage.CODEC.listOf().fieldOf("messages").forGetter(GroupRoom::messages),
            Codec.LONG.fieldOf("sequence").forGetter(GroupRoom::sequence), Codec.LONG.fieldOf("revision").forGetter(GroupRoom::revision),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).fieldOf("read").forGetter(GroupRoom::read),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, UUIDUtil.CODEC.listOf()).fieldOf("deleted").forGetter(GroupRoom::deleted),
            (requireJoinedAfter ? joined.fieldOf("joined_after") : joined.optionalFieldOf("joined_after", Map.of())).forGetter(GroupRoom::joinedAfter)
        ).apply(i, GroupRoom::new));
    }
}
