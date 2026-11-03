package com.november.mcphone.api.sdk;

import com.november.mcphone.api.economy.EconomyApi;
import com.november.mcphone.api.sdk.cycle.CycleApi;
import com.november.mcphone.api.sdk.escrow.EscrowApi;
import com.november.mcphone.api.sdk.groups.GroupsApi;
import com.november.mcphone.api.sdk.item.ItemApi;
import com.november.mcphone.api.sdk.mailbox.MailboxApi;
import com.november.mcphone.api.sdk.notify.NotifyApi;
import com.november.mcphone.api.sdk.player.PlayerApi;
import com.november.mcphone.api.sdk.resources.ResourcesApi;
import com.november.mcphone.api.sdk.stats.StatsApi;
import com.november.mcphone.api.sdk.waypoints.WaypointsApi;

import com.november.mcphone.core.script.pkg.Manifest;
import com.november.mcphone.core.script.pkg.PackageError;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * SDK 版本表与 manifest 的 {@code sdk} 门控（施工方案 §23.4）。
 *
 * <p>跑法：{@code ./gradlew assertTests}（在 {@code platforms/<目标名>/} 下）。
 */
public class SdkGateTest {

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

    /** 每个 Api 类报的 (KEY, VERSION)。加了第 12 个 SDK 忘了写进来，下面那条集合相等就会红。 */
    static final String[][] APIS = {
            {ItemApi.KEY, String.valueOf(ItemApi.VERSION)},
            {PlayerApi.KEY, String.valueOf(PlayerApi.VERSION)},
            {EconomyApi.KEY, String.valueOf(EconomyApi.VERSION)},
            {MailboxApi.KEY, String.valueOf(MailboxApi.VERSION)},
            {NotifyApi.KEY, String.valueOf(NotifyApi.VERSION)},
            {CycleApi.KEY, String.valueOf(CycleApi.VERSION)},
            {GroupsApi.KEY, String.valueOf(GroupsApi.VERSION)},
            {WaypointsApi.KEY, String.valueOf(WaypointsApi.VERSION)},
            {EscrowApi.KEY, String.valueOf(EscrowApi.VERSION)},
            {StatsApi.KEY, String.valueOf(StatsApi.VERSION)},
            {ResourcesApi.KEY, String.valueOf(ResourcesApi.VERSION)},
    };

    /** 一张表 ↔ 11 个 Api 类，双射。两份判据分家正是这条断言要挡的（见 SdkVersions 的注释）。 */
    static void bijection() {
        TreeSet<String> fromApis = new TreeSet<>();
        for (String[] a : APIS) fromApis.add(a[0]);
        eq(fromApis, new TreeSet<>(SdkVersions.keys()), "Api 类的键集合 == SdkVersions 的键集合");
        eq(APIS.length, SdkVersions.keys().size(), "条数一致，没有重复键");

        for (String[] a : APIS) {
            eq(Integer.parseInt(a[1]), SdkVersions.of(a[0]), a[0] + " 的版本两边一致");
            check(SdkVersions.of(a[0]) >= 1, a[0] + " 的版本要 ≥ 1");
        }
    }

    /** A 档六项与 B 档五项都必须在表里（§23.2、§23.5）。 */
    static void tiers() {
        for (String k : new String[]{"item", "player", "economy", "mailbox", "notify", "cycle"}) {
            check(SdkVersions.of(k) >= 1, "A 档 " + k + " 在表里");
        }
        for (String k : new String[]{"groups", "waypoints", "escrow", "stats", "resources"}) {
            check(SdkVersions.of(k) >= 1, "B 档 " + k + " 占位在表里");
        }
    }

    static void absent() {
        eq(SdkVersions.of("quantum"), SdkVersions.ABSENT, "没有的 SDK 返回 ABSENT");
        eq(SdkVersions.of(null), SdkVersions.ABSENT, "null 也返回 ABSENT");
        check(SdkVersions.ABSENT < 1, "ABSENT 必须小于任何合法版本号，否则门控放行");
    }

