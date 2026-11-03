package com.november.mcphone.core.script.layout;

import static com.november.mcphone.core.script.layout.NodeType.BADGE;
import static com.november.mcphone.core.script.layout.NodeType.BOX;
import static com.november.mcphone.core.script.layout.NodeType.BUTTON;
import static com.november.mcphone.core.script.layout.NodeType.COLUMN;
import static com.november.mcphone.core.script.layout.NodeType.DIVIDER;
import static com.november.mcphone.core.script.layout.NodeType.GRID;
import static com.november.mcphone.core.script.layout.NodeType.ICON;
import static com.november.mcphone.core.script.layout.NodeType.IMAGE;
import static com.november.mcphone.core.script.layout.NodeType.ITEM;
import static com.november.mcphone.core.script.layout.NodeType.LIST;
import static com.november.mcphone.core.script.layout.NodeType.PROGRESS;
import static com.november.mcphone.core.script.layout.NodeType.ROW;
import static com.november.mcphone.core.script.layout.NodeType.SCROLL;
import static com.november.mcphone.core.script.layout.NodeType.SPACER;
import static com.november.mcphone.core.script.layout.NodeType.STACK;
import static com.november.mcphone.core.script.layout.NodeType.TAB_BAR;
import static com.november.mcphone.core.script.layout.NodeType.TEXT;
import static com.november.mcphone.core.script.layout.NodeType.TOGGLE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** 布局引擎的断言测试（施工方案 §7），用 javac 单独编，不需要 Minecraft。 */
public class LayoutEngineTest {

    static int checks = 0;
    static final List<String> failures = new ArrayList<>();

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

    /** §7.2 的假实现：每字符 6px，行高 9。 */
    static final TextMeasure FAKE = new TextMeasure() {
        public int width(String t) { return t.length() * 6; }   // 每字符 6px
        public int lineHeight() { return 9; }
        public String truncate(String t, int max) {
            int n = Math.max(0, max / 6);
            return t.length() <= n ? t : t.substring(0, Math.max(0, n - 1)) + "…";
        }
    };

    /**
     * §5.2 的假实现。真的那份读 PNG 头，见 client/tex/AppTextures。
     */
    static final ImageSizes IMAGES = src -> switch (src) {
        case "" -> null;                                   // 没写 src
        case "assets/gone.png" -> null;                    // 不在包里 / 超限 / 坏文件
        case "assets/big.png" -> new int[]{100, 60};
        case "assets/over.png" -> new int[]{128, 128};     // 边长顶格，比 w 的值域上限 120 还大
        default -> new int[]{40, 20};
    };

    public static void main(String[] a) {
        skeleton();
        wrapping();
        sizes();
        containers();
        lists();
        visibilityAndKeys();
        uiState();
        edgeCases();
        randomTrees();
        report();
    }

    // ============================================================
    //  §7.8 骨架
    // ============================================================

    static void skeleton() {
        // ---- 基本纵向流 ----
        var r = layout("""
            { "pages": { "main": { "type":"column", "id":"root", "children":[
                {"type":"text","text":"ab"},
                {"type":"text","text":"cd"} ] } }, "entry":"main" }""",
            ".x{}", 120, 176);
        eq(r.h, 18, "两行文字高 = 2 × lineHeight(9)");
        eq(child(r,0).y, 0, "第一个子在顶部");
        eq(child(r,1).y, 9, "第二个子紧随其后");

        // ---- gap ----
        eq(layoutWith("#root{gap:3;}").children.get(1).y, 12, "gap 插在子之间");
        eq(layoutWith("#root{gap:3;}").h, 21, "gap 计入总高，n-1 个");

        // ---- padding 四种写法都影响位置 ----
        eq(layoutWith("#root{padding:2 4;}").children.get(0).x, 4, "padding 左 = 第二个值");
        eq(layoutWith("#root{padding:2 4;}").children.get(0).y, 2, "padding 上 = 第一个值");

        // ---- grow 分配与余数 ----
        var g = threeRowsWithGrow(1, 1, 1, /*availW*/ 100);   // 100 剩余不能整除 3
        var g0 = child(g, 0);
        var g1 = child(g, 1);
        var g2 = child(g, 2);
        eq(g0.w + g1.w + g2.w, 100, "grow 分配后总宽精确等于可用宽");
        check(g0.w >= g1.w, "余数给第一个 grow>0 的子，不平摊");
        eq(List.of(g0.w, g1.w, g2.w), List.of(34, 33, 33), "余数整个给第一个：34 / 33 / 33");

        // ---- justify 四种 ----
        eq(justify("start",  100, 40).children.get(0).x, 0,  "start");
        eq(justify("center", 100, 40).children.get(0).x, 30, "center");
        eq(justify("end",    100, 40).children.get(0).x, 60, "end");
        eq(justify("between",100, 40, 2).children.get(1).x, 80, "between");
        eq(xs(justify("between", 100, 30, 3)), List.of(0, 45, 90), "between 三个子等距，头尾贴边");

        // ---- align 四种 ----
        eq(align("start",  100, 30).children.get(0).x, 0,  "align start");
        eq(align("center", 100, 30).children.get(0).x, 35, "align center");
        eq(align("end",    100, 30).children.get(0).x, 70, "align end");
        eq(align("stretch",100, 30).children.get(0).w, 100,"align stretch 撑满交叉轴");

        // ---- 溢出：不收缩、不负数、按 start ----
        var of = threeFixedRows(/*each*/ 80, /*avail*/ 100);
        eq(child(of,0).w, 80, "溢出时子不被收缩");
        eq(child(of,2).x, 160, "溢出时按 start 继续往后排");
        for (var n : allNodes(of)) { check(n.w >= 0 && n.h >= 0, "永不负尺寸"); }

        // ---- fill ----
        eq(layoutWith(".f{width:fill;}").children.get(0).w, 120, "fill 占满可用宽");

        // ---- 文本换行 ----
        eq(textLines("aaaa aaaa aaaa", /*wrap*/true,  /*w*/ 60).size(), 2, "ASCII 按空格断行");
        // 10 个字正好 60px，照 §5.2「超过才断」放得下一行；要断成两行得 11 个字
        eq(textLines("中中中中中中中中中中中", true, 60).size(), 2, "CJK 按字断行");
        eq(textLines("ab\ncd", true, 600).size(), 2, "\\n 强制断行");
        eq(textLines("aaaaaaaaaa", false, 30).size(), 1, "wrap:false 只有一行");
        check(textLines("aaaaaaaaaa", false, 30).get(0).endsWith("…"), "wrap:false 超宽加省略号");
        eq(textLines("a a a a a a", true, 12, /*maxLines*/2).size(), 2, "max-lines 截断");

        // ---- badge count==0 真的 0×0 ----
        eq(badge(0).w, 0, "badge count=0 宽 0");
        eq(badge(0).h, 0, "badge count=0 高 0");

        // ---- 无限高容器里 fill 退化成 auto ----
        check(scrollChildWithFillHeight().h < 1000, "scroll 里的 height:fill 不会算出天文数字");

        // ---- list 定高 O(1)：2048 项不超时 ----
        listOf(2048, 12);   // 冷 JVM 的类加载与 JIT 不算布局耗时，先空跑一次
        long t = System.nanoTime();
        var big = listOf(2048, /*item-height*/ 12);
        check((System.nanoTime() - t) / 1_000_000 < 50, "定高 list 2048 项 50ms 内完成");
        eq(big.contentH, 2048 * 12 + 2047 * 0, "定高 contentH 公式正确");
        check(!child(big, 0).measured && !child(big, 2047).measured, "定高 list 的子节点在 layout 时一个都没测");
        Node bigTree = listNode(2048, 12);
        long layoutOnly = System.nanoTime();
        LayoutEngine.layout(bigTree, MssParser.parse(""), UiState.empty(), 120, 176, FAKE, IMAGES);
        check((System.nanoTime() - layoutOnly) / 1_000_000 < 50, "只算 layout 本身，定高 list 2048 项也在 50ms 内");

        // ---- showIf 隐藏的节点完全不参与布局 ----
        var hid = twoRowsFirstHidden();
        eq(hid.children.size(), 1, "隐藏节点不进布局树");
        eq(hid.h, 9, "隐藏节点不占高、不计 gap");
    }

    // ============================================================
    //  断行
    // ============================================================

