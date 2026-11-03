package com.november.mcphone.feature.chat.client;

import com.november.mcphone.core.net.MCphoneNetwork;
import com.november.mcphone.feature.chat.ChatMessage;
import com.november.mcphone.feature.chat.ChatDeletionResult;
import com.november.mcphone.feature.chat.net.ChatClientCache;
import com.november.mcphone.feature.chat.net.ConversationSummary;
import com.november.mcphone.feature.chat.net.RequestMessagesPacket;
import com.november.mcphone.feature.chat.net.RequestConversationsPacket;
import com.november.mcphone.feature.chat.net.SendChatMessagePacket;
import com.november.mcphone.feature.chat.net.MarkReadPacket;
import com.november.mcphone.feature.chat.net.DeleteChatMessagePacket;
import com.november.mcphone.feature.chat.group.net.*;
import java.util.List;
import java.util.UUID;

/** 会话的数据与命令适配器。绘制、选字、菜单和输入不依赖私聊或群聊协议。 */
interface ChatConversationChannel {
    UUID target();
    boolean group();
    void open();
    default boolean activate() { return false; }
    void close();
    List<ChatMessage> messages();
    String title();
    default String titleSuffix() { return ""; }
    default String consumeError() { return null; }
    String senderName(ChatMessage message);
    default void send(String text) { send(text,java.util.Optional.empty(),java.util.Optional.empty()); }
    void send(String text, java.util.Optional<UUID> reply, java.util.Optional<UUID> ticket);
    enum ReplyStatus { ACCEPTED, REJECTED }
    default ReplyStatus replyStatus(UUID ticket) { return null; }
    default void cancelReply(UUID ticket) {}
    void refresh();
    void read();
    void delete(UUID message, UUID request);
    void cancelDelete(UUID request);
    Receipt receipt();
    record Receipt(UUID request, ChatDeletionResult result) {}
    static ChatConversationChannel direct(UUID peer) { return new Direct(peer); }
    static ChatConversationChannel room(UUID group) { return new Room(group); }

    record Direct(UUID target) implements ChatConversationChannel {
        public boolean group() { return false; }
        public void open() { ChatClientCache.openConversation(target); MCphoneNetwork.sendToServer(new RequestMessagesPacket(target)); }
        public void close() { ChatClientCache.closeConversation(); }
        public List<ChatMessage> messages() { return ChatClientCache.getMessages(); }
        public String title() { return ChatClientCache.getConversations().stream().filter(c -> c.id().equals(target))
                .map(ConversationSummary::name).findFirst().orElse(target.toString().substring(0, 8)); }
        public String senderName(ChatMessage message) { return ""; }
        public void send(String text, java.util.Optional<UUID> reply, java.util.Optional<UUID> ticket) {
            MCphoneNetwork.sendToServer(new SendChatMessagePacket(target,text,reply,ticket));
        }
        public void refresh() { MCphoneNetwork.sendToServer(new RequestConversationsPacket()); }
        public void read() { MCphoneNetwork.sendToServer(new MarkReadPacket(target)); }
        public void delete(UUID message, UUID request) {
            ChatClientCache.beginDeletion(target, message, request); MCphoneNetwork.sendToServer(new DeleteChatMessagePacket(target, message, request));
        }
        public void cancelDelete(UUID request) { ChatClientCache.cancelDeletion(request); }
        public Receipt receipt() { var r = ChatClientCache.consumeDeleteResult(); return r == null ? null : new Receipt(r.request(), r.result()); }
    }
    record Room(UUID target) implements ChatConversationChannel {
        public boolean group() { return true; }
        public void open() { var ticket = GroupClientCache.open(target); GroupTransport.send(new GroupCommand.History(ticket, target)); }
        public boolean activate() {
            var ticket = GroupClientCache.activate(target);
            if (ticket == null) return false;
            GroupTransport.send(new GroupCommand.History(ticket, target)); return true;
        }
        public void close() { GroupClientCache.close(); }
        public List<ChatMessage> messages() { return GroupClientCache.messages(); }
        public String title() {
            var s = GroupClientCache.summary(target); var info = GroupClientCache.info();
            return s != null ? s.name() : info != null ? info.name() : target.toString().substring(0, 8);
        }
        public String titleSuffix() {
            var s = GroupClientCache.summary(target); var info = GroupClientCache.info();
            return s != null ? " (" + s.memberCount() + ")" : info != null ? " (" + info.members().size() + ")" : "";
        }
        public String senderName(ChatMessage message) { return GroupClientCache.senderName(message); }
        public void send(String text, java.util.Optional<UUID> reply, java.util.Optional<UUID> ticket) {
            ticket.ifPresent(GroupClientCache::track);
            GroupTransport.send(new GroupCommand.SendText(ticket.orElseGet(UUID::randomUUID),target,text,reply));
        }
        public ReplyStatus replyStatus(UUID ticket) {
            var result = GroupClientCache.consume(ticket);
            if (result == null || !target.equals(result.group())) return null;
            return result.status() == com.november.mcphone.feature.chat.group.GroupStatus.OK ? ReplyStatus.ACCEPTED : ReplyStatus.REJECTED;
        }
        public void cancelReply(UUID ticket) { GroupClientCache.cancel(ticket); }
        public void refresh() {
            GroupTransport.directory();
            // 快速退出再进入可能撞到历史限流，保留票据并周期性重试，不永久停在空列表。
            var request = GroupClientCache.pendingHistory();
            if (request != null && !GroupClientCache.removed()) GroupTransport.send(new GroupCommand.History(request, target));
        }
        public String consumeError() { var error = GroupClientCache.consumeHistoryError(); return error == null ? null : error.key(); }
        public void read() { if (GroupClientCache.sequence() > 0) GroupTransport.send(new GroupCommand.MarkRead(UUID.randomUUID(), target, GroupClientCache.sequence())); }
        public void delete(UUID message, UUID request) {
            GroupClientCache.beginDeletion(target, message, request); GroupTransport.send(new GroupCommand.Delete(request, target, message));
        }
        public void cancelDelete(UUID request) { GroupClientCache.cancelDeletion(request); }
        public Receipt receipt() {
            var request = GroupClientCache.deletionRequest(); var result = GroupClientCache.consumeDeleteResult();
            return result == null ? null : new Receipt(request, result);
        }
    }
}
