package com.november.mcphone.api.sdk.stats;

/**
 * B 档占位（施工方案 §23.2、§23.5）：玩家统计只读。空接口的理由见 {@code IGroups}。
 *
 * <p>将来装的东西：在线时长、死亡数这类。<b>数值一律按字符串过脚本侧</b> ——
 * 在线时长的毫秒数会超过 2^53（§23.3）。
 */
public interface IStats {
}
