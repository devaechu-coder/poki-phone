package com.november.mcphone.core.script.engine;

/**
 * 宿主要中断本次求值时抛的唯一信号（施工方案 §16.4、§16.6）。
 *
 * <h2>为什么必须 extends Error，不能是 RuntimeException</h2>
 *
 * <b>实测</b>（Rhino 1.9.1，脚本 {@code function g(){ try { boom(); } finally { return 'FINALLY_WINS'; } } g()}）：
 *
 * <pre>
 * 宿主抛 Error            → Error: HOST_ABORT     （脚本吞不掉）
 * 宿主抛 RuntimeException → FINALLY_WINS          ← 中断被 finally 里的 return 吞了
 * </pre>
 *
 * Rhino 的解释器对 {@code Error} 给 {@code EX_NO_JS_STATE}（catch 与 finally 都轮不到），
 * 对 {@code RuntimeException} 给 {@code EX_FINALLY_STATE} —— finally 会跑，跑到 {@code return}
 * 就把待抛的异常丢了。
 *
 * <p>§16.4 那句"宿主 Error 没被脚本吞掉"只在字面上成立。而"三件必须做的 ①"里的边界尺寸检查，
 * 最自然的写法正好是 {@code IllegalStateException} —— 写成那样，脚本一个 {@code finally { return }}
 * 就把尺寸闸绕过去了。
 *
 * <p><b>纪律</b>：宿主桥里任何"立即中断本次调用"一律抛这个；
 * "业务失败"走 {@code ctx.fail(...)} 的返回值，不走异常。
 *
 * <p><b>还有一条</b>：宿主函数不许用 {@code FunctionObject} 定义 —— 它把 RuntimeException
 * 转成脚本 catch 得到的 {@code InternalError}。一律 {@code LambdaFunction}。
 */
public final class ScriptAbort extends Error {

    /** 为什么中断。进审计，不给客户端。 */
    public enum Reason {
        /** 指令预算用完。 */
        INSTRUCTIONS,
        /** 墙钟用完。 */
        WALL_CLOCK,
        /** 边界尺寸超限（字符串 > 64 KiB 或数组 > 4096）。 */
        SIZE,
        /** 宿主桥重入太深。 */
        STACK,
        /** 跨调用驻留超限。 */
        RETAINED,
        /** 别的宿主侧拒绝（参数类型不对、require 越界…）。 */
        HOST
    }

    private final Reason reason;

    /** 不填栈：中断每秒可能发生很多次，而栈对定位没用（位置在 detail 里）。 */
    public ScriptAbort(Reason reason, String detail) {
        super(reason + ": " + detail, null, false, false);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
