package com.example.mcphoneaddon;

import com.november.mcphone.api.MCphoneApi;
import com.november.mcphone.api.chat.ContactRelation;
import com.november.mcphone.api.chat.PhoneChat;
import com.november.mcphone.api.chat.PhoneContact;
import com.november.mcphone.api.chat.PhoneConversation;
import com.november.mcphone.api.chat.SendResult;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * api.chat 的用法示例，只要求编得过：谁改了 api.chat 的签名，这里当场编不过。
 *
 * 放在仓库根的 docs/ 而不是 neoforge 层的 AddonApiExamples 里：api.chat 与加载器无关，
 * 放进层里 1.20.1-forge 就不编它，签名在那个目标上改了也没人知道。
 */
public final class ChatApiExamples {

    private ChatApiExamples() {}

    /** 转账成功后以付款人的名义给收款人发一条私信 */
    static void notifyTransfer(ServerPlayer payer, UUID payee, long amount) {
        if (MCphoneApi.VERSION < 3) return;   // 真实附属里这一句要和下面的调用分在两个类里，见 MCphoneApi.VERSION

        SendResult result = PhoneChat.sendText(payer, payee, "向你转账 " + amount);
        switch (result) {
            case DELIVERED, STORED_OFFLINE -> { }
            case NOT_FRIENDS -> payer.sendSystemMessage(Component.literal("对方还不是你的手机好友"));
            case NO_PHONE -> payer.sendSystemMessage(Component.literal("身上没有手机，转账通知没发出去"));
            default -> payer.sendSystemMessage(Component.literal("转账通知没发出去: " + result));
        }
    }

    /** 从后台线程回到主线程再调 */
    static void fromAsync(MinecraftServer server, UUID payer, UUID payee) {
        server.execute(() -> {
            ServerPlayer online = server.getPlayerList().getPlayer(payer);
            if (online != null && PhoneChat.sendText(online, payee, "收款请求").isSent()) {
                online.sendSystemMessage(Component.literal("已发出"));
            }
        });
    }

    static void lookups(MinecraftServer server, ServerPlayer self, String typedName) {
        Optional<PhoneContact> byName = PhoneChat.findPlayer(server, typedName);
        Optional<PhoneContact> byId = PhoneChat.findPlayer(server, self.getUUID());
        List<PhoneContact> friends = PhoneChat.contacts(server, self.getUUID());

        for (PhoneContact c : friends) {
            ContactRelation relation = PhoneChat.relation(server, self.getUUID(), c.id());
            Optional<PhoneConversation> conv = PhoneChat.conversation(self, c.id());
            int unread = conv.map(PhoneConversation::unread).orElse(0);
            long last = conv.flatMap(v -> v.lastMessageTime().isPresent()
                    ? Optional.of(v.lastMessageTime().getAsLong()) : Optional.empty()).orElse(0L);
            String line = c.name() + (c.online() ? "" : "（离线）") + " " + relation + " " + unread + " " + last
                    + " " + byName.isPresent() + byId.isPresent() + PhoneChat.maxTextLength();
        }
    }
}
