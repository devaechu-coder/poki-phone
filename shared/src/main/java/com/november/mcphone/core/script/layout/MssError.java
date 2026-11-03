package com.november.mcphone.core.script.layout;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * MSS 样式表的全部拒绝理由（施工方案 §6.7）。行列都是 1-based，指向原文。
 *
 * <p>文案写在枚举上而不是抛出点：同一条判据在两处抛过，文案就会长出两个版本，而用户看到
 * 哪一个取决于走哪条分支。
 */
public final class MssError extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 错误码与文案。以 {@code ui.mss %d:%d} 开头的模板，头两个参数是出错处的行列。 */
    public enum Code {
        E_MSS_SYNTAX("ui.mss %d:%d 语法错误：%s"),
        E_MSS_BAD_SELECTOR("ui.mss %d:%d 选择器只能是 .class 或 #id，收到 '%s'%s"),
        E_MSS_COMBINATOR("ui.mss %d:%d 不支持组合选择器（后代/子/兄弟）。给节点直接加 class"),
        E_MSS_MULTI_SELECTOR("ui.mss %d:%d 不支持逗号分隔的多选择器，分开写两条规则"),
        E_MSS_PSEUDO("ui.mss %d:%d 不支持伪类。悬停用 hover-background / hover-color"),
        E_UNKNOWN_PROPERTY("ui.mss %d:%d 不认识的属性 '%s'%s"),
        E_MSS_BAD_VALUE("ui.mss %d:%d 属性 '%s' 的值 '%s' 不合法。允许：%s"),
        E_LITERAL_COLOR("ui.mss %d:%d 不支持写死颜色 '%s'。用语义色：" + Style.Token.all()
                + "。写死的颜色不会跟着玩家的皮肤和主题变，语义色会"),
        E_PERCENT_NOT_SUPPORTED("ui.mss %d:%d 不支持百分比。用 fill 占满可用空间，或写固定像素"),
        E_MSS_DUP_SELECTOR("ui.mss %d:%d 选择器 '%s' 重复定义（上一次在 %d:%d）。合并成一条"),
        E_MSS_TOO_MANY_RULES("ui.mss 规则数 %d 超过上限 " + MssParser.MAX_RULES),
        E_MSS_UNCLOSED("ui.mss %d:%d '{' 没有对应的 '}'");

        private final String text;
        private final int argc;

        Code(String text) {
            this.text = text;
            this.argc = specifiers(text);
        }

        /** 这个码的文案模板。测试拿它对着断言，不必把字符串抄第二遍。 */
        public String text() {
            return text;
        }

        /** 只有「规则数超限」不带位置：它说的是整份文件。 */
        boolean positioned() {
            return text.startsWith("ui.mss %d:%d");
        }

        private static int specifiers(String template) {
            int n = 0;
            for (int i = 0; i + 1 < template.length(); i++) {
                if (template.charAt(i) == '%' && "sd".indexOf(template.charAt(i + 1)) >= 0) n++;
            }
            return n;
        }
    }

    /** 原文里的一个位置。当实参传进模板时占两个 %d，并随 {@link #shift} 一起挪。 */
    record Pos(int line, int col) implements Serializable {
    }

    private final Code code;
    private final int line;
    private final int col;
    private final Object[] args;

    private MssError(Code code, int line, int col, Object[] args) {
        super(render(code, line, col, args));
        this.code = code;
        this.line = line;
        this.col = col;
        this.args = args;
    }

    public Code code() {
        return code;
    }

    /** 出错处的行。规则数超限时指第一条超出上限的规则。 */
    public int line() {
        return line;
    }

    public int col() {
        return col;
    }

    public String message() {
        return getMessage();
    }

    /**
     * 行号整体下移 {@code lines} 行，文案里的每个位置一起挪。
     *
     * <p>ui.mss 嵌在 .mcapp 的 {@code <style>} 块里时，解析器报的是块内行号（§9.8）。列不动：块内容从行首开始。
     */
    public MssError shift(int lines) {
        Object[] moved = args.clone();
        for (int i = 0; i < moved.length; i++) {
            if (moved[i] instanceof Pos p) moved[i] = new Pos(p.line() + lines, p.col());
        }
        return new MssError(code, line + lines, col, moved);
    }

    static MssError at(Code code, Pos at, Object... args) {
        return new MssError(code, at.line(), at.col(), args.clone());
    }

    /**
     * 实参个数与模板对不上时当场抛 {@link IllegalStateException}：可变参数没有编译期检查，而少一个
     * 参数的后果是 {@code MissingFormatArgumentException} —— 那要等这条分支被真正走到才暴露。
     */
    private static String render(Code code, int line, int col, Object[] args) {
        List<Object> flat = new ArrayList<>();
        if (code.positioned()) {
            flat.add(line);
            flat.add(col);
        }
        for (Object a : args) {
            if (a instanceof Pos p) {
                flat.add(p.line());
                flat.add(p.col());
            } else {
                flat.add(a);
            }
        }
        if (flat.size() != code.argc) {
            throw new IllegalStateException(code + " 要 " + code.argc + " 个参数，给了 " + flat.size() + " 个");
        }
        return code.name() + "：" + String.format(Locale.ROOT, code.text, flat.toArray());
    }
}
