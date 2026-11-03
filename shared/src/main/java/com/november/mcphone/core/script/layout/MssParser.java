package com.november.mcphone.core.script.layout;

import com.november.mcphone.core.script.layout.MssError.Code;
import com.november.mcphone.core.script.layout.MssError.Pos;
import com.november.mcphone.core.script.layout.Style.Align;
import com.november.mcphone.core.script.layout.Style.Justify;
import com.november.mcphone.core.script.layout.Style.Layout;
import com.november.mcphone.core.script.layout.Style.SizeSpec;
import com.november.mcphone.core.script.layout.Style.TextAlign;
import com.november.mcphone.core.script.layout.Style.Token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;

/**
 * ui.mss → {@link Stylesheet}（施工方案 §6.2 的文法、§6.5 的属性表、§6.7 的错误表）。
 *
 * <p>手写扫描，不递归，不用正则：输入是陌生人写的，任何字符串进来都只能得到 Stylesheet 或 MssError。
 */
public final class MssParser {

    public static final int MAX_RULES = 64;

    private static final int SUGGEST_DISTANCE = 2;

    /** 回显进文案的原文上限。一整行垃圾原样塞进提示里，提示本身就没法看了。 */
    private static final int ECHO_MAX = 32;

    /** 取值最多的 padding 也只要 4 个词。再多就不必攒着：攒着能被一行超长的值吹起内存。 */
    private static final int MAX_VALUE_WORDS = 4;

    /** 选择器名不合规时补在 E_MSS_BAD_SELECTOR 后面。 */
    private static final String NAME_RULE = "。名字要小写字母开头，后面只能是小写字母、数字、_ 和 -，最长 32";

    private static final int BAD = Integer.MIN_VALUE;

    /** 属性表（§6.5）。加属性只动这张表和 Style 的字段；按表的顺序迭代，列「可用的属性」时每次都一样。 */
    private static final Map<String, PropSpec> SPECS = specs();

    private static Map<String, PropSpec> specs() {
        Map<String, PropSpec> m = new LinkedHashMap<>();
        m.put("layout",           PropSpec.keyword(Layout.values(), (b, v) -> b.layout = v));
        m.put("width",            PropSpec.size(0, 120, (b, v) -> b.width = v));
        m.put("height",           PropSpec.size(0, 176, (b, v) -> b.height = v));
        m.put("grow",             PropSpec.integer(0, 16, (b, v) -> b.grow = v));
        m.put("padding",          PropSpec.edges(0, 32, (b, v) -> {
            b.padTop = v[0];
            b.padRight = v[1];
            b.padBottom = v[2];
            b.padLeft = v[3];
        }));
        m.put("gap",              PropSpec.integer(0, 32, (b, v) -> b.gap = v));
        m.put("align",            PropSpec.keyword(Align.values(), (b, v) -> b.align = v));
        m.put("justify",          PropSpec.keyword(Justify.values(), (b, v) -> b.justify = v));
        m.put("background",       PropSpec.token(true, (b, v) -> b.background = v));
        m.put("hover-background", PropSpec.token(true, Style.Builder::hoverBackground));
        m.put("color",            PropSpec.token(false, (b, v) -> b.color = v));
        m.put("hover-color",      PropSpec.token(false, Style.Builder::hoverColor));
        m.put("border",           PropSpec.integer(0, 2, (b, v) -> b.border = v));
        m.put("shadow",           PropSpec.bool((b, v) -> b.shadow = v));
        m.put("text-align",       PropSpec.keyword(TextAlign.values(), (b, v) -> b.textAlign = v));
        m.put("wrap",             PropSpec.bool((b, v) -> b.wrap = v));
        m.put("max-lines",        PropSpec.integerOrNone(1, 32, Style.NO_MAX_LINES, (b, v) -> b.maxLines = v));
        m.put("hidden",           PropSpec.bool((b, v) -> b.hidden = v));
        return Collections.unmodifiableMap(m);
    }

    /** 一个属性能取什么值：{@code allowed} 进错误文案；{@code parse} 把值词串变成一次写入，不合法返回 null。 */
    private record PropSpec(String allowed, Function<List<String>, Consumer<Style.Builder>> parse) {

