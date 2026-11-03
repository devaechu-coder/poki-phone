package com.november.mcphone.core.script.layout;

import static com.november.mcphone.core.script.layout.MssParser.parse;

import com.november.mcphone.api.client.ui.PhoneStyle;
import com.november.mcphone.core.script.layout.MssError.Code;
import com.november.mcphone.core.script.layout.Style.Align;
import com.november.mcphone.core.script.layout.Style.Justify;
import com.november.mcphone.core.script.layout.Style.Layout;
import com.november.mcphone.core.script.layout.Style.SizeSpec;
import com.november.mcphone.core.script.layout.Style.TextAlign;
import com.november.mcphone.core.script.layout.Style.Token;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** MSS 样式表的断言测试（施工方案 §6），用 javac 单独编，不需要 Minecraft。 */
public class MssParserTest {

    static int checks = 0;
    static int rejections = 0;
    static final List<String> failures = new ArrayList<>();
    /** 每个错误码第一次被触发时的输入。§6.7 全表逐条实现，靠它坐实。 */
    static final Map<Code, String> triggered = new EnumMap<>(Code.class);

    static void eq(Object actual, Object expected, String what) {
        checks++;
        if (!java.util.Objects.equals(actual, expected)) {
            failures.add(what + "  期望 " + expected + "，实际 " + actual);
        }
    }

    static void check(boolean cond, String what) {
        checks++;
        if (!cond) failures.add(what);
    }

    static void report() {
        System.out.println();
        if (failures.isEmpty()) {
            System.out.println("全部通过：" + checks + " 条断言");
        } else {
            System.out.println("失败 " + failures.size() + " / " + checks + " 条：");
            for (String f : failures) System.out.println("  ✗ " + f);
            System.exit(1);
        }
    }

    public static void main(String[] a) {
        skeleton();
        propertyTable();
        tokenTable();
        layers();
        grammar();
        rejectionList();
        positions();
        suggestions();
        namesMatchNodeParser();
        templateTable();
        fuzz();
        errorTableCovered();
        report();
    }

    // ============================================================
    //  §6.8 骨架
    // ============================================================

    static void skeleton() {
        // 基本解析
        Stylesheet s = parse(".t { color: $title; padding: 2 4; }");
        eq(s.forClass("t").color(), Token.TITLE, "class 规则生效");
        eq(s.forClass("t").padTop(), 2, "padding 两值：上下");
        eq(s.forClass("t").padLeft(), 4, "padding 两值：左右");

        // padding 四种写法
        eq(parse(".a{padding:1;}").forClass("a").padRight(), 1, "padding 一值");
        eq(parse(".a{padding:1 2 3;}").forClass("a").padBottom(), 3, "padding 三值");
        eq(parse(".a{padding:1 2 3 4;}").forClass("a").padLeft(), 4, "padding 四值");

        // 覆盖顺序：后出现的赢
        // 同名选择器会先触发 E_MSS_DUP_SELECTOR，所以覆盖顺序用不同选择器验证
        rejects(".a{color:$body;} .a{color:$title;}", "E_MSS_DUP_SELECTOR");
        eq(parse(".a{color:$body;} #x{color:$title;}").resolve(node(NodeType.TEXT, "x", "a")).color(),
                Token.TITLE, "#x 覆盖 .a");

        // 全部拒绝项
        rejects(".a .b { color: $title; }", "E_MSS_COMBINATOR");
        rejects(".a, .b { color: $title; }", "E_MSS_MULTI_SELECTOR");
        rejects(".a:hover { color: $title; }", "E_MSS_PSEUDO");
        rejects(".a { color: #ff0000; }", "E_LITERAL_COLOR");
        rejects(".a { width: 50%; }", "E_PERCENT_NOT_SUPPORTED");
        rejects(".a { maxLines: 2; }", "E_UNKNOWN_PROPERTY");
        rejects(".a { width: 999; }", "E_MSS_BAD_VALUE");
        rejects(".a { color: $nope; }", "E_MSS_BAD_VALUE");
        rejects(".a { color: $title; ", "E_MSS_UNCLOSED");
        rejects("a { color: $title; }", "E_MSS_BAD_SELECTOR");

        // 拼写建议
        check(errorOf(".a{maxLines:2;}").message().contains("max-lines"), "拼错属性名给出正解");

        // 行列号
        eq(errorOf("\n\n  .a { color: #fff; }").line(), 3, "错误行号正确");
    }

    // ============================================================
    //  §6.5 属性表，逐行
    // ============================================================

    static final String TOKENS = "$title | $body | $subtle | $accent | $screen | $pressed | $button"
            + " | $button-hover | $button-disabled | $button-disabled-text";

    /** 属性名与「允许」，行序即 §6.5 的行序。 */
    static final String[][] PROPERTY_TABLE = {
            {"layout", "column | row | stack"},
            {"width", "auto | fill | 0..120 的整数"},
            {"height", "auto | fill | 0..176 的整数"},
            {"grow", "0..16 的整数"},
            {"padding", "1 到 4 个 0..32 的整数"},
            {"gap", "0..32 的整数"},
            {"align", "start | center | end | stretch"},
            {"justify", "start | center | end | between"},
            {"background", "none | " + TOKENS},
            {"hover-background", "none | " + TOKENS},
            {"color", TOKENS},
            {"hover-color", TOKENS},
            {"border", "0..2 的整数"},
            {"shadow", "true | false"},
            {"text-align", "left | center | right"},
            {"wrap", "true | false"},
            {"max-lines", "none | 1..32 的整数"},
            {"hidden", "true | false"},
    };

