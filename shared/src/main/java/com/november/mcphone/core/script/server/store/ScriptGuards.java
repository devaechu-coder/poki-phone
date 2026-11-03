package com.november.mcphone.core.script.server.store;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * 守卫数据：{@code once} 与领取次数（施工方案 §17.3、§19.2、§20.2）。
 *
 * <h2>为什么单独一块，而不是塞进 ScriptKv</h2>
 *
 * <b>脚本写不到这里。</b>放一起的话，脚本把"只能领一次"那个键删掉就能重领 ——
 * §20.2 的铁规就是为这件事立的。宿主侧的守卫逻辑（S19）写它，{@code ctx.store} / {@code ctx.shared}
 * 一个口子都不给。
 *
 * <p><b>1.20.1 的 {@code copyDeathPersistentFrom} 必须带上它</b>：不带的话死一次就能重领，
 * 而死亡在 Minecraft 里是随时可以自己安排的事。
 */
public record ScriptGuards(Map<String, Long> counters) {

    public static final ScriptGuards DEFAULT = new ScriptGuards(Map.of());

    public static final Codec<ScriptGuards> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.unboundedMap(Codec.STRING, Codec.LONG)
                            .fieldOf("counters").forGetter(ScriptGuards::counters)
            ).apply(instance, ScriptGuards::new)
    );

    public ScriptGuards {
        counters = Map.copyOf(counters);
    }

    /** 守卫键：命名空间 + 守卫名 + 周期标签（§20.1 的 label 就是 {@code ctx.cycle.label} 给的那个）。 */
    public static String guardKey(String namespace, String guard, String cycleLabel) {
        return namespace + "|" + guard + "|" + cycleLabel;
    }

    public long get(String guardKey) {
        return counters.getOrDefault(guardKey, 0L);
    }

    /** 加一。只由宿主侧的守卫逻辑调。 */
    public ScriptGuards increment(String guardKey) {
        Map<String, Long> next = new HashMap<>(counters);
        next.merge(guardKey, 1L, Long::sum);
        return new ScriptGuards(next);
    }

    /** 清掉过期周期的计数。周期标签变了之后旧的就没用了。 */
    public ScriptGuards pruneOtherCycles(String namespace, String guard, String keepLabel) {
        String prefix = namespace + "|" + guard + "|";
        String keep = guardKey(namespace, guard, keepLabel);
        Map<String, Long> next = new HashMap<>(counters);
        next.keySet().removeIf(k -> k.startsWith(prefix) && !k.equals(keep));
        return new ScriptGuards(next);
    }
}
