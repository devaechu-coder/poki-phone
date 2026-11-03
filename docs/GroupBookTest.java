package com.november.mcphone.feature.chat.group;

import com.november.mcphone.feature.chat.ChatMessage;
import com.november.mcphone.feature.chat.ImageBody;
import net.minecraft.nbt.CompoundTag;
import java.util.List;
import java.util.UUID;

/** 实际群业务和 NBT 重载：权限、离线成员、序号、个人删除和损坏保护。 */
public final class GroupBookTest {
    private static int checks;
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3), ROOM = new UUID(1, 1);
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static void denied(GroupStatus status, Runnable run) {
        try { run.run(); throw new AssertionError("应拒绝 " + status); }
        catch (GroupFault error) { check(error.status() == status, "正确拒绝类型：" + status); }
    }
    private static GroupMessage send(GroupBook book, UUID actor, String text, long time) {
        return book.append(ROOM, actor, actor.equals(A) ? "Alice" : "Bob", ChatMessage.text(actor, text, time));
    }
    private static void accept(GroupBook book, UUID group, UUID player, String name) {
        long now = System.currentTimeMillis();
        var invitation = book.notices(player, now).stream().filter(i -> i.group().equals(group)).findFirst().orElseThrow();
        book.respond(group, invitation.id(), new GroupMember(player, name), true, (a, b) -> true, now);
    }
    public static void main(String[] args) {
        var book = new GroupBook();
        denied(GroupStatus.NOT_FRIEND, () -> book.create(ROOM, new GroupMember(A, "Alice"), "测试群",
                List.of(new GroupMember(B, "Bob")), id -> false));
        check(book.snapshot().isEmpty() && book.groups(B).isEmpty(), "建群拒绝不留下部分成员索引");
        denied(GroupStatus.INVALID, () -> book.create(ROOM, new GroupMember(A, "Alice"), "测试群",
                List.of(new GroupMember(A, "Alice")), id -> true));
        book.create(ROOM, new GroupMember(A, "Alice"), "  测试群  ", List.of(new GroupMember(B, "Bob")), id -> true);
        check(book.room(ROOM, A).name().equals("测试群") && book.groups(B).isEmpty(), "群名清洗，待同意好友不建立成员索引");
        accept(book, ROOM, B, "Bob"); check(book.groups(B).size() == 1, "本人同意后建立成员索引");
        denied(GroupStatus.NOT_MEMBER, () -> book.room(ROOM, C));
        denied(GroupStatus.OWNER_REQUIRED, () -> book.rename(ROOM, B, "恶意改名"));
        denied(GroupStatus.OWNER_REQUIRED, () -> book.dissolve(ROOM, B));
        denied(GroupStatus.OWNER_MUST_DISSOLVE, () -> book.leave(ROOM, A));
        var first = send(book, A, "第一条", 1234);
        var second = send(book, B, "第二条", 1234);
        check(first.sequence() == 1 && second.sequence() == 2, "相同毫秒仍有不同且递增的服务器序号");
        check(book.room(ROOM, A).unread(A) == 1 && book.room(ROOM, B).unread(B) == 0, "未读按序号并排除自己，彼此独立");
        denied(GroupStatus.NOT_MEMBER, () -> send(book, C, "伪造消息", 1234));
        denied(GroupStatus.INVALID, () -> book.append(ROOM, A, "Alice", ChatMessage.text(B, "伪造发送者", 1234)));
        var snapshot = book.room(ROOM, A).messages();
        check(book.hide(ROOM, A, second.message().id()), "本人可以删除对方消息的个人记录");
        check(book.room(ROOM, A).visible(A).size() == 1 && book.room(ROOM, B).visible(B).size() == 2, "个人删除不改变共享记录和其他人");
        check(book.room(ROOM, A).unread(A) == 0, "删除的消息不计未读");
        book.markRead(ROOM, B, Long.MAX_VALUE);
        book.markRead(ROOM, B, 0);
        check(book.room(ROOM, B).read().get(B) == 2L, "已读不后退，客户端不能越过服务器序号");
        check(snapshot.size() == 2 && snapshot.get(1).message().id().equals(second.message().id()), "旧消息快照不可污染");
        try { snapshot.clear(); throw new AssertionError("可变快照"); } catch (UnsupportedOperationException expected) { checks++; }
        var data = new GroupData();
        data.book().create(ROOM, new GroupMember(A, "Alice"), "群聊", List.of(new GroupMember(B, "Bob")), id -> true);
        accept(data.book(), ROOM, B, "Bob");
        var imageId = new UUID(9, 9);
        var image = new ChatMessage(B, 1234, new ImageBody(imageId, 20, 20, 1, 0));
        data.book().append(ROOM, B, "Bob", image); data.book().hide(ROOM, A, image.id());
        var restored = GroupData.load(data.write(new CompoundTag()));
        check(restored.isAvailable() && restored.book().room(ROOM, A).visible(A).isEmpty(), "真实 NBT 重载保留本人删除");
        check(restored.book().room(ROOM, B).visible(B).get(0).message().body().equals(image.body()), "其他人的图片与元数据保留");
        book.leave(ROOM, B);
        check(book.groups(B).isEmpty() && book.room(ROOM, A).messages().get(1).senderName().equals("Bob"), "退出移除索引但旧消息昵称保留");
        denied(GroupStatus.NOT_MEMBER, () -> book.room(ROOM, B).visible(B));
        book.rename(ROOM, A, "新名字"); check(book.room(ROOM, A).name().equals("新名字"), "群主可以改群名");
        for (int i = 0; i < 120; i++) send(book, A, "消息" + i, 1234);
        check(book.room(ROOM, A).messages().size() == 100 && book.room(ROOM, A).sequence() == 122, "有界历史不复用序号");
        check(book.room(ROOM, A).deleted().getOrDefault(A, List.of()).isEmpty(), "容量淘汰同时回收删除标记");
        book.dissolve(ROOM, A); check(book.snapshot().isEmpty() && book.groups(A).isEmpty(), "解散回收所有成员索引");
        var bounded = new GroupBook();
        for (int i = 0; i < GroupLimits.GROUPS_PER_PLAYER; i++) {
            var id = new UUID(3, i); bounded.create(id, new GroupMember(A, "A"), "群" + i, List.of(new GroupMember(B, "B")), player -> true);
            accept(bounded, id, B, "B");
        }
        denied(GroupStatus.LIMIT, () -> bounded.create(new UUID(3, 999), new GroupMember(A, "A"), "超限", List.of(new GroupMember(B, "B")), id -> true));
        check(bounded.groups(A).size() == 32 && bounded.groups(B).size() == 32, "成员群数量有界，拒绝不污染索引");
        var invalid = new CompoundTag(); invalid.putString("rooms", "损坏数据");
        var locked = GroupData.load(invalid);
        check(!locked.isAvailable() && locked.write(new CompoundTag()).equals(invalid), "损坏数据锁定并原样保留，不重建空群覆盖");
        denied(GroupStatus.UNAVAILABLE, locked::book);
        System.out.println("全部通过：" + checks + " 条断言");
    }
}