    static void wrapping() {
        eq(textLines("中中中中中中中中中中", true, 60).size(), 1, "10 个 CJK 正好 60px，没超过就不断");
        eq(textLines("aaaa aaaa aaaa", true, 60), List.of("aaaa aaaa", "aaaa"), "回退到本行最后一个空格，空格不留在行尾");
        eq(textLines("ab cd", true, 24), List.of("ab", "cd"), "断在空格处时空格不带到下一行");
        eq(textLines("aaaaaaaaaaaaaaaaaaaa", true, 60), List.of("aaaaaaaaaa", "aaaaaaaaaa"), "没有空格的长串在宽度处硬断");
        eq(textLines("你好世界", true, 12), List.of("你好", "世界"), "CJK 逐字断");
        eq(textLines("ab 你好", true, 24), List.of("ab 你", "好"), "当前字是 CJK 时就地断，不回退到空格");
        eq(textLines("ab cdefghijk", true, 30), List.of("ab", "cdefg", "hijk"), "退回空格后仍放不下的长词接着硬断");
        eq(textLines("ab\n\ncd", true, 600), List.of("ab", "", "cd"), "连续换行留空行");
        eq(textLines("", true, 60), List.of(""), "空文本占一行");
        eq(textLines("aaaaaaaaaa", false, 30), List.of("aaaa…"), "wrap:false 截断走 TextMeasure.truncate");
        eq(textLines("ab\ncdefghij", false, 30), List.of("ab", "cdef…"), "wrap:false 每段各截一行");
        eq(textLines("a a a a a a", true, 12, 2), List.of("a", "a…"), "max-lines 截断时最后一行补省略号");
        eq(textLines("ab\ncd\nef", false, 60, 2), List.of("ab", "cd…"), "wrap:false 也受 max-lines 管");
        eq(textLines("ab cd", true, 600, 1), List.of("ab cd"), "没超过 max-lines 不加省略号");
        eq(child(layout(TWO_TEXTS, "#root{gap:3;}", 120, 176), 0).lines().size(), 1, "短文字一行");
        eq(textLines("abcdefghij ", true, 60), List.of("abcdefghij"), "段尾空格正好溢出时不多出一行空行");
        eq(textLines("abcdefghij ", true, 60, 1), List.of("abcdefghij"), "…也不因此触发 max-lines 截断");
        eq(textLines("aaaa  bbbb", true, 36), List.of("aaaa", "bbbb"), "断点上的连续空格整个吞掉");
        eq(textLines("aa   bb", true, 18), List.of("aa", "bb"), "连续空格不留在行首行尾");
        eq(textLines("aaaaa  bbbbb", true, 30), List.of("aaaaa", "bbbbb"), "溢出的字是空格时跳过整串空格，不在词中间断");
        eq(textLines("ab ", true, 600), List.of("ab"), "行尾空格去掉");
        eq(textLines(" ab", true, 600), List.of(" ab"), "段首空格保留");

        // 随机串：每行不超过可用宽（单个字就比可用宽还宽的行除外），除断点上的空格外一个字都不丢
        Random rnd = new Random(7);
        String alphabet = "ab 中\n";
        int bad = 0;
        for (int i = 0; i < 500; i++) {
            StringBuilder sb = new StringBuilder();
            for (int k = rnd.nextInt(60); k > 0; k--) sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
            String s = sb.toString();
            int maxW = 6 + rnd.nextInt(90);
            List<String> lines = LayoutEngine.wrap(s, maxW, FAKE, true, 0);
            for (String line : lines) {
                if (FAKE.width(line) > maxW && line.codePointCount(0, line.length()) > 1) bad++;
            }
            if (!s.replace(" ", "").replace("\n", "").equals(String.join("", lines).replace(" ", ""))) bad++;
        }
        eq(bad, 0, "随机串断行：行宽不超、字不丢");

        // 不含换行的随机串：除第一行外不以空格开头，任何一行不以空格结尾
        int spaced = 0;
        for (int i = 0; i < 500; i++) {
            StringBuilder sb = new StringBuilder();
            for (int k = rnd.nextInt(60); k > 0; k--) sb.append("ab  中".charAt(rnd.nextInt(5)));
            List<String> lines = LayoutEngine.wrap(sb.toString(), 6 + rnd.nextInt(90), FAKE, true, 0);
            for (int k = 0; k < lines.size(); k++) {
                if (lines.get(k).endsWith(" ") || k > 0 && lines.get(k).startsWith(" ")) spaced++;
            }
        }
        eq(spaced, 0, "随机串断行：断点上的空格不留在行首行尾");

        // 变宽字体下（截断算法照 GuiUtil.truncate），wrap:false 加 max-lines 每行最多一个省略号
        int doubled = 0;
        for (int i = 0; i < 500; i++) {
            StringBuilder sb = new StringBuilder();
            for (int k = rnd.nextInt(40); k > 0; k--) sb.append("a中 \n".charAt(rnd.nextInt(4)));
            for (String line : LayoutEngine.wrap(sb.toString(), rnd.nextInt(80), VARIABLE, rnd.nextBoolean(), 1 + rnd.nextInt(3))) {
                if (line.indexOf('…') != line.lastIndexOf('…')) doubled++;
            }
        }
        eq(doubled, 0, "截断与 max-lines 叠加时每行最多一个省略号");
    }

    // ============================================================
    //  尺寸
    // ============================================================

