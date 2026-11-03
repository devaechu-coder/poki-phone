package com.november.mcphone.feature.chat.group;

import com.november.mcphone.MCphone;
import com.november.mcphone.core.PhoneSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;

/** 单独的主世界存档，群资料、消息、已读和个人删除一起保存；损坏时保留原文并锁定。 */
public final class GroupData extends PhoneSavedData {
    private final GroupBook book;
    private final CompoundTag unreadable;
    public GroupData() { book = new GroupBook(); unreadable = null; }
    private GroupData(GroupBook book) { this.book = book; unreadable = null; }
    private GroupData(CompoundTag original) { book = new GroupBook(); unreadable = original.copy(); }
    public static GroupData get(MinecraftServer server) {
        return getOrCreate(server, MCphone.MODID + "_groups", GroupData::new, GroupData::load);
    }
    public boolean isAvailable() { return unreadable == null; }
    public GroupBook book() {
        if (!isAvailable()) throw new GroupFault(GroupStatus.UNAVAILABLE);
        return book;
    }
    @Override protected CompoundTag write(CompoundTag tag) {
        if (!isAvailable()) return tag.merge(unreadable.copy());
        // 旧版本不能忽略待同意邀请；格式 3 防止降级后覆盖尚未处理的通知。
        tag.putInt("format", 3);
        tag.put("rooms", GroupRoom.CODEC.listOf().encodeStart(NbtOps.INSTANCE, book.snapshot()).result()
                .orElseThrow(() -> new IllegalStateException("群聊存档写入失败")));
        tag.put("invitations", GroupInvitation.CODEC.listOf().encodeStart(NbtOps.INSTANCE, book.invitationSnapshot()).result()
                .orElseThrow(() -> new IllegalStateException("群邀请存档写入失败")));
        return tag;
    }
    public static GroupData load(CompoundTag tag) {
        try {
            var formatTag = tag.get("format");
            int format = formatTag == null ? -1 : com.mojang.serialization.Codec.INT.parse(NbtOps.INSTANCE, formatTag).result().orElse(-1);
            if (format != 1 && format != 2 && format != 3) throw new IllegalStateException("未知群聊存档格式 " + format);
            var roomCodec = format == 1 ? GroupRoom.LEGACY_CODEC : GroupRoom.CODEC;
            var rooms = roomCodec.listOf().parse(NbtOps.INSTANCE, tag.get("rooms")).result()
                    .orElseThrow(() -> new IllegalStateException("群聊存档读取失败"));
            var invitations = format < 3 ? java.util.List.<GroupInvitation>of()
                    : GroupInvitation.CODEC.listOf().parse(NbtOps.INSTANCE, tag.get("invitations")).result()
                    .orElseThrow(() -> new IllegalStateException("群邀请存档读取失败"));
            return new GroupData(GroupBook.restore(rooms, invitations));
        } catch (RuntimeException error) {
            MCphone.LOGGER.error("群聊存档损坏，群聊暂时锁定；请恢复 mcphone_groups.dat 备份", error);
            return new GroupData(tag);
        }
    }
}
