package com.november.mcphone.feature.chat.group;

/** 可预期的业务拒绝，保留类型，不靠异常文本判断权限。 */
public final class GroupFault extends RuntimeException {
    private final GroupStatus status;
    public GroupFault(GroupStatus status) { super(status.name()); this.status = status; }
    public GroupStatus status() { return status; }
}
