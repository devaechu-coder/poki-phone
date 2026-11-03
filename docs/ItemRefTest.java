package com.november.mcphone.api.sdk.item;

import java.util.ArrayList;
import java.util.List;

/**
 * 物品引用（施工方案 §23.3）：句柄的形状、数量范围、批量上限。
 *
 * <p><b>这里测不了的</b>：句柄背后那张表是宿主侧的（S12/S13），
 * 而 {@code docs/} 的断言测试没有注册表、造不出 {@code ItemStack}。
 * "opaque 在脚本侧不可解析也不可构造"要枚举 {@code ctx.item} 才能验，那是 S13 的事。
 *
 * <p>跑法：{@code ./gradlew assertTests}（在 {@code platforms/<目标名>/} 下）。
 */
public class ItemRefTest {

    static int checks = 0;
    static final List<String> failures = new ArrayList<>();

    static void eq(Object actual, Object expected, String what) {
        checks++;
        if (!java.util.Objects.equals(actual, expected)) {
            failures.add(what + "  期望 " + expected + "，实际 " + actual);
        }
    }

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) failures.add(what);
    }

    /** 跑一段，读它抛出来的话；没抛返回 null。 */
    static String rejects(Runnable body) {
        try {
            body.run();
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    static final String H = "3f2504e0a1b2c3d4e5f60718293a4b5c";

    static void handleShape() {
        check(Handles.isHandle(H), "32 位小写十六进制是句柄");
        eq(H.length(), Handles.LENGTH, "样例长度就是 LENGTH");
        check(!Handles.isHandle(null), "null 不是");
        check(!Handles.isHandle(""), "空串不是");
        check(!Handles.isHandle(H.substring(1)), "短一位不是");
        check(!Handles.isHandle(H + "0"), "长一位不是");
        check(!Handles.isHandle(H.toUpperCase()), "大写不是 —— 形状只有一种，比较才能用字符串相等");
        check(!Handles.isHandle("g" + H.substring(1)), "非十六进制字符不是");

        eq(Handles.format(0L, 0L), "0".repeat(32), "全零");
        eq(Handles.format(-1L, -1L), "f".repeat(32), "全 f —— 高位不许写成负号");
        check(Handles.isHandle(Handles.format(0x123456789abcdefL, 0xfedcba9876543210L)), "format 出来的一定是合法句柄");
    }

    static void counts() {
        eq(new ItemRef("minecraft:stick", 1, "").count(), 1, "下界");
        eq(new ItemRef("minecraft:stick", 99, "").count(), 99, "上界");
        check(rejects(() -> new ItemRef("minecraft:stick", 0, "")) != null, "0 拒");
        check(rejects(() -> new ItemRef("minecraft:stick", -1, "")) != null, "负数拒");
        check(rejects(() -> new ItemRef("minecraft:stick", 100, "")) != null, "100 拒");
        eq(ItemRef.MIN_COUNT, 1, "MIN_COUNT");
        eq(ItemRef.MAX_COUNT, 99, "MAX_COUNT —— 不是 long，物品数量不需要");
    }

    static void ids() {
        check(rejects(() -> new ItemRef(null, 1, "")) != null, "id 为 null 拒");
        check(rejects(() -> new ItemRef("", 1, "")) != null, "id 为空串拒");
        eq(ItemRef.of("minecraft:stick", 3).opaque(), "", "of() 不带句柄");
        check(!ItemRef.of("minecraft:stick", 3).hasOpaque(), "没句柄");
        check(new ItemRef("minecraft:stick", 1, H).hasOpaque(), "有句柄");
    }

    static void opaqueMustBeHandle() {
        eq(new ItemRef("minecraft:stick", 1, null).opaque(), "", "null 归一成空串");
        check(rejects(() -> new ItemRef("minecraft:stick", 1, "not-a-handle")) != null,
                "不是句柄形状的一律拒 —— 脚本递回来的必须是宿主发出去过的那种东西");
        // §23.3 原型那种"base64 字节"现在一律不是合法 opaque
        check(rejects(() -> new ItemRef("minecraft:stick", 1,
                "H4sIAAAAAAAA/6tWKkotLlGyUvIvUNJRykxRslLKTFGyAgBGjE0aGAAAAA==")) != null,
                "base64 字节不是句柄");
    }

    /**
     * 批量上限：27 件 × 32 字符 = 864，要塞得进 RPC 的 PARAMS_MAX = 4096（§10.3）。
     * 这两个数任何一个动了，这条断言就该红。
     */
    static void batchFitsRpc() {
        eq(ItemRef.MAX_BATCH, 27, "一次最多 27 件，与收件箱一箱同源");
        int bytes = ItemRef.MAX_BATCH * Handles.LENGTH;
        check(bytes <= 4096, "27 件的句柄共 " + bytes + " 字符，要塞得进 PARAMS_MAX 4096");

        // 反过来记一笔：方案原型那种 512 字符的 opaque 装不进去，这正是改成句柄的理由之一
        check(ItemRef.MAX_BATCH * 512 > 4096, "512 字符的 opaque × 27 件必然撑爆 4096");
    }

    /** id 与 count 是出站投影：同一个句柄配不同的 id，在类型层面拦不住，所以物化时一律按句柄查表。 */
    static void projectionsAreNotEvidence() {
        ItemRef a = new ItemRef("minecraft:stick", 1, H);
        ItemRef b = new ItemRef("minecraft:netherite_sword", 99, H);
        check(!a.equals(b), "两条 ItemRef 不相等");
        eq(a.opaque(), b.opaque(), "但句柄是同一个 —— 脚本改 id 与 count 改不动它指向的东西");
        check(Handles.isHandle(b.opaque()), "改过 id 的那条，句柄照样合法");
    }

    public static void main(String[] args) {
        handleShape();
        counts();
        ids();
        opaqueMustBeHandle();
        batchFitsRpc();
        projectionsAreNotEvidence();

        System.out.println("断言 " + checks + " 条");
        if (!failures.isEmpty()) {
            System.out.println("失败 " + failures.size() + " 条：");
            for (String f : failures) System.out.println("  - " + f);
            System.exit(1);
        }
        System.out.println("全部通过");
    }
}
