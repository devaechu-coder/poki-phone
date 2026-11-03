package com.november.mcphone.api.sdk.item;

/**
 * 句柄的形状（施工方案 §23.3，见 {@link ItemRef} 的说明）。<b>纯字符串判定，不碰 Minecraft。</b>
 *
 * <p>只管"长什么样"，不管"存在不存在" —— 后者要宿主侧那张表，是 S12/S13 的事。
 * 分开的理由：形状判定要能在 docs 断言里跑，而那里没有注册表、造不出 ItemStack。
 */
public final class Handles {

    private Handles() {
    }

    /**
     * 句柄的字符数。
     *
     * <p>128 位取十六进制。要够随机 —— 脚本猜中一个别人的句柄就等于拿到了别人的物品，
     * 而句柄是在同一台服务器上所有脚本之间流动的。
     */
    public static final int LENGTH = 32;

    /** 是不是一个合法形状的句柄：恰好 {@link #LENGTH} 位小写十六进制。 */
    public static boolean isHandle(String s) {
        if (s == null || s.length() != LENGTH) return false;
        for (int i = 0; i < LENGTH; i++) {
            char c = s.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) return false;
        }
        return true;
    }

    /**
     * 把 128 位随机数写成句柄。大写十六进制与短一位的都不是合法句柄 ——
     * 形状只有一种，比较才能用字符串相等。
     */
    public static String format(long high, long low) {
        return String.format("%016x%016x", high, low);
    }
}
