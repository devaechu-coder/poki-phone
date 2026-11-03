package com.november.mcphone.api.sdk;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 一个包声明的 {@code sdk} 段，本机满不满足（施工方案 §23.4）。<b>纯计算，客户端与服务端各调各的。</b>
 *
 * <h2>为什么是纯函数而不是写在商店里</h2>
 *
 * 客户端的门控是 UX，不是边界（§13.3、§13.8）：恶意客户端把它整段删掉就绕过去了。
 * {@code deploy: "server"} 的包由服主在服务端侧审批部署（§14.4），<b>那一侧必须自己再判一次</b>，
 * 否则一个绕过客户端门控的包会被部署上去，然后在服务端脚本里调一个不存在的 SDK。
 * 判定摆成纯函数，两侧调的就是同一份。
 */
public final class SdkGate {

    private SdkGate() {
    }

    /**
     * 满足不了的那些。全满足就是空表。
     *
     * <p>两种满足不了（§23.4）：本机<b>没有</b>这个 SDK，或者本机的版本<b>低于</b>声明。
     * 本机版本高于声明是正常的 —— 契约只增不减，新版装得下旧版的用法。
     *
     * <p><b>认不出的键归到"没有"那一类</b>，而不是当成清单格式错误。
     * 它表示这个包要一个比本机新的 SDK —— 那是"需要更新 MCphone"，不是"这个包坏了"。
     * 在解析层拒的话，将来每加一个 SDK，旧版 MCphone 就把新包整个跳过（坏包一律跳过并记日志），
     * 玩家在商店里<b>什么都看不到</b>，连"该更新了"都不知道。
     *
     * @param declared 清单里的 {@code sdk} 段，可为 null
     * @return 键 → 本机的版本（没有就是 {@link SdkVersions#ABSENT}）。按键名排序，报错时顺序稳定
     */
    public static Map<String, Integer> unsatisfied(Map<String, Integer> declared) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (declared == null || declared.isEmpty()) return out;
        for (Map.Entry<String, Integer> e : new TreeMap<>(declared).entrySet()) {
            int have = SdkVersions.of(e.getKey());
            int want = e.getValue() == null ? 0 : e.getValue();
            if (have < want) out.put(e.getKey(), have);
        }
        return out;
    }

    /** 装得了吗。 */
    public static boolean satisfied(Map<String, Integer> declared) {
        return unsatisfied(declared).isEmpty();
    }
}
