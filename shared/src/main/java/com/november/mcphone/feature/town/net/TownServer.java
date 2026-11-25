package com.november.mcphone.feature.town.net;

import com.november.mcphone.MCphone;
import com.november.mcphone.feature.chat.FriendGuard;
import com.november.mcphone.feature.chat.PokiNames;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;

/**
 * 마을 앱 서버 쪽: PokiTown 플러그인(kr.devaechu.pokitown.PokiTownApi)을 이름으로 찾아 부름 (고객센터 앱과 같은 방식).
 * 행동은 PokiTown 이 그 플레이어 본인 권한으로 Towny 명령어를 실행 — OP 권한으로 실행하지 않음.
 */
public final class TownServer {
    private TownServer() {}
    private static BiConsumer<ServerPlayer, TownWire.Reply> reply;
    private static BiConsumer<ServerPlayer, TownWire.Notice> notice;
    private static final Map<ServerPlayer, Long> LAST = new WeakHashMap<>();
    private static final Map<String, Method> M = new HashMap<>();
    private static boolean looked, found, hooked;
    private static MinecraftServer server;

    public static void install(BiConsumer<ServerPlayer, TownWire.Reply> r, BiConsumer<ServerPlayer, TownWire.Notice> n) {
        reply = r; notice = n;
    }

    private static boolean api() {
        if (looked) return found;
        looked = true;
        try {
            Class<?> c = Class.forName("kr.devaechu.pokitown.PokiTownApi", true, findLoader());
            for (Method m : c.getMethods()) M.put(m.getName() + "/" + m.getParameterCount(), m);
            found = true;
        } catch (Throwable t) {
            MCphone.LOGGER.info("[포키폰] PokiTown 이 없어서 마을 앱 꺼짐: {}", t.toString());
        }
        return found;
    }

