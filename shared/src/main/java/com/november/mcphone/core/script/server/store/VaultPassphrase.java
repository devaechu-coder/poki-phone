package com.november.mcphone.core.script.server.store;

/**
 * 口令策略（施工方案 §17.4.4）。<b>纯判定，界面照着它画。</b>
 *
 * <p>抽出来是为了能测：界面跑不了断言测试，而"几个字符算够""什么算弱"这些是会被人随手改的数。
 */
public final class VaultPassphrase {

    private VaultPassphrase() {
    }

    /** 最少几个字符（§17.4.4）。 */
    public static final int MIN_LENGTH = VaultCrypto.MIN_PASSPHRASE;

    /** 强度档。界面只按这个给提示，不拦 —— 拦的只有长度。 */
    public enum Strength {
        TOO_SHORT("mcphone.vault.strength.too_short"),
        WEAK("mcphone.vault.strength.weak"),
        FAIR("mcphone.vault.strength.fair"),
        STRONG("mcphone.vault.strength.strong");

        /** 本地化键，<b>不是文本</b>。 */
        public final String key;

        Strength(String key) {
            this.key = key;
        }
    }

    /**
     * 三条必须出现在界面上的文案（§17.4.4）。<b>一条都不许省。</b>
     *
     * <p>少了第三条，玩家会以为"忘了可以找服主重置" —— 而那正是这套设计<b>做不到</b>的事，
     * 也是它安全性的来源。
     */
    public static final String KEY_WARN_NO_RECOVERY = "mcphone.vault.warn.no_recovery";
    public static final String KEY_WARN_NEVER_LEAVES = "mcphone.vault.warn.never_leaves";
    public static final String KEY_WARN_CONFIRM = "mcphone.vault.warn.confirm";

    /** 算强度。只看结构，不查字典 —— 查字典要带一份词表，那不值得。 */
    public static Strength strength(char[] passphrase) {
        if (passphrase == null || passphrase.length < MIN_LENGTH) return Strength.TOO_SHORT;

        boolean lower = false, upper = false, digit = false, other = false;
        for (char c : passphrase) {
            if (c >= 'a' && c <= 'z') lower = true;
            else if (c >= 'A' && c <= 'Z') upper = true;
            else if (c >= '0' && c <= '9') digit = true;
            else other = true;                       // 中文、符号都算这一类
        }
        int kinds = (lower ? 1 : 0) + (upper ? 1 : 0) + (digit ? 1 : 0) + (other ? 1 : 0);

        if (passphrase.length >= 16 || (passphrase.length >= 12 && kinds >= 3)) return Strength.STRONG;
        if (passphrase.length >= 12 || kinds >= 3) return Strength.FAIR;
        return Strength.WEAK;
    }

    /** 两次输入一不一样。<b>不用 String 比</b>：String 会在堆上留副本，内存转储里就看得到。 */
    public static boolean matches(char[] first, char[] second) {
        if (first == null || second == null || first.length != second.length) return false;
        // 常数时间比较：长度已经泄漏了，内容不必再泄漏
        int diff = 0;
        for (int i = 0; i < first.length; i++) diff |= first[i] ^ second[i];
        return diff == 0;
    }

    /** 能不能提交。长度不够或者两次不一致就不能。 */
    public static boolean acceptable(char[] first, char[] second) {
        return strength(first) != Strength.TOO_SHORT && matches(first, second);
    }
}
