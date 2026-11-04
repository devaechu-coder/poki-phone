package com.november.mcphone.feature.chat;

import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 포키: 포키톡 포켓몬 카드.
 *
 * 폰은 "[[포켓몬요청:슬롯]]" 글자를 보내고, 서버가 그 사람 파티를 직접 읽어서 "[[포켓몬:...]]" 카드 글자로 바꿔 넣는다.
 * 그래서 카드 내용은 손으로 꾸밀 수 없다 (사용자가 "[[포켓몬:" 으로 시작하는 글을 직접 치면 막힘).
 * 정보만 보내는 거라 포켓몬이 넘어가거나 복사되지 않는다.
 */
public final class PokemonCard {
    private PokemonCard() {}
    public static final String HEAD = "[[포켓몬:", REQ = "[[포켓몬요청:", TAIL = "]]";
    private static final String[] STAT_ORDER = {"hp", "atk", "def", "spa", "spd", "spe"};

    public static boolean isCard(String text) { return text != null && text.startsWith(HEAD) && text.endsWith(TAIL); }

    public static String request(int slot) { return REQ + slot + TAIL; }

    /**
     * 서버: 보낼 글자 고치기. 카드 요청이면 카드로, 손으로 친 가짜 카드면 null(보내지 않음), 나머지는 그대로.
     */
    public static String serverRewrite(ServerPlayer player, String text) {
        if (text == null) return null;
        if (text.startsWith(HEAD)) return null;
        if (!text.startsWith(REQ) || !text.endsWith(TAIL)) return text;
        int slot;
        try { slot = Integer.parseInt(text.substring(REQ.length(), text.length() - TAIL.length()).trim()); }
        catch (NumberFormatException e) { return null; }
        if (slot < 0 || slot > 5) return null;
        Object pokemon = partySlot(player, slot);
        if (pokemon == null) return null;
        return build(pokemon);
    }

    private static Object partySlot(ServerPlayer p, int slot) {
        try {
            Class<?> c = Class.forName("com.cobblemon.mod.common.Cobblemon");
            Object storage = c.getMethod("getStorage").invoke(c.getField("INSTANCE").get(null));
            Object party = null;
            for (Method m : storage.getClass().getMethods()) {
                if (!m.getName().equals("getParty")) continue;
                Class<?>[] t = m.getParameterTypes();
                if (t.length == 1 && t[0].isAssignableFrom(p.getClass())) { party = m.invoke(storage, p); break; }
                if (t.length == 2 && t[0] == java.util.UUID.class) { party = m.invoke(storage, p.getUUID(), p.level().getServer().registryAccess()); break; }
            }
            if (party == null) return null;
            Method get = party.getClass().getMethod("get", int.class);
            return get.invoke(party, slot);
        } catch (Throwable t) {
            com.november.mcphone.MCphone.LOGGER.warn("[포키폰] 포켓몬 카드: 파티를 못 읽음 {}", t.toString());
            return null;
        }
    }

    private static String clean(Object o) {
        if (o == null) return "";
        String s = o.toString();
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) if (c != ';' && c != '=' && c != ']' && c != '[' && c != '§' && c >= ' ') sb.append(c);
        return sb.toString();
    }

    /** "cobblemon:adamant" → "adamant" (다른 모드 이름공간은 그대로) */
    private static String path(Object o) {
        String s = clean(o);
        return s.startsWith("cobblemon:") ? s.substring("cobblemon:".length()) : s;
    }

    static String build(Object pk) {
        Map<String, String> m = new LinkedHashMap<>();
        Object id = get(get(pk, "species"), "resourceIdentifier");
        if (id == null) return null;
        m.put("sp", clean(id));
        if (Boolean.TRUE.equals(get(pk, "shiny"))) m.put("sh", "1");
        Object lv = get(pk, "level"); if (lv != null) m.put("lv", clean(lv));
        Object gender = get(pk, "gender"); if (gender instanceof Enum<?> e) m.put("g", e.name().substring(0, 1));
        Object nature = get(get(pk, "nature"), "name"); if (nature != null) m.put("na", path(nature));
        Object ability = get(get(pk, "ability"), "name"); if (ability != null) m.put("ab", path(ability));
        // 개체값 6개 (HP 공격 방어 특공 특방 스피드)
        Object ivs = get(pk, "ivs");
        if (ivs instanceof Iterable<?> it) {
            Map<String, Integer> v = new LinkedHashMap<>();
            for (Object e : it) if (e instanceof Map.Entry<?, ?> me && me.getValue() instanceof Number n) {
                Object sid = get(me.getKey(), "showdownId");
                v.put(sid == null ? clean(me.getKey()).toLowerCase(java.util.Locale.ROOT) : clean(sid), n.intValue());
            }
            List<String> out = new ArrayList<>();
            for (String k : STAT_ORDER) out.add(String.valueOf(v.getOrDefault(k, 0)));
            m.put("iv", String.join(".", out));
        }
        // 기술 4개
        Object moveSet = get(pk, "moveSet");
        Object moves = get(moveSet, "moves");
        if (moves instanceof Iterable<?> it) {
            List<String> names = new ArrayList<>();
            for (Object mv : it) { Object n = get(mv, "name"); if (n != null && names.size() < 4) names.add(path(n)); }
            if (!names.isEmpty()) m.put("mv", String.join(",", names));
        }
        Object ot = get(pk, "originalTrainerName"); if (ot != null) m.put("ot", clean(ot));
        StringBuilder sb = new StringBuilder(HEAD);
        boolean first = true;
        for (var e : m.entrySet()) {
            String part = e.getKey() + "=" + e.getValue();
            if (sb.length() + part.length() + 3 > com.november.mcphone.feature.chat.TextBody.MAX_LENGTH) break;
            if (!first) sb.append(';');
            sb.append(part); first = false;
        }
        return sb.append(TAIL).toString();
    }

    /** 카드 글자 → key=value */
    public static Map<String, String> parse(String text) {
        Map<String, String> m = new LinkedHashMap<>();
        if (!isCard(text)) return m;
        for (String part : text.substring(HEAD.length(), text.length() - TAIL.length()).split(";")) {
            int i = part.indexOf('=');
            if (i > 0) m.put(part.substring(0, i), part.substring(i + 1));
        }
        return m;
    }

    private static Object get(Object o, String name) {
        if (o == null) return null;
        String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String n : new String[]{"get" + cap, name, "is" + cap}) {
            try { return o.getClass().getMethod(n).invoke(o); }
            catch (NoSuchMethodException ignored) {}
            catch (Throwable t) { return null; }
        }
        return null;
    }
}
