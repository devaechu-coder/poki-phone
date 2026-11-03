package com.november.mcphone.api.sdk.mailbox;

/**
 * 往收件箱存东西的结果（施工方案 §23.3）。码只增不减，也不许换意思（§23.4）。
 */
public enum DepositResult {

    /** 全部存进去了。 */
    OK,

    /** 放不下。<b>一件都没存</b>，不是存了一半 —— 见 {@link IMailbox#deposit}。 */
    FULL,

    /** 参数不对：玩家为 null、物品表为空或超过 {@link IMailbox#MAX_BATCH}。 */
    INVALID,

    /** 没有这个能力。 */
    NOT_AUTHORIZED,

    /** 收件箱现在用不了（存储没起来）。 */
    UNAVAILABLE,

    /** 实现内部出错，没存成。 */
    FAILED
}
