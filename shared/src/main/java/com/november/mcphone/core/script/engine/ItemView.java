package com.november.mcphone.core.script.engine;

/**
 * {@code ctx.item.*} 的后端（施工方案 §23.3、§32.7）。<b>引擎不碰 Minecraft，所以只认句柄字符串。</b>
 *
 * <p>三个方法就是 §23.3 允许的全部：<b>没有 {@code nbt()} 也没有 {@code enchantments()}</b>。
 * 句柄本身脚本既解析不了也构造不了 —— 它是 32 位十六进制的索引，真东西在宿主侧的表里
 * （见 {@code api/sdk/item/ItemRef}）。
 *
 * <p>真正的实现要 ItemStack，那是平台侧的事（S17/S18）。本步只定形状，
 * 没注入实现时 {@code ctx.item} <b>整个不挂</b> —— 不挂一个返回 UNAVAILABLE 的空壳。
 */
public interface ItemView {

    /** 这件物品符不符合一个标签判定（§18.5）。 */
    boolean matches(String handle, String predicate);

    /** 宿主已经本地化好的显示名。 */
    String displayName(String handle);

    /** 有没有损伤。<b>不给具体耐久</b>。 */
    boolean isDamaged(String handle);
}
