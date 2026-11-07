package com.november.mcphone.feature.support.net;

import com.november.mcphone.MCphone;
import com.november.mcphone.feature.chat.FriendGuard;
import com.november.mcphone.feature.chat.PokiNames;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;

/** 고객센터 서버 쪽: PokiHelp 플러그인(kr.devaechu.pokihelp.PokiHelpApi)을 이름으로 찾아 부름 */
public final class SupportServer {
    private SupportServer() {}
    private static BiConsumer<ServerPlayer, SupportWire.Reply> reply;
    private static final Map<ServerPlayer, Long> LAST = new WeakHashMap<>();
    private static final Map<String, Method> M = new HashMap<>();
    private static boolean looked, found;

    public static void install(BiConsumer<ServerPlayer, SupportWire.Reply> sender) { reply = sender; }

    private static boolean api() {
        if (looked) return found;
        looked = true;
        try {
            Class<?> c = Class.forName("kr.devaechu.pokihelp.PokiHelpApi", true, findLoader());
            for (Method m : c.getMethods()) M.put(m.getName(), m);
            found = true;
        } catch (Throwable t) {
            MCphone.LOGGER.info("[포키폰] PokiHelp 가 없어서 고객센터 앱 꺼짐: {}", t.toString());
        }
        return found;
    }

    /** 플러그인 클래스는 버킷 플러그인 로더에 있음 → Bukkit 으로 플러그인을 찾아 그 로더를 씀 */
    private static ClassLoader findLoader() {
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            Object pm = bukkit.getMethod("getPluginManager").invoke(null);
            Object pl = pm.getClass().getMethod("getPlugin", String.class).invoke(pm, "PokiHelp");
            if (pl != null) return pl.getClass().getClassLoader();
        } catch (Throwable ignored) {
        }
        return SupportServer.class.getClassLoader();
    }

    @SuppressWarnings("unchecked")
    private static <T> T call(String name, Object... args) throws Exception {
        Method m = M.get(name);
        if (m == null) throw new NoSuchMethodException(name);
        return (T) m.invoke(null, args);
    }

    public static void handle(SupportWire.Query q, ServerPlayer player) {
        SupportWire.Reply out;
        long now = System.currentTimeMillis();
        if (!FriendGuard.carriesPhone(player)) out = SupportWire.Reply.of(q, SupportWire.Status.FORBIDDEN, "");
        else if (now - LAST.getOrDefault(player, 0L) < 200) out = SupportWire.Reply.of(q, SupportWire.Status.BUSY, "");
        else if (!api()) out = SupportWire.Reply.of(q, SupportWire.Status.NO_PLUGIN, "");
        else {
            LAST.put(player, now);
            try { out = run(q, player); }
            catch (Throwable t) {
                MCphone.LOGGER.warn("[포키폰] 고객센터 처리 실패: {}", t.toString());
                out = SupportWire.Reply.of(q, SupportWire.Status.ERROR, "");
            }
        }
        reply.accept(player, out);
    }

    private static SupportWire.Reply run(SupportWire.Query q, ServerPlayer player) throws Exception {
        UUID me = player.getUUID();
        SupportWire.Status st = SupportWire.Status.OK;
        String msg = "";
        List<SupportWire.Ticket> tickets = new ArrayList<>();
        List<SupportWire.PlayerRow> players = new ArrayList<>();
        switch (q.kind()) {
            case SUBMIT -> {
                String r = call("submit", me, q.a(), q.b(), q.c(), q.d());
                if (r != null && r.startsWith("OK:")) msg = r.substring(3);
                else { st = SupportWire.Status.ERROR; msg = r == null ? "" : r.replaceFirst("^ERR:", ""); }
            }
            case MINE -> tickets = parse(call("mine", me));
            case SEEN -> call("markSeen", me);
            case BOX -> tickets = parse(call("box", me, q.flag()));
            case ACT -> {
                String err = call("action", me, q.a(), q.id(), q.b());
                if (err != null) { st = SupportWire.Status.ERROR; msg = err; }
                else tickets = parse(call("box", me, false));
            }
            case PLAYERS -> {
                for (ServerPlayer p : player.server.getPlayerList().getPlayers()) {
                    if (p == player) continue;
                    boolean near = p.level() == player.level() && p.distanceToSqr(player) < 48 * 48;
                    players.add(new SupportWire.PlayerRow(p.getUUID(), p.getGameProfile().getName(),
                            PokiNames.display(p.getUUID(), p.getGameProfile().getName()), near));
                }
                players.sort(Comparator.comparing((SupportWire.PlayerRow r) -> !r.near()).thenComparing(SupportWire.PlayerRow::display));
                if (players.size() > 200) players = players.subList(0, 200);
            }
            case STATE -> { }
        }
        boolean staff = Boolean.TRUE.equals(call("isStaff", me));
        int unseen = ((Number) call("unseen", me)).intValue();
        int open = ((Number) call("openCount", me)).intValue();
        return new SupportWire.Reply(q.request(), q.kind(), st, msg, staff, unseen, open, tickets, players);
    }

    private static List<SupportWire.Ticket> parse(Object o) {
        List<SupportWire.Ticket> out = new ArrayList<>();
        if (!(o instanceof List<?> lines)) return out;
        for (Object x : lines) {
            String[] f = String.valueOf(x).split("\t", -1);
            if (f.length < 13) continue;
            out.add(new SupportWire.Ticket(i(f[0]), f[1], f[2], f[3], f[4], f[5], l(f[6]), f[7], f[8], l(f[9]), f[10], f[11], f[12]));
            if (out.size() >= 60) break;
        }
        return out;
    }

    private static int i(String s) { try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; } }
    private static long l(String s) { try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return 0; } }
}
