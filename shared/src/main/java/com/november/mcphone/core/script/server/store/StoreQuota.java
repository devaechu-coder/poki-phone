package com.november.mcphone.core.script.server.store;

/**
 * 三档存储的配额（施工方案 §17.2、§17.3）。<b>一项都不许放宽。</b>
 *
 * <p>超限一律<b>显式拒绝</b>，不静默截断、不静默丢 key —— 静默的后果是玩家的数据悄悄少一块，
 * 而 App 作者要到用户投诉时才知道。
 */
public final class StoreQuota {

    private StoreQuota() {
    }

    // ---------------------------------------------------------------- local（§17.2）

    /** 每 App 的总量。 */
    public static final int LOCAL_PER_APP = 16 * 1024;

    /** 单个值。 */
    public static final int LOCAL_PER_VALUE = 4 * 1024;

    /** key 的个数。 */
    public static final int LOCAL_KEYS = 64;

    // ---------------------------------------------------------------- shared（§17.3）

    /** 每玩家每 App。 */
    public static final int SHARED_PER_PLAYER_APP = 8 * 1024;

    /** 单个值。 */
    public static final int SHARED_PER_VALUE = 2 * 1024;

    /** key 的个数。 */
    public static final int SHARED_KEYS = 64;

    /** 写入频率，次/秒。 */
    public static final int SHARED_WRITES_PER_SEC = 10;

    /** 每 App 全服总量。 */
    public static final long SHARED_PER_APP_TOTAL = 4L * 1024 * 1024;

    /** 全服总量。 */
    public static final long SHARED_SERVER_TOTAL = 64L * 1024 * 1024;

    /** 超限时给玩家看的本地化键。<b>不是文本</b>。 */
    public static final String KEY_QUOTA = "mcphone.store.quota_exceeded";

    /** 超限就抛它，调用方转成脚本可见的错误码。 */
    public static final class QuotaExceeded extends RuntimeException {
        private final String messageKey;

        public QuotaExceeded(String detail) {
            super(KEY_QUOTA + ": " + detail);
            this.messageKey = KEY_QUOTA;
        }

        public String messageKey() {
            return messageKey;
        }
    }

    /** 一个值本身超不超。 */
    public static void checkValue(String value, int max, String where) {
        int n = value == null ? 0 : value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (n > max) throw new QuotaExceeded(where + " 的单值 " + n + " 字节，上限 " + max);
    }

    /** 加上这一条之后 key 数与总量超不超。 */
    public static void checkAdd(int currentKeys, long currentBytes, boolean newKey,
                                int addedBytes, int maxKeys, long maxBytes, String where) {
        if (newKey && currentKeys >= maxKeys) {
            throw new QuotaExceeded(where + " 的 key 已经 " + currentKeys + " 个，上限 " + maxKeys);
        }
        if (currentBytes + addedBytes > maxBytes) {
            throw new QuotaExceeded(where + " 总量 " + (currentBytes + addedBytes) + " 字节，上限 " + maxBytes);
        }
    }
}
