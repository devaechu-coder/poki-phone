package com.november.mcphone.core.script.engine;

/**
 * provider 在不动钱的调用里（{@code balance}）抛了 Error：换成这个再往外抛，cause 是替身（{@code ProviderFailure}）。
 *
 * <p>保持 Error 类型：换成 RuntimeException 的话脚本一个 {@code finally { return }} 就把它吞了、一行日志都不留（理由见 ScriptAbort）。
 * 又不是 ScriptAbort：那个记过失，而这不是脚本的错。
 */
public final class ProviderError extends Error {

    public ProviderError(Throwable standIn) {
        super(standIn.getMessage(), standIn);
    }
}
