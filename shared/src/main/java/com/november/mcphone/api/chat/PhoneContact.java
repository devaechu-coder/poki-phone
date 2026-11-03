package com.november.mcphone.api.chat;

import java.util.Objects;
import java.util.UUID;

/**
 * 一个玩家在查询那一刻的样子。不是记录类：记录的构造函数是公开的，加一个字段就是破坏性改动。
 */
public final class PhoneContact {

    private final UUID id;
    private final String name;
    private final boolean online;

    PhoneContact(UUID id, String name, boolean online) {
        this.id = id;
        this.name = name;
        this.online = online;
    }

    public UUID id() {
        return id;
    }

    /** 在线时是真名；离线时是最后一次见到的名字，实在查不到是 UUID 前 8 位。离线模式与 Geyser 的名字可能超过 16 个字符 */
    public String name() {
        return name;
    }

    public boolean online() {
        return online;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PhoneContact c && id.equals(c.id) && name.equals(c.name) && online == c.online;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, online);
    }

    @Override
    public String toString() {
        return "PhoneContact[" + name + " " + id + (online ? " 在线" : " 离线") + "]";
    }
}