    static void sizes() {
        // 写死尺寸与 fill 是外尺寸（含 padding），auto 是内容加 padding
        LayoutNode fixed = run(col("root", nd(BOX, "b", Map.of(), text("t", "aaaaaaaaaa"))), "#b{width:60; padding:5;}", 120, 176);
        eq(child(fixed, 0).w, 60, "width: 60 含 padding");
        eq(child(child(fixed, 0), 0).lines(), List.of("aaaaaaaa", "aa"), "写死宽度的框里，文字按框自己的内宽 50 折行");
        eq(child(child(fixed, 0), 0).x, 5, "padding 把子节点推进来");
        eq(child(run(col("root", nd(BOX, "b", Map.of(), text("t", "ab"))), "#b{padding:5;}", 120, 176), 0).w, 22,
                "auto 宽 = 内容 12 + 左右 padding 10");
        eq(child(run(col("root", nd(BOX, "b", Map.of())), "#b{width:fill; padding:5;}", 120, 176), 0).w, 120,
                "fill 宽就是可用宽，padding 不再往外加");

        // button
        LayoutNode button = child(run(col("root", nd(BUTTON, "b", Map.of("text", "ab"))), "", 120, 176), 0);
        eq(List.of(button.w, button.h), List.of(24, 13), "button 默认 padding 2 6：12+12 宽、9+4 高");
        eq(button.lines(), List.of("ab"), "无 children 的 button 按一行文字测");
        LayoutNode tiny = child(run(col("root", nd(BUTTON, "b", Map.of("text", ""))), "#b{padding:0;}", 120, 176), 0);
        eq(List.of(tiny.w, tiny.h), List.of(16, 12), "button 最小可点区域 16×12");
        LayoutNode rowButton = child(run(col("root", nd(BUTTON, "b", Map.of(), text("x", "ab"), text("y", "cd"))),
                "#b{layout:row; padding:0;}", 120, 176), 0);
        eq(List.of(child(rowButton, 1).x, child(rowButton, 1).y), List.of(12, 0), "有 children 的 button 按样式的 layout 排");
        LayoutNode emptyChildButton = child(run(col("root", nd(BUTTON, "b", Map.of(), box("x"))), "#b{padding:0;}", 120, 176), 0);
        eq(List.of(emptyChildButton.w, emptyChildButton.h), List.of(16, 12), "有 children 的 button 同样保证最小可点区域");
        LayoutNode hiddenKids = child(run(col("root", nd(BUTTON, "b", Map.of("text", "SUBMIT"), ndc(BOX, "x", List.of("h"), Map.of()))),
                ".h{hidden:true;}", 120, 176), 0);
        eq(List.of(hiddenKids.w, hiddenKids.h, String.valueOf(hiddenKids.lines())), List.of(16, 12, "null"),
                "children 全被藏起来的 button 不变回文字按钮");

        // 叶子
        eq(size(nd(TOGGLE, "k", Map.of("label", "ab"))), List.of(36, 10), "toggle 带标签：12 + 4 + 20，高 max(9,10)");
        eq(size(nd(TOGGLE, "k", Map.of())), List.of(20, 10), "toggle 无标签 20×10");
        eq(size(nd(TAB_BAR, "k", Map.of())), List.of(120, 13), "tab-bar 横向占满，高 max(9,8)+4");
        eq(size(nd(DIVIDER, "k", Map.of())), List.of(120, 1), "横分隔线 宽满 高 1");
        eq(size(nd(PROGRESS, "k", Map.of())), List.of(120, 4), "progress 宽满，高默认 4");
        eq(size(nd(PROGRESS, "k", Map.of("height", 8))), List.of(120, 8), "progress 的 height 字段");
        eq(size(nd(ICON, "k", Map.of())), List.of(8, 8), "icon 默认 8");
        eq(size(nd(ICON, "k", Map.of("size", 12))), List.of(12, 12), "icon 的 size 字段");
        eq(size(nd(ITEM, "k", Map.of())), List.of(16, 16), "item 默认 16");
        eq(size(nd(IMAGE, "k", Map.of("w", 30, "h", 20))), List.of(30, 20), "image 取 w / h");
        eq(size(nd(IMAGE, "k", Map.of("src", "assets/a.png"))), List.of(40, 20),
           "image 没写 w / h 时按原始尺寸（§5.2）");
        eq(size(nd(IMAGE, "k", Map.of("src", "assets/big.png"))), List.of(100, 60), "原始尺寸按 src 分别问");
        eq(size(nd(IMAGE, "k", Map.of("src", "assets/a.png", "w", 30))), List.of(30, 20),
           "写了的按写的，没写的那一边才用原始尺寸");
        eq(size(nd(IMAGE, "k", Map.of("src", "assets/gone.png"))), List.of(16, 16),
           "用不了的图按占位尺寸排 —— 按 0 排的话连占位图都画不出来");
        eq(size(nd(IMAGE, "k", Map.of())), List.of(16, 16), "没有 src 也按占位尺寸");
        eq(size(nd(IMAGE, "k", Map.of("src", "assets/gone.png", "w", 30, "h", 20))), List.of(30, 20),
           "作者写了尺寸的，用不了也按作者写的留位置");
        // 原始尺寸能到 128（AppTextures.MAX_SIDE），而 image 的 w 值域是 1–120（§5.2）：
        // 顶格的图在 120 宽的手机上被夹成 120，高不夹，于是横向压掉 6%。这是方案自己的两个数打架，
        // 先把行为钉住 —— 别让它哪天悄悄变成溢出或者 0
        eq(size(nd(IMAGE, "k", Map.of("src", "assets/over.png"))), List.of(120, 128),
           "128 宽的图在 120 宽的页面上夹到 120，高照旧 128");
        eq(size(nd(IMAGE, "k", Map.of("src", "assets/over.png", "w", 120))), List.of(120, 128),
           "作者自己写满 120 也是同一个结果");
        eq(size(nd(BADGE, "k", Map.of("count", 5))), List.of(10, 9), "badge 文字宽 + 4，高一行");
        eq(size(nd(BADGE, "k", Map.of("count", 120))), List.of(22, 9), "badge 超过 99 显示 99+");
        eq(size(nd(SPACER, "k", Map.of("size", 7))), List.of(0, 7), "定长 spacer 在 column 里只占主轴，交叉轴 0");
        LayoutNode spacerRow = run(nd(ROW, "root", Map.of(), text("t", "ab"), nd(SPACER, "s", Map.of("size", 30))), "", 120, 176);
        eq(List.of(spacerRow.h, child(spacerRow, 1).w, child(spacerRow, 1).h), List.of(9, 30, 0), "row 里的定长 spacer 不撑行高");
        LayoutNode vertical = child(run(nd(ROW, "root", Map.of(), nd(DIVIDER, "d", Map.of("vertical", true))), "#root{height:40;}", 120, 176), 0);
        eq(List.of(vertical.w, vertical.h), List.of(1, 40), "竖分隔线 宽 1，高取可用高");
        LayoutNode dividedRow = child(run(nd(SCROLL, "root", Map.of(),
                nd(ROW, "r", Map.of(), text("t", "ab"), nd(DIVIDER, "d", Map.of("vertical", true)), text("u", "cd"))), "", 120, 176), 0);
        eq(List.of(dividedRow.h, child(dividedRow, 1).h, child(dividedRow, 2).x), List.of(9, 9, 13),
                "竖分隔线取所在行的高：不把 auto 高的行撑到整屏，放在 scroll 里也看得见");

        // 负的可用区域
        LayoutNode negative = run(col("root", text("t", "ab")), "#root{width:fill; height:fill;}", -5, -9);
        eq(List.of(negative.w, negative.h), List.of(0, 0), "可用区域为负时按 0 算");
        LayoutNode hugeAvail = run(col("root", nd(PROGRESS, "p", Map.of()), nd(TAB_BAR, "t", Map.of())), "#root{align:stretch;}",
                Integer.MAX_VALUE, Integer.MAX_VALUE);
        eq(badGeometry(hugeAvail), 0, "可用区域取 int 上限时按无限处理，尺寸不超过无限高");

        // 页面根：宽占满可用宽，高只有内容那么高
        LayoutNode centered = layout(TWO_TEXTS, "#root{align:center;}", 120, 176);
        eq(List.of(centered.w, child(centered, 0).x), List.of(120, 54), "页面根默认占满宽，align:center 按屏宽居中");
        LayoutNode bottomed = layout(TWO_TEXTS, "#root{height:100; justify:end;}", 120, 176);
        eq(List.of(child(bottomed, 0).y, child(bottomed, 1).y), List.of(82, 91), "写死高度的根按 justify 排");
        LayoutNode narrowRoot = layout(TWO_TEXTS, "#root{width:60; align:end;}", 120, 176);
        eq(List.of(narrowRoot.w, child(narrowRoot, 0).x), List.of(60, 48), "写死宽度的根按写死的宽排");

        // 写死宽度超过可用宽
        LayoutNode wide = run(col("root", nd(BOX, "b", Map.of(), text("t", "a".repeat(30)))), "#root{padding:4;} #b{width:120;}", 120, 176);
        eq(child(wide, 0).w, 112, "写死宽度超过可用宽时夹到可用宽");
        eq(child(child(wide, 0), 0).lines(), List.of("a".repeat(18), "a".repeat(12)), "…子节点也按夹过的宽度折行，不溢出屏幕");
        eq(child(run(col("root", nd(BOX, "b", Map.of())), "#b{width:120;}", 100, 176), 0).w, 100, "测出来的宽不超过可用宽");
        eq(align("center", 101, 30).children.get(0).x, 35, "居中的余数向下取整");

        // scroll 里带 padding 的一层框：无限高减去 padding 仍是无限，fill 照样退化
        LayoutNode padded = run(nd(SCROLL, "root", Map.of(), nd(BOX, "p", Map.of(), nd(BOX, "c", Map.of()))),
                "#p{padding:4;} #c{height:fill;}", 120, 176);
        eq(child(child(padded, 0), 0).h, 0, "scroll 里隔一层 padding 的 height:fill 仍退化成 auto");
        eq(padded.h, 176, "scroll 默认 height: fill，占满可用高");
        eq(padded.contentH, 8, "scroll 的 contentH 是子内容总高");
        eq(padded.scrollMax(), 0, "内容比视口矮时 scrollMax 为 0");
        LayoutNode viaGrid = run(nd(SCROLL, "root", Map.of(), nd(GRID, "g", Map.of("cols", 1), box("c"))), "#c{height:fill;}", 120, 176);
        eq(child(child(viaGrid, 0), 0).h, 0, "scroll 里隔一层 grid 的 height:fill 也退化");
        LayoutNode viaButton = run(nd(SCROLL, "root", Map.of(), nd(BUTTON, "b", Map.of(), box("c"))), "#c{height:fill;}", 120, 176);
        eq(child(child(viaButton, 0), 0).h, 0, "scroll 里隔一层 button 的 height:fill 也退化");
        LayoutNode innerScroll = run(nd(SCROLL, "root", Map.of(), nd(SCROLL, "in", Map.of(), box("c"))), "#c{height:30;}", 120, 176);
        eq(child(innerScroll, 0).h, 30, "scroll 里的 scroll 没有可用高，按内容展开");
        LayoutNode paddedScroll = run(nd(SCROLL, "root", Map.of(), box("c")), "#root{height:50; padding:10;} #c{height:100;}", 120, 176);
        eq(paddedScroll.scrollMax(), 70, "scrollMax 把 padding 算进去：100 + 20 - 50");

        // 纵向溢出：写死高的 column 装不下时同样按 start 往下排
        LayoutNode tall = run(col("root", box("a"), box("b"), box("c")),
                "#root{height:100;} #a{height:80;} #b{height:80;} #c{height:80;}", 120, 176);
        eq(List.of(child(tall, 1).y, child(tall, 2).y, child(tall, 2).h), List.of(80, 160, 80), "纵向溢出不收缩、按 start 排");
    }

    // ============================================================
    //  容器
    // ============================================================

