package com.november.mcphone.feature.chat.client;

import com.november.mcphone.core.net.MCphoneNetwork;
import com.november.mcphone.feature.chat.group.net.*;
import com.november.mcphone.feature.chat.group.net.GroupRequestPacket;
import net.minecraft.client.Minecraft;
import java.util.UUID;

/** 客户端发送门面：共用摘要轮询时钟，避免三个页面同帧请求撞到服务端限流。 */
final class GroupTransport {
    private GroupTransport() {}
    private static java.lang.ref.WeakReference<Object> connection = new java.lang.ref.WeakReference<>(null);
    private static long lastDirectory;
    private static long lastNotices;
    private static final java.util.Map<UUID, Long> textRequests = new java.util.LinkedHashMap<>();
    static void send(GroupCommand command) {
        var current = Minecraft.getInstance().getConnection(); if (current == null) return;
        bind(current);
        if (command instanceof GroupCommand.SendText) {
            textRequests.entrySet().removeIf(e -> System.currentTimeMillis() - e.getValue() > GroupClientPolicy.OPERATION_TIMEOUT_MS);
            if (!textRequests.containsKey(command.request()) && textRequests.size() >= GroupClientPolicy.MAX_PENDING_OPERATIONS)
                textRequests.remove(textRequests.keySet().iterator().next());
            textRequests.put(command.request(), System.currentTimeMillis());
        }
        MCphoneNetwork.sendToServer(new GroupRequestPacket(command));
    }
    static void acknowledge(UUID request) { textRequests.remove(request); }
    static void clear() {
        connection.clear(); lastDirectory = lastNotices = 0; textRequests.clear();
    }
    private static void bind(Object current) {
        if (connection.get() == current) return;
        connection = new java.lang.ref.WeakReference<>(current); lastDirectory = lastNotices = 0; textRequests.clear();
    }
    static void result(GroupReply.Result reply) {
        if (textRequests.remove(reply.request()) == null || reply.status() == com.november.mcphone.feature.chat.group.GroupStatus.OK) return;
        var player = Minecraft.getInstance().player;
        if (player != null) com.november.mcphone.platform.PlayerAccess.message(player, net.minecraft.network.chat.Component.translatable(reply.status().key()), true);
    }
    static void directory() {
        var current = Minecraft.getInstance().getConnection(); if (current == null) return;
        long now = System.currentTimeMillis();
        bind(current);
        if (now - lastDirectory < GroupClientPolicy.REFRESH_INTERVAL_MS) return;
        lastDirectory = now; send(new GroupCommand.ListRooms(UUID.randomUUID()));
    }
    static void notices() {
        var current = Minecraft.getInstance().getConnection(); if (current == null) return;
        bind(current); long now = System.currentTimeMillis(); if (now - lastNotices < GroupClientPolicy.REFRESH_INTERVAL_MS) return;
        lastNotices = now; var request = UUID.randomUUID(); GroupClientCache.requestNotices(request);
        send(new GroupCommand.Notices(request));
    }
}
