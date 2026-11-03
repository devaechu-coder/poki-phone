package com.november.mcphone.feature.chat.client;

import com.november.mcphone.core.PhoneLocation;
import com.november.mcphone.core.client.PhoneScreen;
import com.november.mcphone.core.client.PhoneSession;
import net.minecraft.world.InteractionHand;
import java.util.*;

/** 生产选人点击、群/私聊导航命名空间与两台设备的群续开状态。 */
public final class ChatGroupInteractionTest {
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static Object field(Object o, String name) throws Exception { var f = o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o); }
    private static void field(Object o, String name, Object value) throws Exception { var f = o.getClass().getDeclaredField(name); f.setAccessible(true); f.set(o, value); }
    public static void main(String[] args) throws Exception {
        var directory = new ChatContactDirectory(); var a = new ChatContactDirectory.Contact(new UUID(0, 1), "张三", true);
        var b = new ChatContactDirectory.Contact(new UUID(0, 2), "Alice", false);
        var sections = directory.sections(List.of(a, b), "");
        var create = new ChatGroupCreate(); var pane = (ChatContactPane) field(create, "pane");
        field(pane, "bounds", new ChatLayout.Rect(10, 20, 100, 60));
        ((ChatContactScroll) field(pane, "scroll")).update(sections, 60);
        create.mouseClicked(12, 30, 0);
        check(((Set<?>) field(create, "selected")).contains(b.id()), "离线好友也可选入群聊");
        create.mouseClicked(12, 30, 0);
        check(((Set<?>) field(create, "selected")).isEmpty(), "再点一行取消选择");
        create.mouseClicked(12, 21, 0); check(((Set<?>) field(create, "selected")).isEmpty(), "字母标题不选人");
        create.mouseClicked(12, 30, 1); check(((Set<?>) field(create, "selected")).isEmpty(), "右键不改变成员");
        create.mouseClicked(12, 58, 0); check(((Set<?>) field(create, "selected")).contains(a.id()), "中文 A-Z 分组中的好友可选");
        var ready = ChatGroupCreate.class.getDeclaredMethod("ready"); ready.setAccessible(true);
        check((boolean) ready.invoke(create), "选中好友即可建群，群名不再强制填写");
        create.close(); check(((Set<?>) field(create, "selected")).isEmpty() && !create.mouseScrolled(12, 30, -1), "离开清除选择与旧点击区域");

        var contacts = new ChatContacts(); contacts.open(); field(contacts, "groups", true);
        var contactPane = (ChatContactPane) field(contacts, "pane"); field(contactPane, "bounds", new ChatLayout.Rect(10, 20, 100, 60));
        ((ChatContactScroll) field(contactPane, "scroll")).update(sections, 60);
        contacts.mouseClicked(12, 30, 0);
        check(contacts.consumeGroupOpenRequest().equals(b.id()) && contacts.consumeOpenRequest() == null, "同 UUID 的群和好友使用不同导航意图");
        contacts.close(); check(contacts.consumeGroupOpenRequest() == null, "关闭清除群导航");
        var list = new ChatList(); field(list, "hoveredPeer", b.id()); field(list, "hoveredGroup", true);
        list.mouseClicked(1, 1, 0); check(list.consumeGroupOpenRequest().equals(b.id()) && list.consumeOpenRequest() == null, "首页群行不会走私聊打开路径");
        field(list, "hoveredGroup", false); list.mouseClicked(1, 1, 0);
        check(list.consumeOpenRequest().equals(b.id()) && list.consumeGroupOpenRequest() == null, "首页好友行仍走原有私聊路径");
        var phone = new PhoneLocation.InHand(InteractionHand.MAIN_HAND); var tablet = new PhoneLocation.InHand(InteractionHand.OFF_HAND);
        PhoneSession.clearAll(); PhoneSession.save(phone, PhoneScreen.Mode.CHAT_GROUP_CONVERSATION, a.id());
        PhoneSession.save(tablet, PhoneScreen.Mode.CHAT_CONVERSATION, b.id());
        check(PhoneSession.resumePeer(phone).equals(a.id()) && PhoneSession.resumePeer(tablet).equals(b.id()), "群和私聊在不同设备独立续开");
        PhoneSession.save(phone, PhoneScreen.Mode.CHAT_GROUP_DETAILS, a.id());
        check(PhoneSession.resumePeer(phone).equals(a.id()), "群详情关机后保留群会话身份");
        PhoneSession.save(phone, PhoneScreen.Mode.CHAT_GROUP_CREATE, a.id());
        check(PhoneSession.resumePeer(phone) == null && PhoneSession.resumePeer(tablet).equals(b.id()), "建群草稿不续开，不影响另一设备");
        PhoneSession.clearAll();
        System.out.println("全部通过：" + checks + " 条断言");
    }
}
