package com.november.mcphone.core.script.sfc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** 报错时的「是不是想写 X」：编辑距离 ≤ 2 的最近那个（§9.4.1、§9.5.5）。 */
final class Names {

    static final int MAX_DISTANCE = 2;

    private Names() {
    }

    /** 最近的候选，没有距离 ≤ 2 的返回 null。距离相同取字典序靠前的：Set 的迭代顺序逐进程随机，不排的话同一条报错两次跑建议不同。 */
    static String closest(String name, Collection<String> candidates) {
        String best = null;
        int bestDistance = MAX_DISTANCE + 1;
        for (String c : sorted(candidates)) {
            int d = distance(name, c, bestDistance);
            if (d < bestDistance) {
                best = c;
                bestDistance = d;
            }
        }
        return best;
    }

    /** 拼进文案的那一段：{@code （是不是 'count'）}，没有建议时是空串。 */
    static String hint(String name, Collection<String> candidates) {
        String c = closest(name, candidates);
        return c == null ? "" : "（是不是 '" + c + "'）";
    }

    static String list(Collection<String> names) {
        return names.isEmpty() ? "（空）" : String.join(" ", sorted(names));
    }

    static List<String> sorted(Collection<String> names) {
        List<String> out = new ArrayList<>(names);
        out.sort(null);
        return out;
    }

    /** 编辑距离；不小于 cap 时直接返回 cap，长名字不必算满。 */
    static int distance(String a, String b, int cap) {
        if (Math.abs(a.length() - b.length()) >= cap) return cap;
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            int rowMin = cur[0];
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                rowMin = Math.min(rowMin, cur[j]);
            }
            if (rowMin >= cap) return cap;
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return Math.min(prev[b.length()], cap);
    }
}
