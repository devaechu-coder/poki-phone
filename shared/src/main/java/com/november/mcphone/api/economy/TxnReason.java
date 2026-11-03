package com.november.mcphone.api.economy;

/**
 * 一笔变动为什么发生（施工方案 §22.4、§22.3 ⑥）。进流水，服主对账时唯一能看的东西。
 *
 * <p><b>没有 appId 这一格</b>：§22.4 原型是 {@code (appId, kind, ref)}，让调用者自报自己是谁。
 * 那样任何 App 都能把自己的交易记到别人名下，而流水是"经济系统唯一的安全网"（§22.10）——
 * 安全网由被监督的一方填写就不成其为网。<b>appId 由宿主从调用上下文盖章</b>，写流水时拼上。
 * 这与 §13.2"服务端从不问你跑的是什么代码，只问你是谁"是同一条纪律。
 *
 * <p><b>拒竖线与换行</b>：§22.10 的流水行是 {@code |} 分隔的纯文本，
 * 一个换行就能在流水里伪造出一整行不存在的交易。
 *
 * @param kind 操作分类，如 {@code market:buy}
 * @param ref  调用方自己的单号，用于事后对账
 */
public record TxnReason(String kind, String ref) {

    public static final int MAX_KIND = 32;
    public static final int MAX_REF = 64;

    public TxnReason {
        kind = clean(kind, MAX_KIND, "kind");
        ref = clean(ref, MAX_REF, "ref");
    }

    private static String clean(String s, int max, String field) {
        if (s == null) return "";
        if (s.length() > max) {
            throw new IllegalArgumentException("TxnReason." + field + " 最长 " + max + "，收到 " + s.length());
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '|' || c < 0x20 || c == 0x7f) {
                throw new IllegalArgumentException("TxnReason." + field + " 不许含竖线或控制字符：流水行按竖线分隔");
            }
        }
        return s;
    }
}
