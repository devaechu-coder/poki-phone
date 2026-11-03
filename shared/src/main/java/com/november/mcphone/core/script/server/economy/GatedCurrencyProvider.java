package com.november.mcphone.core.script.server.economy;

import com.november.mcphone.api.economy.Currency;
import com.november.mcphone.api.economy.EscrowId;
import com.november.mcphone.api.economy.HoldResult;
import com.november.mcphone.api.economy.ICurrencyProvider;
import com.november.mcphone.api.economy.TxnReason;
import com.november.mcphone.api.economy.TxnResult;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 注册表交出去的都是这一层（{@link CurrencyRegistry}）：provider 的每个方法都经 {@link CurrencyGateway} 在主线程上执行。
 * 调用方拿不到里面那一个 —— 线程模型只有一份，不许有第二条直达 provider 的路。
 *
 * <p>网关拒掉的调用 provider 根本没见到，所以流水里没有这一行；原因在 {@link #unavailableReasonKey()}
 * （这条线程上一次被拒的原因）与网关的限流日志里。
 *
 * <p><b>会动钱的方法只在 provider 还没开始跑时才给 UNAVAILABLE</b>；provider 跑起来之后抛出的 {@link CurrencyUnavailableException}
 * （它自己抛的，或者它里面又调了别的网关被拒）原样抛出去 —— 它可能已经动了一半（外部钱包先记上钱再抛），
 * 变成 UNAVAILABLE 调用方就会当"没动"去重试。按"跑没跑"判断，不按异常从哪来：异常对象会穿过嵌套调用。
 */
public final class GatedCurrencyProvider implements ICurrencyProvider {

    /** 这条线程上、这一种货币上一次被网关拒的原因。每个实例一份：共用的话 A 币被拒，问 B 币也拿到 A 币的原因 */
    private final ThreadLocal<String> lastRefusal = new ThreadLocal<>();

    private final ICurrencyProvider inner;
    private final CurrencyGateway gateway;

    public GatedCurrencyProvider(ICurrencyProvider inner, CurrencyGateway gateway) {
        this.inner = inner;
        this.gateway = gateway;
    }

    /** 注册表用：查重，以及拆掉别的网关的包装、换成自己的。 */
    ICurrencyProvider inner() {
        return inner;
    }

    // 元数据与构造时定死的两个常量：不可变，不必回主线程

    @Override
    public Currency currency() {
        return inner.currency();
    }

    @Override
    public boolean allowNegative() {
        return inner.allowNegative();
    }

    @Override
    public long maxBalance() {
        return inner.maxBalance();
    }

    @Override
    public boolean isAvailable() {
        try {
            boolean ok = gateway.call(inner::isAvailable);
            lastRefusal.remove();
            return ok;
        } catch (CurrencyUnavailableException e) {
            lastRefusal.set(e.reasonKey());
            return false;
        }
    }

    /** 这条线程上一次被网关拒的原因优先；没被拒就问 provider 自己。 */
    @Override
    public String unavailableReasonKey() {
        String refused = lastRefusal.get();
        if (refused != null) return refused;
        try {
            return gateway.call(inner::unavailableReasonKey);
        } catch (CurrencyUnavailableException e) {
            return e.reasonKey();
        }
    }

    /** 读不到就抛 {@link CurrencyUnavailableException}：返回 0 是静默给错数。 */
    @Override
    public long balance(UUID player) {
        try {
            long v = gateway.call(() -> inner.balance(player));
            lastRefusal.remove();
            return v;
        } catch (CurrencyUnavailableException e) {
            lastRefusal.set(e.reasonKey());
            throw e;
        }
    }

    @Override
    public TxnResult transfer(UUID from, UUID to, long amount, TxnReason reason) {
        return txn(() -> inner.transfer(from, to, amount, reason));
    }

    @Override
    public TxnResult mint(UUID to, long amount, TxnReason reason) {
        return txn(() -> inner.mint(to, amount, reason));
    }

    @Override
    public TxnResult burn(UUID from, long amount, TxnReason reason) {
        return txn(() -> inner.burn(from, amount, reason));
    }

    @Override
    public HoldResult hold(UUID from, UUID beneficiary, long amount, TxnReason reason) {
        java.util.concurrent.atomic.AtomicBoolean ran = new java.util.concurrent.atomic.AtomicBoolean();
        try {
            HoldResult h = gateway.call(() -> {
                ran.set(true);
                return inner.hold(from, beneficiary, amount, reason);
            });
            lastRefusal.remove();
            return h;
        } catch (CurrencyUnavailableException e) {
            lastRefusal.set(e.reasonKey());
            if (ran.get()) throw e;
            return HoldResult.fail(TxnResult.UNAVAILABLE);
        }
    }

    @Override
    public TxnResult release(EscrowId id, TxnReason reason) {
        return txn(() -> inner.release(id, reason));
    }

    @Override
    public TxnResult refund(EscrowId id, TxnReason reason) {
        return txn(() -> inner.refund(id, reason));
    }

    private TxnResult txn(Supplier<TxnResult> op) {
        java.util.concurrent.atomic.AtomicBoolean ran = new java.util.concurrent.atomic.AtomicBoolean();
        try {
            TxnResult r = gateway.call(() -> {
                ran.set(true);
                return op.get();
            });
            lastRefusal.remove();
            return r;
        } catch (CurrencyUnavailableException e) {
            lastRefusal.set(e.reasonKey());
            if (ran.get()) throw e;
            return TxnResult.UNAVAILABLE;
        }
    }
}
