package com.november.mcphone.feature.sns;

import com.november.mcphone.MCphone;
import com.november.mcphone.feature.sns.net.SnsServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 포키: 이로치를 잡으면 SNS 에 자랑 카드를 자동으로 올린다.
 * 포키폰은 코블몬 없이도 돌아야 해서 전부 리플렉션 (코블몬이 없거나 이름이 바뀌면 이 기능만 꺼짐).
 */
public final class SnsShiny {
    private SnsShiny() {}
    private static boolean installed;

    public static void install() {
        if (installed) return;
        installed = true;
        boolean ok = subscribe("POKEMON_CAPTURED", e -> {
            Object player = get(e, "player"), pokemon = get(e, "pokemon");
            if (!(player instanceof ServerPlayer p) || pokemon == null) return;
            if (!Boolean.TRUE.equals(get(pokemon, "shiny"))) return;
            String card = card(pokemon, p);
            if (card != null) SnsServer.autoPost(p, card);
        });
        MCphone.LOGGER.info("[포키 SNS] 이로치 자랑 {}", ok ? "켜짐" : "꺼짐 (코블몬 이벤트를 못 찾음)");
    }

    /** 카드 글자: key=value 를 | 로 이음. 화면에서 번역해서 보여 줌 */
    public static String card(Object pokemon, ServerPlayer p) {
        Map<String, String> m = new LinkedHashMap<>();
        Object species = get(pokemon, "species");
        Object id = get(species, "resourceIdentifier");
        if (!(id instanceof ResourceLocation rl)) return null;
        m.put("species", rl.toString());
        m.put("shiny", "1");
        Object lv = get(pokemon, "level"); if (lv != null) m.put("lv", lv.toString());
        Object nature = get(get(pokemon, "nature"), "name"); if (nature != null) m.put("nature", nature.toString());
        Object ability = get(get(pokemon, "ability"), "name"); if (ability != null) m.put("ability", ability.toString());
        Object gender = get(pokemon, "gender"); if (gender instanceof Enum<?> en) m.put("gender", en.name());
        int iv = ivTotal(pokemon); if (iv >= 0) m.put("iv", String.valueOf(iv));
        Object ball = get(get(pokemon, "caughtBall"), "name"); if (ball != null) m.put("ball", ball.toString());
        try {
            p.level().getBiome(p.blockPosition()).unwrapKey().ifPresent(k -> m.put("biome", k.location().toString()));
        } catch (RuntimeException ignored) {}
        StringBuilder sb = new StringBuilder();
        m.forEach((k, v) -> { if (sb.length() > 0) sb.append('|'); sb.append(k).append('=').append(v.replace("|", "").replace("=", "")); });
        return sb.length() > 480 ? sb.substring(0, 480) : sb.toString();
    }

    public static Map<String, String> parse(String card) {
        Map<String, String> m = new LinkedHashMap<>();
        if (card == null || card.isEmpty()) return m;
        for (String part : card.split("\\|")) {
            int i = part.indexOf('=');
            if (i > 0) m.put(part.substring(0, i), part.substring(i + 1));
        }
        return m;
    }

    private static int ivTotal(Object pokemon) {
        Object ivs = get(pokemon, "ivs");
        if (!(ivs instanceof Iterable<?> it)) return -1;
        int sum = 0;
        for (Object e : it) if (e instanceof Map.Entry<?, ?> me && me.getValue() instanceof Number n) sum += n.intValue();
        return sum;
    }

    /** 인자 없는 getter (getX / x / isX) */
    static Object get(Object o, String name) {
        if (o == null) return null;
        String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String n : new String[]{"get" + cap, name, "is" + cap}) {
            try { Method m = o.getClass().getMethod(n); return m.invoke(o); }
            catch (NoSuchMethodException ignored) {}
            catch (Throwable t) { return null; }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean subscribe(String field, Consumer<Object> handler) {
        try {
            Class<?> ev = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            Object obs;
            try {
                Field f = ev.getField(field);
                obs = Modifier.isStatic(f.getModifiers()) ? f.get(null) : f.get(ev.getField("INSTANCE").get(null));
            } catch (NoSuchFieldException e) {
                obs = ev.getMethod("get" + field).invoke(ev.getField("INSTANCE").get(null));
            }
            if (obs == null) return false;
            Class<?> fn1 = Class.forName("kotlin.jvm.functions.Function1");
            Object unit = Class.forName("kotlin.Unit").getField("INSTANCE").get(null);
            Object fn = Proxy.newProxyInstance(SnsShiny.class.getClassLoader(), new Class<?>[]{fn1}, (proxy, method, args) -> {
                if (method.getName().equals("invoke") && args != null && args.length == 1) {
                    try { handler.accept(args[0]); } catch (Throwable t) { MCphone.LOGGER.warn("[포키 SNS] 이로치 자랑 실패: {}", t.toString()); }
                    return unit;
                }
                return switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "PokiSnsShiny";
                    default -> null;
                };
            });
            Method sub2 = null, sub1 = null;
            for (Method m : obs.getClass().getMethods()) {
                if (!m.getName().equals("subscribe")) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 2 && p[0].isEnum() && p[1].isAssignableFrom(fn1)) sub2 = m;
                if (p.length == 1 && p[0].isAssignableFrom(fn1)) sub1 = m;
            }
            if (sub2 != null) sub2.invoke(obs, Enum.valueOf((Class) sub2.getParameterTypes()[0], "NORMAL"), fn);
            else if (sub1 != null) sub1.invoke(obs, fn);
            else return false;
            return true;
        } catch (Throwable t) {
            MCphone.LOGGER.warn("[포키 SNS] 코블몬 이벤트 {} 연결 실패: {}", field, t.toString());
            return false;
        }
    }
}