    static void propertyTable() {
        List<String> names = new ArrayList<>();
        for (String[] row : PROPERTY_TABLE) names.add(row[0]);
        eq(names.size(), 18, "§6.5 表是 18 行");
        eq(MssParser.properties(), names, "18 个属性与 §6.5 同名同序");
        for (String[] row : PROPERTY_TABLE) {
            eq(MssParser.allowed(row[0]), row[1], row[0] + " 的取值与 §6.5 一致");
            check(errorOf(".e{" + row[0] + ": ???;}").message().endsWith("允许：" + row[1]),
                    row[0] + " 取值不合法时文案列出允许的取值");
        }

        // 「默认」列
        Style d = one("hidden: false");
        eq(d.layout(), Layout.COLUMN, "layout 默认 column");
        eq(d.width(), SizeSpec.AUTO, "width 默认 auto");
        eq(d.height(), SizeSpec.AUTO, "height 默认 auto");
        eq(d.grow(), 0, "grow 默认 0");
        eq(pads(d), List.of(0, 0, 0, 0), "padding 默认 0");
        eq(d.gap(), 0, "gap 默认 0");
        eq(d.align(), Align.START, "align 默认 start");
        eq(d.justify(), Justify.START, "justify 默认 start");
        eq(d.background(), null, "background 默认 none");
        eq(d.hoverBackground(), null, "hover-background 默认同 background（none）");
        eq(d.color(), Token.BODY, "color 默认 $body");
        eq(d.hoverColor(), Token.BODY, "hover-color 默认同 color（$body）");
        eq(d.border(), 0, "border 默认 0");
        eq(d.shadow(), false, "shadow 默认 false");
        eq(d.textAlign(), TextAlign.LEFT, "text-align 默认 left");
        eq(d.wrap(), true, "wrap 默认 true");
        eq(d.maxLines(), Style.NO_MAX_LINES, "max-lines 默认 none");
        eq(d.hidden(), false, "hidden 默认 false");
        eq(one("background: $button").hoverBackground(), Token.BUTTON, "hover-background 跟着 background 走");
        eq(one("color: $accent").hoverColor(), Token.ACCENT, "hover-color 跟着 color 走");

        // 每个取值都写得进去，边界两侧各一条
        for (Layout v : Layout.values()) eq(one("layout: " + lower(v)).layout(), v, "layout: " + lower(v));
        bad("layout: grid");

        eq(one("width: auto").width(), SizeSpec.AUTO, "width: auto");
        eq(one("width: fill").width(), SizeSpec.FILL, "width: fill");
        eq(one("width: 0").width(), SizeSpec.fixed(0), "width: 0");
        eq(one("width: 120").width(), SizeSpec.fixed(120), "width: 120");
        bad("width: 121");
        bad("width: -1");
        bad("width: 12px");
        bad("width: auto fill");
        eq(one("height: 176").height(), SizeSpec.fixed(176), "height: 176");
        eq(one("height: fill").height(), SizeSpec.FILL, "height: fill");
        bad("height: 177");

        eq(one("grow: 0").grow(), 0, "grow: 0");
        eq(one("grow: 16").grow(), 16, "grow: 16");
        bad("grow: 17");
        bad("grow: -1");

        eq(pads(one("padding: 5")), List.of(5, 5, 5, 5), "padding 一值 = 四边");
        eq(pads(one("padding: 1 2")), List.of(1, 2, 1, 2), "padding 两值 = 上下 / 左右");
        eq(pads(one("padding: 1 2 3")), List.of(1, 2, 3, 2), "padding 三值 = 上 / 左右 / 下");
        eq(pads(one("padding: 1 2 3 4")), List.of(1, 2, 3, 4), "padding 四值 = 上 / 右 / 下 / 左");
        eq(pads(one("padding: 0 32")), List.of(0, 32, 0, 32), "padding 边界 0 与 32");
        bad("padding: 33");
        bad("padding: 1 2 3 4 5");
        bad("padding: 1 -2");

        eq(one("gap: 32").gap(), 32, "gap: 32");
        bad("gap: 33");
        bad("gap: 1 2");
        bad("gap:");

        for (Align v : Align.values()) eq(one("align: " + lower(v)).align(), v, "align: " + lower(v));
        bad("align: between");
        for (Justify v : Justify.values()) eq(one("justify: " + lower(v)).justify(), v, "justify: " + lower(v));
        bad("justify: stretch");

        eq(one("background: none").background(), null, "background: none");
        eq(one("background: $screen").background(), Token.SCREEN, "background: $screen");
        bad("background: title");
        Style hoverNone = one("background: $button; hover-background: none");
        eq(hoverNone.background(), Token.BUTTON, "写了 hover-background: none 时 background 不变");
        eq(hoverNone.hoverBackground(), null, "hover-background: none 与「没写」不同：悬停时真的没有底色");
        eq(one("hover-background: $button-hover").hoverBackground(), Token.BUTTON_HOVER, "hover-background: $button-hover");

        bad("color: none");
        bad("color: $title $body");
        eq(one("hover-color: $title").hoverColor(), Token.TITLE, "hover-color: $title");
        eq(one("hover-color: $title").color(), Token.BODY, "hover-color 不动 color");
        bad("hover-color: none");

        eq(one("border: 2").border(), 2, "border: 2");
        bad("border: 3");

        for (String flag : List.of("shadow", "wrap", "hidden")) {
            bad(flag + ": yes");
            bad(flag + ": 1");
            bad(flag + ": TRUE");
        }
        eq(one("shadow: true").shadow(), true, "shadow: true");
        eq(one("wrap: false").wrap(), false, "wrap: false");
        eq(one("hidden: true").hidden(), true, "hidden: true");

        for (TextAlign v : TextAlign.values()) {
            eq(one("text-align: " + lower(v)).textAlign(), v, "text-align: " + lower(v));
        }
        bad("text-align: justify");

        eq(one("max-lines: none").maxLines(), Style.NO_MAX_LINES, "max-lines: none");
        eq(one("max-lines: 1").maxLines(), 1, "max-lines: 1");
        eq(one("max-lines: 32").maxLines(), 32, "max-lines: 32");
        bad("max-lines: 0");
        bad("max-lines: 33");

        check(errorOf(".a { width: 999; }").message()
                        .contains("属性 'width' 的值 '999' 不合法。允许：auto | fill | 0..120 的整数"),
                "E_MSS_BAD_VALUE 的文案带属性、原值与允许的取值");
    }

    // ============================================================
    //  §6.6 token 表
    // ============================================================

    /** token 与 PhoneStyle 方法，行序即 §6.6 的行序。 */
    static final String[][] TOKEN_TABLE = {
            {"$title", "titleColor"},
            {"$body", "bodyColor"},
            {"$subtle", "subtleColor"},
            {"$accent", "accentColor"},
            {"$screen", "screenBackground"},
            {"$pressed", "pressedOverlay"},
            {"$button", "buttonColor"},
            {"$button-hover", "buttonHoverColor"},
            {"$button-disabled", "buttonDisabledColor"},
            {"$button-disabled-text", "buttonDisabledTextColor"},
    };

    static void tokenTable() {
        eq(Token.values().length, 10, "10 个 token");
        Set<String> methods = new TreeSet<>();
        for (int i = 0; i < TOKEN_TABLE.length; i++) {
            Token t = Token.values()[i];
            eq(t.mss, TOKEN_TABLE[i][0], "第 " + (i + 1) + " 个 token 与 §6.6 同名同序");
            eq(Token.of(TOKEN_TABLE[i][0]), t, TOKEN_TABLE[i][0] + " 查得回来");
            eq(one("color: " + TOKEN_TABLE[i][0]).color(), t, TOKEN_TABLE[i][0] + " 写得进 color");
            methods.add(TOKEN_TABLE[i][1]);
        }
        Set<String> colorMethods = new TreeSet<>();
        for (Method m : PhoneStyle.class.getMethods()) {
            if (m.getParameterCount() == 0 && m.getReturnType() == int.class) colorMethods.add(m.getName());
        }
        eq(colorMethods, methods, "PhoneStyle 的颜色方法与 §6.6 的十个 token 一一对应");
        eq(Token.of("$nope"), null, "不认识的 token 查不到");
        eq(Token.of("title"), null, "不带 $ 的查不到");
    }

