package com.november.mcphone.api.sdk.mailbox;

import com.november.mcphone.api.sdk.item.Handles;
import com.november.mcphone.api.sdk.item.ItemRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 收件箱（施工方案 §23.3）：容量、批量上限、要么全成要么全不成。
 *
 * <p><b>这里测不了的</b>：保留 30 天与到期销毁要服务端时钟（S14+）；
 * "两个不同作者的 App 一个存一个读 count"要脚本运行时（S13）。
 * 下面那份 {@code Box} 是参照实现，证明"全成或全不成"这条规则可实现且自洽。
 */
public class MailboxTest {

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

    static void constants() {
        eq(IMailbox.CAPACITY, 27, "一个箱子 27 格");
        eq(IMailbox.RETENTION_DAYS, 30, "默认保留 30 天");
        eq(IMailbox.MAX_BATCH, ItemRef.MAX_BATCH, "批量上限与 ItemRef 同源，别各写各的");
        check(IMailbox.MAX_BATCH * Handles.LENGTH <= 4096,
                "一整批的句柄要塞得进 RPC 的 PARAMS_MAX 4096（§10.3）");
    }

    /** §23.4：不许复用错误码；§22.5 那条 NOT_AUTHORIZED 也要有。 */
    static void codes() {
        for (String name : new String[]{"OK", "FULL", "INVALID", "NOT_AUTHORIZED", "UNAVAILABLE", "FAILED"}) {
            boolean found = false;
            for (DepositResult r : DepositResult.values()) if (r.name().equals(name)) found = true;
            check(found, "DepositResult 要有 " + name);
        }
        check(DepositResult.FULL != DepositResult.INVALID, "「满了」与「参数不对」是两回事");
    }

    /** 一份最小的收件箱，只用来证明"全成或全不成"自洽。真正的实现是 S14+ 的事。 */
    static final class Box {
        final Map<UUID, List<ItemRef>> slots = new HashMap<>();

        List<ItemRef> of(UUID p) {
            return slots.computeIfAbsent(p, k -> new ArrayList<>());
        }

        DepositResult deposit(UUID p, List<ItemRef> items) {
            if (p == null || items == null || items.isEmpty()) return DepositResult.INVALID;
            if (items.size() > IMailbox.MAX_BATCH) return DepositResult.INVALID;
            List<ItemRef> box = of(p);
            // 先看放不放得下，再动手：放一半之后调用方没法知道放进去了哪几件，只能整批重试
            if (box.size() + items.size() > IMailbox.CAPACITY) return DepositResult.FULL;
            box.addAll(items);
            return DepositResult.OK;
        }

        int count(UUID p) {
            return of(p).size();
        }
    }

    static ItemRef item(int n) {
        return ItemRef.of("minecraft:stone", Math.max(1, n % 100));
    }

    static List<ItemRef> many(int n) {
        List<ItemRef> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(item(i + 1));
        return out;
    }

    static void allOrNothing() {
        Box b = new Box();
        UUID p = UUID.randomUUID();

        eq(b.deposit(p, many(20)), DepositResult.OK, "存 20 件");
        eq(b.count(p), 20, "件数");

        // 还剩 7 格，存 10 件 —— 不许存进去 7 件
        eq(b.deposit(p, many(10)), DepositResult.FULL, "放不下返回 FULL");
        eq(b.count(p), 20, "一件都没存进去，不是存了 7 件");

        eq(b.deposit(p, many(7)), DepositResult.OK, "刚好填满");
        eq(b.count(p), IMailbox.CAPACITY, "满了");

        eq(b.deposit(p, many(1)), DepositResult.FULL, "满了之后一件也存不下");
        eq(b.count(p), IMailbox.CAPACITY, "还是 27");
    }

    static void badArgs() {
        Box b = new Box();
        UUID p = UUID.randomUUID();
        eq(b.deposit(null, many(1)), DepositResult.INVALID, "玩家为 null");
        eq(b.deposit(p, null), DepositResult.INVALID, "物品表为 null");
        eq(b.deposit(p, List.of()), DepositResult.INVALID, "空表");
        eq(b.deposit(p, many(IMailbox.MAX_BATCH + 1)), DepositResult.INVALID, "超过一次的批量上限");
        eq(b.count(p), 0, "全都没存进去");
    }

    /** 两个不同的 App 往同一个玩家的箱子里存，count 读到的是同一个数（§23.6 的互通那一条，契约层）。 */
    static void oneBoxForEveryone() {
        Box b = new Box();
        UUID p = UUID.randomUUID();
        b.deposit(p, many(3));    // A 作者的 App
        b.deposit(p, many(2));    // B 作者的 App
        eq(b.count(p), 5, "全手机一个收件箱 —— 两个 App 存的加在一起");
    }

    public static void main(String[] args) {
        constants();
        codes();
        allOrNothing();
        badArgs();
        oneBoxForEveryone();

        System.out.println("断言 " + checks + " 条");
        if (!failures.isEmpty()) {
            System.out.println("失败 " + failures.size() + " 条：");
            for (String f : failures) System.out.println("  - " + f);
            System.exit(1);
        }
        System.out.println("全部通过");
    }
}
