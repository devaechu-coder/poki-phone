package com.november.mcphone.api.economy;

import java.util.UUID;

/**
 * 一笔托管的标识（施工方案 §22.4）。
 *
 * <p>包成 record 而不是直接用 {@link UUID}：托管 id、玩家 uuid、订单号在签名里都是 UUID，
 * 传错位置编译器看不出来。
 */
public record EscrowId(UUID value) {

    public EscrowId {
        if (value == null) throw new IllegalArgumentException("托管 id 不能为 null");
    }
}
