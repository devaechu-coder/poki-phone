package com.november.mcphone.feature.chat.group.net;

import com.november.mcphone.feature.chat.*;
import com.november.mcphone.feature.chat.group.*;
import com.november.mcphone.feature.chat.net.ChatImageDataPacket;
import com.november.mcphone.core.ServerConfig;
import net.minecraft.Util;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

/** 平台只注册两类包；线程切换由网络门面保证，群业务与推送协调集中维护。 */
public final class GroupServer {
    private GroupServer() {}
    private static final GroupThrottle THROTTLE = new GroupThrottle();
    private static final GroupImageAssembly UPLOADS = new GroupImageAssembly();
    private static BiConsumer<ServerPlayer, GroupReply> reply;
    private static BiConsumer<ServerPlayer, ChatImageDataPacket> pixels;
    public static void install(BiConsumer<ServerPlayer, GroupReply> send, BiConsumer<ServerPlayer, ChatImageDataPacket> images) {
        reply = send; pixels = images;
    }
    public static void handle(GroupCommand command, ServerPlayer player) {
        if (reply == null || pixels == null) throw new IllegalStateException("群网络推送未注册");
        try {
            var data = GroupData.get(player.level().getServer());
            if (!data.isAvailable()) throw new GroupFault(GroupStatus.UNAVAILABLE);
            GroupImageFiles.sweepOnce(player.level().getServer());
            if (command instanceof GroupCommand.ListRooms) { throttle(player, GroupThrottle.Kind.DIRECTORY); directory(player); }
            else if (command instanceof GroupCommand.Notices) { throttle(player, GroupThrottle.Kind.NOTICES); notices(player); }
            else if (command instanceof GroupCommand.Create v) {
                throttle(player, GroupThrottle.Kind.CREATE); var room = GroupService.create(player, v.name(), v.members());
                refreshMembers(player, room); refreshInvitationRecipients(player, data.book().pendingRecipients(room.id()));
                result(player, v, GroupStatus.OK, room.id());
            } else if (command instanceof GroupCommand.History v) {
                throttle(player, GroupThrottle.Kind.HISTORY); var room = GroupService.read(player, v.group());
                reply.accept(player, new GroupReply.History(v.request(), room.id(), room.revision(), room.sequence(), GroupInfo.of(room), room.visible(player.getUUID())));
            } else if (command instanceof GroupCommand.SendText v) {
                throttle(player, GroupThrottle.Kind.TEXT);
                long before = GroupService.read(player,v.group()).sequence();
                var sent = GroupService.sendText(player,v.group(),v.text(),v.reply(),v.request());
                if (sent.sequence() <= before) result(player,v,GroupStatus.OK,v.group());
                else deliver(player,v.group(),v.request());
            } else if (command instanceof GroupCommand.MarkRead v) {
                throttle(player, GroupThrottle.Kind.READ); GroupService.markRead(player, v.group(), v.sequence()); directory(player);
            } else if (command instanceof GroupCommand.Details v) {
                throttle(player, GroupThrottle.Kind.DETAILS); var room = GroupService.read(player, v.group());
                reply.accept(player, new GroupReply.Details(v.request(), room.revision(), GroupInfo.of(room)));
            } else if (command instanceof GroupCommand.Delete v) delete(v, player);
            else if (command instanceof GroupCommand.Leave v) {
                throttle(player, GroupThrottle.Kind.MANAGE); var recipients = data.book().pendingRecipients(v.group());
                var room = GroupService.leave(player, v.group()); refreshInvitationRecipients(player, recipients);
                refreshMembers(player, room); result(player, v, GroupStatus.OK, v.group());
            } else if (command instanceof GroupCommand.Dissolve v) {
                throttle(player, GroupThrottle.Kind.MANAGE); var recipients = data.book().pendingRecipients(v.group());
                var room = GroupService.dissolve(player, v.group()); refreshInvitationRecipients(player, recipients);
                refreshMembers(player, room); result(player, v, GroupStatus.OK, v.group());
            } else if (command instanceof GroupCommand.Rename v) {
                throttle(player, GroupThrottle.Kind.MANAGE); var room = GroupService.rename(player, v.group(), v.name());
                refreshMembers(player, room); refreshInvitationRecipients(player, data.book().pendingRecipients(v.group()));
                result(player, v, GroupStatus.OK, v.group());
            } else if (command instanceof GroupCommand.Invite v) {
                throttle(player, GroupThrottle.Kind.MANAGE); var room = GroupService.invite(player, v.group(), v.members());
                refreshInvitationRecipients(player, data.book().pendingRecipients(room.id())); result(player, v, GroupStatus.OK, v.group());
            } else if (command instanceof GroupCommand.RespondInvitation v) {
                throttle(player, GroupThrottle.Kind.MANAGE); var room = GroupService.respondInvitation(player, v.group(), v.invitation(), v.accept());
                if (v.accept()) refreshMembers(player, room);
                notices(player); result(player, v, GroupStatus.OK, v.group());
            } else if (command instanceof GroupCommand.Images v) images(v, player);
            else if (command instanceof GroupCommand.Upload v) upload(v, player);
        } catch (GroupFault error) {
            if (command instanceof GroupCommand.Delete v) {
                ChatDeletionResult status = error.status() == GroupStatus.BUSY ? ChatDeletionResult.BUSY
                        : error.status() == GroupStatus.UNAVAILABLE ? ChatDeletionResult.STORAGE_ERROR : ChatDeletionResult.FORBIDDEN;
                reply.accept(player, new GroupReply.Deleted(v.request(), v.group(), v.message(), status, 0, List.of()));
            } else {
                result(player, command, error.status(), command.group());
                if (command instanceof GroupCommand.RespondInvitation && error.status() == GroupStatus.INVITATION_GONE) notices(player);
            }
        }
    }
    private static void delete(GroupCommand.Delete v, ServerPlayer player) {
        throttle(player, GroupThrottle.Kind.DELETE);
        boolean changed = GroupService.delete(player, v.group(), v.message()); var room = GroupService.read(player, v.group());
        reply.accept(player, new GroupReply.Deleted(v.request(), v.group(), v.message(), changed ? ChatDeletionResult.OK
                : ChatDeletionResult.NOT_FOUND, room.revision(), room.visible(player.getUUID())));
        directory(player);
    }
    private static void upload(GroupCommand.Upload v, ServerPlayer player) {
        GroupService.requirePhone(player); GroupService.read(player, v.group());
        if (!ServerConfig.allowChatImages()) throw new GroupFault(GroupStatus.INVALID);
        if (v.index() == 0) {
            throttle(player, GroupThrottle.Kind.UPLOAD);
            if (v.count() > ChatImage.maxChunks()) throw new GroupFault(GroupStatus.INVALID);
        }
        var done = UPLOADS.accept(player, v, ChatImage.maxBytes(), System.currentTimeMillis()); if (done == null) return;
        if (!ChatImageStore.looksLikePng(done.png())) throw new GroupFault(GroupStatus.INVALID);
        var server = player.level().getServer();
        Util.backgroundExecutor().execute(() -> {
            UUID image = GroupImageFiles.write(server, v.group(), done.png());
            server.execute(() -> {
                if (image == null) { result(player, v, GroupStatus.UNAVAILABLE, v.group()); return; }
                try { GroupService.sendImage(player, v.group(), image, v.width(), v.height(), v.frames(), v.frameMs()); deliver(player, v.group(), v.request()); }
                catch (GroupFault error) { result(player, v, error.status(), v.group()); }
                finally {
                    GroupImageFiles.release(server, image);
                    var data = GroupData.get(server);
                    if (data.isAvailable()) GroupImageFiles.clean(server, java.util.Set.of(image), GroupService.imageReferences(server, v.group()));
                }
            });
        });
    }
    private static void images(GroupCommand.Images v, ServerPlayer player) {
        throttle(player, GroupThrottle.Kind.IMAGES);
        var allowed = v.images().stream().distinct().filter(id -> GroupService.mayReadImage(player, v.group(), id)).toList();
        var server = player.level().getServer();
        Util.backgroundExecutor().execute(() -> {
            int total = 0;
            for (var id : allowed) {
                byte[] data = GroupImageFiles.read(server, id);
                if (data != null && total + data.length > ChatImage.maxBytes()) break;
                if (data != null) total += data.length;
                var packet = data == null ? ChatImageDataPacket.gone(id) : new ChatImageDataPacket(id, data);
                server.execute(() -> { if (!player.hasDisconnected() && GroupService.mayReadImage(player, v.group(), id)) pixels.accept(player, packet); });
            }
        });
    }
    private static void throttle(ServerPlayer player, GroupThrottle.Kind kind) {
        if (!THROTTLE.allow(player, kind, System.currentTimeMillis())) throw new GroupFault(GroupStatus.BUSY);
    }
    private static void directory(ServerPlayer player) { if (!player.hasDisconnected()) reply.accept(player, new GroupReply.Directory(GroupService.summaries(player))); }
    private static void notices(ServerPlayer player) { if (!player.hasDisconnected()) reply.accept(player, new GroupReply.Notices(GroupService.notices(player))); }
    private static void refreshInvitationRecipients(ServerPlayer actor, List<UUID> recipients) {
        var server = actor.level().getServer();
        for (var id : recipients) { var player = server.getPlayerList().getPlayer(id); if (player != null) notices(player); }
    }
    private static void result(ServerPlayer player, GroupCommand command, GroupStatus status, UUID group) {
        if (!player.hasDisconnected()) reply.accept(player, new GroupReply.Result(command.request(), group, status));
    }
    private static void refreshMembers(ServerPlayer actor, GroupRoom room) {
        var server = actor.level().getServer();
        for (var member : room.members()) {
            var online = server.getPlayerList().getPlayer(member.id()); if (online == null) continue;
            directory(online);
            // 离群/解散也刷新旧成员目录，但资料推送必须重新读取并验证当前资格。
            try { var current = GroupService.read(online, room.id()); reply.accept(online, new GroupReply.Updated(current.revision(), GroupInfo.of(current))); }
            catch (GroupFault gone) { if (gone.status() != GroupStatus.NOT_MEMBER) throw gone; }
        }
    }
    private static void deliver(ServerPlayer actor, UUID group, UUID request) {
        var room = GroupService.read(actor, group); var server = actor.level().getServer();
        for (var member : room.members()) {
            var online = server.getPlayerList().getPlayer(member.id());
            if (online != null) reply.accept(online, new GroupReply.Message(request, room.revision(), GroupSummary.of(room, member.id())));
        }
    }
}
