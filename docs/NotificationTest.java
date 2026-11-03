package com.november.mcphone.api.sdk.notify;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * 通知（施工方案 §23.3，形状按 §33.2 的扩展版）。
 *
 * <p><b>这里测不了的</b>：角标真的跟着未读数变、服务端推送、去重真的只显示一条 ——
 * 都要运行时（S12/S13、§33.4）。这里钉的是形状与不变量。
 */
public class NotificationTest {

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

    static String rejects(Runnable body) {
        try {
            body.run();
            return null;
        } catch (RuntimeException e) {
            return e.getMessage();
        }
    }

    /** 两个版本都有的那一个构造法；new ResourceLocation(...) 在 1.21 已经私有化。 */
    static final ResourceLocation APP = ResourceLocation.tryParse("example:market");

    static Notification n(String titleKey, String dedupe, long expires) {
        return new Notification(1L, APP, "orders", titleKey, List.of("x"),
                null, null, 1_700_000_000_000L, Priority.NORMAL, dedupe, expires);
    }

    static void keysNotText() {
        eq(n("market.sold", null, 0).titleKey(), "market.sold", "titleKey 存得住");
        check(rejects(() -> n(null, null, 0)) != null, "titleKey 为 null 拒 —— 它是本地化键，不能没有");
        check(rejects(() -> n("", null, 0)) != null, "空串拒");
    }

    static void shapeFollowsSection33() {
        TreeSet<String> names = new TreeSet<>();
        for (var c : Notification.class.getRecordComponents()) names.add(c.getName());
        eq(names, new TreeSet<>(List.of("id", "appId", "topic", "titleKey", "titleArgs",
                        "bodyKey", "bodyArgs", "createdAt", "priority", "dedupeKey", "expiresAt")),
                "形状按 §33.2 的扩展版，不是 §23.3 那个七字段的原型");
        check(!names.contains("read"),
                "read 不在值类型里 —— 它是每玩家的可变状态，存下来的每一份都会撒谎");
        check(names.contains("dedupeKey"), "没有 dedupeKey，§33.4 那条去重判据永远实现不了");
        check(names.contains("id"), "没有 id 就没法标记单条已读");
    }

    static void defaults() {
        Notification a = new Notification(1L, APP, null, "k", null, null, null, 0L, null, null, 0L);
        eq(a.topic(), "", "topic 为 null 归一成空串");
        eq(a.titleArgs(), List.of(), "参数为 null 归一成空表");
        eq(a.bodyArgs(), List.of(), "正文参数同样");
        eq(a.priority(), Priority.NORMAL, "不写就是 NORMAL");
        eq(a.dedupeKey(), null, "dedupeKey 可以没有");
    }

    static void limits() {
        eq(Notification.MAX_PER_APP, 32, "每 App 每玩家 32 条");
        check(rejects(() -> n("k", "x".repeat(Notification.MAX_KEY + 1), 0)) != null, "dedupeKey 超长拒");
        check(rejects(() -> new Notification(1L, APP, "x".repeat(Notification.MAX_KEY + 1),
                "k", null, null, null, 0L, null, null, 0L)) != null, "topic 超长拒");
        check(rejects(() -> new Notification(1L, null, "t", "k", null, null, null, 0L, null, null, 0L)) != null,
                "appId 为 null 拒");
        check(rejects(() -> n("k", null, -1)) != null, "expiresAt 为负拒");
    }

    static void expiry() {
        check(!n("k", null, 0).expired(Long.MAX_VALUE), "expiresAt 为 0 就是永不过期");
        check(!n("k", null, 2000).expired(1999), "还没到");
        check(n("k", null, 2000).expired(2000), "到点即过期");
        check(n("k", null, 2000).expired(2001), "过了");
    }

    static void priorities() {
        eq(Priority.values().length, 3, "三档");
        check(Priority.HIGH != Priority.NORMAL, "只有 HIGH 上锁屏样式");
    }

    public static void main(String[] args) {
        keysNotText();
        shapeFollowsSection33();
        defaults();
        limits();
        expiry();
        priorities();

        System.out.println("断言 " + checks + " 条");
        if (!failures.isEmpty()) {
            System.out.println("失败 " + failures.size() + " 条：");
            for (String f : failures) System.out.println("  - " + f);
            System.exit(1);
        }
        System.out.println("全部通过");
    }
}
