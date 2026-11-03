package com.november.mcphone.feature.chat.client;

import com.november.mcphone.feature.chat.ChatMessage;
import com.november.mcphone.feature.chat.group.*;
import com.november.mcphone.feature.chat.group.net.*;
import java.util.*;

/** 实际收件箱按钮、待确认隔离、服务器同意/拒绝、旧命中清理与迟到结果。 */
public final class ChatGroupNoticesTest {
    private static int checks;
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3), G = new UUID(1, 1);
    private static GroupMember member(UUID id) { return new GroupMember(id, "Player" + id.getLeastSignificantBits()); }
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    private static Object field(Object o, String name) throws Exception { var f = o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o); }
    private static void field(Object o, String name, Object value) throws Exception { var f = o.getClass().getDeclaredField(name); f.setAccessible(true); f.set(o, value); }
    private static void poll(ChatGroupNotices page) throws Exception { var m = ChatGroupNotices.class.getDeclaredMethod("pollResult"); m.setAccessible(true); m.invoke(page); }
    private static void hits(ChatGroupNotices page, List<GroupInviteNotice> invitations) throws Exception {
        var area = new ChatLayout.Rect(10, 20, 100, 68); field(page, "area", area);
        field(page, "hits", ChatGroupNotices.layout(invitations, area, 0));
    }
    public static void main(String[] args) throws Exception {
        GroupClientCache.clear(); var book = new GroupBook(); book.create(G, member(A), "测试群", List.of(member(B)), id -> true, 0);
        book.append(G, A, "A", ChatMessage.text(A, "不能在同意前读取", 1));
        var invitations = book.notices(B, 1);
        GroupClientCache.accept(new GroupReply.Notices(invitations));
        var sent = new ArrayList<GroupCommand>(); var page = new ChatGroupNotices(sent::add); page.open(); hits(page, invitations);
        page.click(12, 23, 0); page.click(60, 40, 1); page.click(999, 999, 0);
        check(sent.isEmpty(), "卡片正文、右键和页面外点击都不会同意邀请");
        page.click(60, 40, 0); var accept = (GroupCommand.RespondInvitation) sent.get(0);
        check(accept.accept() && accept.group().equals(G) && accept.invitation().equals(invitations.get(0).id()), "同意按钮提交正确群与邀请身份");
        check(GroupClientCache.groups().isEmpty() && book.groups(B).isEmpty(), "点击之后等待服务端确认，不提前加入");
        page.click(85, 40, 0); page.click(60, 40, 0); check(sent.size() == 1, "待确认时同意与拒绝不能重复发送");
        GroupClientCache.accept(new GroupReply.Result(UUID.randomUUID(), G, GroupStatus.OK)); poll(page);
        check(field(page, "request").equals(accept.request()), "其他确认不解除当前等待");
        GroupClientCache.accept(new GroupReply.Result(accept.request(), G, GroupStatus.BUSY)); poll(page);
        check(field(page, "request") == null && field(page, "status").equals(GroupStatus.BUSY.key()) && book.groups(B).isEmpty(), "服务端拒绝可重试，不赋予成员资格");
        page.click(60, 40, 0); var retry = (GroupCommand.RespondInvitation) sent.get(1);
        check(!retry.request().equals(accept.request()), "重试更换票据但不更换邀请 ID");
        var room = book.respond(retry.group(), retry.invitation(), member(B), retry.accept(), (a, b) -> true, 2);
        GroupClientCache.accept(new GroupReply.Directory(List.of(GroupSummary.of(room, B))));
        GroupClientCache.accept(new GroupReply.Notices(book.notices(B, 2)));
        GroupClientCache.accept(new GroupReply.Result(retry.request(), G, GroupStatus.OK)); poll(page);
        check(field(page, "status").equals("mcphone.chat.group.invite_accepted") && GroupClientCache.groups().size() == 1, "服务器加入成功才显示已加入并更新群目录");
        check(book.room(G, B).visible(B).isEmpty() && GroupClientCache.invitations().isEmpty(), "同意后的历史边界与通知移除一同生效");
        page.click(60, 40, 0); check(sent.size() == 2, "旧帧已处理邀请的按钮不会重发");

        GroupClientCache.clear(); page.close(); sent.clear(); book.invite(G, B, List.of(member(C)), id -> true, 3);
        invitations = book.notices(C, 3); GroupClientCache.accept(new GroupReply.Notices(invitations)); page.open(); hits(page, invitations);
        page.click(85, 40, 0); var decline = (GroupCommand.RespondInvitation) sent.get(0);
        check(!decline.accept() && decline.invitation().equals(invitations.get(0).id()), "拒绝按钮与同意按钮不混淆");
        book.respond(decline.group(), decline.invitation(), member(C), false, (a, b) -> false, 4);
        GroupClientCache.accept(new GroupReply.Notices(book.notices(C, 4)));
        GroupClientCache.accept(new GroupReply.Result(decline.request(), G, GroupStatus.OK)); poll(page);
        check(field(page, "status").equals("mcphone.chat.group.invite_declined") && book.groups(C).isEmpty(), "拒绝清除通知且始终不进入群");
        book.invite(G, A, List.of(member(C)), id -> true, 5); invitations = book.notices(C, 5);
        GroupClientCache.accept(new GroupReply.Notices(invitations)); hits(page, invitations); page.click(60, 40, 0);
        var abandoned = (GroupCommand.RespondInvitation) sent.get(sent.size() - 1); page.close();
        GroupClientCache.accept(new GroupReply.Result(abandoned.request(), G, GroupStatus.OK));
        check(GroupClientCache.consume(abandoned.request()) == null, "离开页面取消票据，迟到确认不产生界面跳转");
        page.click(60, 40, 0); check(sent.size() == 2, "关闭后清除旧按钮命中区");

        var many = new ArrayList<GroupInviteNotice>();
        for (int i = 0; i < 64; i++) many.add(new GroupInviteNotice(new UUID(5, i), G, "群" + i, member(A), i + 1, i + 1000));
        GroupClientCache.accept(new GroupReply.Notices(many)); page.open(); hits(page, many);
        check(page.mouseScrolled(20, 40, -1) && ((List<?>) field(page, "hits")).isEmpty(), "滚动后立即清除旧帧按钮，防止错同意");
        for (int width : new int[]{64, 100, 184}) for (int height : new int[]{34, 68, 136}) {
            var area = new ChatLayout.Rect(10, 20, width, height);
            var rows = ChatGroupNotices.layout(many, area, 999);
            check(rows.get(rows.size() - 1).invitation().id().equals(many.get(63).id()), "最后一页可处理第 64 条邀请");
            for (var row : rows) {
                check(row.bounds().x() >= area.x() && row.bounds().y() >= area.y() && row.bounds().x() + row.bounds().width() <= area.x() + area.width()
                        && row.bounds().y() + row.bounds().height() <= area.y() + area.height(), "通知卡片完整落在视口中");
                check(row.accept().x() >= row.bounds().x() && row.decline().x() + row.decline().width() <= row.bounds().x() + row.bounds().width()
                        && row.accept().x() + row.accept().width() < row.decline().x(), "同意和拒绝按钮在卡片内，互不重叠");
            }
        }
        page.close(); GroupClientCache.clear();
        System.out.println("全部通过：" + checks + " 条断言");
    }
}
