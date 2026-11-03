package com.november.mcphone.api.chat;

/** {@link PhoneChat#sendText} 的结果。以后可能加取值，switch 要留 default */
public enum SendResult {

    /** 已存进聊天记录，收件人在线，已推到他的客户端 */
    DELIVERED,

    /** 已存进聊天记录，收件人不在线；他下次上线打开聊天就能看到 */
    STORED_OFFLINE,

    /**
     * 传进来的发件人不是玩家列表里此刻登记的那个实体：已经下线、重生前的旧实体、不在玩家列表里的假玩家。什么都没存。
     * 重生的 Clone 事件里新实体还没登记，也会落到这里，重生相关的发信放到 PlayerRespawnEvent 里做。
     */
    SENDER_OFFLINE,

    /** 发件人和收件人是同一个人 */
    SELF,

    /** 两人不是好友。私信只能发给好友 */
    NOT_FRIENDS,

    /** 发件人身上没有手机（主手、副手、背包、饰品槽都算） */
    NO_PHONE,

    /** 去掉格式符、控制字符和首尾空白之后什么都不剩 */
    EMPTY_TEXT,

    /** 超过 {@link PhoneChat#maxTextLength()} */
    TEXT_TOO_LONG;

    /** 消息存下来了（不论收件人在不在线） */
    public boolean isSent() {
        return this == DELIVERED || this == STORED_OFFLINE;
    }
}
