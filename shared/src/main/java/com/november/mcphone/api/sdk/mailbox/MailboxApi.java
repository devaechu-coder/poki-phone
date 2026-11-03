package com.november.mcphone.api.sdk.mailbox;

import com.november.mcphone.api.sdk.SdkVersions;

/**
 * 收件箱 SDK 的版本号（施工方案 §23.4）。
 *
 * <p>只增不减：加方法给 {@code default}、加字段留旧构造器、加错误码都行；
 * 改签名、删字段、改语义、复用错误码都不行。每破一次，已经发布的 App 就坏一批。
 *
 * <p>号存在 {@link SdkVersions} 那一张表里，改在那儿改。这里为什么是静态块、
 * 为什么不能是接口字段，也都写在那儿。
 */
public final class MailboxApi {

    private MailboxApi() {
    }

    /** manifest {@code sdk} 段里写的键。 */
    public static final String KEY = "mailbox";

    /** 别改成声明式赋值。 */
    public static final int VERSION;

    static {
        VERSION = SdkVersions.of(KEY);
    }
}
