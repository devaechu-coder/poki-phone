package com.november.mcphone.core.script.sfc;

import java.util.List;
import java.util.Map;

/** 表达式的值：Integer / String / Boolean / List / Map / null（施工方案 §9.5.3–§9.5.6）。 */
public final class Values {

    private Values() {
    }

    /** 报错文案里的类型名。 */
    public static String kind(Object v) {
        if (v == null) return "null";
        if (v instanceof Integer) return "int";
        if (v instanceof String) return "string";
        if (v instanceof Boolean) return "bool";
        if (v instanceof List<?>) return "array";
        if (v instanceof Map<?, ?>) return "object";
        return "?";
    }

    /** §9.5.4：null / false / 0 / "" 为假，空数组与空对象为真。 */
    public static boolean truthy(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Integer i) return i != 0;
        if (v instanceof String s) return !s.isEmpty();
        return true;
    }

    /** §9.5.6 文本化。null 是空串而不是 "null"：数据没到时显示空白。数组与对象也是空串，由调用方记 warn。 */
    public static String text(Object v) {
        if (v == null || v instanceof List<?> || v instanceof Map<?, ?>) return "";
        return String.valueOf(v);
    }

    /** == 的语义：类型不同一律不等，不做隐式转换。 */
    public static boolean same(Object a, Object b) {
        if (a == null || b == null) return a == b;
        return kind(a).equals(kind(b)) && a.equals(b);
    }

    /** 截到 max 个 UTF-16 码元，不把代理对切成两半。 */
    static String cut(String s, int max) {
        if (s.length() <= max) return s;
        int end = Character.isHighSurrogate(s.charAt(max - 1)) ? max - 1 : max;
        return s.substring(0, end);
    }
}
