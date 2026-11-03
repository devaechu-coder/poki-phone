package com.november.mcphone.core.script.client.render;

import com.november.mcphone.api.client.ui.PhoneCanvas;
import com.november.mcphone.core.script.layout.ImageSizes;
import com.november.mcphone.core.script.layout.LayoutEngine;
import com.november.mcphone.core.script.layout.LayoutNode;
import com.november.mcphone.core.script.layout.MssParser;
import com.november.mcphone.core.script.layout.NodeParser;
import com.november.mcphone.core.script.layout.TextMeasure;
import com.november.mcphone.core.script.layout.UiState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 脚本界面的命中、悬停、滚动与 list 可见区（施工方案 §8.3、§8.5–§8.7）。画不出来，只测不碰渲染状态的这一半。 */
public class ScriptHitTestTest {

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

    /** §7.2 的假实现：每字符 6px，行高 9。一个写着 "x" 的按钮因此是 18×13。 */
    static final TextMeasure FAKE = new TextMeasure() {
        public int width(String t) { return t.length() * 6; }
        public int lineHeight() { return 9; }
        public String truncate(String t, int max) {
            int n = Math.max(0, max / 6);
            return t.length() <= n ? t : t.substring(0, Math.max(0, n - 1)) + "…";
        }
    };

    /** 画布不在原点：命中要把内容区的偏移加回去，漏加的话下面的断言整体错位。 */
    static final int OX = 10;
    static final int OY = 20;
    static final int W = 120;
    static final int H = 176;

    public static void main(String[] a) {
        stacking();
        disabled();
        scrollClip();
        scrolling();
        nestedScrollers();
        fixedList();
        variableList();
        overflow();
        tabs();
        hover();
        rounding();
        icons();
        report();
    }

    // ============================================================
    //  §8.5 要点 1：逆序
    // ============================================================

    static void stacking() {
        LayoutNode r = root("""
            {"type":"stack","id":"root","children":[ %s ]}""".formatted(buttons("under", "over")), ".x{}");
        eq(at(r, 5, 5), "over", "stack 里上层按钮盖住下层时只命中上层");
        eq(at(r, 60, 5), null, "stack 本身不可交互，空白处没有命中");
        eq(at(r, -5, 5), null, "画布左边界之外没有命中");

        LayoutNode badge = root("""
            {"type":"stack","children":[ %s, {"type":"text","text":"xxx"} ]}""".formatted(buttons("under")), ".x{}");
        eq(at(badge, 5, 5), "under", "盖在按钮上的文字不可交互，点击落到按钮上");
    }

    static void disabled() {
        Page p = page("""
            {"type":"column","children":[
              {"type":"stack","children":[ %s,
                {"type":"button","id":"over","text":"x","enabled":false,"onClick":"back"} ]},
              {"type":"button","id":"cond","text":"x","enabledIf":{"key":"on","truthy":true},"onClick":"back"},
              {"type":"toggle","id":"tog","bind":"on","enabled":false} ]}""".formatted(buttons("under")),
            "{\"on\":false}", ".x{}");
        eq(at(p.root, 5, 5), "over", "禁用按钮照样被 pick 到：页面据此吃掉点击，不穿透到下层");
        check(!Renderer.isEnabled(byId(p.root, "over"), p.state), "enabled: false 的按钮不可用");
        check(Renderer.isEnabled(byId(p.root, "under"), p.state), "没写 enabled 的按钮可用");
        check(!Renderer.isEnabled(byId(p.root, "cond"), p.state), "enabledIf 不成立时不可用");
        p.state.set("on", true);
        check(Renderer.isEnabled(byId(p.root, "cond"), p.state), "enabledIf 成立时可用");
        check(!Renderer.isEnabled(byId(p.root, "tog"), p.state), "toggle 的 enabled: false 与 state 无关");
        check(Renderer.isEnabled(p.root, p.state), "不可交互的类型恒为可用");
    }

    // ============================================================
    //  §8.5 要点 2、3：裁剪取交集、滚动偏移
    // ============================================================

    static final String SCROLL_PAGE = """
        {"type":"column","children":[
          {"type":"text","text":"head"},
          {"type":"scroll","id":"sc","children":[ %s ]},
          {"type":"text","text":"tail"} ]}""".formatted(numbered("b", 10));

