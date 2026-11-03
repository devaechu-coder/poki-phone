package com.november.mcphone.api.sdk.resources;

/**
 * B 档占位（施工方案 §23.2、§29）：能源 / 流体 / 气体。空接口的理由见 {@code IGroups}。
 *
 * <p><b>初稿把它归进 C 档"交给适配器、不强求统一"，那是错的</b>：模组服上能源和流体比原版机制
 * 常见得多，而且 {@code IEnergyStorage} 的读数是 {@code int}（§29.4 实测），求和会溢出 ——
 * 不定一层抽象，每个 App 各踩一遍这个坑。
 */
public interface IResources {
}
