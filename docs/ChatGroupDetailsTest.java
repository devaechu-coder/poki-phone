package com.november.mcphone.feature.chat.client;

import com.november.mcphone.feature.chat.group.*;
import com.november.mcphone.feature.chat.group.net.*;
import java.util.*;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/** 生产详情导航、邀请选人、改名草稿、票据确认和网格边界，不替换实际点击处理。 */
public final class ChatGroupDetailsTest {
    private static int checks;
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3), G = new UUID(1, 1);
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    private static Object field(Object o, String name) throws Exception { var f = o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o); }
    private static void field(Object o, String name, Object value) throws Exception { var f = o.getClass().getDeclaredField(name); f.setAccessible(true); f.set(o, value); }
    private static Object call(Object o, String name, Class<?>[] types, Object... args) throws Exception { var m = o.getClass().getDeclaredMethod(name, types); m.setAccessible(true); return m.invoke(o, args); }
    private static final class TestFont extends com.november.mcphone.test.TestFontBase {
        private final StringSplitter splitter = new StringSplitter((cp, style) -> 6);
        @Override public StringSplitter getSplitter() { return splitter; }
        @Override public int width(String text) { return (int) Math.ceil(splitter.stringWidth(text)); }
        @Override public String plainSubstrByWidth(String text, int width) { return splitter.plainHeadByWidth(text, width, Style.EMPTY); }
    }
    private static EditBox input(ChatContactSearch control) throws Exception {
        var box = com.november.mcphone.test.TestEditBoxFactory.create(new TestFont(), 0, 0, 200, 9, Component.empty());
        box.setMaxLength(32); box.setValue(control.query()); field(control, "box", box); return box;
    }
    private static GroupInfo info() { return new GroupInfo(G, "原群名", A, List.of(new GroupMember(A, "A"), new GroupMember(B, "B"))); }
    private static ChatGroupDetails page(List<GroupCommand> sent, UUID player) {
        GroupClientCache.clear(); var page = new ChatGroupDetails(sent::add, () -> player); page.open(G);
        var request = sent.get(sent.size() - 1).request(); GroupClientCache.accept(new GroupReply.Details(request, 1, info())); return page;
    }
    private static void poll(ChatGroupDetails page) throws Exception { call(page, "pollResult", new Class<?>[]{}); }
    public static void main(String[] args) throws Exception {
        for (int width : new int[]{48, 100, 116, 184}) for (int height : new int[]{28, 56, 168}) for (int members : new int[]{1, 2, 9, 10, 128}) {
            var area = new ChatLayout.Rect(10, 20, width, height);
            var cells = ChatGroupMemberGrid.layout(area, members, false, 0);
            check(cells.stream().filter(ChatGroupMemberGrid.Cell::invite).count() == 1, "所有尺寸的预览都有一个邀请入口");
            for (var cell : cells) {
                var b = cell.bounds(); check(b.x() >= area.x() && b.y() >= area.y() && b.x() + b.width() <= area.x() + width
                        && b.y() + b.height() <= area.y() + height, "头像及名字的格子全部落在视口内");
            }
            var end = ChatGroupMemberGrid.layout(area, members, true, 999);
            check(end.stream().anyMatch(ChatGroupMemberGrid.Cell::invite), "完整成员列表滚到底仍可邀请");
            check(end.stream().filter(c -> !c.invite()).allMatch(c -> c.member() < members), "最后一页没有越界成员");
        }
        var sent = new ArrayList<GroupCommand>(); var page = page(sent, A);
        field(page, "nameRow", new ChatLayout.Rect(10, 50, 100, 20)); page.mouseClicked(20, 55, 0);
        check(field(page, "page").toString().equals("RENAME"), "群主一次点击进入改名页");
        var rename = (ChatGroupRename) field(page, "rename"); var control = (ChatContactSearch) field(rename, "name");
        check(rename.value().equals("原群名"), "打开时填入已确认群名"); var box = input(control);
        box.setFocused(true); check(page.keyPressed(999, 0, 0) && page.charTyped('e', 0), "输入握有焦点时消费宿主按键与 e 字符");
        field(rename, "save", new ChatLayout.Rect(10, 100, 100, 14)); box.setValue("  "); page.mouseClicked(20, 105, 0);
        check(sent.size() == 1 && field(page, "status").equals(GroupStatus.INVALID.key()), "空白群名保存有明确提示，不静默禁用");
        box.setValue("  新群名  ");
        page.mouseClicked(20, 105, 0); var command = sent.get(sent.size() - 1);
        check(command instanceof GroupCommand.Rename v && v.name().equals("新群名") && v.group().equals(G), "保存提交当前群和清洗后的名字");
        page.mouseClicked(20, 105, 0); check(sent.size() == 2 && page.back(), "待确认禁止重复提交并保持页面");
        check(GroupClientCache.info().name().equals("原群名"), "服务器确认前不修改展示名");
        GroupClientCache.accept(new GroupReply.Result(command.request(), G, GroupStatus.BUSY)); poll(page);
        check(rename.value().equals("新群名") && field(page, "status").equals(GroupStatus.BUSY.key()), "拒绝保留草稿和错误提示");
        page.mouseClicked(20, 105, 0); command = sent.get(sent.size() - 1);
        check(!command.request().equals(sent.get(1).request()), "重试使用新票据");
        GroupClientCache.accept(new GroupReply.Updated(2, new GroupInfo(G, "新群名", A, info().members())));
        GroupClientCache.accept(new GroupReply.Result(command.request(), G, GroupStatus.OK)); poll(page);
        check(field(page, "page").toString().equals("DETAILS") && !page.consumeFinished() && GroupClientCache.info().name().equals("新群名"), "成功返回当前详情而不是退出群聊");
        page.close(); sent.clear(); page = page(sent, B);
        field(page, "nameRow", new ChatLayout.Rect(10, 50, 100, 20)); page.mouseClicked(20, 55, 0);
        check(field(page, "page").toString().equals("DETAILS") && sent.size() == 1 && field(page, "status").equals(GroupStatus.OWNER_REQUIRED.key()), "普通成员有明确群主权限提示，不发改名包");
        var grid = (ChatGroupMemberGrid) field(page, "grid");
        field(grid, "cells", ChatGroupMemberGrid.layout(new ChatLayout.Rect(10, 20, 100, 56), 2, false, 0));
        page.mouseClicked(63, 24, 0); check(field(page, "page").toString().equals("INVITE"), "普通成员点击加号一次进入邀请页");
        var invite = (ChatGroupInvite) field(page, "invite");
        var friends = com.november.mcphone.feature.chat.net.ChatClientCache.class.getDeclaredMethod("setConversations", List.class); friends.setAccessible(true);
        friends.invoke(null, List.of(new com.november.mcphone.feature.chat.net.ConversationSummary(B, "Bob", false, Optional.empty(), 0, 0),
                new com.november.mcphone.feature.chat.net.ConversationSummary(C, "Carol", false, Optional.empty(), 0, 0)));
        call(invite, "refresh", new Class<?>[]{GroupInfo.class}, info());
        @SuppressWarnings("unchecked") var sections = (List<ChatContactDirectory.Section>) field(invite, "sections");
        check(sections.size() == 1 && sections.get(0).contacts().get(0).id().equals(C), "邀请目录排除现有成员并包含离线好友");
        var pane = (ChatContactPane) field(invite, "pane"); field(pane, "bounds", new ChatLayout.Rect(10, 30, 100, 60));
        ((ChatContactScroll) field(pane, "scroll")).update(sections, 60);
        field(invite, "confirm", new ChatLayout.Rect(10, 100, 100, 14)); page.mouseClicked(20, 105, 0);
        check(sent.size() == 1 && field(page, "status").equals("mcphone.chat.group.select_members"), "未选人点击邀请给出提示");
        page.mouseClicked(12, 40, 0); page.mouseClicked(20, 105, 0); command = sent.get(sent.size() - 1);
        check(command instanceof GroupCommand.Invite v && v.members().equals(List.of(C)), "普通成员邀请发出正确成员批次");
        GroupClientCache.accept(new GroupReply.Result(command.request(), G, GroupStatus.NOT_FRIEND)); poll(page);
        check(((Set<?>) field(invite, "selected")).contains(C), "服务端好友关系变化导致拒绝时保留选择");
        page.mouseClicked(20, 105, 0); command = sent.get(sent.size() - 1);
        GroupClientCache.accept(new GroupReply.Result(command.request(), G, GroupStatus.OK)); poll(page);
        check(field(page, "page").toString().equals("DETAILS") && GroupClientCache.info().members().size() == 2 && !page.consumeFinished()
                && field(page, "status").equals("mcphone.chat.group.invite_sent"), "发送邀请只提示等待同意，成员数不提前增加");
        GroupClientCache.accept(new GroupReply.Updated(2, new GroupInfo(G, "原群名", A,
                List.of(new GroupMember(A, "A"), new GroupMember(B, "B"), new GroupMember(C, "Carol")))));
        check(GroupClientCache.info().members().size() == 3, "本人同意后的服务器资料推送更新实际成员数");
        field(page, "action", new ChatLayout.Rect(10, 100, 100, 14)); int beforeExit = sent.size();
        page.mouseClicked(20, 105, 0); check(sent.size() == beforeExit, "新增详情功能仍保留离群的第一次确认");
        page.mouseClicked(20, 105, 0); command = sent.get(sent.size() - 1);
        check(command instanceof GroupCommand.Leave, "第二次确认只发送普通成员退出请求"); page.close();
        GroupClientCache.accept(new GroupReply.Result(command.request(), G, GroupStatus.OK));
        check(GroupClientCache.consume(command.request()) == null && !page.consumeFinished(), "页面关闭取消票据，迟到确认不导航");
        com.november.mcphone.feature.chat.net.ChatClientCache.clear(); GroupClientCache.clear();
        System.out.println("全部通过：" + checks + " 条断言");
    }
}