        /** 关键字取枚举常量名的小写。 */
        static <E extends Enum<E>> PropSpec keyword(E[] values, BiConsumer<Style.Builder, E> set) {
            List<String> names = new ArrayList<>();
            for (E e : values) names.add(e.name().toLowerCase(Locale.ROOT));
            return new PropSpec(String.join(" | ", names), w -> {
                int i = w.size() == 1 ? names.indexOf(w.get(0)) : -1;
                if (i < 0) return null;
                E v = values[i];
                return b -> set.accept(b, v);
            });
        }

        static PropSpec integer(int min, int max, ObjIntConsumer<Style.Builder> set) {
            return new PropSpec(range(min, max), w -> {
                int n = w.size() == 1 ? intIn(w.get(0), min, max) : BAD;
                return n == BAD ? null : b -> set.accept(b, n);
            });
        }

        static PropSpec integerOrNone(int min, int max, int none, ObjIntConsumer<Style.Builder> set) {
            return new PropSpec("none | " + range(min, max), w -> {
                if (w.size() != 1) return null;
                int n = w.get(0).equals("none") ? none : intIn(w.get(0), min, max);
                return n == BAD ? null : b -> set.accept(b, n);
            });
        }

        static PropSpec size(int min, int max, BiConsumer<Style.Builder, SizeSpec> set) {
            return new PropSpec("auto | fill | " + range(min, max), w -> {
                if (w.size() != 1) return null;
                String s = w.get(0);
                SizeSpec v;
                if (s.equals("auto")) {
                    v = SizeSpec.AUTO;
                } else if (s.equals("fill")) {
                    v = SizeSpec.FILL;
                } else {
                    int n = intIn(s, min, max);
                    if (n == BAD) return null;
                    v = SizeSpec.fixed(n);
                }
                return b -> set.accept(b, v);
            });
        }

        /** 1–4 个值，按 CSS 的顺序展开成上、右、下、左。 */
        static PropSpec edges(int min, int max, BiConsumer<Style.Builder, int[]> set) {
            return new PropSpec("1 到 4 个 " + range(min, max), w -> {
                if (w.isEmpty() || w.size() > 4) return null;
                int[] v = new int[w.size()];
                for (int i = 0; i < v.length; i++) {
                    v[i] = intIn(w.get(i), min, max);
                    if (v[i] == BAD) return null;
                }
                int[] trbl = switch (v.length) {
                    case 1 -> new int[]{v[0], v[0], v[0], v[0]};
                    case 2 -> new int[]{v[0], v[1], v[0], v[1]};
                    case 3 -> new int[]{v[0], v[1], v[2], v[1]};
                    default -> v;
                };
                return b -> set.accept(b, trbl);
            });
        }

        static PropSpec token(boolean noneAllowed, BiConsumer<Style.Builder, Token> set) {
            List<String> names = new ArrayList<>();
            if (noneAllowed) names.add("none");
            for (Token t : Token.values()) names.add(t.mss);
            return new PropSpec(String.join(" | ", names), w -> {
                if (w.size() != 1) return null;
                if (noneAllowed && w.get(0).equals("none")) return b -> set.accept(b, null);
                Token t = Token.of(w.get(0));
                return t == null ? null : b -> set.accept(b, t);
            });
        }

        static PropSpec bool(BiConsumer<Style.Builder, Boolean> set) {
            return new PropSpec("true | false", w -> {
                if (w.size() != 1 || !(w.get(0).equals("true") || w.get(0).equals("false"))) return null;
                boolean v = w.get(0).equals("true");
                return b -> set.accept(b, v);
            });
        }

        private static String range(int min, int max) {
            return min + ".." + max + " 的整数";
        }
    }

    private final String src;
    private final int len;
    private int pos;
    private int line = 1;
    private int col = 1;

    private MssParser(String src) {
        this.src = src;
        this.len = src.length();
    }

    /** 解析一整份 ui.mss。遇到第一处错误就抛，不做恢复。 */
    public static Stylesheet parse(String src) throws MssError {
        return new MssParser(Objects.requireNonNull(src, "src")).sheet();
    }

    /** 属性名，按 §6.5 表的顺序。 */
    static List<String> properties() {
        return List.copyOf(SPECS.keySet());
    }

