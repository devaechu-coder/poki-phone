package com.november.mcphone.feature.chat.client;

import com.november.mcphone.core.PhoneItem;
import com.november.mcphone.core.client.PhoneScreen;
import com.november.mcphone.core.client.PhoneToast;
import com.november.mcphone.feature.chat.group.net.GroupReply;
import net.minecraft.client.Minecraft;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** 群回声先确认上传，再按会话可见性提醒；网络缓存没有客户端类型依赖。 */
public final class ChatGroupEvents {
    private ChatGroupEvents() {}
    public static void install() {
        com.november.mcphone.feature.chat.group.net.GroupClientCache.setMessageListener(ChatGroupEvents::onMessage);
        com.november.mcphone.feature.chat.group.net.GroupClientCache.setResultListener(ChatGroupEvents::onResult);
    }
    public static void onMessage(GroupReply.Message reply) {
        ChatImageSender.onGroupMessage(reply);
        GroupTransport.acknowledge(reply.request());
        var mc = Minecraft.getInstance(); var entry = reply.summary().last().orElseThrow();
        if (mc.player == null || entry.message().sender().equals(mc.player.getUUID()) || !PhoneItem.isCarriedBy(mc.player)
                || mc.screen instanceof PhoneScreen phone && phone.isViewingGroup(reply.summary().id())) return;
        // 通知 token 显式带群命名空间，不与同 UUID 的私聊通知合并。
        UUID token = UUID.nameUUIDFromBytes(("mcphone:group:" + reply.summary().id()).getBytes(StandardCharsets.UTF_8));
        String preview = entry.senderName() + ": " + PhoneToast.preview(entry.message());
        var old = mc.getToasts().getToast(PhoneToast.class, token);
        if (old != null) old.addMessage(preview); else mc.getToasts().addToast(new PhoneToast(token, entry.message().sender(), reply.summary().name(), preview));
    }
    public static void onResult(GroupReply.Result reply) { ChatImageSender.onGroupResult(reply); GroupTransport.result(reply); }
}