    // ============================================================
    //  §6.3 覆盖顺序，逐层
    // ============================================================

    static void layers() {
        // 第 1 层：组件类型默认值
        Stylesheet empty = parse("");
        eq(empty.ruleCount(), 0, "空文件没有规则");
        eq(pads(empty.resolve(node(NodeType.BUTTON, null))), List.of(2, 6, 2, 6), "1 层：button 默认 padding 2 6");
        eq(empty.resolve(node(NodeType.DIVIDER, null)).color(), Token.SUBTLE, "1 层：divider 默认 $subtle");
        eq(empty.resolve(node(NodeType.SCROLL, null)).height(), SizeSpec.FILL, "1 层：scroll 隐含 height: fill");
        eq(empty.resolve(node(NodeType.LIST, null)).height(), SizeSpec.FILL, "1 层：list 隐含 height: fill");
        eq(empty.resolve(node(NodeType.TEXT, null)).height(), SizeSpec.AUTO, "1 层：其余类型取表里的 auto");
        eq(empty.resolve(node(NodeType.TEXT, null)).color(), Token.BODY, "1 层：text 默认 $body");
        Style button = empty.resolve(node(NodeType.BUTTON, null));
        eq(button.background(), Token.BUTTON, "1 层：button 默认底色 $button");
        eq(button.hoverBackground(), Token.BUTTON_HOVER, "1 层：button 默认悬停底色 $button-hover");
        eq(empty.resolve(node(NodeType.BOX, null)).layout(), Layout.COLUMN, "1 层：box 默认 column");
        eq(empty.resolve(node(NodeType.COLUMN, null)).layout(), Layout.COLUMN, "1 层：column 的 layout 与类型一致");
        eq(empty.resolve(node(NodeType.ROW, null)).layout(), Layout.ROW, "1 层：row 的 layout 与类型一致");
        eq(empty.resolve(node(NodeType.STACK, null)).layout(), Layout.STACK, "1 层：stack 的 layout 与类型一致");

        // 第 2 层盖第 1 层
        Stylesheet two = parse(".flat{padding:0;} .tall{height:40;} .sub{color:$accent;}");
        eq(pads(two.resolve(node(NodeType.BUTTON, null, "flat"))), List.of(0, 0, 0, 0), "2 层：class 盖过 button 的默认 padding");
        eq(two.resolve(node(NodeType.SCROLL, null, "tall")).height(), SizeSpec.fixed(40), "2 层：class 盖过 scroll 的 fill");
        eq(two.resolve(node(NodeType.DIVIDER, null, "sub")).color(), Token.ACCENT, "2 层：class 盖过 divider 的 $subtle");
        eq(pads(two.resolve(node(NodeType.BUTTON, null, "other"))), List.of(2, 6, 2, 6), "2 层：不匹配的 class 不生效");
        Style link = parse(".link{background:none;}").resolve(node(NodeType.BUTTON, null, "link"));
        eq(link.background(), null, "2 层：button 写 background: none 去掉底色（§5.2 的可点文字）");
        eq(link.hoverBackground(), Token.BUTTON_HOVER, "2 层：只写 background 时 button 仍保留默认悬停底色");
        Stylesheet layouts = parse(".actions{layout:row;} .x{layout:column;}");
        eq(layouts.resolve(node(NodeType.ROW, null, "actions")).layout(), Layout.ROW,
                "2 层：row 上写 layout: row 与类型一致（§9.2 的写法不该被当成冲突）");
        eq(layouts.resolve(node(NodeType.ROW, null, "x")).layout(), Layout.COLUMN,
                "2 层：row 上写 layout: column 留在 Style 里，S4 据此发现要被忽略的 layout");

        // 第 2 层内部：按文件顺序，不按节点 class 数组的顺序
        Stylesheet ba = parse(".b{color:$title;} .a{color:$accent;}");
        eq(ba.resolve(node(NodeType.TEXT, null, "a", "b")).color(), Token.ACCENT, "2 层：文件里后出现的 .a 赢（节点写 a b）");
        eq(ba.resolve(node(NodeType.TEXT, null, "b", "a")).color(), Token.ACCENT, "2 层：文件里后出现的 .a 赢（节点写 b a）");
        Stylesheet ab = parse(".a{color:$accent;} .b{color:$title;}");
        eq(ab.resolve(node(NodeType.TEXT, null, "a", "b")).color(), Token.TITLE, "2 层：规则顺序反过来，结果跟着反");
        Style merged = parse(".a{color:$accent;} .b{gap:4;}").resolve(node(NodeType.BOX, null, "b", "a"));
        eq(merged.color(), Token.ACCENT, "2 层：两条 class 写不同属性时各自生效（color）");
        eq(merged.gap(), 4, "2 层：两条 class 写不同属性时各自生效（gap）");

        // 第 3 层盖第 2 层，与文件顺序无关
        Stylesheet three = parse("#x{color:$title;} .a{color:$accent; gap:2;}");
        Style x = three.resolve(node(NodeType.TEXT, "x", "a"));
        eq(x.color(), Token.TITLE, "3 层：#x 写在 .a 前面也盖过它");
        eq(x.gap(), 2, "3 层：id 没写的属性保留 class 的值");
        eq(three.resolve(node(NodeType.TEXT, "y", "a")).color(), Token.ACCENT, "3 层：id 不匹配时不生效");
        eq(three.resolve(node(NodeType.TEXT, null, "a")).color(), Token.ACCENT, "3 层：节点没有 id 时不生效");
        eq(three.resolve(node(NodeType.TEXT, "x")).color(), Token.TITLE, "3 层：只有 id 也生效");

        // 第 4 层盖第 3 层
        Stylesheet four = parse("#b{width:20; height:10; padding:3;} #s{grow:5;} .g{grow:4;}");
        Style badge0 = four.resolve(withInt(NodeType.BADGE, "b", "count", 0));
        eq(badge0.width(), SizeSpec.fixed(0), "4 层：count 为 0 的 badge 宽 0，盖过 #b");
        eq(badge0.height(), SizeSpec.fixed(0), "4 层：count 为 0 的 badge 高 0，盖过 #b");
        eq(pads(badge0), List.of(0, 0, 0, 0), "4 层：count 为 0 的 badge padding 归零，盖过 #b");
        eq(four.resolve(node(NodeType.BADGE, "b")).width(), SizeSpec.fixed(0), "4 层：没写 count 按默认 0 算");
        Style badge3 = four.resolve(withInt(NodeType.BADGE, "b", "count", 3));
        eq(badge3.width(), SizeSpec.fixed(20), "4 层：count 不为 0 时 #b 照常生效");
        eq(pads(badge3), List.of(3, 3, 3, 3), "4 层：count 不为 0 时 padding 照常生效");
        eq(four.resolve(withInt(NodeType.SPACER, "s", "size", 0)).grow(), 1, "4 层：弹性 spacer 的 grow 是 1，盖过 #s");
        eq(four.resolve(new Node(NodeType.SPACER, null, List.of("g"), Map.of(), List.of(), null, null)).grow(), 1,
                "4 层：弹性 spacer 的 grow 是 1，盖过 .g");
        eq(four.resolve(withInt(NodeType.SPACER, "s", "size", 8)).grow(), 5, "4 层：定长 spacer 照常取 #s");
        Stylesheet loud = parse("#b{grow:3; border:2; background:$accent; hover-background:$title;}"
                + " #s{width:20; height:30; padding:4;}");
        Style silent = loud.resolve(withInt(NodeType.BADGE, "b", "count", 0));
        eq(List.of(silent.grow(), silent.border()), List.of(0, 0), "4 层：count 为 0 的 badge 不分 grow、不画边框");
        eq(silent.background(), null, "4 层：count 为 0 的 badge 没有底色");
        eq(silent.hoverBackground(), null, "4 层：count 为 0 的 badge 没有悬停底色");
        Style flex = loud.resolve(withInt(NodeType.SPACER, "s", "size", 0));
        eq(List.of(flex.width(), flex.height()), List.of(SizeSpec.fixed(0), SizeSpec.fixed(0)), "4 层：弹性 spacer 是 0×0，盖过 #s");
        eq(pads(flex), List.of(0, 0, 0, 0), "4 层：弹性 spacer 没有 padding");
        eq(loud.resolve(withInt(NodeType.SPACER, "s", "size", 8)).width(), SizeSpec.fixed(20), "4 层：定长 spacer 照常取宽");

        // 没有继承
        Node child = node(NodeType.TEXT, "child");
        Node parent = new Node(NodeType.COLUMN, "parent", List.of("p"), Map.of(), List.of(child), null, null);
        Stylesheet inherit = parse("#parent{color:$title; padding:4;} .p{gap:3;}");
        eq(inherit.resolve(parent).color(), Token.TITLE, "父节点拿到 #parent");
        eq(inherit.resolve(child).color(), Token.BODY, "无继承：子节点不从父节点拿 color");
        eq(pads(inherit.resolve(child)), List.of(0, 0, 0, 0), "无继承：子节点不从父节点拿 padding");
        eq(inherit.resolve(child).gap(), 0, "无继承：子节点不从父节点的 class 拿 gap");

        eq(three.forClass("zzz"), null, "没有这条 class 规则时 forClass 返回 null");
        eq(three.forId("x").color(), Token.TITLE, "forId 取到 id 规则");
        eq(three.forId("a"), null, ".a 不是 #a");
        eq(parse(".a{} #a{}").ruleCount(), 2, ".a 与 #a 是两个选择器，不算重复");
    }