    static void containers() {
        LayoutNode row = run(nd(ROW, "root", Map.of(), box("a"), box("b")), "#root{layout:column;} #a{width:10;} #b{width:10;}", 120, 176);
        eq(List.of(child(row, 1).x, child(row, 1).y), List.of(10, 0), "row 上写 layout: column 被忽略，仍横着排");
        LayoutNode boxRow = run(nd(BOX, "root", Map.of(), box("a"), box("b")), "#root{layout:row;} #a{width:10;} #b{width:10;}", 120, 176);
        eq(child(boxRow, 1).x, 10, "box 按样式的 layout 排");

        // stack：align 管横向，justify 管纵向
        Node icon = nd(STACK, "root", Map.of(), box("icon"), nd(BADGE, "dot", Map.of("count", 3)));
        LayoutNode topRight = run(icon, "#root{width:40; height:40; align:end; justify:start;} #icon{width:40; height:40;}", 120, 176);
        eq(List.of(child(topRight, 1).x, child(topRight, 1).y), List.of(30, 0), "stack 里 align:end + justify:start 是右上角");
        LayoutNode bottom = run(icon, "#root{width:40; height:40; justify:end;} #icon{width:40; height:40;}", 120, 176);
        eq(List.of(child(bottom, 1).x, child(bottom, 1).y), List.of(0, 31), "stack 里 justify:end 贴底");
        eq(List.of(child(topRight, 0).x, child(topRight, 0).y), List.of(0, 0), "stack 的子节点各自结算，互不推开");
        LayoutNode stretchStack = run(nd(STACK, "root", Map.of(), box("bg")), "#root{width:40; height:40; align:stretch;}", 120, 176);
        eq(List.of(child(stretchStack, 0).w, child(stretchStack, 0).h), List.of(40, 40), "stack 里 align:stretch 两个方向都撑满");
        LayoutNode middle = run(icon, "#root{width:40; height:40; justify:center;} #icon{width:40; height:40;}", 120, 176);
        eq(child(middle, 1).y, 15, "stack 里 justify:center 纵向居中");
        LayoutNode overflowJustify = run(col("root", box("a"), box("b")), "#root{height:10; justify:end;} #a{height:10;} #b{height:10;}", 120, 176);
        eq(List.of(child(overflowJustify, 0).y, child(overflowJustify, 1).y), List.of(0, 10), "剩余为负时不按 justify 偏移，按 start 排");

        // count 为 0 的角标不参与 gap
        LayoutNode zeroBadge = run(nd(ROW, "root", Map.of(), box("a"), nd(BADGE, "z", Map.of()), box("b")),
                "#root{gap:4;} #a{width:10;} #b{width:10;}", 120, 176);
        eq(List.of(child(zeroBadge, 2).x, zeroBadge.measuredW()), List.of(14, 24), "count 为 0 的角标不占 gap，行的内容宽 10+4+10");
        LayoutNode zeroFirst = run(col("root", nd(BADGE, "z", Map.of()), box("a")), "#root{gap:4;} #a{height:10;}", 120, 176);
        eq(List.of(child(zeroFirst, 1).y, zeroFirst.h), List.of(0, 10), "排在最前的 0 角标后面也不留 gap");
        LayoutNode zeroInScroll = run(nd(SCROLL, "root", Map.of(), box("a"), nd(BADGE, "z", Map.of()), box("b")),
                "#root{gap:4;} #a{height:10;} #b{height:10;}", 120, 176);
        eq(List.of(child(zeroInScroll, 2).y, zeroInScroll.contentH), List.of(14, 24), "scroll 里 count 为 0 的角标不占 gap");
        LayoutNode zeroInList = run(nd(LIST, "root", Map.of(), box("a"), nd(BADGE, "z", Map.of()), box("b")),
                "#root{gap:4;} #a{height:10;} #b{height:10;}", 120, 176);
        eq(Arrays.toString(zeroInList.cumHeights()), "[0, 10, 14, 24]", "不定高 list 里 count 为 0 的角标不占 gap");

        // grow 吃掉剩余后 justify 不再起作用
        LayoutNode growJustify = run(nd(ROW, "root", Map.of(), box("a"), box("b")),
                "#root{width:fill; justify:end;} #a{grow:1;} #b{width:10;}", 120, 176);
        eq(List.of(child(growJustify, 0).w, child(growJustify, 1).x), List.of(110, 110), "有 grow 时剩余全给 grow，justify 无剩余可分");
        eq(justify("between", 100, 40, 1).children.get(0).x, 30, "between 只有一个子时居中");
        LayoutNode grow12 = threeRowsWithGrow(1, 2, 0, 90);
        eq(List.of(child(grow12, 0).w, child(grow12, 1).w, child(grow12, 2).w), List.of(30, 60, 0), "grow 按权重分");
        LayoutNode stretchRow = run(nd(ROW, "root", Map.of(), box("a"), box("b")),
                "#root{height:30; align:stretch;} #a{width:10;} #b{width:10; height:5;}", 120, 176);
        eq(List.of(child(stretchRow, 0).h, child(stretchRow, 1).h), List.of(30, 30), "row 里 align:stretch 撑满行高");
        LayoutNode centerRow = run(nd(ROW, "root", Map.of(), box("a")), "#root{height:30; align:center;} #a{width:10; height:10;}", 120, 176);
        eq(child(centerRow, 0).y, 10, "row 里 align:center 纵向居中");

        // grid：列宽余数给最后一列
        List<Node> cells = new ArrayList<>();
        for (int i = 0; i < 5; i++) cells.add(box("c" + i));
        LayoutNode grid = run(nd(GRID, "root", Map.of("cols", 3), cells.toArray(new Node[0])),
                "#root{width:33; gap:1;} #c0{height:5;} #c1{height:7;} #c3{height:4;}", 120, 176);
        eq(List.of(child(grid, 0).w, child(grid, 1).w, child(grid, 2).w), List.of(10, 10, 11), "grid 列宽 (33-2)/3，余数给最后一列");
        eq(List.of(child(grid, 0).x, child(grid, 1).x, child(grid, 2).x), List.of(0, 11, 22), "grid 列之间隔 gap");
        eq(List.of(child(grid, 3).y, child(grid, 4).y), List.of(8, 8), "grid 第二行在第一行最高的格子下面加 gap");
        eq(grid.h, 12, "grid 高 = 各行高之和 + gap");
        eq(child(grid, 4).x, 11, "grid 第二行照样按列排");
        LayoutNode gridWithZero = run(nd(GRID, "root", Map.of("cols", 3), box("c0"), nd(BADGE, "z", Map.of()), box("c1"), box("c2")),
                "#root{width:33; gap:1; align:stretch;} #c0{height:5;}", 120, 176);
        eq(List.of(child(gridWithZero, 2).x, child(gridWithZero, 3).x), List.of(11, 22), "grid 里 count 为 0 的角标不占格子");
        eq(List.of(child(gridWithZero, 1).w, child(gridWithZero, 1).h), List.of(0, 0), "…拉伸的 grid 里它仍是 0×0");
        LayoutNode stretchedZero = run(col("root", nd(BADGE, "z", Map.of()), box("a")), "#root{width:40; align:stretch;} #a{height:5;}", 120, 176);
        eq(List.of(child(stretchedZero, 0).w, child(stretchedZero, 0).h), List.of(0, 0), "column 拉伸时 count 为 0 的角标仍是 0×0");
        LayoutNode stretchedZeroRow = run(nd(ROW, "root", Map.of(), nd(BADGE, "z", Map.of()), box("a")), "#root{height:20; align:stretch;} #a{width:5;}", 120, 176);
        eq(List.of(child(stretchedZeroRow, 0).w, child(stretchedZeroRow, 0).h), List.of(0, 0), "row 拉伸时 count 为 0 的角标仍是 0×0");
        LayoutNode stretchedZeroStack = run(nd(STACK, "root", Map.of(), nd(BADGE, "z", Map.of())), "#root{width:20; height:20; align:stretch;}", 120, 176);
        eq(List.of(child(stretchedZeroStack, 0).w, child(stretchedZeroStack, 0).h), List.of(0, 0), "stack 拉伸时 count 为 0 的角标仍是 0×0");
        LayoutNode stretchedZeroList = run(nd(LIST, "root", Map.of(), nd(BADGE, "z", Map.of()), box("a")), "#root{align:stretch;} #a{height:5;}", 120, 176);
        eq(List.of(child(stretchedZeroList, 0).w, child(stretchedZeroList, 0).h), List.of(0, 0), "不定高 list 拉伸时 count 为 0 的角标仍是 0×0");
    }

    // ============================================================
    //  scroll / list
    // ============================================================