    static void scrollClip() {
        // head 占 0..9，滚动区 9..39，里面 10 个 13 高的按钮，内容共 130
        LayoutNode r = root(SCROLL_PAGE, "#sc{height:30;}");
        LayoutNode sc = byId(r, "sc");
        eq(at(r, 5, 9 + 5), "b0", "滚动区里第一个按钮");
        eq(at(r, 5, 9 + 28), "b2", "露出一半的按钮点得到");
        eq(at(r, 5, 9 + 32), null, "b2 在内容里延伸到 39，滚动区只到 30：裁掉的那截点不到");
        sameAsClick(r, "未滚动时，悬停与点击在每个点上判给同一个节点");

        sc.scrollY = 20;
        eq(at(r, 5, 9), "b1", "滚动偏移参与命中：视口顶上是内容里的 20");
        eq(at(r, 5, 1), null, "b0 滚出视口上沿后在 head 那一截里，点不到");
        sameAsClick(r, "滚到一半时，悬停与点击在每个点上判给同一个节点");

        sc.scrollY = 1000;
        eq(at(r, 5, 9 + 5), "b8", "越界的 scrollY 判之前先夹到 scrollMax");
        eq(sc.scrollY, 100, "scrollMax = 内容 130 − 视口 30");
    }

    static void scrolling() {
        LayoutNode sc = byId(root(SCROLL_PAGE, "#sc{height:30;}"), "sc");
        eq(HitTest.SCROLL_STEP, 12, "SCROLL_STEP = 12");
        check(!HitTest.scroll(sc, 1.0) && sc.scrollY == 0, "已在顶上再往上滚：返回 false，事件留给页面");
        check(HitTest.scroll(sc, -1.0) && sc.scrollY == 12, "往下滚一格是 12px");
        check(HitTest.scroll(sc, -100) && sc.scrollY == 100, "滚过头停在 scrollMax");
        check(!HitTest.scroll(sc, -1.0) && sc.scrollY == 100, "到底再往下滚：返回 false");
        check(HitTest.scroll(sc, 0.5) && sc.scrollY == 94, "触控板的半格按比例滚");
        check(HitTest.scroll(sc, 0.01) && sc.scrollY == 93, "不足 1px 的小数格也滚 1px，不会永远推不动");

        LayoutNode padded = root(SCROLL_PAGE, "#sc{height:30;padding:4;}");
        LayoutNode psc = byId(padded, "sc");
        HitTest.scroll(psc, -100);
        eq(psc.scrollY, 108, "scrollMax 含 padding：130 + 8 − 30");
        eq(at(padded, 5, 9 + 20), "b9", "滚到底时最后一项完整露出来，点得到");
    }

    static void nestedScrollers() {
        LayoutNode r = root("""
            {"type":"column","children":[
              {"type":"scroll","id":"outer","children":[
                {"type":"scroll","id":"inner","children":[ %s ]}, %s ]} ]}"""
                .formatted(numbered("i", 5), numbered("o", 5)), "#outer{height:60;} #inner{height:30;}");
        eq(scrollerAt(r, 5, 5), "inner", "滚轮给最内层命中的滚动区");
        eq(scrollerAt(r, 5, 45), "outer", "内层之外、外层之内给外层");
        eq(scrollerAt(r, 5, 70), null, "两个滚动区之外没有");
        eq(at(r, 5, 35), "o0", "外层里内层下面的按钮");

        byId(r, "outer").scrollY = 40;   // 内容 95、视口 60，夹到 35
        eq(scrollerAt(r, 5, 5), "outer", "内层滚出视口后不再接滚轮");
        eq(at(r, 5, 5), "o0", "外层滚过 35 后视口顶上是 o0");
    }

    // ============================================================
    //  §8.3 可见区
    // ============================================================

    static void fixedList() {
        LayoutNode r = root("""
            {"type":"column","children":[
              {"type":"list","id":"l","item-height":10,"children":[ %s ]} ]}""".formatted(numbered("i", 20)),
            "#l{height:30;}");
        LayoutNode l = byId(r, "l");
        eq(l.children.get(1).w, 0, "定高 list 布局时不排项");
        eq(at(r, 5, 15), "i1", "判之前把可见区里的项排好");
        check(l.children.get(1).w > 0, "被判到的项已经排过");
        eq(l.children.get(15).w, 0, "可见区外的项仍然不排");
        eq(range(l, 0), List.of(0, 3), "滚动 0：露出 0–2，下面多一项");

        l.scrollY = 95;
        eq(at(r, 5, 0), "i9", "滚到 95，视口顶上是半露出来的第 9 项");
        eq(at(r, 5, 12), "i10", "往下 12 是第 10 项");
        eq(at(r, 5, 35), null, "视口下面被裁掉");
        eq(range(l, 95), List.of(8, 13), "滚动 95：露出 9–12，上下各多一项");
        eq(range(l, 170), List.of(16, 19), "滚到底：下面没有多余的项可加");

        LayoutNode g = byId(root("""
            {"type":"column","children":[
              {"type":"list","id":"g","item-height":10,"children":[ %s ]} ]}""".formatted(numbered("i", 20)),
            "#g{height:30;gap:2;}"), "g");
        eq(range(g, 23), List.of(0, 5), "gap 计入步长：12 一项");
    }

