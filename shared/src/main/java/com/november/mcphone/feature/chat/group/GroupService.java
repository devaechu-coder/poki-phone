package com.november.mcphone.feature.chat.group;

import com.november.mcphone.feature.chat.ChatMessage;
import com.november.mcphone.feature.chat.FriendData;
import com.november.mcphone.feature.chat.FriendGuard;
import com.november.mcphone.feature.chat.ImageBody;
import com.november.mcphone.feature.chat.TextBody;
import com.november.mcphone.util.TextSanitizer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 服务端群业务，主线程调用；不构造网络包，不信任客户端的名字、身份或成员权限。 */
public final class GroupService {
    private GroupService() {}
    public static GroupRoom read(ServerPlayer player, UUID group) {
        return GroupData.get(player.level().getServer()).book().room(group, player.getUUID());
    }
    public static List<GroupSummary> summaries(ServerPlayer player) {
        return GroupData.get(player.level().getServer()).book().groups(player.getUUID()).stream()
                .map(room -> GroupSummary.of(room, player.getUUID()))
                .sorted(Comparator.comparingLong(GroupSummary::lastTime).reversed().thenComparing(GroupSummary::id)).toList();
    }
    public static GroupRoom create(ServerPlayer player, String name, List<UUID> members) {
        requirePhone(player);
        var server = player.level().getServer(); var friends = FriendData.get(server);
        var data = GroupData.get(server);
        expireInvitations(data);
        var invited = members.stream().map(id -> member(server, friends, id)).toList();
        var room = data.book().create(UUID.randomUUID(), member(server, friends, player.getUUID()), name,
                invited, id -> friends.areFriends(player.getUUID(), id));
        data.setDirty(); return room;
    }
    public static GroupMessage sendText(ServerPlayer player, UUID group, String raw) {
        return sendText(player,group,raw,java.util.Optional.empty());
    }
    public static GroupMessage sendText(ServerPlayer player, UUID group, String raw, java.util.Optional<UUID> reply) {
        return sendText(player,group,raw,reply,UUID.randomUUID());
    }
    public static GroupMessage sendText(ServerPlayer player, UUID group, String raw, java.util.Optional<UUID> reply, UUID ticket) {
        requirePhone(player);
        var room = read(player,group);
        String text = TextSanitizer.sanitize(raw, TextBody.MAX_LENGTH);
        if (text.isBlank()) throw new GroupFault(GroupStatus.INVALID);
        UUID id = reply.isPresent() ? com.november.mcphone.feature.chat.ChatReplyIdentity.messageId(player.getUUID(),group,true,ticket) : UUID.randomUUID();
        if (reply.isPresent()) {
            var existing = room.messages().stream().filter(m -> m.message().id().equals(id)).findFirst();
            if (existing.isPresent()) {
                if (!com.november.mcphone.feature.chat.ChatReplyIdentity.matches(existing.get().message(),player.getUUID(),reply.get(),text)
                        || room.visible(player.getUUID()).stream().noneMatch(m -> m.message().id().equals(id)))
                    throw new GroupFault(GroupStatus.INVALID);
                return existing.get();
            }
        }
        java.util.Optional<com.november.mcphone.feature.chat.ChatQuote> quote = java.util.Optional.empty();
        if (reply.isPresent()) {
            var original = room.visible(player.getUUID()).stream().filter(m -> m.message().id().equals(reply.get())).findFirst()
                    .orElseThrow(() -> new GroupFault(GroupStatus.INVALID));
            quote = java.util.Optional.of(com.november.mcphone.feature.chat.ChatQuote.from(original.message(),original.senderName()));
        }
        return append(player, group, new ChatMessage(id, player.getUUID(), System.currentTimeMillis(), new TextBody(text,quote)));
    }
    public static GroupMessage sendImage(ServerPlayer player, UUID group, UUID image, int w, int h, int frames, int ms) {
        return append(player, group, new ChatMessage(player.getUUID(), System.currentTimeMillis(), new ImageBody(image, w, h, frames, ms)));
    }
    private static GroupMessage append(ServerPlayer player, UUID group, ChatMessage message) {
        requirePhone(player); read(player, group);
        var server = player.level().getServer(); var data = GroupData.get(server);
        var room = data.book().room(group, player.getUUID());
        var before = imageIds(room.messages());
        var entry = data.book().append(group, player.getUUID(), member(server, FriendData.get(server), player.getUUID()).name(), message);
        data.setDirty();
        GroupImageFiles.clean(server, before, imageReferences(server, group));
        return entry;
    }
    public static void markRead(ServerPlayer player, UUID group, long through) {
        var data = GroupData.get(player.level().getServer());
        var before = data.book().room(group, player.getUUID());
        data.book().markRead(group, player.getUUID(), through);
        if (before != data.book().room(group, player.getUUID())) data.setDirty();
    }
    public static boolean delete(ServerPlayer player, UUID group, UUID message) {
        requirePhone(player);
        var data = GroupData.get(player.level().getServer());
        boolean result = data.book().hide(group, player.getUUID(), message);
        if (result) data.setDirty(); return result;
    }
    public static GroupRoom leave(ServerPlayer player, UUID group) {
        requirePhone(player); var before = read(player, group);
        var data = GroupData.get(player.level().getServer()); data.book().leave(group, player.getUUID());
        data.setDirty(); return before;
    }
    public static GroupRoom dissolve(ServerPlayer player, UUID group) {
        requirePhone(player); var server = player.level().getServer(); var data = GroupData.get(server);
        var before = data.book().dissolve(group, player.getUUID()); data.setDirty();
        GroupImageFiles.clean(server, imageIds(before.messages()), Set.of()); return before;
    }
    public static GroupRoom rename(ServerPlayer player, UUID group, String name) {
        requirePhone(player); var data = GroupData.get(player.level().getServer());
        data.book().rename(group, player.getUUID(), name); data.setDirty(); return read(player, group);
    }
    public static GroupRoom invite(ServerPlayer player, UUID group, List<UUID> members) {
        requirePhone(player); var server = player.level().getServer(); var data = GroupData.get(server);
        expireInvitations(data);
        var friends = FriendData.get(server); int before = data.book().invitationCount();
        var room = data.book().invite(group, player.getUUID(), members.stream().map(id -> member(server, friends, id)).toList(),
                id -> friends.areFriends(player.getUUID(), id), System.currentTimeMillis());
        if (data.book().invitationCount() != before) data.setDirty(); return room;
    }
    public static List<GroupInviteNotice> notices(ServerPlayer player) {
        var data = GroupData.get(player.level().getServer()); expireInvitations(data);
        return data.book().notices(player.getUUID(), System.currentTimeMillis());
    }
    public static GroupRoom respondInvitation(ServerPlayer player, UUID group, UUID invitation, boolean accept) {
        requirePhone(player); var server = player.level().getServer(); var data = GroupData.get(server); expireInvitations(data);
        var friends = FriendData.get(server);
        var room = data.book().respond(group, invitation, member(server, friends, player.getUUID()), accept,
                friends::areFriends, System.currentTimeMillis());
        data.setDirty(); return room;
    }
    private static void expireInvitations(GroupData data) {
        if (data.book().expireInvitations(System.currentTimeMillis()) > 0) data.setDirty();
    }
    public static void requirePhone(ServerPlayer player) {
        if (player.hasDisconnected() || player.level().getServer().getPlayerList().getPlayer(player.getUUID()) != player
                || !FriendGuard.carriesPhone(player)) throw new GroupFault(GroupStatus.NO_PHONE);
    }
    public static boolean mayReadImage(ServerPlayer player, UUID group, UUID image) {
        try { return read(player, group).visible(player.getUUID()).stream()
                .anyMatch(m -> m.message().body() instanceof ImageBody body && body.image().equals(image)); }
        catch (GroupFault error) { return false; }
    }
    public static Set<UUID> imageReferences(MinecraftServer server) {
        // 像素只留每群最近 20 个不同 ID；个人删除不缩小这份共享引用集合。
        var result = new java.util.HashSet<UUID>();
        for (var room : GroupData.get(server).book().snapshot()) {
            var ids = new java.util.LinkedHashSet<UUID>();
            for (int i = room.messages().size() - 1; i >= 0 && ids.size() < com.november.mcphone.feature.chat.ChatImage.MAX_IMAGES_PER_CONVERSATION; i--) {
                if (room.messages().get(i).message().body() instanceof ImageBody image) ids.add(image.image());
            }
            result.addAll(ids);
        }
        return Set.copyOf(result);
    }
    public static Set<UUID> imageReferences(MinecraftServer server, UUID group) {
        var room = GroupData.get(server).book().forMaintenance(group);
        if (room == null) return Set.of();
        var result = new java.util.LinkedHashSet<UUID>();
        for (int i = room.messages().size() - 1; i >= 0 && result.size() < com.november.mcphone.feature.chat.ChatImage.MAX_IMAGES_PER_CONVERSATION; i--) {
            if (room.messages().get(i).message().body() instanceof ImageBody image) result.add(image.image());
        }
        return Set.copyOf(result);
    }
    private static Set<UUID> imageIds(List<GroupMessage> messages) {
        return messages.stream().filter(m -> m.message().body() instanceof ImageBody)
                .map(m -> ((ImageBody) m.message().body()).image()).collect(java.util.stream.Collectors.toSet());
    }
    private static GroupMember member(MinecraftServer server, FriendData friends, UUID id) {
        var online = server.getPlayerList().getPlayer(id);
        String name = online == null ? friends.getName(id) : online.getName().getString();
        if (name == null || name.isBlank()) name = id.toString().substring(0, 8);
        name = TextSanitizer.sanitize(name, GroupLimits.NAME_LENGTH);
        return new GroupMember(id, name.isBlank() ? id.toString().substring(0, 8) : name);
    }
}