    // ============================================================
    //  §6.2 文法
    // ============================================================

    static void grammar() {
        eq(parse("  \n\t\r\n ").ruleCount(), 0, "只有空白");
        eq(parse("// 只有注释\n/* 块\n注释 */").ruleCount(), 0, "只有注释");
        eq(parse("// 行注释可以一直到文件尾").ruleCount(), 0, "行注释到文件尾为止也行");
        eq(parse("/*a*/.t/*b*/{/*c*/color/*d*/:/*e*/$title/*f*/;/*g*/}// h").forClass("t").color(), Token.TITLE,
                "注释可以夹在任何两个记号之间");
        eq(parse(".t{color:$title; // 行注释\n gap: 2;}").forClass("t").gap(), 2, "行注释到行尾为止");
        eq(parse(".t{color:/**/$title;}").forClass("t").color(), Token.TITLE, "值前面的块注释");
        eq(parse(".t{}").forClass("t").color(), Token.BODY, "空规则合法");
        eq(parse(".nav-item_2{} #a-b_c{}").ruleCount(), 2, "名字里可以有 - _ 与数字");
        eq(parse(".t{padding:1   2\n\t3 4;}").forClass("t").padBottom(), 3, "多值之间可以是任意空白");
        eq(parse(".t { gap : 7 ; }").forClass("t").gap(), 7, "冒号、分号两边可以有空白");
        eq(parse(".t{gap:007;}").forClass("t").gap(), 7, "前导零照 integer 产生式收");
        eq(parse(".t{grow:-0;}").forClass("t").grow(), 0, "-0 是 0");
        eq(parse("\uFEFF.t{gap:1;}").forClass("t").gap(), 1, "开头的 BOM 被跳过");
        eq(parse(".t{color:$title;}\r\n.u{color:$body;}\r\n").ruleCount(), 2, "CRLF 换行");
        eq(parse("/* 注释里的全角：；｛｝\u3000没关系 */.a{}").ruleCount(), 1, "注释里的全角字符不算错");
        eq(parse("." + "a".repeat(32) + "{}").ruleCount(), 1, "32 个字符的名字正好到顶");

        eq(parse(rules(64)).ruleCount(), 64, "64 条规则正好到顶");
        rejects(rules(65), "E_MSS_TOO_MANY_RULES");
        check(errorOf(rules(65)).message().contains("规则数 65 超过上限 64"), "超限文案带规则数");
        check(errorOf(rules(100)).message().contains("规则数 100"), "报的是总规则数");

        bad("gap: 99999999999999999999");
        bad("gap: -99999999999999999999");
        bad("gap: -");
        bad("gap: +1");
        bad("color: $TITLE");
        bad("layout: COLUMN");
        bad("padding: 1 2 3 4 5 6 7 8");
        check(errorOf(".e{padding: 1 2 3 4 5 6;}").message().contains("'1 2 3 4 5"), "值超过 4 个词时回显已读到的部分");
    }

    // ============================================================
    //  拒绝项
    // ============================================================