    static void variableList() {
        LayoutNode v = byId(root("""
            {"type":"column","children":[
              {"type":"list","id":"v","children":[ %s ]} ]}""".formatted(numbered("i", 10)),
            "#v{height:30;gap:2;}"), "v");
        eq(range(v, 0), List.of(0, 2), "不定高：顶边 0、15、30…，视口 0–29 露出 0、1");
        eq(range(v, 40), List.of(1, 5), "不定高：视口 40–69 露出 2–4");

        LayoutNode r = root("""
            {"type":"column","children":[
              {"type":"list","id":"vp","children":[ %s ]} ]}""".formatted(numbered("i", 10)),
            "#vp{height:80;padding:30 0 0 0;}");
        LayoutNode vp = byId(r, "vp");
        eq(range(vp, 42).get(0), 0, "视口从 scrollY − padTop 算起：顶上露出来的第 0 项在可见区里");
        vp.scrollY = 42;
        eq(at(r, 5, 5), "i1", "带 padding 的 list：屏幕 5 是内容 5 − 30 + 42 = 17，第 1 项");
    }

    // ============================================================
    //  溢出、tab-bar、悬停、取整
    // ============================================================

    static void overflow() {
        LayoutNode r = root("""
            {"type":"column","id":"c","children":[ %s ]}""".formatted(numbered("b", 3)), "#c{height:20;}");
        eq(at(r, 5, 30), "b2", "column 不裁剪：溢出去的按钮画在外面，也点得到");

        LayoutNode z = root("""
            {"type":"column","id":"z","children":[ %s ]}""".formatted(numbered("b", 1)), "#z{height:0;}");
        eq(at(z, 5, 5), "b0", "高 0 的容器里溢出的按钮也点得到");
    }

    static final String TAB_PAGE = """
        {"type":"column","children":[
          {"type":"tab-bar","id":"tb","bind":"t","tabs":[{"text":"a"},{"text":"b"},{"text":"c"}]} ]}""";

    static void tabs() {
        Page p = page(TAB_PAGE, "{\"t\":0}", ".x{}");
        LayoutNode tb = byId(p.root, "tb");
        eq(at(p.root, 60, 5), "tb", "tab-bar 可交互");
        eq(tb.w, 120, "tab-bar 占满宽");
        eq(HitTest.segmentAt(tb, OX, OX), 0, "左边界是第 0 段");
        eq(HitTest.segmentAt(tb, OX, OX + 39.9), 0, "40 以内是第 0 段");
        eq(HitTest.segmentAt(tb, OX, OX + 40), 1, "40 起是第 1 段");
        eq(HitTest.segmentAt(tb, OX, OX + 119), 2, "最右一格是最后一段");
        eq(HitTest.segmentAt(tb, OX, OX + 500), 2, "越界夹到最后一段");
        eq(HitTest.segmentAt(tb, OX, OX - 3), 0, "越界夹到第 0 段");

        LayoutNode narrow = byId(page(TAB_PAGE, "{\"t\":0}", "#tb{width:100;}").root, "tb");
        eq(narrow.w, 100, "写死宽 100");
        eq(HitTest.segmentAt(narrow, OX, OX + 65.9), 1, "段宽 33：66 之前是第 1 段");
        eq(HitTest.segmentAt(narrow, OX, OX + 66), 2, "余数给最后一段：66–99 是第 2 段");
        eq(HitTest.segmentAt(narrow, OX, OX + 99.5), 2, "最后一段吃掉余数");

        LayoutNode tiny = byId(page(TAB_PAGE, "{\"t\":0}", "#tb{width:2;}").root, "tb");
        eq(HitTest.segmentAt(tiny, OX, OX), 2, "比段数还窄：画出来整条是最后一段，判也给最后一段");
    }

    static void hover() {
        LayoutNode r = root("""
            {"type":"stack","id":"root","children":[ %s ]}""".formatted(buttons("under", "over")), ".x{}");
        Frame f = frame(r, OX + 5, OY + 5);
        check(f.hovered(byId(r, "over")), "悬停在上层按钮上");
        check(f.hovered(r), "祖先一起标上");
        check(!f.hovered(byId(r, "under")), "被盖住的下层不算悬停");
        check(!frame(r, OX - 1, OY + 5).hovered(r), "鼠标不在内容区：什么都不悬停");
        check(!frame(r, OX + 60, OY + 5).hovered(r), "没命中可交互节点时祖先也不标");
    }

