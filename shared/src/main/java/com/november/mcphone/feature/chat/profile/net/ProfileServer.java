package com.november.mcphone.feature.chat.profile.net;

import com.november.mcphone.util.TextSanitizer;
import com.november.mcphone.feature.chat.FriendData;
import com.november.mcphone.feature.chat.FriendGuard;
import com.november.mcphone.feature.chat.profile.ProfileData;
import net.minecraft.server.level.ServerPlayer;
import java.util.WeakHashMap;
import java.util.UUID;
import java.util.Map;
import java.util.function.BiConsumer;

/** 网络门面已切到服务端主线程；身份、限流、持久化完全由服务端裁定。 */
public final class ProfileServer {
    private ProfileServer() {}
    private static BiConsumer<ServerPlayer, ProfileWire.Reply> reply;
    private static final Map<ServerPlayer, Long> LAST = new WeakHashMap<>();
    public static void install(BiConsumer<ServerPlayer, ProfileWire.Reply> sender) { reply = sender; }
    public static void handle(ProfileWire.Query q, ServerPlayer player) {
        String name = "", introduction = "";
        ProfileWire.Status status = ProfileWire.Status.OK;
        try {
            var server = player.level().getServer();
            if (player.hasDisconnected() || server.getPlayerList().getPlayer(player.getUUID()) != player || !FriendGuard.carriesPhone(player))
                throw new SecurityException("缺少手机或连接失效");
            if (q.update() && !player.getUUID().equals(q.target())) throw new SecurityException("只能修改自己的介绍");
            long now = System.currentTimeMillis();
            if (now - LAST.getOrDefault(player, 0L) < 200) { status = ProfileWire.Status.BUSY; }
            else {
                LAST.put(player, now);
                var data = ProfileData.get(server);
                if (q.update() && data.book().update(player.getUUID(), q.target(), q.introduction())) data.setDirty();
                introduction = data.book().get(q.target());
                var online = server.getPlayerList().getPlayer(q.target());
                String known = online == null ? FriendData.get(server).getName(q.target()) : online.getName().getString();
                name = known == null ? q.target().toString().substring(0, 8) : TextSanitizer.sanitize(known, ProfileWire.NAME_LENGTH);
            }
        } catch (SecurityException e) { status = ProfileWire.Status.FORBIDDEN; }
        catch (IllegalArgumentException e) { status = ProfileWire.Status.INVALID; }
        catch (RuntimeException e) { com.november.mcphone.MCphone.LOGGER.error("个人资料请求失败", e); status = ProfileWire.Status.UNAVAILABLE; }
        reply.accept(player, new ProfileWire.Reply(q.request(), q.target(), status, name, introduction));
    }
}
