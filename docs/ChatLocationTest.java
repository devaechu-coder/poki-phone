package com.november.mcphone.feature.chat.client;

import com.november.mcphone.feature.chat.ChatMessage;
import com.november.mcphone.feature.chat.ChatQuote;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** 位置复用实际会话发送入口，不能把输入框中的引用草稿当成已提交文字。 */
public final class ChatLocationTest {
    private static int checks;
    private static void check(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }
    private static Object field(Object owner, String name) throws Exception {
        var f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner);
    }
    private static void field(Object owner, String name, Object value) throws Exception {
        var f = owner.getClass().getDeclaredField(name); f.setAccessible(true); f.set(owner, value);
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
    private record Sent(String text, Optional<UUID> reply, Optional<UUID> ticket) {}
    private static final class Channel implements ChatConversationChannel {
        private final UUID target = UUID.randomUUID();
        private final boolean group;
        private final List<ChatMessage> messages = new ArrayList<>();
        private final List<Sent> sent = new ArrayList<>();
        Channel(boolean group) { this.group = group; }
        public UUID target() { return target; }
        public boolean group() { return group; }
        public void open() {}
        public void close() {}
        public List<ChatMessage> messages() { return messages; }
        public String title() { return "Location test"; }
        public String senderName(ChatMessage message) { return "Sender"; }
        public void send(String text, Optional<UUID> reply, Optional<UUID> ticket) { sent.add(new Sent(text, reply, ticket)); }
        public void refresh() {}
        public void read() {}
        public void delete(UUID message, UUID request) {}
        public void cancelDelete(UUID request) {}
        public Receipt receipt() { return null; }
    }
    public static void main(String[] args) throws Exception {
        UUID self = UUID.randomUUID();
        for (boolean group : new boolean[]{false, true}) {
            var channel = new Channel(group);
            var conversation = new ChatConversation();
            var open = ChatConversation.class.getDeclaredMethod("open", ChatConversationChannel.class);
            open.setAccessible(true); open.invoke(conversation, channel);
            var composer = (ChatComposer)field(conversation, "composer");
            var box = com.november.mcphone.test.TestEditBoxFactory.create(new TestFont(), 0, 0, 100, 9, Component.empty());
            var bind = ChatComposer.class.getDeclaredMethod("bindInput", EditBox.class);
            bind.setAccessible(true); bind.invoke(composer, box);
            box.setValue("保留未发送的文字"); box.setCursorPosition(2); box.setHighlightPos(4);
            var draft = composer.draft();
            var original = ChatMessage.text(channel.target(), "被引用的消息", 100);
            var quote = ChatQuote.from(original, "Sender");
            var reply = (ChatReplyDraft)field(conversation, "reply"); reply.select(quote);
            var pending = new ChatReplySubmission(original.id(), draft, self, channel.target(), group, System.nanoTime()/1_000_000L);
            field(conversation, "submission", pending);
            var pane = (ChatMessagePane)field(conversation, "messages");
            var scroll = (ChatScrollState)field(pane, "scroll");
            scroll.contentChanged(500); scroll.viewport(100); scroll.scroll(3);

            field(composer, "attachMenuOpen", true); field(composer, "attachHovered", ChatAttachment.LOCATION);
            check(composer.mouseClicked(0), "位置菜单消费点击");
            check(conversation.consumeAttachRequest() == ChatAttachment.LOCATION && conversation.consumeAttachRequest() == null,
                    "位置请求只消费一次");
            check(!(boolean)field(composer, "attachMenuOpen"), "选择后关闭附件菜单");
            conversation.sendLocation(new BlockPos(123, 64, 456));
            check(channel.sent.size() == 1 && channel.sent.get(0).text().equals("X: 123, Y: 64, Z: 456"),
                    "私聊与群聊都发普通坐标文字");
            check(channel.sent.get(0).reply().isEmpty() && channel.sent.get(0).ticket().isEmpty(),
                    "坐标不冒用草稿的引用或确认票据");
            check(composer.draft().equals(draft) && box.getCursorPosition() == 2 && !box.getHighlighted().isEmpty(),
                    "保留正文、编辑版本、光标和选区");
            check(reply.quote() == quote && field(conversation, "submission") == pending,
                    "不改引用预览或正在等待的提交");
            check(channel.messages.isEmpty() && scroll.offset() == 0, "发送后贴底但等待服务端回声才显示消息");

            conversation.sendLocation(BlockPos.containing(-0.1, -64.3, 123.9));
            check(channel.sent.size() == 2 && channel.sent.get(1).text().equals("X: -1, Y: -65, Z: 123"),
                    "再次点击使用新坐标，负小数沿用原版方块坐标规则");
            channel.messages.add(ChatMessage.text(self, channel.sent.get(1).text(), 200));
            conversation.tick(self);
            check(composer.draft().equals(draft) && reply.quote() == quote && field(conversation, "submission") == pending,
                    "位置回声不能确认或清掉原引用草稿");
            if (!group) {
                conversation.suspendForProfile(channel.target());
                conversation.sendLocation(new BlockPos(0, 0, 0));
                check(channel.sent.size() == 2, "资料页暂离时不从隐藏会话发送位置");
            }
            conversation.close(); conversation.sendLocation(new BlockPos(0, 0, 0));
            check(channel.sent.size() == 2, "关闭后不能发送到旧会话");
        }
        System.out.println("ChatLocationTest: " + checks + " checks passed");
    }
}
