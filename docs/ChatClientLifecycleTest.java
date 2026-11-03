package com.november.mcphone.feature.chat.client;

import com.november.mcphone.feature.chat.client.profile.ProfileClientCache;
import com.november.mcphone.feature.chat.group.net.GroupClientCache;
import com.november.mcphone.feature.chat.group.net.GroupReply;
import com.november.mcphone.feature.chat.group.GroupStatus;
import com.november.mcphone.feature.chat.net.ChatClientCache;
import com.november.mcphone.feature.chat.profile.net.ProfileWire;
import java.util.Map;
import java.util.UUID;

/** 调用实际断线入口，验证在飞票据、上传与传输状态一起失效；不装配 GL 贴图。 */
public final class ChatClientLifecycleTest {
    private static int checks;
    private static Object field(Class<?> type, String name) throws Exception {
        var f = type.getDeclaredField(name); f.setAccessible(true); return f.get(null);
    }
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    public static void main(String[] args) throws Exception {
        UUID player = new UUID(0, 1), group = new UUID(0, 2), operation = new UUID(0, 3);
        ChatClientCache.openConversation(player);
        var history = GroupClientCache.open(group); GroupClientCache.track(operation);
        var profile = ProfileClientCache.begin(player, System.currentTimeMillis());
        var session = (ChatUploadSession) field(ChatImageSender.class, "SESSION");
        Object connection = new Object(); var upload = session.start(connection);
        ((Map<UUID, Long>) field(GroupTransport.class, "textRequests")).put(operation, 123L);
        var binder = GroupTransport.class.getDeclaredMethod("bind", Object.class); binder.setAccessible(true); binder.invoke(null, connection);
        ((Map<UUID, Long>) field(GroupTransport.class, "textRequests")).put(operation, 123L);
        ChatClientLifecycle.disconnect();
        check(!session.accepts(upload, connection), "旧后台上传失效");
        check(GroupClientCache.pendingHistory() == null && GroupClientCache.messages().isEmpty(), "群历史票据和消息清空");
        check(ChatClientCache.getMessages().isEmpty() && field(ChatClientCache.class, "openPeer") == null, "私聊目标清空");
        GroupClientCache.accept(new GroupReply.Result(operation, group, GroupStatus.OK));
        check(GroupClientCache.consume(operation) == null, "晚到群操作无主");
        ProfileClientCache.accept(new ProfileWire.Reply(profile, player, ProfileWire.Status.OK, "旧玩家", "旧服务器"));
        check(ProfileClientCache.consume(profile) == null, "晚到资料回声无主");
        check(((Map<?, ?>) field(GroupTransport.class, "textRequests")).isEmpty(), "待确认文字请求清空");
        check(((java.lang.ref.WeakReference<?>) field(GroupTransport.class, "connection")).get() == null, "旧传输连接清空");
        check(field(com.november.mcphone.feature.chat.client.favorite.ChatFavorites.class, "active") == null, "本机收藏运行时绑定清空");
        ChatClientLifecycle.disconnect();
        check(GroupClientCache.pendingHistory() == null && !ChatImageSender.isBusy(), "重复断线清理安全");
        System.out.println("全部通过：" + checks + " 条断言");
    }
}
