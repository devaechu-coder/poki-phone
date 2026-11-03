package com.november.mcphone.feature.chat.client;

import com.november.mcphone.feature.chat.net.OnlinePlayer;
import com.november.mcphone.feature.chat.net.Relation;
import java.util.List;
import java.util.UUID;

/** 实际页面的导航意图与列表几何，覆盖字母跳转、裁剪边界和生命周期。 */
public final class ChatContactsInteractionTest {
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static void field(Object owner, String name, Object value) throws Exception {
        var f = owner.getClass().getDeclaredField(name); f.setAccessible(true); f.set(owner, value);
    }
    private static Object field(Object owner, String name) throws Exception {
        var f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner);
    }
    public static void main(String[] args) throws Exception {
        var directory = new ChatContactDirectory();
        var alice = new ChatContactDirectory.Contact(new UUID(0, 1), "Alice", true);
        var bob = new ChatContactDirectory.Contact(new UUID(0, 2), "Bob", false);
        var zoe = new ChatContactDirectory.Contact(new UUID(0, 3), "Zoe", true);
        var sections = directory.sections(List.of(zoe, bob, alice), "");
        var scroll = new ChatContactScroll(); scroll.update(sections, 30);
        check(scroll.rows().size() == 6 && scroll.rows().get(0).heading(), "字母标题与好友行交替排列");
        check(scroll.rows().get(1).top() == 8 && scroll.rows().get(2).top() == 27, "可变行高使用共同坐标");
        check(!scroll.scroll(1, 30) && scroll.offset() == 0, "顶部不能向上越界");
        check(scroll.scroll(-1, 30) && scroll.offset() == 19, "滚轮移动一条好友高度");
        check(scroll.firstLetter().equals("A"), "部分可见行仍属于 A");
        check(scroll.jump("B", 30) && scroll.offset() == 27, "字母跳到分组标题");
        check(!scroll.jump("C", 30) && scroll.offset() == 27, "空字母不跳到错误的人");
        scroll.jump("Z", 30); check(scroll.offset() == 51, "末组按视口下沿钳制");
        check(!scroll.scroll(-1, 30), "底部不能越界");
        scroll.update(sections, 100); check(scroll.offset() == 0, "放大视口重新钳制");
        scroll.update(List.of(), 30); check(scroll.rows().isEmpty() && scroll.offset() == 0, "搜索空结果清理旧行");

        var pane = new ChatContactPane();
        field(pane, "bounds", new ChatLayout.Rect(10, 20, 100, 30));
        var paneScroll = (ChatContactScroll) field(pane, "scroll"); paneScroll.update(sections, 30);
        check(pane.click(12, 21, 0) == null, "标题不能打开会话");
        check(alice.id().equals(pane.click(12, 30, 0)), "实际好友区域返回稳定 UUID");
        check(pane.click(12, 30, 1) == null, "右键不打开聊天");
        check(!pane.mouseScrolled(9, 30, -1) && paneScroll.offset() == 0, "列表外滚轮不翻页");
        check(pane.mouseScrolled(12, 30, -1), "列表内可以翻页");
        check(alice.id().equals(pane.click(12, 20, 0)), "裁剪后的部分行点击仍命中对应人");
        check(pane.click(12, 50, 0) == null, "下沿之外不命中未绘制行");
        pane.close(); check(pane.click(12, 30, 0) == null, "关闭后清除命中区");

        var navigation = new ChatNavigationBar(); field(navigation, "bounds", new ChatLayout.Rect(10, 100, 119, 18));
        check(navigation.click(39, 105, 0) == ChatNavigationBar.Page.CONTACTS, "奇数宽底栏边界与绘制一致");
        check(navigation.click(38, 105, 0) == ChatNavigationBar.Page.CHATS, "消息标签下沿不抢联系人");
        check(navigation.click(80, 105, 0) == null, "发现仅占位");
        navigation.clear(); check(navigation.click(39, 105, 0) == null, "底栏旧位置不能复用");

        var home = new ChatList(); var homeNav = (ChatNavigationBar) field(home, "navigation");
        field(homeNav, "bounds", new ChatLayout.Rect(10, 100, 119, 18));
        check(home.mouseClicked(39, 105, 0) && home.consumeContactsRequest(), "主页联系人进入独立页");
        check(!home.consumeAddContactRequest() && !home.consumeContactsRequest(), "不混入添加好友，请求只消费一次");
        var page = new ChatContacts(); page.open();
        field(page, "add", new ChatLayout.Rect(100, 10, 12, 12));
        field(page, "newFriends", new ChatLayout.Rect(10, 30, 100, 14));
        field(page, "groupNotices", new ChatLayout.Rect(10, 45, 100, 14));
        page.mouseClicked(105, 15, 0); check(page.consumeAction() == ChatContacts.Action.ADD_CONTACT, "右上角保留添加好友");
        page.mouseClicked(20, 35, 0); check(page.consumeAction() == ChatContacts.Action.NEW_FRIENDS, "新朋友单独交出申请页导航");
        page.mouseClicked(20, 50, 0);
        check(page.consumeAction() == null && field(page, "add") == null, "群通知进入邀请页并清除右上角按钮");
        page.mouseClicked(105, 15, 0);
        check((boolean) field(page, "notifications") && page.consumeAction() == null, "旧箭头位置不再隐式返回或添加好友");
        check(page.back(), "群通知仍可通过底部返回键返回联系人");
        check(!page.back(), "联系人主页返回交给宿主");
        page.close(); page.mouseClicked(105, 15, 0); check(page.consumeAction() == null, "关闭清除旧按钮和待处理导航");
        List<OnlinePlayer> players = List.of(new OnlinePlayer(alice.id(), "Alice", Relation.FRIEND),
                new OnlinePlayer(bob.id(), "Bob", Relation.REQUEST_RECEIVED),
                new OnlinePlayer(zoe.id(), "Zoe", Relation.REQUEST_SENT),
                new OnlinePlayer(new UUID(0, 4), "Dave", Relation.NONE));
        check(ChatAddContact.requestPlayers(players).equals(players.subList(1, 3)), "申请页不混入解除好友和陌生人按钮");
        System.out.println("全部通过：" + checks + " 条断言");
    }
}
