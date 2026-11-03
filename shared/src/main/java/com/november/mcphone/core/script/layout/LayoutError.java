package com.november.mcphone.core.script.layout;

import java.util.Locale;

/**
 * Node IR 的全部拒绝理由（施工方案 §4.9）。
 *
 * <p>文案写在枚举上而不是抛出点：同一条判据在两处抛过，文案就会长出两个版本，而用户看到
 * 哪一个取决于走哪条分支。
 */
public final class LayoutError extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 错误码与文案。每条都把「允许的取值」列出来 —— 不列的话第三方只能去翻文档，而文档会过期。 */
    public enum Code {
        E_JSON_SYNTAX("ui.json 第 %d 行第 %d 列：%s"),
        E_UNKNOWN_FIELD("%s：不认识的字段 '%s'。这个类型接受：%s"),
        E_MISSING_FIELD("%s：缺少必填字段 '%s'"),
        E_BAD_TYPE("%s：字段 '%s' 要 %s，给的是 %s"),
        E_BAD_VALUE("%s：字段 '%s' 的值 '%s' 不合法。允许：%s"),
        E_UNKNOWN_TYPE("%s：不认识的节点类型 '%s'。允许：%s"),
        E_DUP_ID("%s：id '%s' 和 %s 撞了，id 必须全树唯一"),
        E_CHILDREN_NOT_ALLOWED("%s：类型 '%s' 不接受 children。接受 children 的有：%s"),
        E_TOO_MANY_NODES("节点总数 %d 超过上限 " + NodeParser.MAX_NODES),
        E_TOO_DEEP("%s：嵌套深度 %d 超过上限 " + NodeParser.MAX_DEPTH),
        E_UNKNOWN_STATE_KEY("%s：state 里没有 '%s'。已声明的有：%s"),
        E_STATE_TYPE("%s：state['%s'] 是 %s，不能和 %s 比较"),
        E_UNKNOWN_PAGE("%s：pages 里没有 '%s'。已声明的有：%s"),
        E_NO_ACTION("%s：onClick 至少要有 set / toggle / nav 之一"),
        E_DUP_KEY("%s：键 '%s' 出现了两次 —— 同一份 IR 会被读出两种结果，人看到第一个，跑的是第二个");

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

        /** 模板要几个参数。{@link LayoutError#of} 拿它挡住实参个数不对的调用。 */
        public int argc() {
            return argc;
        }

        private static int specifiers(String template) {
            int n = 0;
            for (int i = 0; i + 1 < template.length(); i++) {
                if (template.charAt(i) == '%' && "sd".indexOf(template.charAt(i + 1)) >= 0) n++;
            }
            return n;
        }
    }

    private final Code code;
    private final String path;
    private final int offset;

    private LayoutError(Code code, String path, int offset, String message) {
        super(message);
        this.code = code;
        this.path = path;
        this.offset = offset;
    }

    public Code code() {
        return code;
    }

    /** 出错节点的路径，形如 {@code pages.main.children[2].children[0]}。 */
    public String path() {
        return path;
    }

    /** 出错处在原文里的字符偏移，-1 表示没有对应位置。 */
    public int offset() {
        return offset;
    }

    /**
     * 成一条错误。
     *
     * <p>实参个数与模板对不上时当场抛 {@link IllegalStateException}：可变参数没有编译期检查，
     * 而少一个参数的后果是 {@code MissingFormatArgumentException} —— 那要等这条分支被真正走到
     * 才暴露。
     */
    public static LayoutError of(Code code, String path, int offset, Object... args) {
        if (args.length != code.argc()) {
            throw new IllegalStateException(
                    code + " 要 " + code.argc() + " 个参数，给了 " + args.length + " 个");
        }
        String body = String.format(Locale.ROOT, code.text(), args);
        return new LayoutError(code, path, offset, code.name() + "：" + body + "（偏移 " + offset + "）");
    }
}
