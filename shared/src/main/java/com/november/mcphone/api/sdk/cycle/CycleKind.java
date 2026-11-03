package com.november.mcphone.api.sdk.cycle;

/**
 * 周期的粒度（施工方案 §23.3）。
 *
 * <p>标签格式是<b>契约的一部分</b>，不许改：它直接当 §20.1 {@code limit} 守卫的 label 用，
 * 改了格式就等于把所有 App 的限量计数清零一次。
 */
public enum CycleKind {

    /** {@code 2026-09-08}。分界点由 {@code daily_at} 定，默认 04:00 不是 00:00。 */
    DAILY("daily"),

    /** {@code 2026-W37}。ISO 周号，周几开始由 {@code weekly_on} 定。 */
    WEEKLY("weekly"),

    /** {@code 2026-09}。 */
    MONTHLY("monthly");

    /** 脚本侧写的那个字符串，{@code ctx.cycle.label('daily')} 里的那个。 */
    public final String key;

    CycleKind(String key) {
        this.key = key;
    }

    /** 认不出返回 null —— 调用方按参数错处理，不要替它猜一个。 */
    public static CycleKind of(String key) {
        for (CycleKind k : values()) {
            if (k.key.equals(key)) return k;
        }
        return null;
    }
}
