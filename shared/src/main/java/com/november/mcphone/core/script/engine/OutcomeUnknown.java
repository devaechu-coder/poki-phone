package com.november.mcphone.core.script.engine;

/**
 * 脚本调的货币操作结果不明：provider 在动钱时抛了，或者没给结果（施工方案 §22）。钱可能已经动了一半。
 *
 * <p>和 {@link ScriptAbort} 一样 extends Error：RuntimeException 会被脚本的 {@code finally { return }} 吞掉
 * （理由与实测见 ScriptAbort），App 就会把"结果不明"当成"没动"再付一次。
 * 又<b>不是</b> ScriptAbort：那个记过失，而这不是脚本的错（S15h）。RhinoEvaluator 回 UNKNOWN。
 */
public final class OutcomeUnknown extends Error {

    public OutcomeUnknown(String detail, Throwable cause) {
        super(detail, cause);
    }
}
