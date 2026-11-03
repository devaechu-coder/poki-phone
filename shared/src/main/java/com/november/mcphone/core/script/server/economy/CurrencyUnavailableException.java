package com.november.mcphone.core.script.server.economy;

/**
 * 这一次读不到，原因是 {@link #reasonKey()}（翻译键）。
 *
 * <p>给返回 {@code long} 的那几个方法用（{@code balance}）：它们没有位置放 {@code UNAVAILABLE}，
 * 返回 0 就是静默给错数 —— 调用方会以为这个人没钱。返回 {@code TxnResult} 的方法不抛这个，直接给 UNAVAILABLE。
 */
public final class CurrencyUnavailableException extends RuntimeException {

    private final String reasonKey;

    public CurrencyUnavailableException(String reasonKey) {
        super(reasonKey);
        this.reasonKey = reasonKey;
    }

    public String reasonKey() {
        return reasonKey;
    }
}
