package com.november.mcphone.core.script.server.economy;

import java.util.Map;
import java.util.UUID;

/**
 * builtin 提供者把余额存在哪（施工方案 §22.7）。
 *
 * <p>生产环境的实现是 {@link EconomyData}：世界级存档，按 UUID 索引 ——
 * 余额不能挂在玩家身上，离线玩家没有那份数据，而收款方离线照样要收得到（§22.4）。
 * 断言测试喂一个内存实现，就能把 §22.9 的五条不变量与守恒逐条判掉。
 */
public interface BalanceStore {

    long get(UUID player, String currencyId);

    void set(UUID player, String currencyId, long value);

    /** 对账要把所有玩家的余额加起来（§22.10）。离线玩家也要算进去。 */
    Map<UUID, Long> all(String currencyId);

    /**
     * 这种货币的账现在动不动得了。动不了返回原因的翻译键，动得了返回 null。
     *
     * <p>存档读坏了的那种货币要锁住：拿一本空账接着记，下次保存就把原来的账盖掉了（见 {@link EconomyData}）。
     */
    default String unavailableReasonKey(String currencyId) {
        return null;
    }
}
