package com.november.mcphone.core.script.pkg;

/**
 * 签名相关的全部文案（施工方案 §12.5）。<b>集中一处，UI 不许自己拼。</b>
 *
 * <h2>不许改成「安全」</h2>
 *
 * §12.1 的区分是这套设计的前提：签名确认的是「谁做的、有没有被改过」，
 * <b>不是「内容安不安全」</b>。挡毒的是 §3.4 的包内容白名单与 §16 的沙箱。
 * 文案里一旦出现"安全"，玩家就会把绿色当成无害 —— 而那正是 {@link #INSTALL_NOTE} 要防的事。
 *
 * <p>所以这些常量配了断言（{@code docs/SignCopyTest.java}）：谁把"安全"两个字写进来，测试当场红。
 */
public final class SigCopy {

    private SigCopy() {
    }

    // ---------------------------------------------------------------- 六档文案（§12.5）

    public static final String SIG_INVALID = "mcphone.sig.invalid";
    public static final String SIG_KEY_CHANGED = "mcphone.sig.key_changed";
    public static final String SIG_UNSIGNED = "mcphone.sig.unsigned";
    public static final String SIG_UNKNOWN = "mcphone.sig.unknown";
    public static final String SIG_TRUSTED = "mcphone.sig.trusted";

    /** 「上次签过、这次没签」。§12.5 没有这一条，是补 sig.json 降级那个洞时加的。 */
    public static final String SIG_REMOVED = "mcphone.sig.removed";

    /**
     * <b>每次安装都显示</b>（§12.5 最后一段）。
     *
     * <p>它把 §12.1 那个区分讲给玩家，而不是让玩家误以为绿色对勾等于无害。
     * 少了它，那几档提示反而会帮倒忙：玩家看到绿色就放心了。
     */
    public static final String INSTALL_NOTE = "mcphone.sig.install_note";

    /** 未签名时指纹那一格写什么（§12.4：指纹位置写「无」）。 */
    public static final String FINGERPRINT_NONE = "mcphone.sig.fingerprint_none";

    /** 「作者密钥变了」那一档的输入提示。 */
    public static final String CONFIRM_PROMPT = "mcphone.sig.confirm_prompt";

    /** 输入的东西不对时的提示。 */
    public static final String CONFIRM_MISMATCH = "mcphone.sig.confirm_mismatch";

    /** 一档对应哪一条文案。<b>UI 查这张表，不自己判。</b> */
    public static String keyFor(TrustState.State state) {
        return switch (state) {
            case INVALID -> SIG_INVALID;
            case KEY_CHANGED -> SIG_KEY_CHANGED;
            case UNSIGNED -> SIG_UNSIGNED;
            case UNKNOWN_AUTHOR -> SIG_UNKNOWN;
            case TRUSTED -> SIG_TRUSTED;
            case SIGNATURE_REMOVED -> SIG_REMOVED;
        };
    }

    // ---------------------------------------------------------------- 确认短语

    /**
     * 「作者密钥变了」要输入什么才放行（§12.4：<b>要输入确认短语，不是点一下</b>）。
     *
     * <p>要的是<b>新指纹本身</b>，不是"确认"两个字。理由：让玩家真的去看一眼新指纹 ——
     * 打两个字谁都会打，而抄一串十六进制必须先把目光挪到指纹那一行。
     * 这一档存在的全部意义就是让玩家注意到"这次不是上次那个人"。
     */
    public static String requiredPhrase(String newFingerprint) {
        return newFingerprint == null ? "" : newFingerprint;
    }

    /**
     * 输入的对不对。
     *
     * <p>去掉首尾空白、忽略大小写 —— 指纹是十六进制，大小写不该成为拦路虎；
     * 但<b>中间的连字符不许省</b>：那是分组的一部分，省了就不是在抄那一串了。
     */
    public static boolean phraseAccepted(String typed, String newFingerprint) {
        if (typed == null || newFingerprint == null) return false;
        return typed.trim().equalsIgnoreCase(newFingerprint.trim());
    }

    /**
     * 这一档要不要拦住安装按钮。
     *
     * @param typed 玩家输入的东西；不需要确认短语的档位传什么都行
     */
    public static boolean canProceed(TrustState.Verdict verdict, String typed) {
        if (!verdict.state().installable) return false;                 // 两档硬拒绝，不给"仍然继续"
        if (!verdict.state().needsPhrase()) return true;
        return phraseAccepted(typed, verdict.fingerprint());
    }
}
