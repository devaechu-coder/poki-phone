package com.november.mcphone.api.sdk.groups;

/**
 * B 档占位（施工方案 §23.2、§23.5）："这个玩家是不是 VIP"。
 *
 * <p><b>接口现在是空的，这是有意的</b>：P2 才填实现，但版本常量与 manifest 的 {@code sdk}
 * 段现在就要存在 —— 等实现写完再加，那时已经有 App 发布了，而 {@code sdk} 段的格式一旦
 * 有人在用就改不动（§23.4）。占位的代价是一个空文件，不占位的代价是永久分裂。
 *
 * <p>将来装的东西：包到谓词（§18.3）+ 队伍 + 权限模组的组判定。
 */
public interface IGroups {
}
