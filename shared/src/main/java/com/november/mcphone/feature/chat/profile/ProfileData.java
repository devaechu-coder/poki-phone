package com.november.mcphone.feature.chat.profile;

import com.mojang.serialization.Codec;
import com.november.mcphone.MCphone;
import com.november.mcphone.core.PhoneSavedData;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;

/** 介绍保存在当前服务端主世界；异常存档保留原文，禁止空账本覆盖。 */
public final class ProfileData extends PhoneSavedData {
    private static final Codec<java.util.Map<java.util.UUID, String>> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING);
    private final ProfileBook book;
    private final CompoundTag unreadable;
    public ProfileData() { this(new ProfileBook(), null); }
    private ProfileData(ProfileBook book, CompoundTag unreadable) { this.book = book; this.unreadable = unreadable; }
    public static ProfileData get(MinecraftServer server) {
        return getOrCreate(server, MCphone.MODID + "_profiles", ProfileData::new, ProfileData::load);
    }
    public ProfileBook book() {
        if (unreadable != null) throw new IllegalStateException("个人资料存档不可用");
        return book;
    }
    @Override protected CompoundTag write(CompoundTag tag) {
        if (unreadable != null) return tag.merge(unreadable.copy());
        tag.putInt("format", 1);
        tag.put("introductions", CODEC.encodeStart(NbtOps.INSTANCE, book.snapshot()).result().orElseThrow());
        return tag;
    }
    public static ProfileData load(CompoundTag tag) {
        try {
            if (Codec.INT.parse(NbtOps.INSTANCE, tag.get("format")).result().orElse(-1) != 1)
                throw new IllegalArgumentException("未知资料格式");
            return new ProfileData(ProfileBook.restore(CODEC.parse(NbtOps.INSTANCE, tag.get("introductions")).result().orElseThrow()), null);
        } catch (RuntimeException error) {
            MCphone.LOGGER.error("个人资料存档损坏，保留原文并锁定；请恢复 mcphone_profiles.dat 备份", error);
            return new ProfileData(new ProfileBook(), tag.copy());
        }
    }
}