    static void lists() {
        LayoutNode loose = run(nd(LIST, "root", Map.of(), text("a", "ab"), text("b", "cd"), text("c", "ef")), "#root{gap:2;}", 120, 176);
        eq(Arrays.toString(loose.cumHeights()), "[0, 11, 22, 31]", "不定高 list 的累积高度表：每项顶边 + 总高");
        eq(List.of(child(loose, 1).y, child(loose, 2).y), List.of(11, 22), "不定高 list 按累积高度摆");
        eq(List.of(loose.contentH, loose.h), List.of(31, 176), "list 自身占可用高，内容总高另存");

        LayoutNode fixed = run(nd(LIST, "root", Map.of("item-height", 12), text("a", "ab"), text("b", "cd"), text("c", "ef")),
                "#root{gap:1; padding:2;}", 120, 176);
        eq(fixed.contentH, 38, "定高 contentH = 3×12 + 2×1");
        LayoutNode item = child(fixed, 1);
        check(!item.measured && item.w == 0, "定高 list 的项在 layoutItem 之前没排");
        LayoutEngine.layoutItem(fixed, 1, FAKE);
        eq(List.of(item.x, item.y, item.w, item.h), List.of(2, 15, 12, 12), "layoutItem 把第 1 项排进它的格子");
        eq(item.lines(), List.of("cd"), "layoutItem 也测了项的内容");
        item.w = 99;
        LayoutEngine.layoutItem(fixed, 1, FAKE);
        eq(item.w, 99, "排过的项再调 layoutItem 不会重排");
        String beforeFixed = geometry(fixed);
        String beforeLoose = geometry(loose);
        LayoutEngine.layoutItem(fixed, 3, FAKE);
        LayoutEngine.layoutItem(fixed, -1, FAKE);
        LayoutEngine.layoutItem(loose, 0, FAKE);
        LayoutEngine.layoutItem(child(loose, 0), 0, FAKE);
        eq(geometry(fixed) + geometry(loose), beforeFixed + beforeLoose, "下标越界、不定高 list、不是 list 时 layoutItem 什么都不做");
        LayoutNode stretched = run(nd(LIST, "root", Map.of("item-height", 12), text("a", "ab")), "#root{align:stretch;}", 120, 176);
        LayoutEngine.layoutItem(stretched, 0, FAKE);
        eq(child(stretched, 0).w, 120, "定高 list 的 align:stretch 让项占满宽");
        LayoutNode centeredItems = run(nd(LIST, "root", Map.of("item-height", 12), text("a", "ab")), "#root{align:center;}", 120, 176);
        LayoutEngine.layoutItem(centeredItems, 0, FAKE);
        eq(child(centeredItems, 0).x, 54, "定高 list 的 align:center 让项居中");

        Node innerNode = nd(LIST, "in", Map.of("item-height", 10), text("x", "ab"));
        LayoutNode outerList = run(nd(LIST, "root", Map.of("item-height", 20), innerNode), "", 120, 176);
        LayoutNode innerList = child(outerList, 0);
        LayoutEngine.layoutItem(innerList, 0, FAKE);
        check(!child(innerList, 0).measured, "外层的项还没排时，排内层的项什么都不做，不会按宽 0 缓存下来");
        LayoutEngine.layoutItem(outerList, 0, FAKE);
        LayoutEngine.layoutItem(innerList, 0, FAKE);
        eq(List.of(child(innerList, 0).w, child(innerList, 0).h), List.of(12, 10), "外层排好之后内层的项照常排");

        LayoutNode huge = run(col("root", nd(LIST, "l", Map.of("item-height", Integer.MAX_VALUE),
                        nd(ROW, "r0", Map.of(), box("b0")), nd(STACK, "r1", Map.of(), box("b1")))),
                "#root{padding:3;} #r0{align:end;} #r1{justify:end; align:end;} #b0{width:10; height:10;} #b1{width:10; height:10;}",
                120, 176);
        LayoutEngine.layoutItem(child(huge, 0), 0, FAKE);
        LayoutEngine.layoutItem(child(huge, 0), 1, FAKE);
        eq(badGeometry(huge), 0, "item-height 取 int 上限时坐标不溢出成负数、尺寸不超过无限高");

        LayoutNode nested = run(nd(SCROLL, "root", Map.of(), nd(LIST, "l", Map.of("item-height", 10), text("a", "x"), text("b", "y"))), "", 120, 176);
        eq(child(nested, 0).h, 20, "放在 scroll 里的 list 没有可用高，按内容总高展开");
        LayoutNode longScroll = run(nd(SCROLL, "root", Map.of(), box("a"), box("b")), "#root{height:50; gap:2;} #a{height:40;} #b{height:40;}", 120, 176);
        eq(List.of(child(longScroll, 1).y, longScroll.contentH, longScroll.scrollMax()), List.of(42, 82, 32),
                "scroll 的子节点在内容坐标系里，超出视口的照样往下排");
    }

    // ============================================================
    //  显隐与 key
    // ============================================================

    static void visibilityAndKeys() {
        LayoutNode hidden = run(col("root", ndc(TEXT, "a", List.of("h"), Map.of("text", "ab")), text("b", "cd")), ".h{hidden:true;}", 120, 176);
        eq(hidden.children.size(), 1, "hidden: true 与 showIf 效果一致");

        LayoutNode shown = layout("""
            { "state": { "tab": 1, "on": false },
              "pages": { "main": { "type":"column", "id":"root", "children":[
                {"type":"text","id":"eq","text":"a","showIf":{"key":"tab","eq":1}},
                {"type":"text","id":"ne","text":"b","showIf":{"key":"tab","ne":1}},
                {"type":"text","id":"falsy","text":"c","showIf":{"key":"on","truthy":false}},
                {"type":"text","id":"truthy","text":"d","showIf":{"key":"on","truthy":true}} ] } }, "entry":"main" }""",
                "", 120, 176);
        eq(keys(shown.children), List.of("#eq", "#falsy"), "showIf 的 eq / ne / truthy:false / truthy:true");

        LayoutNode keyed = layout("""
            { "state": { "on": false },
              "pages": { "main": { "type":"column", "id":"root", "children":[
                {"type":"text","text":"gone","showIf":{"key":"on","truthy":true}},
                {"type":"box","children":[ {"type":"text","id":"t","text":"a"}, {"type":"text","text":"b"} ]} ] } }, "entry":"main" }""",
                "", 120, 176);
        eq(keyed.key, "#root", "有 id 的 key 是 #id");
        eq(child(keyed, 0).key, "#root.children[1]", "没 id 的 key 是路径，用原始下标，前面被藏起来的兄弟不让它错位");
        eq(keys(child(keyed, 0).children), List.of("#t", "#root.children[1].children[1]"), "路径从最近的带 id 祖先起算");
        LayoutNode anonymous = run(nd(COLUMN, null, Map.of(), text(null, "a")), "", 120, 176);
        eq(List.of(anonymous.key, child(anonymous, 0).key), List.of("", "children[0]"), "根没 id 时路径从根起算");

        LayoutNode hiddenRoot = run(col("root", text("t", "ab")), "#root{hidden:true;}", 120, 176);
        eq(List.of(hiddenRoot.children.size(), hiddenRoot.w, hiddenRoot.h), List.of(0, 0, 0), "根被藏起来时返回 0×0 的空根");

        Node deep = box("leaf");
        for (int i = 0; i < 40; i++) deep = nd(BOX, null, Map.of(), deep);
        LayoutNode deepTree = run(deep, "", 120, 176);
        eq(depth(deepTree), NodeParser.MAX_DEPTH, "超过 32 层的子树不排，不会把栈打穿");

        Node leafWithKids = nd(TEXT, "t", Map.of("text", "hi"), nd(LIST, "l", Map.of(), text("x", "x")));
        eq(child(run(col("root", leafWithKids), "", 120, 176), 0).children.size(), 0,
                "叶子类型挂的 children 不排，绕过校验造出来的树不抛异常");

        Node shared = box("leaf2");
        for (int i = 0; i < 25; i++) shared = nd(BOX, null, Map.of(), shared, shared);
        long dagStart = System.nanoTime();
        LayoutNode dag = run(shared, "", 120, 176);
        check(allNodes(dag).size() <= LayoutEngine.MAX_LAYOUT_NODES, "同一个 Node 挂在多处的共享子树不会指数展开");
        check((System.nanoTime() - dagStart) / 1_000_000 < 1000, "共享子树的布局在 1 秒内结束");
    }

    // ============================================================
    //  边角：每条都对着一种会改变坐标的错误写法
    // ============================================================

