package com.november.mcphone.core.script.layout;

import com.november.mcphone.core.script.layout.MssError.Pos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** 解析好的 ui.mss（施工方案 §6）。不可变，由 {@link MssParser#parse} 产出。 */
public final class Stylesheet {

    /** 一条规则：选择器和它的声明。每条声明是对 Builder 的一次写入，按原文顺序。 */
    record Rule(boolean id, String name, Pos at, List<Consumer<Style.Builder>> decls) {
        void applyTo(Style.Builder b) {
            for (Consumer<Style.Builder> d : decls) d.accept(b);
        }
    }

    private final List<Rule> classRules = new ArrayList<>();
    private final Map<String, Rule> byClass = new HashMap<>();
    private final Map<String, Rule> byId = new HashMap<>();

    Stylesheet(List<Rule> rules) {
        for (Rule r : rules) {
            if (r.id()) {
                byId.put(r.name(), r);
            } else {
                classRules.add(r);
                byClass.put(r.name(), r);
            }
        }
    }

    public int ruleCount() {
        return classRules.size() + byId.size();
    }

    /** 只套这一条 class 规则、不看节点类型时的样式，测试用：任何真实节点都要走 {@link #resolve}。没有这条规则返回 null。 */
    Style forClass(String name) {
        return alone(byClass.get(name));
    }

    /** 同 {@link #forClass}，取 id 规则。 */
    Style forId(String name) {
        return alone(byId.get(name));
    }

    /**
     * 按 §6.3 的四层叠出一个节点的样式，后一层盖前一层。
     *
     * <p>没有继承：只看这个节点自己的类型、class、id 与内容字段，父节点写了什么与它无关。
     */
    public Style resolve(Node node) {
        Style.Builder b = Style.defaults(node.type());
        // 按规则在文件里的顺序套，不按节点 class 数组的顺序：否则同一组 class 在两个节点上会叠出两种结果
        for (Rule r : classRules) {
            if (node.classes().contains(r.name())) r.applyTo(b);
        }
        // id 选择器不会重复（E_MSS_DUP_SELECTOR），一个节点最多命中一条
        Rule byNodeId = node.id() == null ? null : byId.get(node.id());
        if (byNodeId != null) byNodeId.applyTo(b);
        implied(node, b);
        return b.build();
    }

    /** 第 4 层：节点内容字段对样式的强制影响（§5.2），盖过样式表里写的任何值。 */
    private static void implied(Node node, Style.Builder b) {
        switch (node.type()) {
            case BADGE -> {
                // 留下 grow、边框或底色中的任何一样，count 为 0 的角标都会占位或画出一块色
                if (node.num("count", 0) == 0) {
                    collapse(b);
                    b.grow = 0;
                    b.border = 0;
                    b.background = null;
                    b.hoverBackground(null);
                }
            }
            case SPACER -> {
                if (node.num("size", 0) == 0) {
                    collapse(b);
                    b.grow = 1;
                }
            }
            default -> { }
        }
    }

    private static void collapse(Style.Builder b) {
        b.width = b.height = Style.SizeSpec.fixed(0);
        b.padTop = b.padRight = b.padBottom = b.padLeft = 0;
    }

    private static Style alone(Rule r) {
        if (r == null) return null;
        Style.Builder b = Style.defaults(null);
        r.applyTo(b);
        return b.build();
    }
}
