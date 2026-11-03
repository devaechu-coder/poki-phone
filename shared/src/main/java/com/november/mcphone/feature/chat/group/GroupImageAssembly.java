package com.november.mcphone.feature.chat.group;

import com.november.mcphone.feature.chat.ChatImage;
import com.november.mcphone.feature.chat.group.net.GroupCommand;
import java.util.Arrays;
import java.util.UUID;
import java.util.WeakHashMap;

/** 每条连接仅一份群上传；群 ID、独立票据、元数据和顺序必须全匹配。 */
public final class GroupImageAssembly {
    public record Complete(GroupCommand.Upload header, byte[] png) {
        public Complete { png = png.clone(); }
        @Override public byte[] png() { return png.clone(); }
    }
    private static final class Session {
        final GroupCommand.Upload header;
        final byte[] bytes;
        final long started;
        int size, next;
        Session(GroupCommand.Upload header, int limit, long now) { this.header = header; bytes = new byte[limit]; started = now; }
    }
    private final WeakHashMap<Object, Session> pending = new WeakHashMap<>();
    public Complete accept(Object connection, GroupCommand.Upload chunk, int limit, long now) {
        if (chunk.width() < 1 || chunk.width() > ChatImage.MAX_SIDE || chunk.height() < 1 || chunk.height() > ChatImage.MAX_SIDE
                || chunk.frames() < 1 || chunk.frames() > ChatImage.MAX_FRAMES
                || (chunk.frames() > 1 && (chunk.frameMs() < 20 || chunk.frameMs() > 5000))
                || limit < 1 || limit > ChatImage.MAX_BYTES_CEILING) { pending.remove(connection); return null; }
        if (chunk.index() == 0) pending.put(connection, new Session(chunk, limit, now));
        var session = pending.get(connection); if (session == null) return null;
        var first = session.header; byte[] data = chunk.data();
        if (!first.group().equals(chunk.group()) || !first.request().equals(chunk.request()) || first.count() != chunk.count()
                || first.width() != chunk.width() || first.height() != chunk.height() || first.frames() != chunk.frames()
                || first.frameMs() != chunk.frameMs() || chunk.index() != session.next || now - session.started > ChatImage.UPLOAD_TIMEOUT_MS
                || now < session.started || data.length == 0 || session.size + data.length > session.bytes.length) {
            pending.remove(connection); return null;
        }
        System.arraycopy(data, 0, session.bytes, session.size, data.length); session.size += data.length; session.next++;
        if (session.next < first.count()) return null;
        pending.remove(connection); return new Complete(first, Arrays.copyOf(session.bytes, session.size));
    }
}
