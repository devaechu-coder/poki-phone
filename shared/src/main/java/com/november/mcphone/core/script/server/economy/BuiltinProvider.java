package com.november.mcphone.core.script.server.economy;

import com.november.mcphone.api.economy.Balances;
import com.november.mcphone.api.economy.Currency;
import com.november.mcphone.api.economy.EscrowId;
import com.november.mcphone.api.economy.HoldResult;
import com.november.mcphone.api.economy.ICurrencyProvider;
import com.november.mcphone.api.economy.TxnReason;
import com.november.mcphone.api.economy.TxnResult;

import java.time.Instant;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * 自带的货币提供者（施工方案 §22.7 的 builtin 一行）。余额在世界级存档里（{@link EconomyData}），服主什么都不用装。
 *
 * <h2>五条不变量都在这里落地（§22.9）</h2>
 *
 * <ul>
 *   <li>余额不为负（除非 {@link #allowNegative()}）</li>
 *   <li>超 {@link #maxBalance()} 返回 {@link TxnResult#LIMIT}，<b>不回绕</b></li>
 *   <li>加法用 {@code Math.addExact}，溢出也是 LIMIT</li>
 *   <li><b>{@code amount <= 0} 一律 {@link TxnResult#INVALID}</b> ——
 *       {@code transfer(A, B, -1000)} 若实现成"from 减 amount、to 加 amount"就是从对方账上偷钱</li>
 *   <li>总量守恒（除 mint / burn），由流水对账</li>
 * </ul>
 *
 * 判定全部走 {@link Balances}（S11 定的），不在这里各写一份。
 *
 * <h2>原子性</h2>
 *
 * 余额的"读 → 判断 → 写"要原子，靠的是<b>只在服务端主线程上执行</b>（{@link CurrencyGateway}）。
 * 这把锁只在断言测试多线程直调时起作用，<b>不是跨线程保证</b>：两个实例各拿各的锁写同一份账，
 * 实测照样丢钱（E25 ③）。
 */
public final class BuiltinProvider implements ICurrencyProvider {

    private final Currency currency;
    private final BalanceStore balances;
    private final EscrowLedger escrow;
    private final TxnLog log;
    private final LongSupplier clock;
    private final boolean allowNegative;
    private final long maxBalance;

    private final Object lock = new Object();

    public BuiltinProvider(Currency currency, BalanceStore balances, EscrowLedger escrow,
                           TxnLog log, LongSupplier clock, boolean allowNegative, long maxBalance) {
        this.currency = currency;
        this.balances = balances;
        this.escrow = escrow;
        this.log = log;
        this.clock = clock;
        this.allowNegative = allowNegative;
        this.maxBalance = maxBalance;
    }

    /** 宿主在进入脚本调用前盖章。 */
    public void enterApp(String appId) {
        CallingApp.enter(appId);
    }

    public void leaveApp() {
        CallingApp.leave();
    }

    private String id() {
        return currency.id().toString();
    }

    @Override
    public Currency currency() {
        return currency;
    }

    @Override
    public boolean isAvailable() {
        return lockedKey() == null;
    }

    @Override
    public String unavailableReasonKey() {
        String k = lockedKey();
        return k == null ? "" : k;
    }

    /** 读不到就抛：返回 0 是静默给错数，调用方会以为这个人没钱。 */
    @Override
    public long balance(UUID player) {
        String k = lockedKey();
        if (k != null) throw new CurrencyUnavailableException(k);
        return balances.get(player, id());
    }

    /** 存档读坏了的货币是锁住的（见 {@link EconomyData}）。null = 动得了。 */
    private String lockedKey() {
        String k = balances.unavailableReasonKey(id());
        return k != null ? k : escrow.unavailableReasonKey(id());
    }

    @Override
    public boolean allowNegative() {
        return allowNegative;
    }

    @Override
    public long maxBalance() {
        return maxBalance;
    }

    @Override
    public TxnResult transfer(UUID from, UUID to, long amount, TxnReason reason) {
        // 【早退，不走读-判-写】：两端是同一个人时下面那两个快照是同一个数，
        // 后一笔写会覆盖前一笔，净效果是凭空多出 amount（E25）
        TxnResult parties = Balances.checkParties(from, to);
        if (parties != TxnResult.OK) {
            return record(TxnLog.Kind.TRANSFER, from, to, amount, reason, parties);
        }
        TxnResult bad = Balances.checkAmount(amount);
        if (bad != TxnResult.OK) return record(TxnLog.Kind.TRANSFER, from, to, amount, reason, bad);
        if (lockedKey() != null) return record(TxnLog.Kind.TRANSFER, from, to, amount, reason, TxnResult.UNAVAILABLE);
        synchronized (lock) {
            long a = balances.get(from, id());
            long b = balances.get(to, id());
            TxnResult r = Balances.checkDebit(a, amount, allowNegative);
            if (r != TxnResult.OK) return record(TxnLog.Kind.TRANSFER, from, to, amount, reason, r);
            r = Balances.checkCredit(b, amount, maxBalance);
            if (r != TxnResult.OK) return record(TxnLog.Kind.TRANSFER, from, to, amount, reason, r);
            // 两端在同一把锁里改完：中间态不许存在（§22.3 ④）
            balances.set(from, id(), a - amount);
            balances.set(to, id(), b + amount);
        }
        return record(TxnLog.Kind.TRANSFER, from, to, amount, reason, TxnResult.OK);
    }

    @Override
    public TxnResult mint(UUID to, long amount, TxnReason reason) {
        if (to == null) return record(TxnLog.Kind.MINT, null, to, amount, reason, TxnResult.INVALID);
        TxnResult bad = Balances.checkAmount(amount);
        if (bad != TxnResult.OK) return record(TxnLog.Kind.MINT, null, to, amount, reason, bad);
        if (lockedKey() != null) return record(TxnLog.Kind.MINT, null, to, amount, reason, TxnResult.UNAVAILABLE);
        synchronized (lock) {
            long b = balances.get(to, id());
            TxnResult r = Balances.checkCredit(b, amount, maxBalance);
            if (r != TxnResult.OK) return record(TxnLog.Kind.MINT, null, to, amount, reason, r);
            balances.set(to, id(), b + amount);
        }
        return record(TxnLog.Kind.MINT, null, to, amount, reason, TxnResult.OK);
    }

    @Override
    public TxnResult burn(UUID from, long amount, TxnReason reason) {
        if (from == null) return record(TxnLog.Kind.BURN, from, null, amount, reason, TxnResult.INVALID);
        TxnResult bad = Balances.checkAmount(amount);
        if (bad != TxnResult.OK) return record(TxnLog.Kind.BURN, from, null, amount, reason, bad);
        if (lockedKey() != null) return record(TxnLog.Kind.BURN, from, null, amount, reason, TxnResult.UNAVAILABLE);
        synchronized (lock) {
            long a = balances.get(from, id());
            TxnResult r = Balances.checkDebit(a, amount, allowNegative);
            if (r != TxnResult.OK) return record(TxnLog.Kind.BURN, from, null, amount, reason, r);
            balances.set(from, id(), a - amount);
        }
        return record(TxnLog.Kind.BURN, from, null, amount, reason, TxnResult.OK);
    }

    @Override
    public HoldResult hold(UUID from, UUID beneficiary, long amount, TxnReason reason) {
        if (from == null || beneficiary == null) {
            record(TxnLog.Kind.HOLD, from, beneficiary, amount, reason, TxnResult.INVALID);
            return HoldResult.fail(TxnResult.INVALID);
        }
        TxnResult bad = Balances.checkAmount(amount);
        if (bad != TxnResult.OK) {
            record(TxnLog.Kind.HOLD, from, beneficiary, amount, reason, bad);
            return HoldResult.fail(bad);
        }
        if (lockedKey() != null) {
            record(TxnLog.Kind.HOLD, from, beneficiary, amount, reason, TxnResult.UNAVAILABLE);
            return HoldResult.fail(TxnResult.UNAVAILABLE);
        }
        EscrowId id;
        synchronized (lock) {
            long a = balances.get(from, id());
            TxnResult r = Balances.checkDebit(a, amount, allowNegative);
            if (r != TxnResult.OK) {
                record(TxnLog.Kind.HOLD, from, beneficiary, amount, reason, r);
                return HoldResult.fail(r);
            }
            // 当场扣款，受益人此刻定死、之后不可更改（§22.3 ⑤）
            balances.set(from, id(), a - amount);
            id = escrow.create(from, beneficiary, id(), amount);
        }
        record(TxnLog.Kind.HOLD, from, beneficiary, amount, reason, TxnResult.OK);
        return HoldResult.ok(id);
    }

    @Override
    public TxnResult release(EscrowId id, TxnReason reason) {
        return settle(id, reason, true);
    }

    @Override
    public TxnResult refund(EscrowId id, TxnReason reason) {
        return settle(id, reason, false);
    }

    /** 放款给受益人、或者退给原主。<b>方向是创建时定死的，这里只能二选一，不能指定第三方。</b> */
    private TxnResult settle(EscrowId id, TxnReason reason, boolean toBeneficiary) {
        TxnLog.Kind kind = toBeneficiary ? TxnLog.Kind.RELEASE : TxnLog.Kind.REFUND;
        // 锁住的货币，它的托管条目根本没读进来 —— 先判锁，否则会答成 UNKNOWN_ESCROW，调用方以为号是假的
        if (lockedKey() != null) return record(kind, null, null, 0, reason, TxnResult.UNAVAILABLE);
        EscrowLedger.Entry e = escrow.get(id);
        if (e == null) return record(kind, null, null, 0, reason, TxnResult.UNKNOWN_ESCROW);
        // 托管号要认货币，否则就是拿 A 币的号在 B 币上放款（E25）
        TxnResult wrongCurrency = Balances.checkEscrowCurrency(e.currencyId(), id());
        if (wrongCurrency != TxnResult.OK) {
            return record(kind, e.owner(), e.beneficiary(), e.amount(), reason, wrongCurrency);
        }
        if (e.settled()) return record(kind, e.owner(), e.beneficiary(), e.amount(), reason, TxnResult.ALREADY_SETTLED);

        UUID target = toBeneficiary ? e.beneficiary() : e.owner();
        synchronized (lock) {
            long b = balances.get(target, id());
            TxnResult r = Balances.checkCredit(b, e.amount(), maxBalance);
            if (r != TxnResult.OK) return record(kind, e.owner(), target, e.amount(), reason, r);
            if (!escrow.settle(id)) {
                return record(kind, e.owner(), target, e.amount(), reason, TxnResult.ALREADY_SETTLED);
            }
            balances.set(target, id(), b + e.amount());
        }
        return record(kind, e.owner(), target, e.amount(), reason, TxnResult.OK);
    }

    /** 每一笔都进流水，<b>失败的也进</b> —— 失败的尝试正是查"谁在试探"的依据（§22.3 ⑥）。 */
    private TxnResult record(TxnLog.Kind kind, UUID from, UUID to, long amount,
                             TxnReason reason, TxnResult result) {
        if (log != null) {
            log.append(Instant.ofEpochMilli(clock.getAsLong()), id(), kind, from, to,
                    amount, CallingApp.current(), reason, result);
        }
        return result;
    }
}
