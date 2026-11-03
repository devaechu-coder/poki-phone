package com.november.mcphone.feature.chat.group.net;

/** 客户端群请求策略。没有游戏类型，页面和票据缓存共用，服务端限流仍独立裁定。 */
public final class GroupClientPolicy {
    private GroupClientPolicy() {}
    public static final long REFRESH_INTERVAL_MS = 3_000L;
    public static final long OPERATION_TIMEOUT_MS = 15_000L;
    public static final int MAX_PENDING_OPERATIONS = 16;
}