    static Map<String, Integer> decl(Object... kv) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Integer) kv[i + 1]);
        return m;
    }

    /** §23.4：低于声明 → 拦；高于声明 → 放行。 */
    static void gate() {
        check(SdkGate.satisfied(null), "没写 sdk 段就是没要求");
        check(SdkGate.satisfied(decl()), "空表也是没要求");
        check(SdkGate.satisfied(decl("economy", 1, "mailbox", 1, "cycle", 1)), "§23.4 举的那个例子放行");

        // 本机低于声明
        Map<String, Integer> bad = SdkGate.unsatisfied(decl("economy", 2));
        eq(bad.size(), 1, "差一项");
        eq(bad.get("economy"), 1, "报的是本机的版本");
        check(!SdkGate.satisfied(decl("economy", 2)), "要第 2 版而本机第 1 版 → 拦");

        // 认不出的键归到"没有"，不是清单格式错误
        Map<String, Integer> unknown = SdkGate.unsatisfied(decl("quantum", 1));
        eq(unknown.get("quantum"), SdkVersions.ABSENT, "没听说过的 SDK 报 ABSENT");
        check(!SdkGate.satisfied(decl("quantum", 1)), "没听说过的 SDK → 拦，而不是当成清单坏了");

        // 多项里只要有一项不满足就拦，且报全
        Map<String, Integer> many = SdkGate.unsatisfied(decl("economy", 1, "quantum", 3, "mailbox", 9));
        eq(new ArrayList<>(many.keySet()), List.of("mailbox", "quantum"), "只报不满足的，按键名排序");
    }

    /** 高于声明是正常的：契约只增不减，新版装得下旧版的用法。 */
    static void newerHostIsFine() {
        check(SdkGate.satisfied(decl("cycle", 1)), "本机 1 声明 1");
        // 模拟本机将来到了第 5 版：声明 1 仍然该放行。这里用"声明 0"等价地表达"声明比本机旧"
        check(SdkGate.satisfied(decl("cycle", 0)), "声明比本机旧 → 放行");
    }

    // ---------------------------------------------------------------- manifest 的 sdk 段

    /** 一份最小的合法清单，只差 sdk 段。 */
    static String manifestJson(String sdkSegment) {
        return "{"
                + "\"format\": 1,"
                + "\"id\": \"example:demo\","
                + "\"version\": \"1.0.0\","
                + "\"name\": \"demo\","
                + "\"author\": \"a\","
                + "\"description\": \"d\","
                + "\"icon\": \"icon.png\","
                + "\"engine\": \"declarative-1\""
                + (sdkSegment == null ? "" : "," + sdkSegment)
                + "}";
    }

    static Manifest parse(String sdkSegment) {
        return Manifest.parse(manifestJson(sdkSegment));
    }

    /** 跑一段，把它抛的 PackageError 的码取出来；没抛返回 null。 */
    static PackageError.Code codeOf(Runnable body) {
        try {
            body.run();
            return null;
        } catch (PackageError e) {
            return e.code();
        }
    }

    static void manifestParsing() {
        eq(parse(null).sdk(), Map.of(), "没写 sdk 段就是空表，不是 null —— 老包不许因此炸");
        eq(parse("\"sdk\": {}").sdk(), Map.of(), "空对象也是空表");
        eq(parse("\"sdk\": {\"economy\": 1, \"cycle\": 2}").sdk(),
                Map.of("economy", 1, "cycle", 2), "读得出来");

        // 认不出的键照收：它是"需要更新 MCphone"的输入，不是清单坏了
        eq(parse("\"sdk\": {\"quantum\": 1}").sdk(), Map.of("quantum", 1),
                "没听说过的键解析层照收，判给门控");
        check(!SdkGate.satisfied(parse("\"sdk\": {\"quantum\": 1}").sdk()),
                "然后由门控拦下来");

        eq(codeOf(() -> parse("\"sdk\": {\"economy\": 0}")), PackageError.Code.E_PKG_BAD_SDK, "版本 0 拒");
        // 负数先被 requireInt 的"整数字面量"判据拦住（PLAIN_INT 不收负号），报的是类型不对 ——
        // 两条判据都拒，只是先后不同；把它改成 E_PKG_BAD_SDK 要动 requireInt，不值得
        eq(codeOf(() -> parse("\"sdk\": {\"economy\": -1}")), PackageError.Code.E_PKG_BAD_TYPE, "负版本拒");
        eq(codeOf(() -> parse("\"sdk\": {\"Economy\": 1}")), PackageError.Code.E_PKG_BAD_SDK, "大写键拒");
        eq(codeOf(() -> parse("\"sdk\": {\"\": 1}")), PackageError.Code.E_PKG_BAD_SDK, "空键拒");
        eq(codeOf(() -> parse("\"sdk\": []")), PackageError.Code.E_PKG_BAD_TYPE, "不是对象拒");
        eq(codeOf(() -> parse("\"sdk\": {\"economy\": \"1\"}")), PackageError.Code.E_PKG_BAD_TYPE, "字符串版本拒");
        eq(codeOf(() -> parse("\"sdk\": {\"economy\": 1.0}")), PackageError.Code.E_PKG_BAD_TYPE, "小数拒");

        StringBuilder many = new StringBuilder("\"sdk\": {");
        for (int i = 0; i <= Manifest.MAX_SDK_ENTRIES; i++) {
            if (i > 0) many.append(", ");
            many.append("\"k").append(i).append("\": 1");
        }
        many.append("}");
        eq(codeOf(() -> parse(many.toString())), PackageError.Code.E_PKG_BAD_SDK,
                "超过 " + Manifest.MAX_SDK_ENTRIES + " 项拒");
    }

    public static void main(String[] args) {
        manifestParsing();
        bijection();
        tiers();
        absent();
        gate();
        newerHostIsFine();

        System.out.println("断言 " + checks + " 条");
        if (!failures.isEmpty()) {
            System.out.println("失败 " + failures.size() + " 条：");
            for (String f : failures) System.out.println("  - " + f);
            System.exit(1);
        }
        System.out.println("全部通过");
    }
}