    static void edgeCases() {
        LayoutNode tallBox = run(col("root", nd(BOX, "b", Map.of(), box("f"))), "#b{height:150;} #f{height:fill;}", 120, 100);
        eq(List.of(child(tallBox, 0).h, child(child(tallBox, 0), 0).h), List.of(150, 150), "写死高超过可用高时，fill 的子节点填满写死的高");

        LayoutNode capped = run(nd(ROW, "root", Map.of(), box("a"), box("b")), "#a{width:120;} #b{width:10;}", 100, 176);
        eq(List.of(child(capped, 0).w, child(capped, 1).x, child(capped, 1).w), List.of(100, 100, 10), "row 里写死宽超过可用宽的子节点夹到可用宽");
        eq(child(run(nd(ROW, "root", Map.of(), nd(BUTTON, "b", Map.of("text", ""))), "", 10, 176), 0).w, 10,
                "可用宽小于最小可点宽时按可用宽");

        LayoutNode spacerColumn = child(run(nd(STACK, "root", Map.of(), col("c", nd(SPACER, "s", Map.of("size", 30)), text("t", "ab"))), "", 120, 176), 0);
        eq(spacerColumn.w, 12, "column 的宽不算定长 spacer");
        eq(run(nd(STACK, "root", Map.of(), nd(SPACER, "s", Map.of("size", 30)), text("t", "ab")), "", 120, 176).measuredW(), 12,
                "stack 的宽不算定长 spacer");
        LayoutNode spacerList = run(nd(LIST, "root", Map.of(), nd(SPACER, "s", Map.of("size", 30))), "", 120, 176);
        eq(List.of(child(spacerList, 0).w, child(spacerList, 0).h), List.of(0, 30), "不定高 list 里的定长 spacer 交叉轴为 0");
        LayoutNode spacerFixedList = run(nd(LIST, "root", Map.of("item-height", 12), nd(SPACER, "s", Map.of("size", 30))), "", 120, 176);
        LayoutEngine.layoutItem(spacerFixedList, 0, FAKE);
        eq(child(spacerFixedList, 0).w, 0, "定高 list 里的定长 spacer 交叉轴为 0");

        LayoutNode tallRow = run(nd(ROW, "root", Map.of(), text("t", "ab\ncd"), nd(DIVIDER, "d", Map.of("vertical", true))), "#root{align:end;}", 120, 176);
        eq(List.of(tallRow.h, child(tallRow, 0).y, child(tallRow, 1).h), List.of(18, 0, 18), "竖分隔线不把 align:end 的行撑高");
        eq(child(run(col("root", nd(DIVIDER, "d", Map.of("vertical", true))), "", 120, 100), 0).h, 0, "column 里的竖分隔线高为 0");
        eq(child(run(nd(STACK, "root", Map.of(), nd(DIVIDER, "d", Map.of("vertical", true))), "#root{height:30;}", 120, 176), 0).h, 30,
                "stack 里的竖分隔线取 stack 的高");

        LayoutNode zeroGrid = run(nd(GRID, "root", Map.of("cols", 3), box("a"), box("b"), box("c"), nd(BADGE, "z", Map.of())),
                "#root{gap:4; padding:3;} #a{height:5;} #b{height:5;} #c{height:5;}", 120, 176);
        eq(List.of(zeroGrid.h, child(zeroGrid, 3).x, child(zeroGrid, 3).y), List.of(11, 3, 3), "count 为 0 的角标不另起一行，放在 grid 内容区左上角");
        LayoutNode zeroFixedList = run(nd(LIST, "root", Map.of("item-height", 12), nd(BADGE, "z", Map.of())), "#root{align:stretch;}", 120, 176);
        LayoutEngine.layoutItem(zeroFixedList, 0, FAKE);
        eq(child(zeroFixedList, 0).w, 0, "定高 list 拉伸时 count 为 0 的角标宽仍为 0");

        LayoutNode hugeRow = run(nd(LIST, "root", Map.of("item-height", Integer.MAX_VALUE), box("a"), nd(ROW, "r", Map.of(), box("b"))),
                "#r{align:end;} #b{width:10; height:10;}", 120, 176);
        LayoutEngine.layoutItem(hugeRow, 1, FAKE);
        eq(badGeometry(hugeRow), 0, "item-height 取上限时第 1 项里 align:end 的子节点坐标也不越界");

        eq(textLines("aaaa aaaa aaaa", true, 54, 1), List.of("aaaa aaa…"), "max-lines 截掉后面几行时，补了省略号的末行仍截到可用宽");
        eq(xs(justify("center", 101, 10)), List.of(45), "justify:center 的余数向下取整");
        eq(textLines("  hello world", true, 30), List.of("hello", "world"), "段首的空格放不下首词时不单独占一行空行");
        eq(textLines("  hello world", true, 30, 1), List.of("hell…"), "…加 max-lines:1 也不会只剩省略号");
        eq(textLines("ab  ", false, 60), List.of("ab"), "wrap:false 同样去掉行尾空格");

        Node hiddenBox = ndc(BOX, null, List.of("h"), Map.of());
        Node crowded = new Node(COLUMN, "root", List.of(), Map.of(), java.util.Collections.nCopies(1_000_000, hiddenBox), null, null);
        long crowdedStart = System.nanoTime();
        LayoutNode crowdedLayout = run(crowded, ".h{hidden:true;}", 120, 176);
        check(crowdedLayout.truncated() && (System.nanoTime() - crowdedStart) / 1_000_000 < 1000,
                "一百万个藏起来的子节点也按预算停下，并在根上标出截断");
        Node doubled = box("leaf3");
        for (int i = 0; i < 25; i++) doubled = nd(BOX, null, Map.of(), doubled, doubled);
        check(run(doubled, "", 120, 176).truncated(), "共享子树超出预算时根上标出截断");
        check(!run(col("root", text("t", "ab")), "", 120, 176).truncated(), "正常的树不标截断");
        check(!listOf(2048, 12).truncated(), "2048 项的定高 list 不超预算");
    }

    // ============================================================
    //  UiState
    // ============================================================

    static void uiState() {
        UiState st = UiState.of(Map.of("n", 1, "b", false, "s", "x"));
        eq(st.revision(), 0, "新建的 state revision 为 0");
        st.set("n", 1);
        eq(st.revision(), 0, "写入相同的值不算改动");
        st.set("n", 2);
        eq(List.of(st.getInt("n"), st.revision()), List.of(2, 1), "改了值 revision 加一");
        st.toggle("b");
        eq(List.of(st.getBool("b"), st.revision()), List.of(true, 2), "toggle 取反");
        check(throwsIae(() -> st.set("n", "str")), "类型不符拒绝写入");
        check(throwsIae(() -> st.set("zz", 1)), "没声明的 key 拒绝写入");
        check(throwsIae(() -> st.toggle("n")), "toggle 只对 bool");
        check(throwsIae(() -> UiState.of(Map.of("l", 1L))), "初值只能是 int / bool / string / array / object，Long 不算 int");
        eq(List.of(st.getInt("zz"), st.getBool("zz"), st.getString("zz")), List.of(0, false, ""), "缺键读默认值");
        eq(st.get("zz"), null, "缺键 get 返回 null");
        UiState empty = UiState.of(Map.of("s", ""));
        check(empty.test(new Node.ShowIf("s", Node.ShowIf.Kind.TRUTHY, false)), "空字符串为假，truthy:false 成立");
        check(!empty.test(new Node.ShowIf("s", Node.ShowIf.Kind.TRUTHY, true)), "空字符串为假，truthy:true 不成立");
        check(UiState.empty().test(new Node.ShowIf("zz", Node.ShowIf.Kind.NE, 1)), "缺键与任何值都不相等");
    }

    // ============================================================
    //  随机树
    // ============================================================

    static final NodeType[] CONTAINERS = {BOX, COLUMN, ROW, STACK, SCROLL, GRID, LIST, BUTTON};
    static final NodeType[] LEAVES = {SPACER, DIVIDER, TEXT, IMAGE, ICON, ITEM, BADGE, PROGRESS, TOGGLE, TAB_BAR};
    static final String[] TEXTS = {
            "", "ab", "hello world foo bar baz", "中文字符测试一段比较长的文本", "a\nb\n\nc",
            "x".repeat(200), "mixed 中英 text 混排 abc def", " leading and trailing "};
    static final String[] DECLS = {
            "width: fill", "width: 40", "width: 0", "width: 120", "height: fill", "height: 30", "height: 176",
            "grow: 1", "grow: 16", "padding: 2", "padding: 1 3 5 7", "padding: 32", "gap: 4", "gap: 32",
            "align: center", "align: stretch", "align: end", "justify: between", "justify: center", "justify: end",
            "layout: row", "layout: stack", "wrap: false", "max-lines: 2", "border: 1", "hidden: true"};

