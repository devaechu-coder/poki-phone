package com.november.mcphone.api.sdk.item;

/**
 * 跨 App、跨版本引用一件物品（施工方案 §23.3）。
 *
 * <pre>
 * { id: "minecraft:diamond_sword", count: 1, opaque: "3f2504e0a1b2c3d4e5f60718293a4b5c" }
 * </pre>
 *
 * <h2>opaque 是句柄，不是数据</h2>
 *
 * §23.3 原文把 {@code opaque} 写成"平台专有附加数据编码后的字节，≤512B base64"。<b>那个形状定不下来</b>，
 * 三处实测：
 *
 * <ul>
 *   <li><b>装不下</b>：一把 6 附魔 + 自定义名 + 一行 lore 的剑，1.20.1 的 NBT ≈ 421 字节，
 *       base64 是 564 字符 —— 一件毫不稀奇的物品当场超限。压缩能压到 388，
 *       但同一套压缩会把裸木棍从 48 撑到 76，最常见的那一档反而变大。</li>
 *   <li><b>撑爆参数</b>：{@code mailbox.deposit} 一次可带 27 件（§23.3 的容量），
 *       27 × 400 字符 ≈ 11 KB，而 RPC 的 {@code PARAMS_MAX} 是 4096（§10.3）。</li>
 *   <li><b>挡不住移植</b>：三个字段是分开的，而 {@code opaque} 脚本<b>拿得到</b>。
 *       它一个字节都不用构造，把木棍的 opaque 贴到下界合金剑的 id 上、count 从 1 改成 99 即可 ——
 *       §18.8 的能力白名单只认 id，这条路从它底下穿过去。</li>
 * </ul>
 *
 * 所以 {@code opaque} 是<b>一个 32 位十六进制的句柄</b>，真东西留在宿主侧的表里：
 *
 * <ul>
 *   <li><b>移植不成立</b>：物化一律按句柄查表，{@link #id} 与 {@link #count} 在<b>入站方向完全不采信</b> ——
 *       它们只是给脚本读的投影。改它们不改变任何东西。</li>
 *   <li><b>长度与物品内容无关</b>：裸木棍与 27 格各不相同的附魔装备一样长，
 *       "某件物品恰好表示不了"这一整类故障不存在。</li>
 *   <li><b>跨版本</b>：NBT 还是 DataComponents 从来不出宿主，脚本侧一个版本差异都看不见。</li>
 * </ul>
 *
 * <p><b>句柄不跨重启</b>：要持久的场景（市场挂单）存的应当是那个 App 自己的单号，
 * 由它在服务端侧的存储（§17）保住真实物品，不是把 opaque 存下来。
 *
 * <p>判断物品属性用宿主谓词（{@code ctx.item.matches} / {@code displayName} / {@code isDamaged}），
 * <b>没有 {@code nbt()} 也没有 {@code enchantments()}</b>。
 *
 * @param id     注册表标识。<b>出站给脚本读的投影</b>，入站不采信
 * @param count  1..99。同上，出站投影，入站不采信
 * @param opaque 宿主侧那件物品的句柄，{@link Handles#LENGTH} 位小写十六进制。没有就是空串
 */
public record ItemRef(String id, int count, String opaque) {

    /** {@link #count} 的下界与上界（§23.3）。 */
    public static final int MIN_COUNT = 1;
    public static final int MAX_COUNT = 99;

    /**
     * 一批 {@code ItemRef} 最多几件。
     *
     * <p>取 27 有两个来源，必须同时成立：收件箱一次存满一箱（§23.3），
     * 而 27 × {@link Handles#LENGTH} = 864 字符要塞得进 RPC 的 {@code PARAMS_MAX = 4096}（§10.3）。
     */
    public static final int MAX_BATCH = 27;

    public ItemRef {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("ItemRef.id 不能为空");
        if (count < MIN_COUNT || count > MAX_COUNT) {
            throw new IllegalArgumentException("ItemRef.count 要在 " + MIN_COUNT + ".." + MAX_COUNT + "，收到 " + count);
        }
        if (opaque == null) opaque = "";
        if (!opaque.isEmpty() && !Handles.isHandle(opaque)) {
            throw new IllegalArgumentException("ItemRef.opaque 要是 " + Handles.LENGTH + " 位小写十六进制的句柄，收到 '" + opaque + "'");
        }
    }

    /** 没有宿主侧实体的一栈，只有 id 与数量。 */
    public static ItemRef of(String id, int count) {
        return new ItemRef(id, count, "");
    }

    /** 背后有没有挂着宿主侧的那件真物品。 */
    public boolean hasOpaque() {
        return !opaque.isEmpty();
    }
}
