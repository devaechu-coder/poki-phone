package com.november.mcphone.api.economy;

/**
 * {@link ICurrencyProvider#hold} 的结果（扩展施工方案 §22.4）。
 *
 * <p>§22.4 原型是 {@code EscrowId hold(...)} —— 返回值里<b>放不下失败原因</b>：
 * 余额不够与"货币用不了"都只能返回 null，而调用方对这两种的处理完全不同。
 *
 * @param result 成了就是 {@link TxnResult#OK}
 * @param id     成了才有，失败时为 null
 */
public record HoldResult(TxnResult result, EscrowId id) {

    public HoldResult {
        if (result == null) throw new IllegalArgumentException("HoldResult.result 不能为 null");
        if (result == TxnResult.OK && id == null) throw new IllegalArgumentException("OK 必须带 EscrowId");
        if (result != TxnResult.OK && id != null) throw new IllegalArgumentException("失败不许带 EscrowId");
    }

    public static HoldResult ok(EscrowId id) {
        return new HoldResult(TxnResult.OK, id);
    }

    public static HoldResult fail(TxnResult why) {
        return new HoldResult(why, null);
    }
}