    static void randomTrees() {
        Random rnd = new Random(20260914L);
        Stylesheet[] sheets = new Stylesheet[16];
        for (int i = 0; i < sheets.length; i++) sheets[i] = MssParser.parse(randomSheet(rnd));

        // 预热：类加载与 JIT 不算进单棵耗时
        for (int i = 0; i < 50; i++) {
            LayoutEngine.layout(randomTree(rnd), sheets[i % sheets.length], randomState(rnd), 120, 176, FAKE, IMAGES);
        }

        int exceptions = 0;
        int negatives = 0;
        double maxMs = 0;
        int deepest = 0;
        int largest = 0;
        for (int i = 0; i < 1000; i++) {
            Node root = randomTree(rnd);
            deepest = Math.max(deepest, depth(root));
            largest = Math.max(largest, count(root));
            try {
                long t0 = System.nanoTime();
                LayoutNode ln = LayoutEngine.layout(root, sheets[rnd.nextInt(sheets.length)], randomState(rnd),
                        rnd.nextInt(200), rnd.nextInt(300), FAKE, IMAGES);
                maxMs = Math.max(maxMs, (System.nanoTime() - t0) / 1e6);
                layoutAllItems(ln);   // 画之前可见的定高 list 项都会被排，这里全排上一起查
                negatives += badGeometry(ln);
            } catch (Throwable e) {
                exceptions++;
                if (exceptions <= 3) failures.add("随机树 #" + i + " 抛出 " + e);
            }
        }
        System.out.printf(Locale.ROOT, "随机树 1000 棵（上限深度 32、节点 512；实际最深 %d 层、最多 %d 个节点）："
                + "异常 %d，几何越界 %d，单棵最大耗时 %.2f ms%n", deepest, largest, exceptions, negatives, maxMs);
        check(deepest <= NodeParser.MAX_DEPTH && largest <= NodeParser.MAX_NODES, "随机树不超过深度 32、节点 512");
        eq(exceptions, 0, "随机树无异常");
        eq(negatives, 0, "随机树无负尺寸、负坐标、超过无限高的尺寸");
        check(maxMs <= 100, "随机树单棵不超过 100ms，实际 " + maxMs);

        // 绕过 IR 校验的树：属性取极值、叶子挂 children、同一个子树挂在多处、可用区取极值
        int extremeExceptions = 0;
        int extremeBad = 0;
        double extremeMaxMs = 0;
        int[] avails = {-5, 0, 1, 120, 5000, LayoutEngine.UNBOUNDED, Integer.MAX_VALUE};
        for (int i = 0; i < 1000; i++) {
            Node root = extremeNode(rnd, 1, 1 + rnd.nextInt(40), new int[]{1 + rnd.nextInt(600)});
            try {
                long t0 = System.nanoTime();
                LayoutNode ln = LayoutEngine.layout(root, sheets[rnd.nextInt(sheets.length)], randomState(rnd),
                        avails[rnd.nextInt(avails.length)], avails[rnd.nextInt(avails.length)], FAKE, IMAGES);
                extremeMaxMs = Math.max(extremeMaxMs, (System.nanoTime() - t0) / 1e6);
                layoutAllItems(ln);
                extremeBad += badGeometry(ln);
            } catch (Throwable e) {
                extremeExceptions++;
                if (extremeExceptions <= 3) failures.add("极端树 #" + i + " 抛出 " + e);
            }
        }
        System.out.printf(Locale.ROOT, "极端树 1000 棵（属性取极值、叶子挂 children、共享子树、可用区取极值）："
                + "异常 %d，几何越界 %d，单棵最大耗时 %.2f ms%n", extremeExceptions, extremeBad, extremeMaxMs);
        eq(extremeExceptions, 0, "极端树无异常");
        eq(extremeBad, 0, "极端树无负尺寸、负坐标、超过无限高的尺寸");
    }

    static final int[] EXTREME_INTS = {Integer.MIN_VALUE, -1, 0, 1, 7, 176, 2048, LayoutEngine.UNBOUNDED, Integer.MAX_VALUE};

    /** 任意类型都可能挂 children，同一层后面的子节点有时直接复用第一个子节点对象。 */
    static Node extremeNode(Random rnd, int depth, int depthLimit, int[] left) {
        left[0]--;
        NodeType type = NodeType.values()[rnd.nextInt(NodeType.values().length)];
        List<Node> kids = new ArrayList<>();
        if (depth < depthLimit && left[0] > 0) {
            for (int k = rnd.nextInt(Math.min(left[0], 6) + 1); k > 0 && left[0] > 0; k--) {
                kids.add(!kids.isEmpty() && rnd.nextInt(4) == 0 ? kids.get(0) : extremeNode(rnd, depth + 1, depthLimit, left));
            }
        }
        int v = EXTREME_INTS[rnd.nextInt(EXTREME_INTS.length)];
        Map<String, Object> props = Map.of("item-height", v, "size", v, "w", v, "h", v, "count", v, "cols", v,
                "height", v, "text", TEXTS[rnd.nextInt(TEXTS.length)], "vertical", rnd.nextBoolean());
        return new Node(type, null, List.of("c" + rnd.nextInt(6)), props, kids, null, null);
    }

    static Node randomTree(Random rnd) {
        int[] left = {1 + rnd.nextInt(NodeParser.MAX_NODES)};
        return randomNode(rnd, 1, 1 + rnd.nextInt(NodeParser.MAX_DEPTH), left, rnd.nextInt(5) == 0);
    }

    /** chain 为 true 时每个容器只挂一个子节点，用来把深度推到上限。 */
    static Node randomNode(Random rnd, int depth, int depthLimit, int[] left, boolean chain) {
        left[0]--;
        int serial = left[0];
        boolean container = depth < depthLimit && left[0] > 0 && (chain || rnd.nextInt(10) < 5);
        NodeType type = container ? CONTAINERS[rnd.nextInt(CONTAINERS.length)] : LEAVES[rnd.nextInt(LEAVES.length)];
        List<Node> kids = new ArrayList<>();
        if (container) {
            int want = chain ? 1 : rnd.nextInt(Math.min(left[0], 10) + 1);
            for (int k = 0; k < want && left[0] > 0; k++) kids.add(randomNode(rnd, depth + 1, depthLimit, left, chain));
        }
        List<String> classes = new ArrayList<>();
        for (int k = rnd.nextInt(3); k > 0; k--) classes.add("c" + rnd.nextInt(6));
        Node.ShowIf showIf = switch (rnd.nextInt(10)) {
            case 0 -> new Node.ShowIf("flag", Node.ShowIf.Kind.TRUTHY, rnd.nextBoolean());
            case 1 -> new Node.ShowIf("n", Node.ShowIf.Kind.EQ, 1);
            case 2 -> new Node.ShowIf("s", Node.ShowIf.Kind.NE, "x");
            default -> null;
        };
        return new Node(type, rnd.nextInt(4) == 0 ? "n" + serial : null, classes, randomProps(rnd, type), kids, showIf, null);
    }

    static Map<String, Object> randomProps(Random rnd, NodeType type) {
        return switch (type) {
            case TEXT -> Map.of("text", TEXTS[rnd.nextInt(TEXTS.length)]);
            case BUTTON -> rnd.nextBoolean() ? Map.of("text", TEXTS[rnd.nextInt(TEXTS.length)]) : Map.of();
            case BADGE -> Map.of("count", new int[]{0, 1, 7, 150}[rnd.nextInt(4)]);
            case SPACER -> Map.of("size", rnd.nextBoolean() ? 0 : 6);
            case GRID -> Map.of("cols", 1 + rnd.nextInt(6));
            case LIST -> Map.of("item-height", new int[]{0, 0, 1, 12}[rnd.nextInt(4)]);
            case IMAGE -> rnd.nextBoolean() ? Map.of("w", 1 + rnd.nextInt(120), "h", 1 + rnd.nextInt(176)) : Map.of();
            case ICON -> Map.of("size", 6 + rnd.nextInt(15));
            case ITEM -> Map.of("size", 8 + rnd.nextInt(13), "count", 1 + rnd.nextInt(99));
            case PROGRESS -> Map.of("value", rnd.nextInt(101), "height", 2 + rnd.nextInt(11));
            case DIVIDER -> Map.of("vertical", rnd.nextBoolean());
            case TOGGLE -> rnd.nextBoolean() ? Map.of("label", TEXTS[rnd.nextInt(TEXTS.length)]) : Map.of();
            default -> Map.of();
        };
    }

    static UiState randomState(Random rnd) {
        return UiState.of(Map.of("flag", rnd.nextBoolean(), "n", rnd.nextInt(3), "s", rnd.nextBoolean() ? "" : "x"));
    }

    /** 几条 class 规则加几条 id 规则，每条的属性互不重复（重复属性 MssParser 会拒）。 */
    static String randomSheet(Random rnd) {
        StringBuilder sb = new StringBuilder();
        Set<String> selectors = new HashSet<>();
        for (int r = rnd.nextInt(12); r > 0; r--) {
            String selector = rnd.nextInt(3) == 0 ? "#n" + rnd.nextInt(NodeParser.MAX_NODES) : ".c" + rnd.nextInt(6);
            if (!selectors.add(selector)) continue;
            sb.append(selector).append(" {");
            Set<String> props = new HashSet<>();
            for (int d = rnd.nextInt(5); d > 0; d--) {
                String decl = DECLS[rnd.nextInt(DECLS.length)];
                if (decl.startsWith("hidden") && rnd.nextInt(4) != 0) continue;
                if (props.add(decl.substring(0, decl.indexOf(':')))) sb.append(' ').append(decl).append(';');
            }
            sb.append(" }\n");
        }
        return sb.toString();
    }

