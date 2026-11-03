package com.november.mcphone.feature.chat;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.Optional;

/**
 * 把一条已落库的消息推给在线的双方。
 *
 * 发包那一步由各平台的 ChatNetworking.register 装进来：NewMessagePacket 在 1.20.1-forge 是平台内部的类，
 * 共享代码直接 new 它会把它改判成接缝，所以别在这里直接引用它。
 */
public final class ChatDelivery {

    /** 给一个在线玩家发一条新消息；peer 是站在他角度的对端 */
    @FunctionalInterface
    public interface Push {
        void push(ServerPlayer player, UUID peer, ChatMessage message);
    }

    private static volatile Push push;

    private ChatDelivery() {}

    public static void install(Push p) {
        push = p;
    }

    /** 没装就抛。发消息的一方要在落库之前问：deliver 里也会抛，但那时消息已经存下了 */
    public static void requireInstalled() {
        if (push == null) {
            throw new IllegalStateException("[MCphone] 聊天推送没装上：这个平台的 ChatNetworking.register 没跑到");
        }
    }

    /**
     * 回声给发件人、推给在线的收件人，返回收件人此刻是否在线。
     * 发件人可能在异步写盘期间下线了，那就只推收件人——他下次上线拉历史照样看得见。
     */
    public static boolean deliver(ServerPlayer sender, UUID targetId, ChatMessage message) {
        requireInstalled();
        Push p = push;

        if (!sender.hasDisconnected()) p.push(sender, targetId, message);

        ServerPlayer receiver = sender.level().getServer().getPlayerList().getPlayer(targetId);
        if (receiver == null) return false;
        p.push(receiver, sender.getUUID(), message);
        return true;
    }

    /** 已存下的引用重试只补发给提交者，避免给对方重复通知。 */
    public static void echo(ServerPlayer sender, UUID targetId, ChatMessage message) {
        requireInstalled();
        if (!sender.hasDisconnected()) push.push(sender,targetId,message);
    }

    /** 四个平台共用的文本提交入口；重试不落第二条记录，也不重复打扰收件人。 */
    public static void sendText(ServerPlayer sender, UUID targetId, String text, Optional<UUID> reply, Optional<UUID> ticket) {
        requireInstalled();
        boolean retry = ticket.map(value -> ChatReplyIdentity.messageId(sender.getUUID(),targetId,false,value))
                .map(id -> ChatData.get(sender.level().getServer()).getMessages(sender.getUUID(),targetId).stream()
                        .anyMatch(m -> m.id().equals(id))).orElse(false);
        ChatMessage message = ChatService.sendMessage(sender,targetId,text,reply,ticket);
        if (message == null) return;
        if (retry) echo(sender,targetId,message);
        else deliver(sender,targetId,message);
    }
}
