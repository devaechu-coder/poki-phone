package com.november.mcphone.feature.chat.client;

import com.november.mcphone.feature.chat.ChatMessage;
import com.november.mcphone.feature.chat.ChatQuote;
import com.november.mcphone.feature.chat.TextBody;
import com.november.mcphone.feature.chat.client.messageaction.ChatMessageActions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** 实际会话、输入框与滚动状态：资料往返、在飞确认和最终关闭，不创建窗口或 GL 贴图。 */
public final class ChatConversationProfileTest {
    private static int checks;
    private static void check(boolean condition, String why) {
        checks++;
        if (!condition) throw new AssertionError(why);
    }
    private static Object field(Object owner, String name) throws Exception {
        var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static void field(Object owner, String name, Object value) throws Exception {
        var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner, value);
    }
    private static final class TestFont extends com.november.mcphone.test.TestFontBase {
        private final StringSplitter splitter = new StringSplitter((cp, style) -> 6);
        @Override public StringSplitter getSplitter() { return splitter; }
        @Override public int width(String text) { return (int)Math.ceil(splitter.stringWidth(text)); }
        @Override public int width(FormattedText text) { return (int)Math.ceil(splitter.stringWidth(text)); }
        @Override public int width(FormattedCharSequence text) { return (int)Math.ceil(splitter.stringWidth(text)); }
        @Override public String plainSubstrByWidth(String text, int width) { return splitter.plainHeadByWidth(text, width, Style.EMPTY); }
        @Override public String plainSubstrByWidth(String text, int width, boolean tail) {
            return tail ? splitter.plainTailByWidth(text, width, Style.EMPTY) : plainSubstrByWidth(text, width);
        }
    }
    private static final class Channel implements ChatConversationChannel {
        private final UUID target;
        private final boolean group;
        private final List<ChatMessage> messages = new ArrayList<>();
        int opens, closes, reads, refreshes, sends, cancelledReplies;
        Channel(UUID target, boolean group) { this.target = target; this.group = group; }
        public UUID target() { return target; }
        public boolean group() { return group; }
        public void open() { opens++; }
        public void close() { closes++; }
        public List<ChatMessage> messages() { return messages; }
        public String title() { return "已知玩家名"; }
        public String senderName(ChatMessage message) { return "Sender"; }
        public void send(String text, Optional<UUID> reply, Optional<UUID> ticket) { sends++; }
        public void refresh() { refreshes++; }
        public void read() { reads++; }
        public void delete(UUID message, UUID request) {}
        public void cancelDelete(UUID request) {}
        public Receipt receipt() { return null; }
        public void cancelReply(UUID ticket) { cancelledReplies++; }
    }
    private static ChatConversation open(Channel channel) throws Exception {
        var conversation = new ChatConversation();
        var open = ChatConversation.class.getDeclaredMethod("open", ChatConversationChannel.class);
        open.setAccessible(true); open.invoke(conversation, channel);
        return conversation;
    }
    private static EditBox input(ChatComposer composer) throws Exception {
        var box = com.november.mcphone.test.TestEditBoxFactory.create(new TestFont(), 0, 0, 100, 9, Component.empty());
        var bind = ChatComposer.class.getDeclaredMethod("bindInput", EditBox.class);
        bind.setAccessible(true); bind.invoke(composer, box);
        return box;
    }
    public static void main(String[] args) throws Exception {
        UUID self = new UUID(0, 1), peer = new UUID(0, 2), other = new UUID(0, 3);
        var channel = new Channel(peer, false);
        var conversation = open(channel);
        var composer = (ChatComposer)field(conversation, "composer");
        var box = input(composer);
        box.setValue("未发送的中文草稿"); box.setCursorPosition(2); box.setHighlightPos(5);
        var draft = composer.draft();
        var original = ChatMessage.text(peer, "引用原文", 100);
        var quote = ChatQuote.from(original, "Sender");
        var reply = (ChatReplyDraft)field(conversation, "reply"); reply.select(quote);
        var pane = (ChatMessagePane)field(conversation, "messages");
        var scroll = (ChatScrollState)field(pane, "scroll");
        scroll.contentChanged(500); scroll.viewport(100); scroll.scroll(3);
        int offset = scroll.offset();

        field(conversation, "moreHovered", true);
        check(conversation.mouseClicked(0, 0, 0) && conversation.consumeDetailsRequest(), "私聊三个点产生详情意图");
        check(!conversation.consumeDetailsRequest(), "详情请求只消费一次");
        check(conversation.title().equals("已知玩家名"), "向资料页提供已知会话名字作加载占位");
        check(!conversation.suspendForProfile(other) && !conversation.profileSuspended(), "其他玩家资料不能误保留当前会话");

        var actions = (ChatMessageActions)field(conversation, "actions");
        actions.open(peer, original.id(), "引用原文", "", 10, 10);
        field(composer, "attachMenuOpen", true);
        check(conversation.suspendForProfile(peer) && conversation.profileSuspended(), "私聊可暂离到对应玩家资料");
        check(channel.opens == 1 && channel.closes == 0, "暂离不关闭或重开会话数据通道");
        check(composer.draft().equals(draft) && field(composer, "box") == box, "保留同一个输入框及草稿版本");
        check(box.getCursorPosition() == 2 && !box.getHighlighted().isEmpty(), "保留输入光标和选区");
        check(reply.quote() == quote && scroll.offset() == offset, "保留引用和历史消息滚动位置");
        check(!actions.isOpen() && !(boolean)field(composer, "attachMenuOpen") && !box.isFocused(), "收起临时菜单并停止隐藏输入");
        check(!conversation.charTyped('X', 0) && composer.draft().equals(draft), "资料页期间字符不写入隐藏草稿");
        check(!conversation.resumeFromProfile(other) && conversation.profileSuspended(), "不同会话不能错误恢复草稿");
        channel.messages.add(ChatMessage.text(peer, "查看资料期间到达的新消息", 200));
        conversation.tick(self);
        check(channel.reads == 0 && channel.refreshes == 0 && channel.sends == 0, "资料页维护不发历史、已读或消息请求");
        check(scroll.offset() == offset && composer.draft().equals(draft), "新消息不会重置隐藏页面的草稿与滚动");
        check(conversation.resumeFromProfile(peer) && !conversation.profileSuspended(), "返回恢复同一私聊");
        check(box.isFocused() && composer.draft().equals(draft) && channel.opens == 1, "返回可继续输入且不重拉历史");
        check(!conversation.resumeFromProfile(peer), "没有暂离状态时不误判为恢复");

        // 查看资料时服务端确认仍能处理，既不留下待确认状态，也不清除后来改写的草稿。
        var pending = new ChatReplySubmission(original.id(), composer.draft(), self, peer, false, System.nanoTime()/1_000_000L);
        field(conversation, "submission", pending);
        conversation.suspendForProfile(peer);
        channel.messages.add(new ChatMessage(pending.message(), self, 300, new TextBody(box.getValue())));
        conversation.tick(self);
        check(field(conversation, "submission") == null && box.getValue().isEmpty() && reply.id().isEmpty(), "资料页期间确认原提交并清理对应草稿");
        check(channel.cancelledReplies == 1 && channel.sends == 0, "只完成旧票据，不重复发送");
        conversation.resumeFromProfile(peer); box.setValue("已发送草稿"); reply.select(quote);
        pending = new ChatReplySubmission(original.id(), composer.draft(), self, peer, false, System.nanoTime()/1_000_000L);
        field(conversation, "submission", pending);
        conversation.suspendForProfile(peer); box.setValue("后来编辑的新草稿");
        channel.messages.add(new ChatMessage(pending.message(), self, 301, new TextBody("已发送草稿")));
        conversation.tick(self);
        check(box.getValue().equals("后来编辑的新草稿") && reply.quote() == quote, "旧确认不能清除已改写的草稿及引用");

        pending = new ChatReplySubmission(original.id(), composer.draft(), self, peer, false, System.nanoTime()/1_000_000L);
        field(conversation, "submission", pending);
        int cancelled = channel.cancelledReplies;
        conversation.close();
        check(channel.closes == 1 && channel.cancelledReplies == cancelled + 1, "资料页直接关机仍关闭数据通道和未完成票据");
        check(conversation.peer() == null && !conversation.profileSuspended(), "关闭后不残留暂离会话");
        check(box.getValue().isEmpty() && reply.id().isEmpty() && scroll.offset() == 0, "真正关闭统一清理正文、引用和滚动");
        conversation.close(); check(channel.closes == 1, "重复关闭安全");
        check(!conversation.suspendForProfile(peer) && !conversation.resumeFromProfile(peer), "关闭后不能恢复旧会话");

        var groupChannel = new Channel(other, true); var group = open(groupChannel);
        field(group, "moreHovered", true);
        check(group.mouseClicked(0, 0, 0) && group.consumeDetailsRequest() && group.isGroup(), "群聊三个点仍交出群详情意图");
        check(!group.suspendForProfile(other) && !group.profileSuspended(), "群聊不套用私聊资料暂离路径");
        group.close(); check(groupChannel.closes == 1, "群聊沿用正常关闭流程");
        System.out.println("ChatConversationProfileTest: " + checks + " checks passed");
    }
}
