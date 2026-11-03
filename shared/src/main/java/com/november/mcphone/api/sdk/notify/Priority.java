package com.november.mcphone.api.sdk.notify;

/**
 * 通知的轻重（施工方案 §23.3）。首页按它加时间统一排序。
 *
 * <p>只增不减：加新档要加在两端或中间都行，但<b>不许改已有三档的含义</b>（§23.4）。
 */
public enum Priority {

    /** 不打扰，只在列表里。 */
    LOW,

    /** 默认。进列表、算进角标。 */
    NORMAL,

    /** <b>只有这一档上锁屏样式</b>。 */
    HIGH
}
