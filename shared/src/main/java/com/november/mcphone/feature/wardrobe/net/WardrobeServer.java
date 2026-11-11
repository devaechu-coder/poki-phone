package com.november.mcphone.feature.wardrobe.net;

import com.november.mcphone.MCphone;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;

/**
 * 옷장 서버 쪽: PokiCosmetics 모드의 kr.devaechu.pokicosmetics.Wardrobe 를 이름으로 찾아 부름
 * (owned(player) · wear(player, slot, cmd) → 실패 이유 또는 null · off(player, slot) → 벗은 개수).
 * 옷장은 폰을 안 들어도 쓸 수 있게 둠 (치장 갈아입기는 폰 기능이 아니라 옷장 기능).
 */
public final class WardrobeServer {
    private WardrobeServer() {}
    private static BiConsumer<ServerPlayer, WardrobeWire.Reply> reply;
    private static final Map<ServerPlayer, Long> LAST = new WeakHashMap<>();
    private static Method owned, wear, off;
    private static boolean looked;

    public static void install(BiConsumer<ServerPlayer, WardrobeWire.Reply> sender) { reply = sender; }

    private static boolean api() {
        if (looked) return owned != null;
        looked = true;
        try {
            Class<?> c = Class.forName("kr.devaechu.pokicosmetics.Wardrobe");
            for (Method m : c.getMethods()) {
                switch (m.getName()) {
                    case "owned" -> owned = m;
                    case "wear" -> wear = m;
                    case "off" -> off = m;
                    default -> { }
                }
            }
            if (owned == null || wear == null || off == null) owned = null;
        } catch (Throwable t) {
            MCphone.LOGGER.info("[포키폰] PokiCosmetics 옷장이 없어서 옷장 입기 꺼짐: {}", t.toString());
        }
        return owned != null;
    }

    public static void handle(WardrobeWire.Query q, ServerPlayer player) {
        WardrobeWire.Reply out;
        long now = System.currentTimeMillis();
        if (q.kind() != WardrobeWire.Kind.LIST && now - LAST.getOrDefault(player, 0L) < 250) out = WardrobeWire.Reply.of(q, WardrobeWire.Status.BUSY, "");
        else if (!api()) out = WardrobeWire.Reply.of(q, WardrobeWire.Status.NO_MOD, "");
        else {
            if (q.kind() != WardrobeWire.Kind.LIST) LAST.put(player, now);
            try {
                String msg = "";
                WardrobeWire.Status st = WardrobeWire.Status.OK;
                switch (q.kind()) {
                    case WEAR -> {
                        Object err = wear.invoke(null, player, q.slot(), q.cmd());
                        if (err != null) { st = WardrobeWire.Status.ERROR; msg = err.toString(); }
                    }
                    case OFF -> off.invoke(null, player, q.slot().isEmpty() ? null : q.slot());
                    case LIST -> { }
                }
                @SuppressWarnings("unchecked") List<String> list = (List<String>) owned.invoke(null, player);
                out = new WardrobeWire.Reply(q.request(), q.kind(), st, msg, list);
            } catch (Throwable t) {
                MCphone.LOGGER.warn("[포키폰] 옷장 처리 실패: {}", t.toString());
                out = WardrobeWire.Reply.of(q, WardrobeWire.Status.ERROR, "");
            }
        }
        reply.accept(player, out);
    }
}
