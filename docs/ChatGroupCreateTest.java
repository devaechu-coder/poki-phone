package com.november.mcphone.feature.chat.client;

import com.november.mcphone.feature.chat.group.*;
import com.november.mcphone.feature.chat.group.net.*;
import com.google.gson.JsonParser;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import java.util.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** 实际选人、原版输入、按钮提交、业务建群和确认；以回调隔离连接和游戏语言管理器。 */
public final class ChatGroupCreateTest {
    private static int checks;
    private static final UUID ALICE = new UUID(0, 2), ZHANG = new UUID(0, 1), OWNER = new UUID(0, 9);
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static Object field(Object o, String name) throws Exception { var f = o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o); }
    private static void field(Object o, String name, Object value) throws Exception { var f = o.getClass().getDeclaredField(name); f.setAccessible(true); f.set(o, value); }
    private static boolean ready(ChatGroupCreate page) throws Exception {
        var method = ChatGroupCreate.class.getDeclaredMethod("ready"); method.setAccessible(true); return (boolean) method.invoke(page);
    }
    private static void poll(ChatGroupCreate page) throws Exception {
        var method = ChatGroupCreate.class.getDeclaredMethod("pollResult"); method.setAccessible(true); method.invoke(page);
    }
    private static final class TestFont extends com.november.mcphone.test.TestFontBase {
        private final StringSplitter splitter = new StringSplitter((cp, style) -> 6);
        @Override public StringSplitter getSplitter() { return splitter; }
        @Override public int width(String text) { return (int) Math.ceil(splitter.stringWidth(text)); }
        @Override public int width(FormattedText text) { return (int) Math.ceil(splitter.stringWidth(text)); }
        @Override public int width(FormattedCharSequence text) { return (int) Math.ceil(splitter.stringWidth(text)); }
        @Override public String plainSubstrByWidth(String text, int width) { return splitter.plainHeadByWidth(text, width, Style.EMPTY); }
        @Override public String plainSubstrByWidth(String text, int width, boolean tail) {
            return tail ? splitter.plainTailByWidth(text, width, Style.EMPTY) : plainSubstrByWidth(text, width);
        }
    }
    private static ChatGroupCreate page(List<GroupCommand.Create> sent) throws Exception {
        String defaultName;
        try (var resource = ChatGroupCreateTest.class.getClassLoader().getResourceAsStream("assets/mcphone/lang/zh_cn.json")) {
            if (resource == null) throw new AssertionError("测试类路径缺少模组语言资源");
            defaultName = JsonParser.parseReader(new InputStreamReader(resource, StandardCharsets.UTF_8))
                    .getAsJsonObject().get("mcphone.chat.group.default_name").getAsString();
        }
        var page = new ChatGroupCreate(sent::add, () -> defaultName);
        for (var input : List.of("name", "search")) {
            var control = (ChatContactSearch) field(page, input);
            var box = com.november.mcphone.test.TestEditBoxFactory.create(new TestFont(), 0, 0, 100, 9, Component.empty());
            box.setBordered(false); box.setMaxLength(input.equals("name") ? GroupLimits.NAME_LENGTH : 64);
            field(control, "box", box);
            field(control, "bounds", new ChatLayout.Rect(10, input.equals("name") ? 0 : 14, 100, 12));
        }
        var sections = new ChatContactDirectory().sections(List.of(
                new ChatContactDirectory.Contact(ALICE, "Alice", false),
                new ChatContactDirectory.Contact(ZHANG, "张三", true)), "");
        var pane = (ChatContactPane) field(page, "pane");
        field(pane, "bounds", new ChatLayout.Rect(10, 30, 100, 80));
        ((ChatContactScroll) field(pane, "scroll")).update(sections, 80);
        field(page, "confirm", new ChatLayout.Rect(10, 120, 100, 14));
        return page;
    }
    public static void main(String[] args) throws Exception {
        GroupClientCache.clear(); var sent = new ArrayList<GroupCommand.Create>(); var page = page(sent);
        check(!ready(page), "空名单不能创建");
        check(page.mouseClicked(50, 126, 0) && sent.isEmpty()
                && field(page, "status").equals("mcphone.chat.group.select_members"), "空名单点击给出选人提示，不静默也不发包");
        page.mouseClicked(12, 31, 0); check(!ready(page), "分组标题不算选中了好友");
        page.mouseClicked(12, 40, 0);
        check(ready(page) && ((ChatContactSearch) field(page, "name")).query().isEmpty(), "复现反馈：选好友且没有群名也可创建");
        page.mouseClicked(50, 126, 1); page.mouseClicked(9, 126, 0);
        check(sent.isEmpty(), "右键和按钮外部点击都不提交");
        check(page.mouseClicked(50, 126, 0) && sent.size() == 1, "一次左键发出一个真实建群命令");
        var command = sent.get(0);
        String defaultName = command.name();
        check(command.members().equals(List.of(ALICE)), "请求含已选择的离线好友");
        check(command.name().equals("新群聊"), "未填群名使用真实语言文件提供的默认名称");
        check(command.request().equals(field(page, "request")) && !ready(page), "提交票据匹配并禁用重复创建");
        page.mouseClicked(50, 126, 0); page.mouseClicked(12, 68, 0);
        check(sent.size() == 1 && ((Set<?>) field(page, "selected")).equals(Set.of(ALICE)), "等待确认时重复点击和选人不会重发或改变请求");
        var book = new GroupBook(); var id = new UUID(8, 1);
        var room = book.create(id, new GroupMember(OWNER, "Owner"), command.name(),
                command.members().stream().map(member -> new GroupMember(member, "Alice")).toList(), ALICE::equals);
        check(room.members().size() == 1 && book.groups(ALICE).isEmpty(), "建群命令只建立创建者，选中的好友等待本人同意");
        var invitation = book.notices(ALICE, System.currentTimeMillis()).get(0);
        book.respond(id, invitation.id(), new GroupMember(ALICE, "Alice"), true, (a, b) -> true, System.currentTimeMillis());
        check(book.room(id, OWNER).members().size() == 2 && book.groups(ALICE).size() == 1, "好友同意建群邀请后才成为实际成员");
        GroupClientCache.accept(new GroupReply.Result(command.request(), id, GroupStatus.OK)); poll(page);
        check(page.consumeCreated().equals(id) && page.consumeCreated() == null, "确认后只交出一次新群导航");
        check(GroupClientCache.consume(command.request()) == null, "确认票据只消费一次");
        page.close();
        check(!ready(page) && !page.mouseScrolled(12, 40, -1), "离开建群页清除选择和命中区域");

        sent.clear(); page = page(sent);
        var name = (ChatContactSearch) field(page, "name");
        var box = (EditBox) field(name, "box");
        box.setFocused(true);
        for (char c : "  周末 Mine  ".toCharArray()) check(page.charTyped(c, 0), "中英文群名进入原版输入控件");
        page.mouseClicked(12, 40, 0); page.mouseClicked(12, 68, 0); page.mouseClicked(50, 126, 0);
        command = sent.get(0);
        check(command.name().equals("周末 Mine") && command.members().equals(List.of(ALICE, ZHANG)), "填写的群名清洗后保留，多选按顺序完整提交");
        check(!name.focused(), "点击提交正确释放群名焦点");
        GroupClientCache.accept(new GroupReply.Result(command.request(), null, GroupStatus.NOT_FRIEND)); poll(page);
        check(page.consumeCreated() == null && ready(page) && field(page, "status").equals(GroupStatus.NOT_FRIEND.key()), "服务端拒绝显示原因且允许重试");
        check(name.query().equals("  周末 Mine  ") && ((Set<?>) field(page, "selected")).size() == 2, "失败保留群名和选人");
        page.mouseClicked(50, 126, 0);
        check(sent.size() == 2 && !sent.get(1).request().equals(command.request()), "重试使用新票据");
        command = sent.get(1); field(page, "requestedAt", System.currentTimeMillis() - 16000); poll(page);
        check(ready(page) && field(page, "status").equals("mcphone.chat.group.timeout"), "超时恢复按钮并显示提示");
        GroupClientCache.accept(new GroupReply.Result(command.request(), id, GroupStatus.OK)); poll(page);
        check(page.consumeCreated() == null, "超时后迟到的确认不跳转");
        box.setValue(" \t "); page.mouseClicked(50, 126, 0);
        check(sent.size() == 3 && sent.get(2).name().equals(defaultName), "仅空白群名回退默认名称，而不是无效请求");
        command = sent.get(2); page.close();
        GroupClientCache.accept(new GroupReply.Result(command.request(), id, GroupStatus.OK)); poll(page);
        check(page.consumeCreated() == null && !ready(page), "关闭后迟到确认不恢复旧建群页面");
        GroupClientCache.clear();
        System.out.println("全部通过：" + checks + " 条断言");
    }
}
