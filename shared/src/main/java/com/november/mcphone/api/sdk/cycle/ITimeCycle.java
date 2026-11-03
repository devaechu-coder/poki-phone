package com.november.mcphone.api.sdk.cycle;

/**
 * 所有 App 对"这一周"的共识（施工方案 §23.3）。
 *
 * <p><b>为什么必须统一</b>：A 的"这一周"和 B 的"这一周"不是同一周，玩家就会看到签到 App
 * 已经刷新、而市场 App 的周限额还没到。差别来自时区，而时区是服务器的属性，不是 App 的。
 *
 * <p><b>{@link #label} 直接当 §20.1 {@code limit} 守卫的 label 用</b>，
 * 所有 App 因此自动对齐同一个周期。
 *
 * <p>配置在 {@code mcphone-server.toml} 的 {@code [cycle]} 段：
 * <pre>
 * timezone = "Asia/Shanghai"   必填，留空即拒绝启动
 * daily_at = "04:00"
 * </pre>
 *
 * <p><b>没有 weekly_on</b>：周恒按 ISO，理由见 {@link CycleLabels}。
 *
 * <p><b>时区必填、不许用系统默认</b>：服务器迁机房会让"每日"的分界点悄悄改变，
 * 而玩家只会觉得"今天怎么没刷新"。
 *
 * <p><b>{@code daily_at} 默认 04:00 不是 00:00</b>：0 点在线人数最多，刷新挤在那一刻对服务端不友好，
 * 而且跨午夜还在玩的玩家会觉得"这一天"被提前切断。
 */
public interface ITimeCycle {

    /** {@code daily_at} 的默认值。 */
    String DEFAULT_DAILY_AT = "04:00";

    /** 当前周期的标签，格式见 {@link CycleKind}。 */
    String label(CycleKind kind);

    /**
     * 下一个分界点的毫秒时间戳。
     *
     * <p>脚本侧读到的是<b>十进制字符串</b>：毫秒数早超过 2^53，用数字会静默丢精度（§23.3）。
     */
    long nextBoundary(CycleKind kind);

    /** 服主配的时区 id，如 {@code Asia/Shanghai}。 */
    String timezone();
}
