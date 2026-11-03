package com.november.mcphone.api.sdk.player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 玩家引用（施工方案 §23.3）。
 *
 * <p><b>这里测不了的</b>："脚本不能凭名字构造 PlayerRef"要枚举脚本桥才能验（S13）；
 * 离线模式服务器的身份可伪造是服务端配置的事（§13.5）。
 */
public class PlayerRefTest {

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
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    static final UUID U = UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301");

    static void uuidIsIdentity() {
        eq(new PlayerRef(U, "yumeka").uuid(), U, "uuid 存得住");
        check(rejects(() -> new PlayerRef(null, "yumeka")) != null, "uuid 为 null 拒 —— 它是唯一身份");

        // 同一个 uuid 改了名还是同一个人；名字相同但 uuid 不同不是
        eq(new PlayerRef(U, "old").uuid(), new PlayerRef(U, "new").uuid(), "改名不改身份");
        check(!new PlayerRef(U, "a").equals(new PlayerRef(UUID.randomUUID(), "a")),
                "名字相同、uuid 不同 → 不是同一个人");
    }

    static void nameIsDisplayOnly() {
        eq(new PlayerRef(U, null).name(), "", "null 归一成空串");
        eq(new PlayerRef(U, "").name(), "", "空名字收下 —— 离线玩家可能没见过名字");
        eq(PlayerRef.MAX_NAME, 32, "上限");
        String longName = "x".repeat(100);
        eq(new PlayerRef(U, longName).name().length(), PlayerRef.MAX_NAME, "超长截断，不抛");
    }

    /**
     * 没有 online 这一格。
     *
     * <p>PlayerRef 会被存进 App 的 KV、写进挂单、发进邮件；存下来的那一刻 online 就开始撒谎。
     * 而 §23.4 明令"删字段"不允许 —— 加错了就是永久的。这条断言钉住它别被加回来。
     */
    static void noMutableState() {
        int components = PlayerRef.class.getRecordComponents().length;
        eq(components, 2, "只有 uuid 与 name 两格");
        java.util.TreeSet<String> names = new java.util.TreeSet<>();
        for (var c : PlayerRef.class.getRecordComponents()) names.add(c.getName());
        eq(names, new java.util.TreeSet<>(List.of("name", "uuid")), "字段就这两个，别把 online 加回来");
    }

    public static void main(String[] args) {
        uuidIsIdentity();
        nameIsDisplayOnly();
        noMutableState();

        System.out.println("断言 " + checks + " 条");
        if (!failures.isEmpty()) {
            System.out.println("失败 " + failures.size() + " 条：");
            for (String f : failures) System.out.println("  - " + f);
            System.exit(1);
        }
        System.out.println("全部通过");
    }
}
