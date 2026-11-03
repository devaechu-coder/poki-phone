package com.november.mcphone.api.economy;

import java.util.UUID;

/**
 * 一种货币的实现（施工方案 §22.4）。服主选谁来提供，见 §22.7。
 *
 * <h2>为什么全部用 UUID 而不是 §22.4 写的 ServerPlayer</h2>
 *
 * <ul>
 *   <li><b>市场当场不成立</b>：卖家下线的那一秒买家就付不了钱了。而 §22.6 那张表
 *       把"市场 App 的全套货币操作"判成 {@code plain} 档，前提是它跑得起来。</li>
 *   <li><b>§22.4 自己就不一致</b>：{@code hold(from, UUID beneficiary, ...)} 的受益人已经是 UUID ——
 *       同一份接口里两种身份表示，迟早有人在边界上把它们弄混。</li>
 *   <li><b>测不了</b>：{@code ServerPlayer} 要 {@code MinecraftServer} 加 {@code ServerLevel} 才造得出来，
 *       而 {@code docs/} 的断言测试是裸 {@code JavaExec}，没有注册表也没有 mock。
 *       签名里留着它，§22.12 第一条判据（{@code docs/CurrencyTest.java} 全绿）就永远打不了勾。</li>
 * </ul>
 *
 * 要 {@code ServerPlayer} 的实现自己去 {@code server.getPlayerList().getPlayer(uuid)}。
 *
 * <h2>钱只能往三个方向流，方向在创建时定死（§22.3 ⑤）</h2>
 *
 * 调用者 → 收款人、调用者 → 托管 → 创建托管时指定的受益人、托管 → 原主。
 * <b>没有"把 A 的钱转给 B"这种方法</b>，所以任何 App 都搬不动第三方的钱。
 * 想加一个"管理员任意转账"的口子之前先读 §22.6 最后一行。
 *
 * <p><b>转账是单操作</b>（§22.3 ④）：不提供 withdraw + deposit 两步 API，
 * 不给这个口子就不会有人写出崩在中间、钱凭空消失的代码。
 *
 * <p><b>金额是最小单位的整数，永不用浮点</b>（§22.3 ②）：{@code decimals: 2} 时 {@code 1234} 是 12.34。
 *
 * <p>五条不变量在 §22.9，写成代码在 {@link Balances} —— 实现照着调，就不会各漏各的。
 * 其中最要紧的一条：<b>{@code amount <= 0} 一律 {@link TxnResult#INVALID}</b>，
 * {@code transfer(A, B, -1000)} 若实现成"from 减 amount、to 加 amount"就是从对方账上偷钱。
 *
 * <p>全部方法只在服务端调用。
 */
public interface ICurrencyProvider {

    /** 这个实现提供的是哪种货币。元数据仅供显示，判定按 {@link Currency#id()}。 */
    Currency currency();

    /** false 时所有操作返回 {@link TxnResult#UNAVAILABLE}，界面把按钮画灰。 */
    boolean isAvailable();

    /**
     * 用不了时的<b>本地化键</b>，不是文本。
     *
     * <p>与 §23.3 对 {@code Notification.titleKey} 的要求同一条理由：
     * 自由文本是一条把任意字符串推到客户端上的路。
     */
    String unavailableReasonKey();

    /** 余额，最小单位。只读自己是 {@code plain} 档，读别人要 {@code currency.read.other}（§22.6）。 */
    long balance(UUID player);

    /** 允不允许负余额。false 时扣款前检查，不够就 {@link TxnResult#INSUFFICIENT}。 */
    boolean allowNegative();

    /** 余额上限。入账后会超过它就返回 {@link TxnResult#LIMIT}，不许回绕（§22.9）。 */
    long maxBalance();

    /**
     * 原子转账，两端都是玩家。要么全成要么全不成 —— <b>中间态不许落盘</b>。
     *
     * <p>{@code from} 恒为调用者（§22.6：这样它才是 {@code plain} 档）。收款方离线照样收得到。
     *
     * <p>{@code from.equals(to)} 返回 {@link TxnResult#INVALID}，而且要在读余额之前判：
     * 两端各读一份快照再分别写回，后写覆盖前写就是凭空造币（{@link Balances#checkParties}）。
     */
    TxnResult transfer(UUID from, UUID to, long amount, TxnReason reason);

    /** 凭空铸造。要 {@code currency.mint} 能力，没批返回 {@link TxnResult#NOT_AUTHORIZED}（§22.6）。 */
    TxnResult mint(UUID to, long amount, TxnReason reason);

    /** 销毁。要 {@code currency.burn} 能力，没批返回 {@link TxnResult#NOT_AUTHORIZED}（§22.6）。 */
    TxnResult burn(UUID from, long amount, TxnReason reason);

    /**
     * 托管：当场扣 {@code from} 的钱，<b>受益人在此刻定死，之后不可更改</b>（§22.3 ⑤）。
     *
     * <p>托管必须持久化（{@code world/mcphone/economy/escrow.dat}）——
     * 服务器重启不能把托管中的钱吃掉；启动时扫一遍，超过 {@code escrowTimeout}（默认 7 天）
     * 的自动 {@link #refund} 并记流水（§22.4）。
     */
    HoldResult hold(UUID from, UUID beneficiary, long amount, TxnReason reason);

    /**
     * 放款，只能给创建托管时指定的那个受益人。不认识的号、以及别的货币的号，都返回
     * {@link TxnResult#UNKNOWN_ESCROW}：不比对货币的话，A 币的号递给 B 币就是销毁 A、铸出 B
     * （{@link Balances#checkEscrowCurrency}）。
     */
    TxnResult release(EscrowId id, TxnReason reason);

    /** 退款，只能退给创建托管的那个人。已经结过的返回 {@link TxnResult#ALREADY_SETTLED}；号的判定同 {@link #release}。 */
    TxnResult refund(EscrowId id, TxnReason reason);
}