    static void rejectionList() {
        rejects(".a.b{}", "E_MSS_BAD_SELECTOR");
        rejects(".a#b{}", "E_MSS_BAD_SELECTOR");
        rejects(".a[x]{}", "E_MSS_BAD_SELECTOR");
        rejects(".a*{}", "E_MSS_BAD_SELECTOR");
        rejects("*{}", "E_MSS_BAD_SELECTOR");
        rejects("div{}", "E_MSS_BAD_SELECTOR");
        rejects("@media screen { .a{} }", "E_MSS_BAD_SELECTOR");
        rejects("@import \"x.mss\";", "E_MSS_BAD_SELECTOR");
        rejects("--x: 1;", "E_MSS_BAD_SELECTOR");
        rejects(".Title{}", "E_MSS_BAD_SELECTOR");
        rejects(".1a{}", "E_MSS_BAD_SELECTOR");
        rejects("._a{}", "E_MSS_BAD_SELECTOR");
        rejects("." + "a".repeat(33) + "{}", "E_MSS_BAD_SELECTOR");
        rejects(".{}", "E_MSS_BAD_SELECTOR");
        rejects("#{}", "E_MSS_BAD_SELECTOR");
        rejects(";", "E_MSS_BAD_SELECTOR");
        check(errorOf("div{}").message().contains("收到 'div'"), "选择器文案回显收到的原文");
        check(errorOf("div{}").message().endsWith("收到 'div'"), "不是名字的问题时不带名字规则");
        check(errorOf(".Title{}").message().endsWith("收到 '.Title'。名字要小写字母开头，后面只能是小写字母、数字、_ 和 -，最长 32"),
                "名字不合规时补一句名字规则");
        rejects(".a/**/.b{}", "E_MSS_BAD_SELECTOR");
        check(errorOf(".a/**/.b{}").message().endsWith("收到 '.a.b'"), "注释隔开的复合选择器按紧贴算，回显拼起来的写法");

        rejects(".a > .b{}", "E_MSS_COMBINATOR");
        rejects(".a>.b{}", "E_MSS_COMBINATOR");
        rejects(".a + .b{}", "E_MSS_COMBINATOR");
        rejects(".a ~ .b{}", "E_MSS_COMBINATOR");
        rejects(".a #b{}", "E_MSS_COMBINATOR");
        rejects(".a div{}", "E_MSS_COMBINATOR");
        rejects(".a *{}", "E_MSS_COMBINATOR");
        rejects(".a,.b{}", "E_MSS_MULTI_SELECTOR");
        rejects(".a , .b{}", "E_MSS_MULTI_SELECTOR");
        rejects(".a::before{}", "E_MSS_PSEUDO");
        rejects(".a :hover{}", "E_MSS_PSEUDO");
        rejects(".a [x]{}", "E_MSS_COMBINATOR");
        rejects(".a;", "E_MSS_SYNTAX");
        rejects(".a}", "E_MSS_SYNTAX");
        check(errorOf(".a;").message().contains("选择器 '.a' 后面要 '{'"), "合法选择器后面紧贴 ';' 报少了 '{'，不报选择器不合法");

        rejects("}", "E_MSS_SYNTAX");
        rejects(".a", "E_MSS_SYNTAX");
        rejects(".a ;", "E_MSS_SYNTAX");
        rejects(".a{ .b{} }", "E_MSS_SYNTAX");
        rejects(".a{ = }", "E_MSS_SYNTAX");
        rejects(".a{ color $title; }", "E_MSS_SYNTAX");
        rejects(".a{ color: $title }", "E_MSS_SYNTAX");
        rejects(".a{ color: { }", "E_MSS_SYNTAX");
        rejects(".a{ color: $title; color: $body; }", "E_MSS_SYNTAX");
        rejects(".a{ /* 没闭合 }", "E_MSS_SYNTAX");
        check(errorOf(".a{ color: $title }").message().contains("要以 ';' 结尾"), "漏分号的文案");
        check(errorOf(".a{ color: $title; color: $body; }").message().contains("写了两次"), "同一规则里属性重复的文案");

        rejects(".a{", "E_MSS_UNCLOSED");
        rejects(".a{ color", "E_MSS_UNCLOSED");
        rejects(".a{ color:", "E_MSS_UNCLOSED");
        rejects(".a{ color: $title", "E_MSS_UNCLOSED");

        rejects(".a{ radius: 2; }", "E_UNKNOWN_PROPERTY");
        rejects(".a{ hover: $title; }", "E_UNKNOWN_PROPERTY");
        rejects(".a{ --x: 1; }", "E_UNKNOWN_PROPERTY");
        rejects(".a{ Color: $title; }", "E_UNKNOWN_PROPERTY");
        rejects(".a{ max_lines: 2; }", "E_UNKNOWN_PROPERTY");

        rejects(".a{ color: #FFF; }", "E_LITERAL_COLOR");
        rejects(".a{ background: #12345678; }", "E_LITERAL_COLOR");
        rejects(".a{ padding: 1 #fff; }", "E_LITERAL_COLOR");
        check(errorOf(".a{ color: #FFF; }").message().contains("'#FFF'"), "写死颜色的文案回显原值");
        check(errorOf(".a{ color: #FFF; }").message().contains(Token.all()), "写死颜色的文案列出十个语义色");

        rejects(".a{ height: 100%; }", "E_PERCENT_NOT_SUPPORTED");
        rejects(".a{ padding: 2 10%; }", "E_PERCENT_NOT_SUPPORTED");
        rejects(".a{ width: %; }", "E_PERCENT_NOT_SUPPORTED");
        rejects(".a{ gap: 5%0; }", "E_PERCENT_NOT_SUPPORTED");

        rejects(".a{ width: calc(1); }", "E_MSS_BAD_VALUE");
        rejects(".a{ color: rgb(1,2,3); }", "E_MSS_BAD_VALUE");
        rejects(".a{ color: $title !important; }", "E_MSS_BAD_VALUE");
        rejects(".a{ color: \"$title\"; }", "E_MSS_BAD_VALUE");
        rejects(".a{ gap: 1.5; }", "E_MSS_BAD_VALUE");
        rejects(".a{ padding: 1,2; }", "E_MSS_BAD_VALUE");
        rejects(".a{ color: $; }", "E_MSS_BAD_VALUE");

        rejects("#x{} #x{}", "E_MSS_DUP_SELECTOR");

        rejects(".a｛color: $title;}", "E_MSS_SYNTAX");
        rejects(".a{color：$title;}", "E_MSS_SYNTAX");
        rejects(".a{color: $title；}", "E_MSS_SYNTAX");
        rejects(".a{\u3000color: $title;}", "E_MSS_SYNTAX");
        rejects("．a{}", "E_MSS_SYNTAX");
        check(errorOf(".a{color：$title;}").message().contains("换成半角的 ':'"), "全角冒号提示换成半角");
        check(errorOf(".a{\u3000color: $title;}").message().contains("换成半角的 ' '"), "全角空格提示换成半角");
        rejects(".a{col\uFF4Fr: $title;}", "E_MSS_SYNTAX");
        rejects(".t\uFF49tle{}", "E_MSS_SYNTAX");
        check(errorOf(".a{col\uFF4Fr: $title;}").message().contains("换成半角的 'o'"), "属性名中间的全角字母提示换成半角");
        check(errorOf(".a{color: $title;\n.b{gap: 1;}").message().contains("漏了 '}'"), "漏写 '}' 时提示补上");
        check(errorOf(".a{hover: $title;}").message().contains("悬停用 hover-background / hover-color"), "hover 指向两个 hover- 属性");
    }

