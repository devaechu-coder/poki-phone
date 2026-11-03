package com.november.mcphone.api.economy;

/**
 * 一次货币操作的结果（施工方案 §22.4）。
 *
 * <p><b>码只增不减，也不许换意思</b>（§23.4）：已经发布的 App 会照着这些码分支，
 * 把 {@link #LIMIT} 改成表示别的东西，等于悄悄改了它们的行为。要表达新情况就加新的码。
 *
 * <p>§22.4 原表只有前六项。后三项是补的，因为少了它们就必须复用已有的码 ——
 * 而复用正是 §23.4 明令不许的那一条：
 * §22.5 写着"没批就是 {@code NOT_AUTHORIZED}"而原表没有它；
 * "这个托管号不认识"与"这个托管已经结过了"若都塞进 {@link #INVALID}，
 * 就与"金额是负数"共用一个码，调用方分不出该重试还是该报错。
 */
public enum TxnResult {

    /** 成了。 */
    OK,

    /** 付款方余额不够（§22.9）。 */
    INSUFFICIENT,

    /** 收款方会超过 {@link ICurrencyProvider#maxBalance()}，或者加法会溢出 long —— 回绕是不许的（§22.9）。 */
    LIMIT,

    /** 这种货币现在用不了，原因见 {@link ICurrencyProvider#unavailableReasonKey()}。 */
    UNAVAILABLE,

    /** 参数不对：金额 ≤ 0、玩家为 null（§22.9），转账两端是同一个人（{@link Balances#checkParties}）。 */
    INVALID,

    /** 没有这个能力。{@code currency.mint} / {@code burn} / {@code read.other} 是 granted 档（§22.6）。 */
    NOT_AUTHORIZED,

    /** 这个托管号本 provider 不认识 —— 多货币服上把 A 币的托管号递给 B 币是一定会发生的。 */
    UNKNOWN_ESCROW,

    /** 这个托管已经放过款或退过款了。重复调用是幂等的，不是错误，但要分得出来。 */
    ALREADY_SETTLED,

    /** 实现内部出错，没做成也没扣钱。 */
    FAILED
}
