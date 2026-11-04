package com.november.mcphone.feature.chat.client;

import com.november.mcphone.core.net.MCphoneNetwork;
import com.november.mcphone.feature.call.net.CallRequestPacket;
import com.november.mcphone.feature.call.net.CallWire;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 포키폰 통화 — 클라이언트 쪽 상태 (주 스레드) */
public final class CallClient {
    private CallClient() {}

    /** 걸려 온 전화 (받기 전) */
    static CallWire.Event incoming;
    /** 내가 들어가 있는 통화 (거는 중 포함) */
    static CallWire.Event active;
    /** 그룹방 id → 진행 중인 그룹 통화 (참여 버튼용) */
    static final Map<UUID, CallWire.Event> GROUP_CALLS = new HashMap<>();
    private static boolean show;
    private static long ringAt;

    public static boolean inCall() { return active != null; }
    public static boolean ringing() { return incoming != null; }
    public static long activeStarted() { return active == null ? 0 : active.started(); }

    /** 폰이 열려 있으면 통화 화면으로 넘어가야 하나 */
    public static boolean consumeShow() { boolean b = show; show = false; return b; }

    public static void send(CallWire.Action action, UUID target, boolean group) {
        if (Minecraft.getInstance().getConnection() == null) return;
        MCphoneNetwork.sendToServer(new CallRequestPacket(new CallWire.Query(action, target, group)));
    }

    public static void call(UUID peer, boolean group) { send(CallWire.Action.START, peer, group); show = true; }
    public static void accept() { if (incoming != null) { send(CallWire.Action.ACCEPT, incoming.call(), false); incoming = null; } }
    public static void decline() { if (incoming != null) { send(CallWire.Action.DECLINE, incoming.call(), false); incoming = null; } }
    public static void hangup() { send(CallWire.Action.HANGUP, null, false); }
    public static void join(UUID call) { send(CallWire.Action.JOIN, call, false); show = true; }

    private static void tell(String key, Object... args) {
        var p = Minecraft.getInstance().player;
        if (p != null) com.november.mcphone.platform.PlayerAccess.message(p, Component.translatable(key, args), true);
    }

    public static void accept(CallWire.Event e) {
        switch (e.kind()) {
            case RESULT -> { if (e.status() != CallWire.Status.OK) tell("mcphone.call.status." + e.status().name().toLowerCase(java.util.Locale.ROOT)); }
            case INCOMING -> {
                if (active != null) return;
                incoming = e; show = true; ringAt = System.currentTimeMillis();
                var mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 1f, 1.2f);
                    com.november.mcphone.platform.PlayerAccess.message(mc.player,
                            Component.translatable(e.group() ? "mcphone.call.incoming_group_chat" : "mcphone.call.incoming_chat", e.title()), false);
                }
            }
            case STATE -> {
                if (incoming != null && incoming.call().equals(e.call())) incoming = null;
                boolean first = active == null;
                // 1:1 거는 사람 화면 제목(상대 이름)은 처음 받은 걸 유지
                if (!first && !active.group() && active.call().equals(e.call()))
                    active = new CallWire.Event(e.kind(), e.status(), e.call(), e.from(), active.title(), e.group(), e.groupChat(), e.started(), e.count(), e.members());
                else active = e;
                if (first) show = true;
            }
            case ENDED -> {
                if (incoming != null && incoming.call().equals(e.call())) incoming = null;
                if (active != null && active.call().equals(e.call())) {
                    active = null;
                    tell("mcphone.call.status." + e.status().name().toLowerCase(java.util.Locale.ROOT));
                }
            }
            case GROUP -> {
                if (e.count() > 0 && !CallWire.none(e.call())) GROUP_CALLS.put(e.groupChat(), e);
                else GROUP_CALLS.remove(e.groupChat());
            }
        }
    }

    /** 30초 넘게 울리면 화면에서 치움 (서버도 부재중 처리함) */
    static void tick() {
        if (incoming != null && System.currentTimeMillis() - ringAt > 35_000L) incoming = null;
    }

    public static void clear() { incoming = null; active = null; GROUP_CALLS.clear(); show = false; }
}
