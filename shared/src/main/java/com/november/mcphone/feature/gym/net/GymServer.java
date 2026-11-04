package com.november.mcphone.feature.gym.net;

import com.november.mcphone.MCphone;
import com.november.mcphone.feature.chat.FriendGuard;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;

/** 체육관 정보는 PokiGymCap 모드(GymInfo.forPlayer)에서 가져온다. 없으면 NO_GYM. */
public final class GymServer {
    private GymServer() {}
    private static BiConsumer<ServerPlayer, GymWire.Reply> reply;
    private static final Map<ServerPlayer, Long> LAST = new WeakHashMap<>();
    private static Method forPlayer;
    private static boolean looked;

    public static void install(BiConsumer<ServerPlayer, GymWire.Reply> sender) { reply = sender; }

    private static Method method() {
        if (looked) return forPlayer;
        looked = true;
        try {
            for (Method m : Class.forName("kr.devaechu.pokigymcap.GymInfo").getMethods())
                if (m.getName().equals("forPlayer") && m.getParameterCount() == 1) forPlayer = m;
        } catch (Throwable t) {
            MCphone.LOGGER.info("[포키폰] PokiGymCap 이 없어서 체육관 앱 정보 없음");
        }
        return forPlayer;
    }

    public static void handle(UUID request, ServerPlayer player) {
        GymWire.Status status = GymWire.Status.OK;
        List<GymWire.Gym> gyms = new ArrayList<>();
        long now = System.currentTimeMillis();
        if (!FriendGuard.carriesPhone(player)) status = GymWire.Status.FORBIDDEN;
        else if (now - LAST.getOrDefault(player, 0L) < 500) status = GymWire.Status.BUSY;
        else {
            LAST.put(player, now);
            Method m = method();
            if (m == null) status = GymWire.Status.NO_GYM;
            else try {
                Object out = m.invoke(null, player);
                if (out instanceof List<?> lines) for (Object o : lines) {
                    String[] f = String.valueOf(o).split("\t", -1);
                    if (f.length < 10) continue;
                    gyms.add(new GymWire.Gym(cut(f[0], 64), cut(f[1], 64), cut(f[2], 32), num(f[3]), "1".equals(f[4]), lnum(f[5]),
                            num(f[6]), num(f[7]), num(f[8]), lnum(f[9])));
                    if (gyms.size() >= 32) break;
                }
            } catch (Throwable t) {
                MCphone.LOGGER.warn("[포키폰] 체육관 정보 가져오기 실패: {}", t.toString());
                status = GymWire.Status.NO_GYM;
            }
        }
        reply.accept(player, new GymWire.Reply(request, status, gyms));
    }

    private static String cut(String s, int n) { return s.length() > n ? s.substring(0, n) : s; }
    private static int num(String s) { try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; } }
    private static long lnum(String s) { try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return 0; } }
}