    static void layoutAllItems(LayoutNode n) {
        if (n.node.type() == LIST) {
            for (int i = 0; i < n.children.size(); i++) LayoutEngine.layoutItem(n, i, FAKE);
        }
        for (LayoutNode c : n.children) layoutAllItems(c);
    }

    /** 负尺寸、负坐标、超过无限高的尺寸与坐标，以及不单调的累积高度表，一处记一次。 */
    static int badGeometry(LayoutNode n) {
        int bad = 0;
        for (int v : new int[]{n.x, n.y, n.w, n.h, n.measuredW, n.measuredH, n.contentH}) {
            if (v < 0 || v > LayoutEngine.UNBOUNDED) bad++;
        }
        if (n.cumHeights != null) {
            for (int i = 1; i < n.cumHeights.length; i++) {
                if (n.cumHeights[i] < n.cumHeights[i - 1]) bad++;
            }
        }
        for (LayoutNode c : n.children) bad += badGeometry(c);
        return bad;
    }

    // ============================================================
    //  造树助手
    // ============================================================

    /** §7.8 layoutWith 用的两行文字，第一行带 class f。 */
    static final String TWO_TEXTS = """
        { "pages": { "main": { "type":"column", "id":"root", "children":[
            {"type":"text","text":"ab","class":["f"]},
            {"type":"text","text":"cd"} ] } }, "entry":"main" }""";

    static LayoutNode layout(String ui, String mss, int w, int h) {
        NodeParser.Ui parsed = NodeParser.parse(ui);
        return LayoutEngine.layout(parsed.root(), MssParser.parse(mss), UiState.of(parsed.state()), w, h, FAKE, IMAGES);
    }

    static LayoutNode layoutWith(String mss) {
        return layout(TWO_TEXTS, mss, 120, 176);
    }

    static LayoutNode run(Node root, String mss, int w, int h) {
        return LayoutEngine.layout(root, MssParser.parse(mss), UiState.empty(), w, h, FAKE, IMAGES);
    }

    static LayoutNode child(LayoutNode n, int i) {
        return n.children.get(i);
    }

    static Node ndc(NodeType type, String id, List<String> classes, Map<String, Object> props, Node... children) {
        return new Node(type, id, classes, props, List.of(children), null, null);
    }

    static Node nd(NodeType type, String id, Map<String, Object> props, Node... children) {
        return ndc(type, id, List.of(), props, children);
    }

    static Node col(String id, Node... children) {
        return nd(COLUMN, id, Map.of(), children);
    }

    static Node box(String id) {
        return nd(BOX, id, Map.of());
    }

    static Node text(String id, String text) {
        return nd(TEXT, id, Map.of("text", text));
    }

    /** 放在 120×176 的 column 里的单个节点的宽高。 */
    static List<Integer> size(Node leaf) {
        LayoutNode n = child(run(col("root", leaf), "", 120, 176), 0);
        return List.of(n.w, n.h);
    }

    /** 宽 availW 的 row 里三个空 box，grow 分别为 g1 / g2 / g3。 */
    static LayoutNode threeRowsWithGrow(int g1, int g2, int g3, int availW) {
        return run(nd(ROW, "root", Map.of(), box("c0"), box("c1"), box("c2")),
                "#c0{grow:" + g1 + ";} #c1{grow:" + g2 + ";} #c2{grow:" + g3 + ";}", availW, 176);
    }

    static LayoutNode justify(String mode, int avail, int childrenWidth) {
        return justify(mode, avail, childrenWidth, 1);
    }

    /** 宽 avail 的 row 里 count 个等宽 box，总宽 childrenWidth。 */
    static LayoutNode justify(String mode, int avail, int childrenWidth, int count) {
        Node[] kids = new Node[count];
        StringBuilder mss = new StringBuilder("#root{justify:" + mode + ";}");
        for (int i = 0; i < count; i++) {
            kids[i] = box("k" + i);
            mss.append(" #k").append(i).append("{width:").append(childrenWidth / count).append(";}");
        }
        return run(nd(ROW, "root", Map.of(), kids), mss.toString(), avail, 176);
    }

    /** 宽 avail 的 column 里一个宽 childW 的 box。 */
    static LayoutNode align(String mode, int avail, int childW) {
        return run(col("root", box("k")), "#root{align:" + mode + ";} #k{width:" + childW + ";}", avail, 176);
    }

    /** 宽 avail 的 row 里三个宽 each 的 box。 */
    static LayoutNode threeFixedRows(int each, int avail) {
        return run(nd(ROW, "root", Map.of(), box("a"), box("b"), box("c")),
                "#a{width:" + each + ";} #b{width:" + each + ";} #c{width:" + each + ";}", avail, 176);
    }

    static List<LayoutNode> allNodes(LayoutNode root) {
        List<LayoutNode> out = new ArrayList<>();
        out.add(root);
        for (LayoutNode c : root.children) out.addAll(allNodes(c));
        return out;
    }

    static List<String> textLines(String text, boolean wrap, int w) {
        return textLines(text, wrap, w, 0);
    }

    /** 宽 w 的 column 里一个 text 的换行结果；maxLines 为 0 表示不写 max-lines。 */
    static List<String> textLines(String text, boolean wrap, int w, int maxLines) {
        String mss = "#t{wrap:" + wrap + ";" + (maxLines > 0 ? " max-lines:" + maxLines + ";" : "") + "}";
        return child(run(col("root", text("t", text)), mss, w, 176), 0).lines();
    }

    static LayoutNode badge(int count) {
        return child(run(col("root", nd(BADGE, "b", Map.of("count", count))), "", 120, 176), 0);
    }

    static LayoutNode scrollChildWithFillHeight() {
        return child(run(nd(SCROLL, "root", Map.of(), box("c")), "#c{height:fill;}", 120, 176), 0);
    }

    static LayoutNode listOf(int count, int itemHeight) {
        return run(listNode(count, itemHeight), "", 120, 176);
    }

    static Node listNode(int count, int itemHeight) {
        Node[] items = new Node[count];
        for (int i = 0; i < count; i++) items[i] = text(null, "项" + i);
        return nd(LIST, "root", Map.of("item-height", itemHeight), items);
    }

    static List<Integer> xs(LayoutNode n) {
        List<Integer> out = new ArrayList<>();
        for (LayoutNode c : n.children) out.add(c.x);
        return out;
    }

    /** 整棵树每个节点的 x、y、w、h 与是否测过，拼成一串，用来比较前后有没有变。 */
    static String geometry(LayoutNode root) {
        StringBuilder sb = new StringBuilder();
        for (LayoutNode n : allNodes(root)) {
            sb.append(n.x).append(',').append(n.y).append(',').append(n.w).append(',').append(n.h)
                    .append(n.measured ? '+' : '-').append(';');
        }
        return sb.toString();
    }

    /** 变宽的假实现：CJK 12px、其余 6px，truncate 照 GuiUtil.truncate 的算法。 */
    static final TextMeasure VARIABLE = new TextMeasure() {
        public int width(String t) {
            int w = 0;
            for (int i = 0; i < t.length(); i++) w += t.charAt(i) >= 0x4E00 && t.charAt(i) <= 0x9FFF ? 12 : 6;
            return w;
        }

        public int lineHeight() {
            return 9;
        }

        public String truncate(String t, int max) {
            if (max <= 0) return "";
            if (width(t) <= max) return t;
            int limit = Math.max(0, max - width("…"));
            int end = 0;
            while (end < t.length() && width(t.substring(0, end + 1)) <= limit) end++;
            return t.substring(0, end) + "…";
        }
    };

    static LayoutNode twoRowsFirstHidden() {
        return layout("""
            { "state": { "show": false },
              "pages": { "main": { "type":"column", "id":"root", "children":[
                {"type":"text","text":"ab","showIf":{"key":"show","truthy":true}},
                {"type":"text","text":"cd"} ] } }, "entry":"main" }""",
                "#root{gap:3;}", 120, 176);
    }

    static List<String> keys(List<LayoutNode> nodes) {
        List<String> out = new ArrayList<>();
        for (LayoutNode n : nodes) out.add(n.key);
        return out;
    }

    static int depth(LayoutNode n) {
        int d = 0;
        for (LayoutNode c : n.children) d = Math.max(d, depth(c));
        return d + 1;
    }

    static int depth(Node n) {
        int d = 0;
        for (Node c : n.children()) d = Math.max(d, depth(c));
        return d + 1;
    }

    static int count(Node n) {
        int total = 1;
        for (Node c : n.children()) total += count(c);
        return total;
    }

    static boolean throwsIae(Runnable body) {
        try {
            body.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }
}
