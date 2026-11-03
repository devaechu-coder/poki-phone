package com.november.mcphone.core.script.server;

import java.util.UUID;

/**
 * "这个玩家现在能不能做这个动作"（施工方案 §15.5 第二条）。
 *
 * <h2>为什么是接口而不是静态调用</h2>
 *
 * §15.9 有一条判据：<b>请求在队列里时 OP 撤销授权 → 落地前被拒，效果没发生</b>。
 * 判的是"落地前重查了没有"，不是"物品真的没进背包"。做成接口，
 * 喂一个"第一次 true、第二次 false"的替身就能在 {@code docs/} 的断言测试里判它；
 * 写成静态调用的话，这条判据永远只能靠人在服务器上试。
 *
 * <p>真正的授权表是 S17 的交付物（§14.4）。本步只定这个形状。
 */
@FunctionalInterface
public interface AuthorityView {

    /** 现在允不允许。<b>每次落地前都要重新问一遍</b>，不许沿用异步计算开始时的答案。 */
    boolean allows(UUID player, String appId, String actionId);
}
