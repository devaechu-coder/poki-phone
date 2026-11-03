package com.november.mcphone.feature.sns.net;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 포키 SNS 프로토콜. 요청 하나에 답 하나(티켓 = request).
 *
 * 사진은 채팅 사진 파이프라인을 그대로 탄다: 대상 UUID 자리에 {@link #PEER} 를 넣어 올리면
 * 서버가 채팅 대신 SNS 로 받는다. 글을 먼저 보내(POST, withImage=true) 자리를 잡고,
 * 사진이 다 올라오면 서버가 같은 request 로 완성된 글을 돌려준다.
 */
public final class SnsWire {
    private SnsWire() {}

    /** 채팅 사진 업로드·다운로드에서 "이건 SNS" 를 뜻하는 예약 대상 */
    public static final UUID PEER = new UUID(0x506F6B69536E7300L, 0x0000000000000001L);
    private static final UUID NONE = new UUID(0L, 0L);

    public static final int MAX_TEXT = 300;
    public static final int MAX_COMMENT = 200;
    public static final int NAME_LENGTH = 32;
    public static final int PAGE_SIZE = 8;
    public static final int MAX_COMMENTS_SENT = 60;

    public enum Kind { FEED, DETAIL, POST, LIKE, COMMENT, DELETE_POST, DELETE_COMMENT, REPORT, BLOCK, UNBLOCK, MINE, BLOCKS }

    public enum Status {
        OK, PENDING, FORBIDDEN, INVALID, BUSY, TOO_FAST, PROFANITY, DUPLICATE, GONE, MUTED, NO_IMAGE, UNAVAILABLE;
        public String key() { return "mcphone.sns.status." + name().toLowerCase(java.util.Locale.ROOT); }
    }

    public record Query(UUID request, Kind kind, UUID post, UUID target, String text, boolean flag, int page) {
        public Query {
            Objects.requireNonNull(request); Objects.requireNonNull(kind);
            post = post == null ? NONE : post; target = target == null ? NONE : target;
            text = text == null ? "" : text;
            if (text.length() > MAX_TEXT || page < 0 || page > 1000) throw new IllegalArgumentException("SNS 요청 형식 오류");
        }
        public static Query of(Kind kind) { return new Query(UUID.randomUUID(), kind, null, null, "", false, 0); }
    }

    public record Comment(UUID id, UUID author, String name, long time, String text, boolean mine) {}

    public record Post(UUID id, UUID author, String name, long time, String text,
                       UUID image, int width, int height, int likes, boolean liked,
                       int commentCount, boolean mine, List<Comment> comments) {
        public boolean hasImage() { return image != null && !image.equals(NONE); }
    }

    public record Reply(UUID request, Kind kind, Status status, int page, boolean more, List<Post> posts) {
        public Reply {
            Objects.requireNonNull(request); Objects.requireNonNull(kind); Objects.requireNonNull(status);
            posts = posts == null ? List.of() : List.copyOf(posts);
        }
        public static Reply of(Query q, Status status) { return new Reply(q.request(), q.kind(), status, q.page(), false, List.of()); }
    }

    public static boolean none(UUID id) { return id == null || id.equals(NONE); }

    public static void writeQuery(Query q, FriendlyByteBuf b) {
        b.writeUUID(q.request()); b.writeVarInt(q.kind().ordinal()); b.writeUUID(q.post()); b.writeUUID(q.target());
        b.writeUtf(q.text(), MAX_TEXT * 4); b.writeBoolean(q.flag()); b.writeVarInt(q.page());
    }

    public static Query readQuery(FriendlyByteBuf b) {
        UUID request = b.readUUID();
        Kind kind = enumOf(Kind.values(), b.readVarInt());
        UUID post = b.readUUID(), target = b.readUUID();
        String text = b.readUtf(MAX_TEXT * 4);
        if (text.length() > MAX_TEXT) text = text.substring(0, MAX_TEXT);
        return new Query(request, kind, post, target, text, b.readBoolean(), b.readVarInt());
    }

    public static void writeReply(Reply r, FriendlyByteBuf b) {
        b.writeUUID(r.request()); b.writeVarInt(r.kind().ordinal()); b.writeVarInt(r.status().ordinal());
        b.writeVarInt(r.page()); b.writeBoolean(r.more());
        b.writeVarInt(r.posts().size());
        for (Post p : r.posts()) {
            b.writeUUID(p.id()); b.writeUUID(p.author()); b.writeUtf(p.name(), NAME_LENGTH * 4); b.writeLong(p.time());
            b.writeUtf(p.text(), MAX_TEXT * 4);
            b.writeUUID(p.hasImage() ? p.image() : NONE); b.writeVarInt(p.width()); b.writeVarInt(p.height());
            b.writeVarInt(p.likes()); b.writeBoolean(p.liked()); b.writeVarInt(p.commentCount()); b.writeBoolean(p.mine());
            b.writeVarInt(p.comments().size());
            for (Comment c : p.comments()) {
                b.writeUUID(c.id()); b.writeUUID(c.author()); b.writeUtf(c.name(), NAME_LENGTH * 4); b.writeLong(c.time());
                b.writeUtf(c.text(), MAX_COMMENT * 4); b.writeBoolean(c.mine());
            }
        }
    }

    public static Reply readReply(FriendlyByteBuf b) {
        UUID request = b.readUUID();
        Kind kind = enumOf(Kind.values(), b.readVarInt());
        Status status = enumOf(Status.values(), b.readVarInt());
        int page = b.readVarInt(); boolean more = b.readBoolean();
        int n = b.readVarInt();
        if (n < 0 || n > 64) throw new io.netty.handler.codec.DecoderException("SNS 글 개수 오류 " + n);
        List<Post> posts = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            UUID id = b.readUUID(), author = b.readUUID(); String name = b.readUtf(NAME_LENGTH * 4); long time = b.readLong();
            String text = b.readUtf(MAX_TEXT * 4);
            UUID image = b.readUUID(); int w = b.readVarInt(), h = b.readVarInt();
            int likes = b.readVarInt(); boolean liked = b.readBoolean(); int count = b.readVarInt(); boolean mine = b.readBoolean();
            int cn = b.readVarInt();
            if (cn < 0 || cn > MAX_COMMENTS_SENT) throw new io.netty.handler.codec.DecoderException("SNS 댓글 개수 오류 " + cn);
            List<Comment> comments = new ArrayList<>(cn);
            for (int j = 0; j < cn; j++)
                comments.add(new Comment(b.readUUID(), b.readUUID(), b.readUtf(NAME_LENGTH * 4), b.readLong(), b.readUtf(MAX_COMMENT * 4), b.readBoolean()));
            posts.add(new Post(id, author, name, time, text, none(image) ? null : image, w, h, likes, liked, count, mine, comments));
        }
        return new Reply(request, kind, status, page, more, posts);
    }

    private static <E> E enumOf(E[] values, int i) {
        if (i < 0 || i >= values.length) throw new io.netty.handler.codec.DecoderException("SNS 열거값 오류 " + i);
        return values[i];
    }
}