    /** 这个属性的「允许」文案，不认识的属性返回 null。 */
    static String allowed(String property) {
        PropSpec spec = SPECS.get(property);
        return spec == null ? null : spec.allowed();
    }

    // ============================================================
    //  规则
    // ============================================================

    private Stylesheet sheet() {
        if (len > 0 && src.charAt(0) == '\uFEFF') pos = 1;   // BOM 不占列
        List<Stylesheet.Rule> rules = new ArrayList<>();
        Map<String, Pos> seen = new HashMap<>();
        int count = 0;
        Pos firstOverflow = null;
        while (true) {
            skipTrivia();
            if (pos >= len) break;
            Pos at = here();
            String selector = selector();
            Pos prev = seen.putIfAbsent(selector, at);
            if (prev != null) throw MssError.at(Code.E_MSS_DUP_SELECTOR, at, selector, prev);
            Pos open = here();
            advance();
            List<Consumer<Style.Builder>> decls = body(open);
            // 超限之后照样读完（文案报总数），但不再留规则：留着的话内存随条数涨
            if (++count <= MAX_RULES) {
                rules.add(new Stylesheet.Rule(selector.charAt(0) == '#', selector.substring(1), at, decls));
            } else if (firstOverflow == null) {
                firstOverflow = at;
            }
        }
        if (firstOverflow != null) throw MssError.at(Code.E_MSS_TOO_MANY_RULES, firstOverflow, count);
        return new Stylesheet(rules);
    }

    /** 读一个选择器，停在它后面的 '{' 上。返回原文写法 {@code .name} 或 {@code #name}。 */
    private String selector() {
        int start = pos;
        Pos at = here();
        char c = src.charAt(pos);
        if (c == '}') throw syntax(at, "多出来的 '}'");
        if (fullWidth(c)) throw halfWidthHint(at, c);
        if (c != '.' && c != '#') throw badSelector(at, "", start, false);
        advance();
        int nameStart = pos;
        while (pos < len && identPart(src.charAt(pos))) advance();
        if (pos < len && fullWidth(src.charAt(pos))) throw halfWidthHint(here(), src.charAt(pos));
        if (!nodeName(src.substring(nameStart, pos))) throw badSelector(at, "", start, true);
        String selector = src.substring(start, pos);

        // 注释在 CSS 里什么都不算，.a/**/.b 仍是紧贴的复合选择器；隔着空白才算组合子
        skipComments();
        if (pos < len && ".#[*".indexOf(src.charAt(pos)) >= 0) throw badSelector(at, selector, pos, false);

        skipTrivia();
        if (pos >= len) throw syntax(here(), "选择器 '" + selector + "' 后面要 '{'");
        char n = src.charAt(pos);
        if (n == '{') return selector;
        if (n == ':') throw MssError.at(Code.E_MSS_PSEUDO, here());
        if (n == ',') throw MssError.at(Code.E_MSS_MULTI_SELECTOR, here());
        if (">+~*.#[".indexOf(n) >= 0 || identStart(n)) throw MssError.at(Code.E_MSS_COMBINATOR, here());
        if (fullWidth(n)) throw halfWidthHint(here(), n);
        throw syntax(here(), "选择器 '" + selector + "' 后面要 '{'");
    }

    /** 读规则体，从 '{' 之后读到配对的 '}'（含）。 */
    private List<Consumer<Style.Builder>> body(Pos open) {
        List<Consumer<Style.Builder>> decls = new ArrayList<>();
        Set<String> written = new HashSet<>();
        while (true) {
            skipTrivia();
            if (pos >= len) throw MssError.at(Code.E_MSS_UNCLOSED, open);
            if (src.charAt(pos) == '}') {
                advance();
                return decls;
            }
            decls.add(declaration(open, written));
        }
    }