    // ============================================================
    //  行列号
    // ============================================================

    static void positions() {
        MssError color = errorOf("\n\n  .a { color: #fff; }");
        eq(pos(color), List.of(3, 15), "写死颜色指向 # 所在的行列");
        check(color.message().contains("ui.mss 3:15 "), "文案里带 3:15");
        eq(pos(errorOf(".a { width: 50%; }")), List.of(1, 13), "百分比指向值");
        eq(pos(errorOf(".a {\n  color: $title;\n")), List.of(1, 4), "未闭合指向 '{'");
        eq(pos(errorOf(".a{\n  maxLines: 2;\n}")), List.of(2, 3), "不认识的属性指向属性名");
        eq(pos(errorOf(".a{\n  width:\n    999;\n}")), List.of(3, 5), "不合法的值指向值");
        eq(pos(errorOf(".a{ gap: ; }")), List.of(1, 10), "空值指向 ';'");
        eq(pos(errorOf("\t.a .b{}")), List.of(1, 5), "tab 算一列；组合子指向第二个选择器");
        eq(pos(errorOf(".a:hover{}")), List.of(1, 3), "伪类指向 ':'");
        eq(pos(errorOf(".a{}\n/* 没闭合")), List.of(2, 1), "未闭合的注释指向 /*");
        eq(pos(errorOf(".a\n")), List.of(2, 1), "文件尾的错误指向文件尾");
        eq(pos(errorOf(".a{}\r\n.b{color:#fff;}")), List.of(2, 10), "CRLF 算一次换行，\\r 不另起一行");
        eq(pos(errorOf("\uFEFF.a{color:#fff;}")), List.of(1, 10), "BOM 不占列");
        eq(pos(errorOf(".a{color: $title; /* 中文注释 */ gap: 1%;}")), List.of(1, 35), "中文字符一个算一列");
        eq(pos(errorOf(".a{/*\uD83D\uDE00*/color:#fff;}")), List.of(1, 16), "代理对算两列（UTF-16 码元）");
        eq(pos(errorOf(".a{\n color")), List.of(1, 3), "属性名后到文件尾，未闭合仍指向 '{'");
        eq(pos(errorOf(".a{\n color:")), List.of(1, 3), "冒号后到文件尾，未闭合仍指向 '{'");
        eq(pos(errorOf(".a{\n color: $title")), List.of(1, 3), "值后到文件尾，未闭合仍指向 '{'");

        MssError dup = errorOf(".a{}\n\n  .a{}");
        eq(pos(dup), List.of(3, 3), "重复选择器指向第二次出现");
        check(dup.message().contains("上一次在 1:1"), "重复选择器带上一次的位置");

        MssError many = errorOf(rules(65));
        eq(pos(many), List.of(65, 1), "规则数超限指向第一条超出的规则");

        // §9.8：<style> 块里的错误挪到 .mcapp 的原始行号
        MssError moved = color.shift(4);
        eq(pos(moved), List.of(7, 15), "shift 挪行不挪列");
        eq(moved.code(), Code.E_LITERAL_COLOR, "shift 不改错误码");
        check(moved.message().contains("ui.mss 7:15 ") && moved.message().contains("'#fff'"), "shift 之后文案跟着变");
        check(dup.shift(10).message().contains("ui.mss 13:3 ") && dup.shift(10).message().contains("上一次在 11:1"),
                "shift 连「上一次」的位置一起挪");
        eq(many.shift(3).line(), 68, "不带位置的错误也挪行");
        eq(many.shift(3).message(), many.message(), "不带位置的文案 shift 后不变");
        check(((MssError) roundTrip(dup)).shift(1).message().contains("上一次在 2:1"), "序列化往返之后还能 shift");
    }

