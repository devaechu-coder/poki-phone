package com.november.mcphone.api.chat;

import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * 一对好友之间的私信会话在查询那一刻的样子，站在 {@link #self()} 的角度。
 */
public final class PhoneConversation {

    private final UUID self;
    private final PhoneContact peer;
    private final int unread;
    private final OptionalLong lastMessageTime;

    PhoneConversation(UUID self, PhoneContact peer, int unread, OptionalLong lastMessageTime) {
        this.self = self;
        this.peer = peer;
        this.unread = unread;
        this.lastMessageTime = lastMessageTime;
    }

    public UUID self() {
        return self;
    }

    public PhoneContact peer() {
        return peer;
    }

    /** peer 发来、self 还没看过的条数 */
    public int unread() {
        return unread;
    }

    /** 最后一条消息的服务端时间（currentTimeMillis），还没聊过是空 */
    public OptionalLong lastMessageTime() {
        return lastMessageTime;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PhoneConversation c && self.equals(c.self) && peer.equals(c.peer)
                && unread == c.unread && lastMessageTime.equals(c.lastMessageTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(self, peer, unread, lastMessageTime);
    }

    @Override
    public String toString() {
        return "PhoneConversation[" + self + " ↔ " + peer + " 未读 " + unread + "]";
    }
}
