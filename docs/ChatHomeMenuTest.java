package com.november.mcphone.feature.chat.client;

import com.november.mcphone.feature.chat.client.contextmenu.ChatContextMenuView;
import com.november.mcphone.core.client.PhoneScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 无窗口装配实际菜单几何，验证宿主同次点击导航、操作意图及点击隔离。 */
public final class ChatHomeMenuTest {
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static Object field(Object owner, String name) throws Exception {
        var f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner);
    }
    private static void field(Object owner, String name, Object value) throws Exception {
        var f = owner.getClass().getDeclaredField(name); f.setAccessible(true); f.set(owner, value);
    }
    /** 使用生产视图的排版方法，不替换命中或操作逻辑。 */
    private static void open(ChatHomeMenu menu) throws Exception {
        menu.open(120, 20);
        var bounds = Class.forName("com.november.mcphone.feature.chat.client.contextmenu.ChatContextMenuLayout$Bounds");
        var constructor = bounds.getDeclaredConstructor(int.class, int.class, int.class, int.class); constructor.setAccessible(true);
        var method = ChatContextMenuView.class.getDeclaredMethod("arrange", bounds, int.class, int.class, List.class); method.setAccessible(true);
        var entries = ChatHomeMenu.class.getDeclaredField("ENTRIES"); entries.setAccessible(true);
        method.invoke(field(menu, "view"), constructor.newInstance(0, 0, 120, 160), 27, 7, entries.get(null));
    }
    public static void main(String[] args) throws Exception {
        var menu = new ChatHomeMenu();
        check(!menu.isOpen() && !menu.dismiss(), "初始没有弹窗");
        open(menu); check(menu.choose(78, 26, 0) == ChatHomeMenu.Action.CREATE_GROUP && !menu.isOpen(), "首行创建群聊，选择后关闭");
        open(menu); check(menu.choose(78, 39, 0) == ChatHomeMenu.Action.ADD_CONTACT && !menu.isOpen(), "第二行添加好友，图标区域也可点");
        open(menu); check(menu.choose(-100, -100, 0) == null && !menu.isOpen(), "外部点击不产生操作");
        open(menu); check(menu.choose(78, 39, 1) == null && !menu.isOpen(), "右键只关闭，不误选好友");
        open(menu); check(menu.choose(78, 39, 2) == null, "中键也不执行菜单操作");
        open(menu); menu.dismiss(); menu.open(120, 20);
        check(menu.choose(78, 39, 0) == null, "重新打开且尚未绘制时不会使用旧命中区");

        var page = new ChatList(); page.open();
        field(page, "plusBounds", new ChatLayout.Rect(104, 4, 12, 12));
        check(page.mouseClicked(110, 10, 0) && page.hasContextMenu(), "加号改为打开菜单");
        check(!page.consumeAddContactRequest(), "打开菜单不会直接进入添加好友");
        var pageMenu = (ChatHomeMenu) field(page, "homeMenu");
        // 底下故意保留悬停人，确认选择与关闭不会打开旧目标。
        open(pageMenu); field(page, "hoveredPeer", UUID.randomUUID());
        check(page.mouseClicked(-100, -100, 0) && !page.hasContextMenu(), "点击外部只关闭菜单并消费事件");
        check(page.consumeOpenRequest() == null && !page.consumeCloseRequest(), "没有隐式打开会话或执行传送");
        open(pageMenu);
        check(page.contextMouseClicked(78, 39, 0) && page.consumeAddContactRequest(), "好友项交出原有导航请求");
        check(!page.consumeAddContactRequest(), "导航请求只能消费一次");
        open(pageMenu);
        check(page.contextMouseClicked(78, 26, 0) && !page.consumeAddContactRequest(), "群聊项不混入好友导航");
        check(page.consumeCreateRequest() && !page.consumeCreateRequest(), "群聊项交出一次建群导航请求");
        var dispatch = PhoneScreen.class.getDeclaredMethod("dispatchChatHomeMenu", ChatList.class,
                double.class, double.class, int.class, java.util.function.Consumer.class);
        dispatch.setAccessible(true);
        var destinations = new ArrayList<PhoneScreen.Mode>();
        java.util.function.Consumer<PhoneScreen.Mode> navigate = destinations::add;
        open(pageMenu);
        check((boolean) dispatch.invoke(null, page, 78d, 26d, 0, navigate)
                && destinations.equals(List.of(PhoneScreen.Mode.CHAT_GROUP_CREATE)), "宿主在选择群聊的同一次点击直接跳转");
        check(!page.hasContextMenu() && !page.consumeCreateRequest() && !page.consumeAddContactRequest(), "跳转后没有等待下一次点击的请求");
        check(!(boolean) dispatch.invoke(null, page, 110d, 10d, 0, navigate)
                && destinations.size() == 1, "下一次普通点击不会重复打开建群页");
        open(pageMenu);
        check((boolean) dispatch.invoke(null, page, 78d, 39d, 0, navigate)
                && destinations.get(1) == PhoneScreen.Mode.CHAT_ADD_CONTACT, "宿主同一入口也立即打开添加好友");
        open(pageMenu);
        check((boolean) dispatch.invoke(null, page, -100d, -100d, 0, navigate)
                && destinations.size() == 2 && !page.hasContextMenu(), "宿主关闭菜单的外部点击不穿透也不导航");
        open(pageMenu);
        check((boolean) dispatch.invoke(null, page, 78d, 26d, 1, navigate)
                && destinations.size() == 2 && !page.hasContextMenu(), "宿主右键不误触建群");
        open(pageMenu); field(page, "scrollOffset", 3);
        check(page.mouseScrolled(-1) && !page.hasContextMenu() && (int) field(page, "scrollOffset") == 3, "滚轮只收起菜单，不滚动底下列表");
        open(pageMenu); check(page.dismissContextMenu() && !page.dismissContextMenu(), "首次返回收起菜单，第二次交回页面");
        open(pageMenu); page.close();
        check(!page.hasContextMenu() && !page.consumeCreateRequest(), "离开页面同时清理弹窗与建群请求");
        check(page.consumeOpenRequest() == null && !page.consumeAddContactRequest(), "离开页面清理未消费意图");
        page.open(); check(!page.mouseClicked(110, 10, 0) && !page.hasContextMenu(), "重新进入尚未绘制时旧加号位置不能命中");
        System.out.println("全部通过：" + checks + " 条断言");
    }
}