    static Object roundTrip(Object o) {
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            try (java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(bytes)) {
                out.writeObject(o);
            }
            try (java.io.ObjectInputStream in = new java.io.ObjectInputStream(
                    new java.io.ByteArrayInputStream(bytes.toByteArray()))) {
                return in.readObject();
            }
        } catch (java.io.IOException | ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    // ============================================================
    //  拼写建议
    // ============================================================

    static void suggestions() {
        for (String typo : List.of("maxLines", "max_lines", "maxline")) {
            MssError e = errorOf(".a{" + typo + ":2;}");
            eq(e.code(), Code.E_UNKNOWN_PROPERTY, typo + " 报不认识的属性");
            check(e.message().contains("不认识的属性 '" + typo + "'，你是想写 'max-lines' 吗？"), typo + " 给出 max-lines");
        }
        suggests("textAlign", "text-align");
        suggests("hoverColor", "hover-color");
        suggests("hoverBackground", "hover-background");
        suggests("colour", "color");
        suggests("Color", "color");
        suggests("paddings", "padding");
        suggests("hiden", "hidden");

        MssError far = errorOf(".a{radius:2;}");
        check(!far.message().contains("你是想写"), "距离超过 2 不瞎猜");
        check(far.message().endsWith("可用的属性：" + String.join(" ", MssParser.properties())), "猜不到时列出全部属性");
        MssError huge = errorOf(".a{" + "x".repeat(10_000) + ":1;}");
        check(huge.message().length() < 400, "超长属性名的回显被截断");
        check(!errorOf(".a{\u0001:1;}").message().contains("\u0001"), "控制字符不原样进文案");
        String bidi = errorOf(".a{color:$title\u202Eeulav;}").message();
        check(!bidi.contains("\u202E") && bidi.contains("$title\uFFFDeulav"), "双向控制符不原样进文案");
        check(!errorOf(".a{color:$title\u2028;}").message().contains("\u2028"), "行分隔符不原样进文案");
    }

    static void suggests(String typo, String expected) {
        check(errorOf(".a{" + typo + ":1;}").message().contains("你是想写 '" + expected + "' 吗？"),
                typo + " 给出 " + expected);
    }

    // ============================================================
    //  选择器名与节点名同一判据
    // ============================================================

    /** 一边收一边拒的名字，写出来的规则永远匹配不到节点。 */
    static void namesMatchNodeParser() {
        List<String> names = List.of("a", "z9", "nav-item", "a_b", "a-", "a".repeat(32),
                "A", "aB", "1a", "_a", "-a", "a".repeat(33), "a.b", "a:b", "é", "");
        for (String name : names) {
            boolean mssClass = accepts(() -> parse("." + name + "{}"));
            boolean mssId = accepts(() -> parse("#" + name + "{}"));
            boolean nodeClass = accepts(() -> NodeParser.parse(ScriptNodeTest.onePage(
                    ScriptNodeTest.node("column", "class", ScriptNodeTest.arr(ScriptNodeTest.q(name))))));
            boolean nodeId = accepts(() -> NodeParser.parse(ScriptNodeTest.onePage(
                    ScriptNodeTest.node("column", "id", ScriptNodeTest.q(name)))));
            eq(mssClass, nodeClass, "class 名 '" + name + "'：MSS 与 NodeParser 同收同拒");
            eq(mssId, nodeId, "id 名 '" + name + "'：MSS 与 NodeParser 同收同拒");
        }
    }

    static boolean accepts(Runnable body) {
        try {
            body.run();
            return true;
        } catch (MssError | LayoutError e) {
            return false;
        }
    }

    // ============================================================
    //  §6.7 的模板逐字对照
    // ============================================================

    static final Map<Code, String> SPEC_6_7 = new EnumMap<>(Code.class);

    static {
        SPEC_6_7.put(Code.E_MSS_SYNTAX, "ui.mss %d:%d 语法错误：%s");
        SPEC_6_7.put(Code.E_MSS_BAD_SELECTOR, "ui.mss %d:%d 选择器只能是 .class 或 #id，收到 '%s'");
        SPEC_6_7.put(Code.E_MSS_COMBINATOR, "ui.mss %d:%d 不支持组合选择器（后代/子/兄弟）。给节点直接加 class");
        SPEC_6_7.put(Code.E_MSS_MULTI_SELECTOR, "ui.mss %d:%d 不支持逗号分隔的多选择器，分开写两条规则");
        SPEC_6_7.put(Code.E_MSS_PSEUDO, "ui.mss %d:%d 不支持伪类。悬停用 hover-background / hover-color");
        SPEC_6_7.put(Code.E_UNKNOWN_PROPERTY, "ui.mss %d:%d 不认识的属性 '%s'%s");
        SPEC_6_7.put(Code.E_MSS_BAD_VALUE, "ui.mss %d:%d 属性 '%s' 的值 '%s' 不合法。允许：%s");
        SPEC_6_7.put(Code.E_LITERAL_COLOR, "ui.mss %d:%d 不支持写死颜色 '%s'。用语义色：$title $body $subtle $accent"
                + " $screen $pressed $button $button-hover $button-disabled $button-disabled-text");
        SPEC_6_7.put(Code.E_PERCENT_NOT_SUPPORTED, "ui.mss %d:%d 不支持百分比。用 fill 占满可用空间，或写固定像素");
        SPEC_6_7.put(Code.E_MSS_DUP_SELECTOR, "ui.mss %d:%d 选择器 '%s' 重复定义（上一次在 %d:%d）。合并成一条");
        SPEC_6_7.put(Code.E_MSS_TOO_MANY_RULES, "ui.mss 规则数 %d 超过上限 64");
        SPEC_6_7.put(Code.E_MSS_UNCLOSED, "ui.mss %d:%d '{' 没有对应的 '}'");
    }

    /** 在 §6.7 原文后面追加过内容的码。原文一字不动地留作前缀；多一条没登记的追加就红。 */
    static final Map<Code, String> APPENDED = Map.of(
            // §6.6 要求讲清为什么只收语义色
            Code.E_LITERAL_COLOR, "。写死的颜色不会跟着玩家的皮肤和主题变，语义色会",
            // 名字不合规时填名字规则（判据与 NodeParser 相同），其余情况填空串
            Code.E_MSS_BAD_SELECTOR, "%s");

    static void templateTable() {
        eq(EnumSet.allOf(Code.class), EnumSet.copyOf(SPEC_6_7.keySet()), "错误码与 §6.7 的表一一对应");
        for (Code c : Code.values()) {
            eq(c.text(), SPEC_6_7.get(c) + APPENDED.getOrDefault(c, ""),
                    c + " 的文案与 §6.7 逐字相同" + (APPENDED.containsKey(c) ? "（外加登记过的追加）" : ""));
        }
        check(Code.E_PERCENT_NOT_SUPPORTED.text().contains("用 fill") && Code.E_PERCENT_NOT_SUPPORTED.text().contains("固定像素"),
                "百分比的文案写明用 fill 或固定像素");
        check(Code.E_LITERAL_COLOR.text().contains("语义色") && Code.E_LITERAL_COLOR.text().contains("皮肤"),
                "写死颜色的文案写明用语义色才跟着皮肤变");
    }

    // ============================================================
    //  fuzz（§6.9）
    // ============================================================

    static final String[] SEEDS = {
            ".t { color: $title; padding: 2 4; }",
            "/* 头 */\n.row { layout: row; gap: 2; align: center; justify: between; }\n#go { hover-background: $button-hover; }",
            ".x{width:fill;height:120;grow:1;border:1;shadow:true;text-align:right;wrap:false;max-lines:3;hidden:false;}",
            "// c\n#a-b_c { background: none; hover-color: $accent; }\r\n.d{}",
    };

    static final String ALPHABET = ".#{}:;,$%-_/*<>+~@!\"'()[]\\ \n\t\r0123456789abcdefghijklmnopqrstuvwxyzAZ"
            + "：；｛｝，．\u3000\uFEFF\uD83D\uDE00\0\u007F\u0085\u2028é";

    static void fuzz() {
        Random rnd = new Random(20260914L);
        Map<String, Integer> randomBytes = new TreeMap<>();
        for (int i = 0; i < 2000; i++) {
            byte[] bytes = new byte[rnd.nextInt(257)];
            rnd.nextBytes(bytes);
            fuzzOne(new String(bytes, StandardCharsets.UTF_8), randomBytes);
        }
        // 纯随机字节几乎都死在第一个字符上，再拿合法样本做变异，走到规则体与值的深处
        Map<String, Integer> mutated = new TreeMap<>();
        for (int i = 0; i < 2000; i++) {
            StringBuilder sb = new StringBuilder(SEEDS[rnd.nextInt(SEEDS.length)]);
            for (int k = 1 + rnd.nextInt(6); k > 0; k--) {
                int at = rnd.nextInt(sb.length() + 1);
                char c = ALPHABET.charAt(rnd.nextInt(ALPHABET.length()));
                switch (rnd.nextInt(3)) {
                    case 0 -> sb.insert(at, c);
                    case 1 -> { if (at < sb.length()) sb.deleteCharAt(at); }
                    default -> { if (at < sb.length()) sb.setCharAt(at, c); }
                }
            }
            fuzzOne(sb.toString(), mutated);
        }
        // 规则数在 64 上下、会撞名：参数最多的 DUP 与 TOO_MANY 两条模板只有这样才走得到
        Map<String, Integer> structured = new TreeMap<>();
        for (int i = 0; i < 2000; i++) {
            fuzzOne(structuredSheet(rnd), structured);
        }
        System.out.println("fuzz（§6.9）：");
        printOutcome("随机字节串 2000 条", randomBytes);
        printOutcome("合法样本变异 2000 条", mutated);
        printOutcome("55–70 条规则的生成样本 2000 条", structured);
        System.out.println();
    }

    static final String[] DECLS = {
            "color: $title;", "padding: 1 2;", "gap: 3;", "width: fill;", "max-lines: none;",
            "hover-color: $accent;", "layout: row;", "background: none;",
    };

    static String structuredSheet(Random rnd) {
        StringBuilder sb = new StringBuilder();
        int n = 55 + rnd.nextInt(16);
        for (int i = 0; i < n; i++) {
            int name = rnd.nextInt(25) == 0 ? rnd.nextInt(i + 1) : i;
            sb.append(rnd.nextInt(8) == 0 ? '#' : '.').append('r').append(name).append(rnd.nextBoolean() ? " {\n" : "{");
            for (int k = rnd.nextInt(3); k > 0; k--) {
                sb.append("  ").append(DECLS[rnd.nextInt(DECLS.length)]).append(rnd.nextBoolean() ? "\n" : " ");
            }
            sb.append("}\n");
        }
        if (rnd.nextInt(4) == 0) sb.setCharAt(rnd.nextInt(sb.length()), ALPHABET.charAt(rnd.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    static void fuzzOne(String src, Map<String, Integer> outcome) {
        String key;
        try {
            parse(src);
            key = "解析成功";
        } catch (MssError e) {
            key = "MssError " + e.code();
            String broken = inconsistency(src, e);
            if (broken != null) failures.add("fuzz 的错误不自洽（" + broken + "），输入：" + show(src));
        } catch (Throwable t) {
            key = "其他异常 " + t.getClass().getName();
            failures.add("fuzz 抛出了 MssError 以外的异常 " + t + "，输入：" + show(src));
        }
        outcome.merge(key, 1, Integer::sum);
        checks++;
    }

    /** 行列落在原文里、文案头与 line()/col() 一致、shift 只挪行。都成立返回 null。 */
    static String inconsistency(String src, MssError e) {
        String[] lines = src.split("\n", -1);
        if (e.line() < 1 || e.line() > lines.length || e.col() < 1 || e.col() > lines[e.line() - 1].length() + 1) {
            return "行列越界 " + e.line() + ":" + e.col();
        }
        String head = e.code() == Code.E_MSS_TOO_MANY_RULES ? "ui.mss 规则数 " : "ui.mss " + e.line() + ":" + e.col() + " ";
        if (!e.message().startsWith(e.code() + "：" + head)) return "文案头与行列不符";
        MssError moved = e.shift(7);
        if (moved.line() != e.line() + 7 || moved.col() != e.col() || moved.code() != e.code()) return "shift 之后行列不对";
        return null;
    }

    static void printOutcome(String title, Map<String, Integer> outcome) {
        int other = 0;
        for (Map.Entry<String, Integer> e : outcome.entrySet()) {
            if (e.getKey().startsWith("其他异常")) other += e.getValue();
        }
        System.out.println("  " + title + "，其他异常 " + other + " 条：");
        for (Map.Entry<String, Integer> e : outcome.entrySet()) {
            System.out.printf(Locale.ROOT, "    %-40s %5d%n", e.getKey(), e.getValue());
        }
    }

    // ============================================================
    //  §6.7 对照表
    // ============================================================

    static void errorTableCovered() {
        System.out.println("§6.7 错误文案对照表：");
        for (Code c : Code.values()) {
            String sample = triggered.get(c);
            System.out.printf(Locale.ROOT, "  %-24s %s%n", c, sample == null ? "✗ 没有触发样例" : "已实现，触发样例：" + show(sample));
            check(sample != null, c + " 至少被真正触发过一次");
        }
        System.out.println("拒绝项断言 " + rejections + " 条");
        check(rejections >= 10, "拒绝项不少于 10 条（§6.9）");
    }

    // ============================================================
    //  助手
    // ============================================================

    static void rejects(String src, String code) {
        rejections++;
        eq(errorOf(src).code().name(), code, "拒绝 " + show(src));
    }

    static final MssError NOT_REJECTED = MssError.at(Code.E_MSS_SYNTAX, new MssError.Pos(-1, -1), "（没有报错）");

    /** 取这份输入的错误。没报错时记一条失败并返回占位，调用方不必判 null。 */
    static MssError errorOf(String src) {
        try {
            parse(src);
        } catch (MssError e) {
            triggered.putIfAbsent(e.code(), src);
            return e;
        }
        failures.add("本该被拒：" + show(src));
        return NOT_REJECTED;
    }

    static Style one(String declarations) {
        return parse(".e{" + declarations + ";}").forClass("e");
    }

    static void bad(String declaration) {
        rejects(".e{" + declaration + ";}", "E_MSS_BAD_VALUE");
    }

    static Node node(NodeType type, String id, String... classes) {
        return new Node(type, id, List.of(classes), Map.of(), List.of(), null, null);
    }

    static Node withInt(NodeType type, String id, String key, int value) {
        return new Node(type, id, List.of(), Map.<String, Object>of(key, value), List.of(), null, null);
    }

    static List<Integer> pads(Style s) {
        return List.of(s.padTop(), s.padRight(), s.padBottom(), s.padLeft());
    }

    static List<Integer> pos(MssError e) {
        return List.of(e.line(), e.col());
    }

    static String rules(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(".r").append(i).append("{}\n");
        return sb.toString();
    }

    static String lower(Enum<?> e) {
        return e.name().toLowerCase(Locale.ROOT);
    }

    /** 输入的可读形式：转义不可见字符，截到 60 个字符。 */
    static String show(String s) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (; i < s.length() && sb.length() < 60; i++) {
            char c = s.charAt(i);
            if (c == '\n') sb.append("\\n");
            else if (c == '\r') sb.append("\\r");
            else if (c == '\t') sb.append("\\t");
            else if (c < 0x20 || c == 0x7F || c == '\uFEFF' || Character.isSurrogate(c) || c == '\u0085' || c == '\u2028') {
                sb.append(String.format(Locale.ROOT, "\\u%04X", (int) c));
            } else sb.append(c);
        }
        if (i < s.length()) sb.append('…');
        return sb.toString();
    }
}
