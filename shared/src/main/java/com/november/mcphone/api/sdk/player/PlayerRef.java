package com.november.mcphone.api.sdk.player;

import java.util.UUID;

/**
 * 跨 App 引用一个玩家（施工方案 §23.3）。
 *
 * <p><b>{@link #uuid} 是唯一身份，{@link #name} 只用于显示</b> —— 玩家会改名，
 * 拿名字当键的表在改名当天就对不上了。
 *
 * <h2>为什么没有 online 这一格</h2>
 *
 * §23.3 的原型是 {@code { uuid, name, online }}。<b>{@code online} 是会变的状态，
 * 不该塞进一个会被存下来的值类型</b>：App 会把 PlayerRef 写进自己的 KV、写进挂单、发进邮件，
 * 存下来的那一刻 {@code online} 就开始撒谎。而 §23.4 明令"删字段"不允许 —— 错了就是永久的。
 * 在不在线现查：{@code ctx.player.isOnline(ref)}。
 *
 * <p><b>脚本不能凭名字构造 PlayerRef</b>：必须由宿主给出（防止靠猜名字定位玩家）。
 * 这条约束落在脚本桥上（S12/S13），这里只定形状。
 *
 * <p>⚠ <b>离线模式服务器的 uuid 是由名字推导的，可伪造</b>（与 §13.5 同一个问题）。
 * 离线服必须在管理界面警告"玩家身份不可信" —— 这不是这个 record 能挡住的事。
 *
 * @param uuid 唯一身份，不为 null
 * @param name 最后一次见到的显示名，只用于显示
 */
public record PlayerRef(UUID uuid, String name) {

    /** 显示名的上限。原版名字 16 个字符，留一倍余量给别的来源。 */
    public static final int MAX_NAME = 32;

    public PlayerRef {
        if (uuid == null) throw new IllegalArgumentException("PlayerRef.uuid 不能为 null");
        if (name == null) name = "";
        if (name.length() > MAX_NAME) name = name.substring(0, MAX_NAME);
    }
}
