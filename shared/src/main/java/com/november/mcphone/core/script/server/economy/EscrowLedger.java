package com.november.mcphone.core.script.server.economy;

import com.november.mcphone.api.economy.EscrowId;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * 托管账（施工方案 §22.4）。<b>受益人在创建时定死，之后不可更改。</b>
 *
 * <h2>必须持久化</h2>
 *
 * <b>服务器重启不能把托管中的钱吃掉</b>。生产环境的这一本归 {@link EconomyData} 所有，
 * 与余额同一份世界存档、同一次落盘；启动时扫一遍，超过 {@link #DEFAULT_TIMEOUT_MS} 的自动退款并记流水。
 *
 * <p><b>没有锁，只许主线程碰</b>（{@link CurrencyGateway}）。别在这里加锁当跨线程保证 ——
 * 两个 provider 实例各拿各的锁写同一份账，实测照样丢钱（E25 ③）。
 *
 * <h2>重复结算要认得出来</h2>
 *
 * 已经放过款或退过款的再来一次，返回 {@code ALREADY_SETTLED} 而不是 {@code FAILED}：
 * 调用方据此知道"这笔已经成了"，而不是"出错了要重试"。
 */
public final class EscrowLedger {

    /** 托管超时，默认 7 天（§22.4）。到点自动退款。 */
    public static final long DEFAULT_TIMEOUT_MS = 7L * 24 * 3600 * 1000;

    /**
     * 已结清的留多久。留着是为了重复结算时答得出 {@code ALREADY_SETTLED}；
     * 过了这个期限再来就是 {@code UNKNOWN_ESCROW}。工程常值，不是方案里的数。
     */
    public static final long SETTLED_KEEP_MS = 30L * 24 * 3600 * 1000;

    /** 一笔托管。{@code settledAt} 是结清时刻，没结清是 0。 */
    public record Entry(UUID owner, UUID beneficiary, String currencyId, long amount,
                        long createdAt, boolean settled, long settledAt) {
    }

    private final Map<UUID, Entry> entries = new HashMap<>();
    private final LongSupplier clock;
    private final long timeoutMs;
    private final Runnable onChange;
    private final java.util.function.Function<String, String> lockedReason;

    public EscrowLedger(LongSupplier clock) {
        this(clock, DEFAULT_TIMEOUT_MS);
    }

    public EscrowLedger(LongSupplier clock, long timeoutMs) {
        this(clock, timeoutMs, () -> { }, currencyId -> null);
    }

    /**
     * @param onChange     每次改动之后调一次 —— 落盘靠它标脏，与改动在同一个主线程操作里
     * @param lockedReason 货币 id → 这种货币的账为什么动不了（翻译键）；动得了就是 null
     */
    public EscrowLedger(LongSupplier clock, long timeoutMs, Runnable onChange,
                        java.util.function.Function<String, String> lockedReason) {
        this.clock = clock;
        this.timeoutMs = timeoutMs;
        this.onChange = onChange;
        this.lockedReason = lockedReason;
    }

    /** 这种货币的托管现在动不动得了。动不了返回原因的翻译键（存档读坏了，见 {@link EconomyData}），动得了返回 null。 */
    public String unavailableReasonKey(String currencyId) {
        return lockedReason.apply(currencyId);
    }

    /** 建一笔。钱已经从 owner 身上扣掉了 —— 这里只记账。 */
    public EscrowId create(UUID owner, UUID beneficiary, String currencyId, long amount) {
        UUID id = UUID.randomUUID();
        entries.put(id, new Entry(owner, beneficiary, currencyId, amount, clock.getAsLong(), false, 0));
        onChange.run();
        return new EscrowId(id);
    }

    /** 查。不认识返回 null。 */
    public Entry get(EscrowId id) {
        return id == null ? null : entries.get(id.value());
    }

    /** 标记结算。返回 false 表示这笔已经结过了。 */
    public boolean settle(EscrowId id) {
        Entry e = entries.get(id.value());
        if (e == null || e.settled()) return false;
        entries.put(id.value(), new Entry(e.owner(), e.beneficiary(), e.currencyId(),
                e.amount(), e.createdAt(), true, clock.getAsLong()));
        onChange.run();
        return true;
    }

    /** 还没结算的托管里，这种货币一共押着多少钱。<b>对账要把它算进总量</b>（§22.10）。 */
    public long held(String currencyId) {
        long n = 0;
        for (Entry e : entries.values()) {
            if (!e.settled() && e.currencyId().equals(currencyId)) n += e.amount();
        }
        return n;
    }

    /** 超时的那些（§22.4 默认 7 天）。启动时扫一遍，逐个退款。 */
    public java.util.List<Map.Entry<EscrowId, Entry>> expired() {
        long now = clock.getAsLong();
        java.util.List<Map.Entry<EscrowId, Entry>> out = new java.util.ArrayList<>();
        for (Map.Entry<UUID, Entry> e : entries.entrySet()) {
            Entry v = e.getValue();
            if (!v.settled() && now - v.createdAt() >= timeoutMs) {
                out.add(Map.entry(new EscrowId(e.getKey()), v));
            }
        }
        return out;
    }

    /**
     * 清掉结清已超过 {@link #SETTLED_KEEP_MS} 的条目。启动扫描时调。
     *
     * <p>按结清时刻算，不按建立时刻：服务器连着跑一个多月不重启，开服扫描刚退款结清的那一笔建立时间早就过了期限，
     * 按建立时刻算会在同一趟里被清掉，之后再来就答不出 {@code ALREADY_SETTLED}。
     *
     * @return 清掉几条
     */
    public int pruneSettled() {
        long cutoff = clock.getAsLong() - SETTLED_KEEP_MS;
        int before = entries.size();
        entries.values().removeIf(e -> e.settled() && e.settledAt() < cutoff);
        int n = before - entries.size();
        if (n > 0) onChange.run();
        return n;
    }

    /** 落盘用：全部条目。 */
    public Map<UUID, Entry> snapshot() {
        return Map.copyOf(entries);
    }

    /** 从盘上读回来。 */
    public void restore(Map<UUID, Entry> saved) {
        entries.clear();
        entries.putAll(saved);
    }

    public int size() {
        return entries.size();
    }
}
