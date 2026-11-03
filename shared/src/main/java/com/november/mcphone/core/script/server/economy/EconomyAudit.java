package com.november.mcphone.core.script.server.economy;

/**
 * 对账（施工方案 §22.10）。<b>经济系统唯一的安全网。</b>
 *
 * <pre>所有玩家余额之和 + 托管中的钱  ==  历史 mint 之和 − burn 之和</pre>
 *
 * 不相等说明有 bug，或者有人直接改了存档。<b>立刻醒目报给服主</b>，
 * 别埋在日志里 —— 通胀发生了没人知道从哪来，正是这个类要防的事。
 */
public final class EconomyAudit {

    private EconomyAudit() {
    }

    /**
     * 一次对账的结果。
     *
     * @param balanceSum 所有玩家余额之和
     * @param heldSum    还押在托管里的
     * @param minted     历史 mint 之和
     * @param burned     历史 burn 之和
     */
    public record Result(String currencyId, long balanceSum, long heldSum, long minted, long burned) {

        /** 账上有多少钱。 */
        public long actual() {
            return balanceSum + heldSum;
        }

        /** 按流水该有多少钱。 */
        public long expected() {
            return minted - burned;
        }

        /** 差多少。0 就是平。 */
        public long delta() {
            return actual() - expected();
        }

        public boolean balanced() {
            return delta() == 0;
        }

        /** 给服主看的一行。<b>不平的那一行要显眼</b>。 */
        public String describe() {
            if (balanced()) {
                return "[对账] " + currencyId + " 平：余额 " + balanceSum + " + 托管 " + heldSum
                        + " = 铸造 " + minted + " − 销毁 " + burned;
            }
            return "[对账] ⚠⚠ " + currencyId + " 不平，差 " + delta() + " ⚠⚠"
                    + "  实际 " + actual() + "（余额 " + balanceSum + " + 托管 " + heldSum + "）"
                    + "  应有 " + expected() + "（铸造 " + minted + " − 销毁 " + burned + "）"
                    + "  —— 有 bug，或者有人改过存档";
        }
    }

    /**
     * 算一次，铸造与销毁取存档里的累计（{@link EconomyData}）。生产环境用这一个。
     *
     * <p>不从流水现算：流水 {@link TxnLog#RETENTION_DAYS} 天后会被清掉，强杀之后又会比存档超前一截 ——
     * 拿它对账，要么早晚不平，要么重启之后不平。累计与余额是同一份存档、同一次落盘。
     */
    public static Result run(String currencyId, EconomyData data) {
        long sum = 0;
        for (long v : data.all(currencyId).values()) sum += v;
        long[] s = data.supply(currencyId);
        return new Result(currencyId, sum, data.escrow().held(currencyId), s[0], s[1]);
    }

    /** 算一次，铸造与销毁从流水现算。只给没有存档的场合（断言测试）用，理由见上一个。 */
    public static Result run(String currencyId, BalanceStore balances, EscrowLedger escrow, TxnLog log) {
        long sum = 0;
        for (long v : balances.all(currencyId).values()) sum += v;
        long[] mb = log.sumMintAndBurn(currencyId);
        return new Result(currencyId, sum, escrow.held(currencyId), mb[0], mb[1]);
    }
}
