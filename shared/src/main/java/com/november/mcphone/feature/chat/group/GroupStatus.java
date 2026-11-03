package com.november.mcphone.feature.chat.group;

/** 服务端确认的操作结果；客户端不能把尚未确认的操作显示成成功。 */
public enum GroupStatus {
    OK(0), INVALID(1), NOT_MEMBER(2), OWNER_REQUIRED(3), OWNER_MUST_DISSOLVE(4), NOT_FRIEND(5),
    LIMIT(6), NO_PHONE(7), BUSY(8), UNAVAILABLE(9), INVITATION_GONE(10), NOT_INVITED(11);
    private final int wireId;
    GroupStatus(int wireId) { this.wireId = wireId; }
    /** 保留已有字节编号；调整枚举声明顺序不能改变网络含义。 */
    public int wireId() { return wireId; }
    public static GroupStatus fromWire(int id) {
        for (var status : values()) if (status.wireId == id) return status;
        throw new io.netty.handler.codec.DecoderException("非法群状态 " + id);
    }
    public String key() { return "mcphone.chat.group.result." + name().toLowerCase(java.util.Locale.ROOT); }
}