    private Consumer<Style.Builder> declaration(Pos open, Set<String> written) {
        Pos at = here();
        char c = src.charAt(pos);
        if (fullWidth(c)) throw halfWidthHint(at, c);
        if (c == '.' || c == '#') throw syntax(at, "规则里不能再套规则；如果是上一条规则漏了 '}'，补上它");
        if (!identPart(c)) throw syntax(at, "这里要属性名，收到 '" + echo(codePointAt(pos)) + "'");
        int start = pos;
        while (pos < len && identPart(src.charAt(pos))) advance();
        if (pos < len && fullWidth(src.charAt(pos))) throw halfWidthHint(here(), src.charAt(pos));
        String name = src.substring(start, pos);
        PropSpec spec = SPECS.get(name);
        if (spec == null) throw MssError.at(Code.E_UNKNOWN_PROPERTY, at, echo(name), suggestion(name));
        // 同一条规则里写两次，读的人看到第一个，生效的是第二个
        if (!written.add(name)) throw syntax(at, "属性 '" + name + "' 在这条规则里写了两次，留一个");

        skipTrivia();
        if (pos >= len) throw MssError.at(Code.E_MSS_UNCLOSED, open);
        if (src.charAt(pos) != ':') {
            if (fullWidth(src.charAt(pos))) throw halfWidthHint(here(), src.charAt(pos));
            throw syntax(here(), "属性 '" + name + "' 后面要 ':'");
        }
        advance();

        Pos valueAt = null;
        List<String> words = new ArrayList<>();
        while (true) {
            skipTrivia();
            if (pos >= len) throw MssError.at(Code.E_MSS_UNCLOSED, open);
            char v = src.charAt(pos);
            if (v == ';') break;
            if (v == '{' || v == '}') throw syntax(here(), "属性 '" + name + "' 的声明要以 ';' 结尾");
            Pos wordAt = here();
            if (valueAt == null) valueAt = wordAt;
            String w = word();
            if (w.charAt(0) == '#') throw MssError.at(Code.E_LITERAL_COLOR, wordAt, echo(w));
            if (w.indexOf('%') >= 0) throw MssError.at(Code.E_PERCENT_NOT_SUPPORTED, wordAt);
            if (words.size() == MAX_VALUE_WORDS) {
                throw MssError.at(Code.E_MSS_BAD_VALUE, valueAt, name,
                        echo(String.join(" ", words) + " " + w), spec.allowed());
            }
            words.add(w);
        }
        if (valueAt == null) valueAt = here();
        advance();

        Consumer<Style.Builder> write = spec.parse().apply(words);
        if (write == null) {
            throw MssError.at(Code.E_MSS_BAD_VALUE, valueAt, name, echo(String.join(" ", words)), spec.allowed());
        }
        return write;
    }

    // ============================================================
    //  扫描
    // ============================================================

    /** 一个值词：读到空白、';'、'{'、'}' 或注释开头为止。调用方保证当前字符不是这些。 */
    private String word() {
        int start = pos;
        while (pos < len) {
            char c = src.charAt(pos);
            if (space(c) || c == ';' || c == '{' || c == '}' || commentAt(pos)) break;
            if (fullWidth(c)) throw halfWidthHint(here(), c);
            advance();
        }
        return src.substring(start, pos);
    }

    private void skipTrivia() {
        skip(true);
    }

    private void skipComments() {
        skip(false);
    }

    private void skip(boolean spaces) {
        while (pos < len) {
            char c = src.charAt(pos);
            if (spaces && space(c)) {
                advance();
            } else if (c == '/' && pos + 1 < len && src.charAt(pos + 1) == '/') {
                while (pos < len && src.charAt(pos) != '\n') advance();
            } else if (c == '/' && pos + 1 < len && src.charAt(pos + 1) == '*') {
                Pos at = here();
                advance();
                advance();
                while (!(pos + 1 < len && src.charAt(pos) == '*' && src.charAt(pos + 1) == '/')) {
                    if (pos >= len) throw syntax(at, "注释 /* 没有对应的 */");
                    advance();
                }
                advance();
                advance();
            } else {
                return;
            }
        }
    }

    /** 只有 \n 算换行：.mcapp 的块切分按 \n 数行（§9.3），两边数法不同，shift 之后的行号就对不上。 */
    private void advance() {
        if (src.charAt(pos++) == '\n') {
            line++;
            col = 1;
        } else {
            col++;
        }
    }

    private Pos here() {
        return new Pos(line, col);
    }

    private boolean commentAt(int i) {
        return src.charAt(i) == '/' && i + 1 < len && (src.charAt(i + 1) == '/' || src.charAt(i + 1) == '*');
    }

