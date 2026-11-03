package com.november.mcphone.core.script.sfc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 表达式的 AST（施工方案 §9.5）。由 {@link ExprParser} 在编译期建好并缓存，重排时只遍历、不重新解析。
 *
 * <p>求值不向外抛：类型不对、嵌套过深时整条表达式得 null 并记一条 warn。
 */
sealed interface Expr {

    Object eval(EvalContext c);

    /** 一条编译好的表达式：根节点与它在原文件里的行。 */
    record Compiled(Expr root, int line, String source) {

        Object run(EvalContext c) {
            if (!c.budget()) return null;
            c.line = line;
            c.depth = 0;
            try {
                return root.eval(c);
            } catch (Abort a) {
                c.warn(a.getMessage());
                return null;
            }
        }
    }

    /** 整条表达式放弃求值。不带栈：它是控制流，每帧都可能抛。 */
    final class Abort extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Abort(String reason) {
            super(reason, null, false, false);
        }

        static Abort type(String op, Object a, Object b) {
            String body = SfcError.Code.E_EXPR_TYPE.text().substring("%s:%d ".length());
            return new Abort(String.format(java.util.Locale.ROOT, body, op, Values.kind(a), b == NONE ? "（一元）" : Values.kind(b)));
        }
    }

    /** 一元运算报类型错时占右边那一格。 */
    Object NONE = new Object();

    static void enter(EvalContext c) {
        if (++c.depth > EvalContext.MAX_DEPTH) throw new Abort("表达式求值超过 " + EvalContext.MAX_DEPTH + " 层");
    }

    record Lit(Object value) implements Expr {
        public Object eval(EvalContext c) {
            return value;
        }
    }

    record Ref(String name) implements Expr {
        public Object eval(EvalContext c) {
            return c.lookup(name);
        }
    }

    /** a.b：a 是对象且有这个键才有值；.length 只对数组与字符串；其余都是 null，不报错（§9.5.5）。 */
    record Member(Expr target, String name) implements Expr {
        public Object eval(EvalContext c) {
            enter(c);
            Object v = target.eval(c);
            c.depth--;
            if (v instanceof Map<?, ?> m) return m.get(name);
            if (name.equals("length")) {
                if (v instanceof List<?> l) return l.size();
                if (v instanceof String s) return s.length();
            }
            return null;
        }
    }

    record Index(Expr target, Expr index) implements Expr {
        public Object eval(EvalContext c) {
            enter(c);
            Object v = target.eval(c);
            Object i = index.eval(c);
            c.depth--;
            if (v instanceof List<?> l && i instanceof Integer n && n >= 0 && n < l.size()) return l.get(n);
            return null;
        }
    }

    record Unary(char op, Expr operand) implements Expr {
        public Object eval(EvalContext c) {
            enter(c);
            Object v = operand.eval(c);
            c.depth--;
            if (op == '!') return !Values.truthy(v);
            if (v instanceof Integer n) return -n;
            throw Abort.type("-", v, NONE);
        }
    }

    record Binary(String op, Expr left, Expr right) implements Expr {
        public Object eval(EvalContext c) {
            enter(c);
            Object a = left.eval(c);
            Object b = right.eval(c);
            c.depth--;
            switch (op) {
                case "==":
                    return Values.same(a, b);
                case "!=":
                    return !Values.same(a, b);
                case "+":
                    if (a instanceof Integer x && b instanceof Integer y) return x + y;
                    if (a instanceof String || b instanceof String) {
                        Object other = a instanceof String ? b : a;
                        if (other instanceof List<?> || other instanceof Map<?, ?>) {
                            c.warn(Values.kind(other) + " 拼进字符串显示为空");
                        }
                        return c.capped(Values.text(a) + Values.text(b));
                    }
                    throw Abort.type(op, a, b);
                default:
                    break;
            }
            if (a instanceof Integer x && b instanceof Integer y) {
                switch (op) {
                    case "-":
                        return x - y;
                    case "*":
                        return x * y;
                    case "/":
                    case "%":
                        if (y == 0) {
                            c.warn("除以 0，结果按 0 算");
                            return 0;
                        }
                        return op.equals("/") ? x / y : x % y;
                    case "<":
                        return x < y;
                    case "<=":
                        return x <= y;
                    case ">":
                        return x > y;
                    default:
                        return x >= y;
                }
            }
            if (a instanceof String x && b instanceof String y && isRelational(op)) {
                int cmp = x.compareTo(y);
                return switch (op) {
                    case "<" -> cmp < 0;
                    case "<=" -> cmp <= 0;
                    case ">" -> cmp > 0;
                    default -> cmp >= 0;
                };
            }
            throw Abort.type(op, a, b);
        }

        static boolean isRelational(String op) {
            return op.equals("<") || op.equals("<=") || op.equals(">") || op.equals(">=");
        }
    }

    /** && 与 || 短路，返回 bool 而不是操作数本身（§9.5.3）。 */
    record Logic(boolean and, Expr left, Expr right) implements Expr {
        public Object eval(EvalContext c) {
            enter(c);
            boolean l = Values.truthy(left.eval(c));
            boolean out = and ? l && Values.truthy(right.eval(c)) : l || Values.truthy(right.eval(c));
            c.depth--;
            return out;
        }
    }

    record Cond(Expr test, Expr then, Expr otherwise) implements Expr {
        public Object eval(EvalContext c) {
            enter(c);
            Object v = Values.truthy(test.eval(c)) ? then.eval(c) : otherwise.eval(c);
            c.depth--;
            return v;
        }
    }

    record ArrayLit(List<Expr> items) implements Expr {
        public Object eval(EvalContext c) {
            enter(c);
            List<Object> out = new ArrayList<>(items.size());
            for (Expr e : items) out.add(e.eval(c));
            c.depth--;
            return Collections.unmodifiableList(out);
        }
    }

    record ObjectLit(List<String> keys, List<Expr> values) implements Expr {
        public Object eval(EvalContext c) {
            enter(c);
            Map<String, Object> out = new LinkedHashMap<>();
            for (int i = 0; i < keys.size(); i++) out.put(keys.get(i), values.get(i).eval(c));
            c.depth--;
            return Collections.unmodifiableMap(out);
        }
    }

    /**
     * 插值：各段文本化后拼起来。不走 +：{{ a }}{{ b }} 两个 int 该拼成 "12"，不是加成 3。
     * 第二个起每个 {{ }} 各记一次求值（第一个已由 Compiled.run 记过）：一段文字里塞几万个插值时，整段只算一次会绕过 4096 的上限。
     */
    record Concat(List<Expr> parts) implements Expr {
        public Object eval(EvalContext c) {
            enter(c);
            StringBuilder sb = new StringBuilder();
            boolean charged = false;
            for (Expr p : parts) {
                Object v;
                if (p instanceof Lit) {
                    v = p.eval(c);
                } else if (!charged) {
                    charged = true;
                    v = p.eval(c);
                } else {
                    v = c.budget() ? p.eval(c) : null;
                }
                if (v instanceof List<?> || v instanceof Map<?, ?>) c.warn(Values.kind(v) + " 放进文字里显示为空");
                sb.append(Values.text(v));
                if (sb.length() > EvalContext.MAX_CONCAT) break;
            }
            c.depth--;
            return c.capped(sb.toString());
        }
    }
}
