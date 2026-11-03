package com.november.mcphone.core.script.layout;

import java.util.List;
import java.util.Map;

/**
 * 一个界面节点。不可变（施工方案 §4.6）。
 *
 * <p>这个类刻意不带任何样式字段：样式全在 Stylesheet 里按 id/class 查。往这里加一个 color
 * 字段，换肤和统一调整当场失效。
 */
public record Node(
        NodeType type,
        String id,                  // 可为 null
        List<String> classes,       // 永不为 null，可为空
        Map<String, Object> props,  // 组件自己的字段，已按 §5 校验过类型
        List<Node> children,        // 永不为 null，可为空
        ShowIf showIf,              // 可为 null
        Action onClick              // 可为 null
) {
    public String str(String key, String def) {
        Object v = props.get(key);
        return v instanceof String s ? s : def;
    }

    public int num(String key, int def) {
        Object v = props.get(key);
        return v instanceof Integer i ? i : def;
    }

    public boolean flag(String key, boolean def) {
        Object v = props.get(key);
        return v instanceof Boolean b ? b : def;
    }

    /** 条件显示（§4.4）。{@code button} 的 {@code enabledIf} 用的是同一个形状。 */
    public record ShowIf(String key, Kind kind, Object value) {
        public enum Kind { EQ, NE, TRUTHY }
    }

    /**
     * 点击动作（§4.5）。执行顺序固定 set → toggle → nav。
     *
     * <p>{@code builtin} 是字符串形式的 {@code "close"}/{@code "back"}，与另外三个互斥。
     */
    public record Action(Map<String, Object> set, String toggle, String nav, Builtin builtin) {
        public enum Builtin { NONE, CLOSE, BACK }
    }
}
