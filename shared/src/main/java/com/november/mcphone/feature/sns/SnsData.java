package com.november.mcphone.feature.sns;

import com.november.mcphone.MCphone;
import com.november.mcphone.core.PhoneSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 포키 SNS 저장소 (주 세계 data/mcphone_sns.dat). 글은 5일 지나면 사진과 함께 지워진다. */
public final class SnsData extends PhoneSavedData {
    public static final long KEEP_MS = 5L * 24 * 60 * 60 * 1000;

    public static final class Comment {
        public final UUID id, author; public final String name, text; public final long time;
        public Comment(UUID id, UUID author, String name, long time, String text) {
            this.id = id; this.author = author; this.name = name; this.time = time; this.text = text;
        }
    }

    public static final class Post {
        public final UUID id, author; public final String name, text; public final long time;
        public UUID image; public int width, height;
        public final Set<UUID> likes = new LinkedHashSet<>();
        public final Set<UUID> reports = new LinkedHashSet<>();
        public final List<Comment> comments = new ArrayList<>();
        /** 신고가 쌓여 운영진 검토함으로 간 글 (피드에서 숨김) */
        public boolean held;
        public Post(UUID id, UUID author, String name, long time, String text) {
            this.id = id; this.author = author; this.name = name; this.time = time; this.text = text;
        }
    }

    /** 최신 글이 앞 */
    private final List<Post> posts = new ArrayList<>();
    private final Map<UUID, Set<UUID>> blocks = new HashMap<>();
    private final Set<UUID> muted = new HashSet<>();

    public static SnsData get(MinecraftServer server) {
        return getOrCreate(server, MCphone.MODID + "_sns", SnsData::new, SnsData::load);
    }

    public List<Post> posts() { return posts; }
    public Post find(UUID id) { for (Post p : posts) if (p.id.equals(id)) return p; return null; }
    public Post findPrefix(String prefix) {
        Post hit = null;
        for (Post p : posts) if (p.id.toString().startsWith(prefix)) { if (hit != null) return null; hit = p; }
        return hit;
    }
    public void add(Post post) { posts.add(0, post); setDirty(); }
    public boolean remove(Post post) { boolean r = posts.remove(post); if (r) setDirty(); return r; }

    public Set<UUID> blocked(UUID viewer) { return blocks.getOrDefault(viewer, Set.of()); }
    public void block(UUID viewer, UUID target, boolean on) {
        if (on) blocks.computeIfAbsent(viewer, k -> new LinkedHashSet<>()).add(target);
        else { var s = blocks.get(viewer); if (s != null) { s.remove(target); if (s.isEmpty()) blocks.remove(viewer); } }
        setDirty();
    }
    public boolean muted(UUID id) { return muted.contains(id); }
    public void mute(UUID id, boolean on) { if (on) muted.add(id); else muted.remove(id); setDirty(); }

    /** 만료된 글을 떼어내 돌려준다 (사진 지우기는 부른 쪽 몫) */
    public List<Post> expire(long now) {
        List<Post> out = new ArrayList<>();
        posts.removeIf(p -> { if (now - p.time > KEEP_MS) { out.add(p); return true; } return false; });
        if (!out.isEmpty()) setDirty();
        return out;
    }

    public Set<UUID> referencedImages() {
        Set<UUID> out = new HashSet<>();
        for (Post p : posts) if (p.image != null) out.add(p.image);
        return out;
    }

    @Override
    protected CompoundTag write(CompoundTag tag) {
        tag.putInt("format", 1);
        ListTag list = new ListTag();
        for (Post p : posts) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", p.id); t.putUUID("author", p.author); t.putString("name", p.name);
            t.putLong("time", p.time); t.putString("text", p.text);
            if (p.image != null) { t.putUUID("image", p.image); t.putInt("w", p.width); t.putInt("h", p.height); }
            t.put("likes", uuids(p.likes)); t.put("reports", uuids(p.reports));
            t.putBoolean("held", p.held);
            ListTag cs = new ListTag();
            for (Comment c : p.comments) {
                CompoundTag ct = new CompoundTag();
                ct.putUUID("id", c.id); ct.putUUID("author", c.author); ct.putString("name", c.name);
                ct.putLong("time", c.time); ct.putString("text", c.text);
                cs.add(ct);
            }
            t.put("comments", cs);
            list.add(t);
        }
        tag.put("posts", list);
        CompoundTag bl = new CompoundTag();
        blocks.forEach((k, v) -> bl.put(k.toString(), uuids(v)));
        tag.put("blocks", bl);
        tag.put("muted", uuids(muted));
        return tag;
    }

    public static SnsData load(CompoundTag tag) {
        SnsData d = new SnsData();
        try {
            for (Tag raw : tag.getList("posts", Tag.TAG_COMPOUND)) {
                CompoundTag t = (CompoundTag) raw;
                Post p = new Post(t.getUUID("id"), t.getUUID("author"), t.getString("name"), t.getLong("time"), t.getString("text"));
                if (t.hasUUID("image")) { p.image = t.getUUID("image"); p.width = t.getInt("w"); p.height = t.getInt("h"); }
                readUuids(t.getList("likes", Tag.TAG_STRING), p.likes);
                readUuids(t.getList("reports", Tag.TAG_STRING), p.reports);
                p.held = t.getBoolean("held");
                for (Tag cr : t.getList("comments", Tag.TAG_COMPOUND)) {
                    CompoundTag ct = (CompoundTag) cr;
                    p.comments.add(new Comment(ct.getUUID("id"), ct.getUUID("author"), ct.getString("name"), ct.getLong("time"), ct.getString("text")));
                }
                d.posts.add(p);
            }
            CompoundTag bl = tag.getCompound("blocks");
            for (String k : bl.getAllKeys()) {
                Set<UUID> s = new LinkedHashSet<>();
                readUuids(bl.getList(k, Tag.TAG_STRING), s);
                d.blocks.put(UUID.fromString(k), s);
            }
            readUuids(tag.getList("muted", Tag.TAG_STRING), d.muted);
        } catch (RuntimeException e) {
            MCphone.LOGGER.error("포키 SNS 저장 파일을 읽다가 일부를 건너뜀", e);
        }
        return d;
    }

    private static ListTag uuids(Set<UUID> set) {
        ListTag l = new ListTag();
        for (UUID u : set) l.add(StringTag.valueOf(u.toString()));
        return l;
    }

    private static void readUuids(ListTag l, Set<UUID> into) {
        for (int i = 0; i < l.size(); i++) {
            try { into.add(UUID.fromString(l.getString(i))); } catch (IllegalArgumentException ignored) {}
        }
    }
}
