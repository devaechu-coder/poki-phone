package com.november.mcphone.feature.chat.group;

import com.november.mcphone.feature.chat.ChatMessage;
import com.november.mcphone.feature.chat.ImageBody;
import java.util.*;
import net.minecraft.nbt.CompoundTag;

/** 真实邀请、同意/拒绝、批次原子性、入群边界、失效与存档迁移；不绕过被邀请者身份。 */
public final class GroupInviteTest {
    private static int checks;
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3), D = new UUID(0, 4), G = new UUID(1, 1);
    private static GroupMember member(UUID id) { return new GroupMember(id, "玩家" + id.getLeastSignificantBits()); }
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    private static void denied(GroupStatus status, Runnable action) {
        try { action.run(); throw new AssertionError("应拒绝 " + status); }
        catch (GroupFault error) { check(error.status() == status, "拒绝类型 " + status); }
    }
    private static GroupInviteNotice invitation(GroupBook book, UUID player, UUID group, long now) {
        return book.notices(player, now).stream().filter(i -> i.group().equals(group)).findFirst().orElseThrow();
    }
    private static void accept(GroupBook book, UUID player, UUID group, long now) {
        var invitation = invitation(book, player, group, now);
        book.respond(group, invitation.id(), member(player), true, (a, b) -> true, now);
    }
    public static void main(String[] args) {
        var data = new GroupData(); var book = data.book();
        book.create(G, member(A), "群聊", List.of(member(B)), id -> true, 0);
        check(book.room(G, A).members().size() == 1 && book.groups(B).isEmpty(), "选好友建群不会直接把好友加入");
        var initial = invitation(book, B, G, 1);
        check(book.notices(C, 1).isEmpty() && book.invitationCount() == 1, "通知仅属于被邀请者");
        denied(GroupStatus.NOT_MEMBER, () -> book.room(G, B));
        denied(GroupStatus.NOT_INVITED, () -> book.respond(G, initial.id(), member(C), true, (a, b) -> true, 1));
        denied(GroupStatus.NOT_INVITED, () -> book.respond(new UUID(9, 9), initial.id(), member(B), true, (a, b) -> true, 1));
        var first = book.append(G, A, "A", ChatMessage.text(A, "同意前的消息", 1));
        accept(book, B, G, 2);
        check(book.groups(B).size() == 1 && book.room(G, B).visible(B).isEmpty(), "本人同意后建立索引，历史边界取同意时的序号");
        denied(GroupStatus.INVITATION_GONE, () -> book.respond(G, initial.id(), member(B), true, (a, b) -> true, 3));
        var before = book.room(G, A);
        denied(GroupStatus.NOT_MEMBER, () -> book.invite(G, C, List.of(member(D)), id -> true, 3));
        denied(GroupStatus.NOT_FRIEND, () -> book.invite(G, B, List.of(member(C), member(D)), C::equals, 3));
        check(book.room(G, A) == before && book.notices(C, 3).isEmpty() && book.notices(D, 3).isEmpty(), "好友校验失败整批回滚，不留下第一位邀请");
        denied(GroupStatus.INVALID, () -> book.invite(G, A, List.of(member(C), member(C)), id -> true, 3));
        denied(GroupStatus.INVALID, () -> book.invite(G, A, List.of(), id -> true, 3));
        book.invite(G, B, List.of(member(C)), id -> true, 4);
        var rejected = invitation(book, C, G, 4);
        check(book.room(G, A) == before && book.groups(C).isEmpty(), "普通成员可以邀请离线好友，实际成员数和版本不提前变化");
        book.respond(G, rejected.id(), member(C), false, (a, b) -> false, 5);
        check(book.groups(C).isEmpty() && book.notices(C, 5).isEmpty(), "本人可拒绝，即使好友关系已变，也不取得读取权限");
        denied(GroupStatus.INVITATION_GONE, () -> book.respond(G, rejected.id(), member(C), true, (a, b) -> true, 5));
        book.invite(G, B, List.of(member(C)), id -> true, 6);
        var offered = invitation(book, C, G, 6);
        book.invite(G, A, List.of(member(C)), id -> true, 7);
        check(invitation(book, C, G, 7).id().equals(offered.id()) && book.invitationCount() == 1, "多人重复邀请同一个人不重复通知或延长有效期");
        book.append(G, B, "B", new ChatMessage(B, 8, new ImageBody(D, 16, 16, 1, 0)));
        book.append(G, A, "A", ChatMessage.text(A, "等待同意期间", 9));
        denied(GroupStatus.NOT_MEMBER, () -> book.room(G, C));
        denied(GroupStatus.NOT_FRIEND, () -> book.respond(G, offered.id(), member(C), true, (a, b) -> false, 10));
        check(book.notices(C, 10).size() == 1 && book.groups(C).isEmpty(), "同意时重新检查好友关系，失败保留待处理邀请");
        var pendingSaved = GroupData.load(data.write(new CompoundTag()));
        check(pendingSaved.isAvailable() && invitation(pendingSaved.book(), C, G, 10).id().equals(offered.id()), "格式 3 真实 NBT 重载保留离线待处理邀请");
        accept(pendingSaved.book(), C, G, 11);
        check(pendingSaved.book().room(G, C).visible(C).isEmpty(), "重启后的邀请仍须本人同意，且不能读取等待期间的文字和图片");
        accept(book, C, G, 11); var joined = book.room(G, C);
        check(joined.visible(C).isEmpty() && joined.unread(C) == 0 && GroupSummary.of(joined, C).last().isEmpty(), "同意后预览与未读不包含之前内容");
        check(!book.hide(G, C, first.message().id()), "猜测旧消息 ID 不能删除不可见记录");
        check(book.room(G, A).visible(A).size() == 3 && book.room(G, B).visible(B).size() == 2, "已有成员的历史保留各自入群边界");
        book.invite(G, A, List.of(member(C)), id -> false, 12);
        check(book.room(G, A) == joined && book.invitationCount() == 0, "邀请现有成员为无操作，不改成员版本和入群时间");
        var next = book.append(G, A, "A", ChatMessage.text(A, "欢迎", 13));
        check(book.room(G, C).visible(C).equals(List.of(next)) && book.room(G, C).unread(C) == 1, "同意后的新消息可见并计未读");
        book.rename(G, A, "新群名"); book.markRead(G, C, 4); book.hide(G, B, next.message().id());
        var restored = GroupData.load(data.write(new CompoundTag()));
        check(restored.isAvailable() && restored.book().groups(C).size() == 1 && restored.book().room(G, C).visible(C).equals(List.of(next)), "改名、已读、个人删除和重载都保留同意边界");
        book.invite(G, C, List.of(member(D)), id -> true, 14);
        check(invitation(book, D, G, 14).name().equals("新群名"), "群通知显示当前群名");
        book.leave(G, C);
        check(book.notices(D, 15).isEmpty(), "邀请人离群时取消其尚未被接受的邀请");
        book.invite(G, A, List.of(member(C)), id -> true, 16);
        book.append(G, B, "B", ChatMessage.text(B, "退出期间", 17));
        accept(book, C, G, 18);
        check(book.room(G, C).visible(C).isEmpty() && book.room(G, C).joinedAfter().get(C) == 5, "重新入群仍需同意，从新时间点开始");
        book.invite(G, A, List.of(member(D)), id -> true, 19);
        var expiring = invitation(book, D, G, 19);
        check(book.notices(D, expiring.expiresAt() - 1).size() == 1, "有效期末仍可处理");
        denied(GroupStatus.INVITATION_GONE, () -> book.respond(G, expiring.id(), member(D), true, (a, b) -> true, expiring.expiresAt()));
        check(book.expireInvitations(expiring.expiresAt()) == 1 && book.notices(D, expiring.expiresAt()).isEmpty(), "到期不能同意并回收两个索引");
        book.invite(G, A, List.of(member(D)), id -> true, expiring.expiresAt() + 1);
        book.dissolve(G, A);
        check(book.groups(C).isEmpty() && book.groups(B).isEmpty() && book.invitationCount() == 0, "解散回收成员与全部待处理邀请");

        var oldData = new GroupData(); oldData.book().create(G, member(A), "旧群", List.of(member(B)), id -> true, 0);
        accept(oldData.book(), B, G, 1); oldData.book().append(G, B, "B", ChatMessage.text(B, "旧数据", 10));
        for (int version : new int[]{1, 2}) {
            var legacy = oldData.write(new CompoundTag()); legacy.putInt("format", version); legacy.remove("invitations");
            var migrated = GroupData.load(legacy);
            check(migrated.isAvailable() && migrated.book().room(G, A).visible(A).size() == 1 && migrated.book().invitationCount() == 0, "旧格式 " + version + " 保留已入群成员及历史");
        }
        var broken = oldData.write(new CompoundTag()); broken.remove("invitations");
        var locked = GroupData.load(broken);
        check(!locked.isAvailable() && locked.write(new CompoundTag()).equals(broken), "格式 3 缺邀请字段时保留原文并锁定，不静默清空邀请");
        for (int version : new int[]{1, 2, 3}) {
            var missingBoundary = oldData.write(new CompoundTag()); missingBoundary.putInt("format", version);
            var savedRooms = (net.minecraft.nbt.ListTag) missingBoundary.get("rooms");
            ((CompoundTag) savedRooms.get(0)).remove("joined_after");
            var loadedBoundary = GroupData.load(missingBoundary);
            if (version == 1) {
                check(loadedBoundary.isAvailable() && loadedBoundary.book().room(G, B).visible(B).size() == 1,
                        "真实格式 1 缺入群边界仍保留旧成员历史");
            } else {
                check(!loadedBoundary.isAvailable(), "格式 " + version + " 缺历史边界不能放宽可见性");
                check(loadedBoundary.write(new CompoundTag()).equals(missingBoundary), "边界损坏原文保留");
            }
        }
        var malformedBoundary = oldData.write(new CompoundTag());
        ((CompoundTag) ((net.minecraft.nbt.ListTag) malformedBoundary.get("rooms")).get(0)).putString("joined_after", "错误类型");
        var malformedLocked = GroupData.load(malformedBoundary);
        check(!malformedLocked.isAvailable() && malformedLocked.write(new CompoundTag()).equals(malformedBoundary),
                "新版历史边界类型错误同样保留并锁定");

        var quota = new GroupBook(); quota.create(G, member(A), "目标群", List.of(member(B)), id -> true, 0); accept(quota, B, G, 1);
        quota.invite(G, A, List.of(member(C)), id -> true, 2);
        for (int i = 0; i < GroupLimits.GROUPS_PER_PLAYER; i++) quota.create(new UUID(2, i), member(C), "满额", List.of(member(D)), id -> true, 3);
        var untouched = quota.room(G, A); var overQuota = invitation(quota, C, G, 4);
        denied(GroupStatus.LIMIT, () -> quota.respond(G, overQuota.id(), member(C), true, (a, b) -> true, 4));
        check(quota.room(G, A) == untouched && quota.groups(C).size() == 32, "邀请后个人群数达上限，同意时拒绝且不污染成员索引");
        quota.respond(G, overQuota.id(), member(C), false, (a, b) -> true, 5);
        denied(GroupStatus.LIMIT, () -> quota.invite(G, A, List.of(member(new UUID(0, 8)), member(C)), id -> true, 5));
        check(quota.notices(new UUID(0, 8), 5).isEmpty(), "批次后面的成员超限，前面的邀请也不落地");

        var crowded = new GroupBook(); var people = new ArrayList<GroupMember>();
        for (int i = 2; i <= GroupLimits.MEMBERS; i++) people.add(member(new UUID(0, i)));
        crowded.create(G, member(A), "满群", people, id -> true, 0);
        denied(GroupStatus.LIMIT, () -> crowded.invite(G, A, List.of(member(new UUID(0, 999))), id -> true, 1));
        check(crowded.room(G, A).members().size() == 1 && crowded.invitationCount() == 127, "待处理邀请预留人数容量，不能超额发送");
        for (var person : people) accept(crowded, person.id(), G, 2);
        check(crowded.room(G, A).members().size() == 128 && crowded.invitationCount() == 0, "所有被邀请者本人同意后才形成满群");

        var inbox = new GroupBook();
        for (int i = 0; i < GroupLimits.INVITATIONS_PER_PLAYER; i++) inbox.create(new UUID(3, i), member(new UUID(0, 1000 + i)), "通知", List.of(member(C)), id -> true, 0);
        denied(GroupStatus.LIMIT, () -> inbox.create(new UUID(3, 999), member(new UUID(0, 9999)), "超额", List.of(member(C)), id -> true, 1));
        check(inbox.notices(C, 1).size() == 64 && inbox.groups(new UUID(0, 9999)).isEmpty(), "收件箱配额失败不留下只有群主的残余群");
        System.out.println("全部通过：" + checks + " 条断言");
    }
}