    static void rounding() {
        LayoutNode r = root("""
            {"type":"row","children":[ %s ]}""".formatted(buttons("l", "r")), ".x{}");
        LayoutNode left = byId(r, "l");
        LayoutNode right = byId(r, "r");
        PhoneCanvas c = canvas(0, 0);
        eq(left.w, 18, "左按钮 18 宽，右按钮从 18 起");
        // 100% 手机缩放下宿主给悬停的是原版截断后的整数，给点击的是小数
        eq(HitTest.pick(r, c, OX + 17.9, OY + 5), left, "点击 17.9 不取整，是左按钮");
        check(frame(r, OX + 17, OY + 5).hovered(left), "同一处的悬停截断成 17，也是左按钮");
        eq(HitTest.pick(r, c, OX + 18, OY + 5), right, "18 起是右按钮");

        LayoutNode tb = byId(page(TAB_PAGE, "{\"t\":0}", ".x{}").root, "tb");
        eq(HitTest.segmentAt(tb, c, OX + 39.9), 0, "segmentAt 同样不取整：39.9 还在第 0 段");
    }

    static void icons() {
        eq(new HashSet<>(IconAtlas.NAMES), NodeParser.ICON_NAMES, "图集覆盖 NodeParser 认的全部图标名，不多不少");
        eq(IconAtlas.NAMES.size(), 12, "12 个图标");
    }

    // ============================================================
    //  小工具
    // ============================================================

    record Page(LayoutNode root, UiState state) {
    }

    static Page page(String body, String state, String mss) {
        NodeParser.Ui ui = NodeParser.parse("{\"state\":" + state + ",\"pages\":{\"main\":" + body + "},\"entry\":\"main\"}");
        UiState s = UiState.of(ui.state());
        return new Page(LayoutEngine.layout(ui.root(), MssParser.parse(mss), s, W, H, FAKE, ImageSizes.NONE), s);
    }

    static LayoutNode root(String body, String mss) {
        return page(body, "{}", mss).root;
    }

    static String buttons(String... ids) {
        StringBuilder sb = new StringBuilder();
        for (String id : ids) {
            if (sb.length() > 0) sb.append(',');
            sb.append("{\"type\":\"button\",\"id\":\"").append(id).append("\",\"text\":\"x\",\"onClick\":\"back\"}");
        }
        return sb.toString();
    }

    static String numbered(String prefix, int n) {
        String[] ids = new String[n];
        for (int i = 0; i < n; i++) ids[i] = prefix + i;
        return buttons(ids);
    }

    static String at(LayoutNode root, double x, double y) {
        return id(HitTest.pick(root, OX, OY, W, H, OX + x, OY + y, FAKE));
    }

    static String scrollerAt(LayoutNode root, double x, double y) {
        return id(HitTest.pickScroller(root, OX, OY, W, H, OX + x, OY + y, FAKE));
    }

    static String id(LayoutNode n) {
        return n == null ? null : n.node.id();
    }

    static LayoutNode byId(LayoutNode n, String id) {
        if (id.equals(n.node.id())) return n;
        for (LayoutNode c : n.children) {
            LayoutNode found = byId(c, id);
            if (found != null) return found;
        }
        return null;
    }

    static List<Integer> range(LayoutNode list, int scrollY) {
        int[] r = Renderer.visibleRange(list, scrollY);
        return List.of(r[0], r[1]);
    }

    static PhoneCanvas canvas(int mouseX, int mouseY) {
        return new PhoneCanvas(null, null, OX, OY, W, H, mouseX, mouseY, 0f, null);
    }

    static Frame frame(LayoutNode root, int mouseX, int mouseY) {
        return new Frame(root, canvas(mouseX, mouseY), UiState.empty(), null, null);
    }

    /** 画布上逐点比：悬停链里的可交互节点恰好是 pick 的结果。 */
    static void sameAsClick(LayoutNode root, String what) {
        int bad = 0;
        for (int y = -3; y < 60; y++) {
            for (int x = -3; x < W + 3; x += 2) {
                LayoutNode hit = HitTest.pick(root, OX, OY, W, H, OX + x, OY + y, FAKE);
                Set<LayoutNode> chain = HitTest.hoverChain(root, OX, OY, W, H, OX + x, OY + y, FAKE);
                long interactive = chain.stream().filter(n -> n.node.type().interactive).count();
                if (hit == null ? !chain.isEmpty() : !chain.contains(hit) || interactive != 1) bad++;
            }
        }
        eq(bad, 0, what);
    }
}