    private String codePointAt(int i) {
        return new String(Character.toChars(src.codePointAt(i)));
    }

    private static boolean space(char c) {
        return c == ' ' || c == '\t' || c == '\r' || c == '\n';
    }

    private static boolean identStart(char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c == '_';
    }

    private static boolean identPart(char c) {
        return identStart(c) || c >= '0' && c <= '9' || c == '-';
    }

    /** 与 NodeParser 的 ID / CLASS_NAME 同一判据 {@code [a-z][a-z0-9_-]{0,31}}：放得更宽，写出来的规则永远匹配不到节点。 */
    private static boolean nodeName(String s) {
        if (s.isEmpty() || s.length() > 32 || s.charAt(0) < 'a' || s.charAt(0) > 'z') return false;
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '-')) return false;
        }
        return true;
    }

    /** 全角空格与全角 ASCII。中文输入法下最常见的笔误，按普通的「语法错误」报没人看得出差在哪。 */
    private static boolean fullWidth(char c) {
        return c == '\u3000' || c >= '\uFF01' && c <= '\uFF5E';
    }

    // ============================================================
    //  错误
    // ============================================================

    private static MssError syntax(Pos at, String detail) {
        return MssError.at(Code.E_MSS_SYNTAX, at, detail);
    }

    private static MssError halfWidthHint(Pos at, char c) {
        char half = c == '\u3000' ? ' ' : (char) (c - 0xFEE0);
        return syntax(at, "全角字符 '" + c + "'，换成半角的 '" + half + "'");
    }

    /** 回显 head 加上从 from 起的一段原文：到空白、花括号、';'、','、注释开头为止，至少一个字符。 */
    private MssError badSelector(Pos at, String head, int from, boolean nameRule) {
        int end = from;
        while (end < len && end - from <= ECHO_MAX) {
            char c = src.charAt(end);
            if (space(c) || c == '{' || c == '}' || c == ';' || c == ',' || commentAt(end)) break;
            end++;
        }
        if (end == from && from < len) end = from + Character.charCount(src.codePointAt(from));
        return MssError.at(Code.E_MSS_BAD_SELECTOR, at, echo(head + src.substring(from, end)), nameRule ? NAME_RULE : "");
    }

    /** 编辑距离 2 以内最近的属性名（同距离取表里靠前的）；没有就全列出来。 */
    private static String suggestion(String name) {
        // §6.2 正文写过 hover，而它离哪个属性都超过 2
        if (name.equals("hover")) return "，悬停用 hover-background / hover-color";
        String best = null;
        int bestDistance = SUGGEST_DISTANCE + 1;
        for (String p : SPECS.keySet()) {
            if (Math.abs(p.length() - name.length()) > SUGGEST_DISTANCE) continue;
            int d = distance(name, p);
            if (d < bestDistance) {
                best = p;
                bestDistance = d;
            }
        }
        return best != null
                ? "，你是想写 '" + best + "' 吗？"
                : "。可用的属性：" + String.join(" ", SPECS.keySet());
    }

    private static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitute = prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                cur[j] = Math.min(substitute, Math.min(prev[j], cur[j - 1]) + 1);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    /** 截到 ECHO_MAX，控制、格式与行分隔字符换成 U+FFFD：换行会把单行提示撕开，双向控制符能让提示读起来和原文不同。 */
    private static String echo(String s) {
        int n = Math.min(s.length(), ECHO_MAX);
        if (n < s.length() && Character.isHighSurrogate(s.charAt(n - 1))) n--;
        StringBuilder sb = new StringBuilder(n + 1);
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            int type = Character.getType(c);
            boolean hidden = type == Character.CONTROL || type == Character.FORMAT
                    || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR;
            sb.append(hidden ? '\uFFFD' : c);
        }
        if (n < s.length()) sb.append('…');
        return sb.toString();
    }

    private static int intIn(String s, int min, int max) {
        boolean negative = s.startsWith("-");
        int i = negative ? 1 : 0;
        if (i == s.length()) return BAD;
        long n = 0;
        for (; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return BAD;
            n = n * 10 + (c - '0');
            if (n > Integer.MAX_VALUE) return BAD;
        }
        if (negative) n = -n;
        return n < min || n > max ? BAD : (int) n;
    }
}