    private static ClassLoader findLoader() {
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            Object pm = bukkit.getMethod("getPluginManager").invoke(null);
            Object pl = pm.getClass().getMethod("getPlugin", String.class).invoke(pm, "PokiTown");
            if (pl != null) return pl.getClass().getClassLoader();
        } catch (Throwable ignored) {
        }
        return TownServer.class.getClassLoader();
    }

    @SuppressWarnings("unchecked")
    private static <T> T call(String name, Object... args) throws Exception {
        Method m = M.get(name + "/" + args.length);
        if (m == null) throw new NoSuchMethodException(name);
        return (T) m.invoke(null, args);
    }

    /**
     * 누가 처음 접속했을 때 한 번: PokiTown 알림(초대 받음·주민 들어옴 등)을 포키폰 팝업으로 받게 연결.
     * (모드가 켜질 땐 아직 버킷 플러그인이 안 켜져 있어서 접속 때 연결)
     */
    public static void hook(MinecraftServer s) {
        server = s;
        if (hooked) return;
        hooked = true;
        if (!api()) return;
        try {
            BiConsumer<UUID, String> l = TownServer::notify;
            call("onNotify", l);
            MCphone.LOGGER.info("[포키폰] PokiTown 연결: 마을 알림 팝업 켜짐");
        } catch (Throwable t) {
            MCphone.LOGGER.warn("[포키폰] PokiTown 알림 연결 실패: {}", t.toString());
        }
    }

    private static void notify(UUID to, String text) {
        if (server == null || notice == null || to == null || text == null) return;
        ServerPlayer p = server.getPlayerList().getPlayer(to);
        if (p == null) return;
        try { notice.accept(p, new TownWire.Notice(text.length() > 200 ? text.substring(0, 200) : text)); }
        catch (RuntimeException ignored) { }
    }

    public static void handle(TownWire.Query q, ServerPlayer player) {
        TownWire.Reply out;
        long now = System.currentTimeMillis();
        if (server == null) server = player.server;
        if (!FriendGuard.carriesPhone(player)) out = TownWire.Reply.of(q, TownWire.Status.FORBIDDEN, "");
        else if (now - LAST.getOrDefault(player, 0L) < 200) out = TownWire.Reply.of(q, TownWire.Status.BUSY, "");
        else if (!api()) out = TownWire.Reply.of(q, TownWire.Status.NO_PLUGIN, "");
        else {
            LAST.put(player, now);
            try { out = run(q, player); }
            catch (Throwable t) {
                MCphone.LOGGER.warn("[포키폰] 마을 앱 처리 실패: {}", t.toString());
                out = TownWire.Reply.of(q, TownWire.Status.ERROR, "");
            }
        }
        reply.accept(player, out);
    }

    private static TownWire.Reply run(TownWire.Query q, ServerPlayer player) throws Exception {
        UUID me = player.getUUID();
        List<String> lines = new ArrayList<>();
        TownWire.Status st = TownWire.Status.OK;
        String msg = "";
        switch (q.kind()) {
            case ME -> {
                List<String> mine = call("me", me);
                lines.addAll(mine);
                // 마을이 있으면 상세도 같이 (한 번에 그리게)
                if (!mine.isEmpty()) {
                    String[] f = mine.get(0).split("\t", -1);
                    if (f.length > 1 && !f[1].isEmpty()) lines.addAll(nicks(call("town", me, f[1])));
                }
            }
            case TOWNS -> {
                for (String l : (List<String>) call("towns", me)) {
                    String[] f = l.split("\t", -1);
                    // 이장 한글닉을 맨 끝에 붙임
                    lines.add(l + "\t" + (f.length > 6 ? nick(f[5], f[6]) : ""));
                }
            }
            case TOWN -> lines.addAll(nicks(call("town", me, q.a())));
            case ACT -> {
                String target = q.b();
                if (q.a().equals("invite") || q.a().equals("trust_add")) {
                    String[] res = resolve(player.server, target);
                    if (res[0] == null) return new TownWire.Reply(q.request(), q.kind(), TownWire.Status.ERROR, res[1], List.of());
                    target = res[0];
                }
                String r = call("act", me, q.a(), target);
                if (r == null || !r.startsWith("OK")) { st = TownWire.Status.ERROR; msg = r == null ? "" : r.replaceFirst("^ERR:", ""); }
            }
            case SWITCH -> {
                String r = call("setListPublic", me, q.flag());
                if (r == null || !r.startsWith("OK")) { st = TownWire.Status.ERROR; msg = r == null ? "" : r.replaceFirst("^ERR:", ""); }
            }
        }
        return new TownWire.Reply(q.request(), q.kind(), st, msg, lines);
    }

    /**
     * 입력한 이름 → 마크닉. 마크닉(서버에 들어온 적 있는 사람)이면 그대로, 아니면 한글닉으로 찾음.
     * {마크닉, null} 또는 {null, 안내 문구}
     */
    static String[] resolve(MinecraftServer s, String in) {
        String t = in == null ? "" : in.trim();
        if (t.isEmpty()) return new String[]{null, "이름을 적어 주세요"};
        if (t.matches("[A-Za-z0-9_.]{1,20}")) {
            if (s.getPlayerList().getPlayerByName(t) != null) return new String[]{t, null};
            var cache = s.getProfileCache();
            if (cache != null && cache.get(t).isPresent()) return new String[]{cache.get(t).get().getName(), null};
        }
        List<UUID> ids = PokiNames.findByNick(t);
        if (ids.size() > 1) return new String[]{null, "같은 한글닉이 여러 명이에요 · 마크닉으로 적어 주세요"};
        if (ids.size() == 1) {
            ServerPlayer on = s.getPlayerList().getPlayer(ids.get(0));
            if (on != null) return new String[]{on.getGameProfile().getName(), null};
            var cache = s.getProfileCache();
            if (cache != null) {
                var gp = cache.get(ids.get(0));
                if (gp.isPresent()) return new String[]{gp.get().getName(), null};
            }
        }
        // 마크닉 모양이면 Towny 에 그대로 넘김 (처음 보는 사람이면 Towny 가 채팅으로 알려줌)
        if (t.matches("[A-Za-z0-9_.]{1,20}")) return new String[]{t, null};
        return new String[]{null, "'" + t + "' 한글닉을 가진 사람을 못 찾았어요"};
    }

    /** 마을 상세 줄: T 줄은 이장 한글닉, R·A 줄은 그 사람 한글닉을 맨 끝 칸에 */
    private static List<String> nicks(List<String> in) {
        List<String> out = new ArrayList<>(in.size());
        for (String l : in) {
            String[] f = l.split("\t", -1);
            if (f.length > 2 && (f[0].equals("R") || f[0].equals("A"))) out.add(l + "\t" + nick(f[1], f[2]));
            else if (f.length > 6 && f[0].equals("T")) out.add(l + "\t" + nick(f[5], f[6]));
            else out.add(l);
        }
        return out;
    }

    private static String nick(String uuid, String name) {
        try { return PokiNames.display(UUID.fromString(uuid), name); }
        catch (IllegalArgumentException e) { return name; }
    }
}
