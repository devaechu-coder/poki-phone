package com.november.mcphone.feature.chat.group.net;

import java.util.EnumMap;
import java.util.WeakHashMap;

/** 主线程按连接限流，读目录、读历史和写操作分别计时；断线对象可被回收。 */
public final class GroupThrottle {
    public enum Kind { DIRECTORY, HISTORY, DETAILS, READ, TEXT, CREATE, MANAGE, DELETE, IMAGES, UPLOAD, NOTICES }
    private final WeakHashMap<Object, EnumMap<Kind, Long>> times = new WeakHashMap<>();
    public boolean allow(Object connection, Kind kind, long now) {
        long interval = kind == Kind.CREATE ? 10_000 : kind == Kind.UPLOAD ? 2000
                : kind == Kind.TEXT || kind == Kind.DELETE || kind == Kind.MANAGE || kind == Kind.READ ? 250 : 500;
        var record = times.computeIfAbsent(connection, ignored -> new EnumMap<>(Kind.class));
        Long previous = record.get(kind);
        if (previous != null && now >= previous && now - previous < interval) return false;
        record.put(kind, now); return true;
    }
}
