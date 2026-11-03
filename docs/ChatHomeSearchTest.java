package com.november.mcphone.feature.chat.client;

import com.november.mcphone.feature.chat.net.*;
import com.november.mcphone.feature.chat.group.*;
import com.november.mcphone.feature.chat.group.net.*;
import java.util.*;

/** 首页实际过滤、快照更新和同 UUID 导航隔离；输入改变后不能复用旧传送目标。 */
public final class ChatHomeSearchTest {
    private static int checks;
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    private static Object field(Object o, String name) throws Exception { var f = o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o); }
    private static void field(Object o, String name, Object value) throws Exception { var f = o.getClass().getDeclaredField(name); f.setAccessible(true); f.set(o, value); }
    private static List<?> rows(ChatList list) throws Exception { var m = ChatList.class.getDeclaredMethod("rows"); m.setAccessible(true); return (List<?>) m.invoke(list); }
    private static void friends(List<ConversationSummary> value) throws Exception { var m = ChatClientCache.class.getDeclaredMethod("setConversations", List.class); m.setAccessible(true); m.invoke(null, value); }
    private static ConversationSummary friend(UUID id, String name, long time) { return new ConversationSummary(id, name, false, Optional.empty(), time, 0); }
    private static GroupSummary group(UUID id, String name) { return new GroupSummary(id, name, id, 1, List.of(id), Optional.empty(), 0); }
    public static void main(String[] args) throws Exception {
        ChatClientCache.clear(); GroupClientCache.clear(); var id = new UUID(0, 1); var chinese = new UUID(0, 2);
        friends(List.of(friend(id, "Ａlice", 20), friend(chinese, "张三", 10)));
        GroupClientCache.accept(new GroupReply.Directory(List.of(group(id, "周末 Alice 群"))));
        var list = new ChatList(); var search = (ChatContactSearch) field(list, "search");
        var all = rows(list); check(all.size() == 3, "好友和群会话都进入首页");
        check(((ConversationSummary) field(all.get(0), "summary")).lastTime() == 20, "保留原有最后消息时间排序");
        check(rows(list) == all, "未变的快照复用排版输入");
        search.value("  ALICE  "); field(list, "scrollOffset", 9);
        var filtered = rows(list); check(filtered.size() == 2 && (int) field(list, "scrollOffset") == 0, "大小写、全角、空白规则与联系人一致，搜索回到顶部");
        check(field(filtered.get(0), "group") == null && field(filtered.get(1), "group") != null, "同 UUID 好友和群仍保留两个独立会话");
        check(rows(list) == filtered, "相同查询复用过滤快照");
        search.value("张"); check(rows(list).size() == 1, "中文按名称过滤");
        search.value("没有结果"); check(rows(list).isEmpty() && !list.mouseScrolled(-1), "无结果不能滚入不存在的行");
        search.value("Alice"); rows(list); field(list, "hoveredPeer", id); field(list, "teleportHoveredPeer", id);
        search.value("张"); list.charTyped('x', 0);
        check(field(list, "hoveredPeer") == null && field(list, "teleportHoveredPeer") == null, "两帧之间查询变更清除旧打开与传送目标");
        check(!list.mouseClicked(1, 1, 0) && list.consumeOpenRequest() == null, "未重新绘制时不能误开旧行");
        search.value("Alice"); friends(List.of(friend(chinese, "Alice New", 30)));
        check(rows(list).size() == 2 && ((ConversationSummary) field(rows(list).get(0), "summary")).name().equals("Alice New"), "服务端新好友摘要即使查询没变也刷新结果");
        GroupClientCache.accept(new GroupReply.Directory(List.of(group(id, "改名后的群"))));
        check(rows(list).size() == 1, "群改名立即影响搜索结果");
        search.value("   "); check(rows(list).size() == 2, "空白搜索恢复完整目录");
        list.close(); check(search.query().isEmpty() && rows(list).size() == 2, "离开清除搜索和旧导航状态");
        ChatClientCache.clear(); GroupClientCache.clear();
        System.out.println("全部通过：" + checks + " 条断言");
    }
}
