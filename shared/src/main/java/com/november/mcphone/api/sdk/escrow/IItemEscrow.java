package com.november.mcphone.api.sdk.escrow;

/**
 * B 档占位（施工方案 §23.2、§23.5）：物品托管，市场用。空接口的理由见 {@code IGroups}。
 *
 * <p>将来装的东西：与 {@code ICurrencyProvider} 的 hold / release / refund 同构 ——
 * 受益人在创建时定死，不可更改。
 */
public interface IItemEscrow {
}
