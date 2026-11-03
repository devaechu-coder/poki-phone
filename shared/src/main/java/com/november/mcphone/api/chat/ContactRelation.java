package com.november.mcphone.api.chat;

/** 一个玩家看另一个玩家。以后可能加取值，switch 要留 default */
public enum ContactRelation {

    /** 两个 UUID 是同一个人 */
    SELF,

    /** 互为好友，可以发私信 */
    FRIEND,

    /** 自己发了好友申请，对方还没答复 */
    REQUEST_SENT,

    /** 对方发来了好友申请，自己还没答复 */
    REQUEST_RECEIVED,

    NONE
}
