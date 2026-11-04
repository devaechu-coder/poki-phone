package com.november.mcphone.feature.call;

import com.november.mcphone.MCphone;
import com.november.mcphone.feature.call.net.CallWire;
import com.november.mcphone.feature.chat.FriendData;
import com.november.mcphone.feature.chat.FriendGuard;
import com.november.mcphone.feature.chat.PokiNames;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * 포키폰 통화 (1:1 · 그룹). 음성은 Simple Voice Chat 의 숨김·격리 그룹으로 붙인다.
 * 음성채팅 모드가 없으면 {@link #voice} 가 null 이라 "음성채팅이 없어요" 로 끝남. 모든 처리는 서버 주 스레드.
 */
public final class CallService {
    private CallService() {}

    /** 음성채팅 모드와의 연결 (fabric 쪽 PokiVoicePlugin 이 채움) */
    public interface Voice {
        Object createGroup(String name);
        UUID groupId(Object group);
        boolean connected(UUID player);
        void join(UUID player, Object group);
        void leave(UUID player);
    }

    public static volatile Voice voice;
    private static BiConsumer<ServerPlayer, CallWire.Event> push;
    private static MinecraftServer server;

    private static final long RING_MS = 30_000L;

    private static final class Call {
        final UUID id = UUID.randomUUID();
        UUID groupChat;          // 그룹 통화면 포키톡 그룹방 id
        String title = "";
        UUID owner;
        final Set<UUID> members = new LinkedHashSet<>();
        final Map<UUID, Long> ringing = new LinkedHashMap<>();
        long started;
        Object group;
        UUID voiceGroup;
        boolean isGroup() { return groupChat != null; }
    }

    private static final Map<UUID, Call> CALLS = new HashMap<>();
    private static final Map<UUID, Call> BY_PLAYER = new HashMap<>();
    private static final Map<UUID, Call> BY_CHAT = new HashMap<>();
    /** 우리가 일부러 그룹에서 뺀 사람 (그때 오는 "그룹 나감" 이벤트는 무시) */
    private static final Set<UUID> LEAVING = new java.util.HashSet<>();

    public static void install(BiConsumer<ServerPlayer, CallWire.Event> sender) { push = sender; }

    private static String name(UUID id) {
        ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(id);
        String raw = p != null ? p.getName().getString() : server == null ? null : FriendData.get(server).getName(id);
        String n = PokiNames.display(id, raw == null ? id.toString().substring(0, 8) : raw);
        return n.length() > 32 ? n.substring(0, 32) : n;
    }

    private static void send(UUID to, CallWire.Event e) {
        ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(to);
        if (p != null && push != null) push.accept(p, e);
    }

    private static CallWire.Event state(Call c, CallWire.Kind kind, CallWire.Status status) {
        List<CallWire.Member> ms = new ArrayList<>();
        for (UUID m : c.members) ms.add(new CallWire.Member(m, name(m), false));
        for (UUID m : c.ringing.keySet()) ms.add(new CallWire.Member(m, name(m), true));
        return new CallWire.Event(kind, status, c.id, c.owner, c.title, c.isGroup(), c.groupChat, c.started, c.members.size(), ms);
    }

    private static void broadcast(Call c) {
        var e = state(c, CallWire.Kind.STATE, CallWire.Status.OK);
        for (UUID m : c.members) send(m, e);
        if (c.isGroup()) groupNotice(c, c.members.size());
    }

    /** 그룹방 사람들에게 "통화 중 n명" 알림 (0 이면 끝남) */
    private static void groupNotice(Call c, int count) {
        if (c.groupChat == null || server == null) return;
        var e = new CallWire.Event(CallWire.Kind.GROUP, CallWire.Status.OK, count > 0 ? c.id : null, c.owner, c.title, true, c.groupChat, c.started, count, null);
        for (UUID m : groupMembers(c.groupChat)) send(m, e);
    }

    private static List<UUID> groupMembers(UUID chat) {
        List<UUID> out = new ArrayList<>();
        try {
            var data = com.november.mcphone.feature.chat.group.GroupData.get(server);
            for (var room : data.book().snapshot())
                if (room.id().equals(chat)) { for (var m : room.members()) out.add(m.id()); break; }
        } catch (RuntimeException e) {
            MCphone.LOGGER.warn("[포키폰 통화] 그룹방 멤버를 못 읽음: {}", e.toString());
        }
        return out;
    }

    public static void handle(CallWire.Query q, ServerPlayer player) {
        server = player.level().getServer();
        CallWire.Status st;
        try {
            st = run(q, player);
        } catch (RuntimeException e) {
            MCphone.LOGGER.warn("[포키폰 통화] 처리 실패: {}", e.toString());
            st = CallWire.Status.FAILED;
        }
        if (st != null && push != null) push.accept(player, CallWire.Event.result(st));
    }

    private static CallWire.Status run(CallWire.Query q, ServerPlayer p) {
        Voice v = voice;
        UUID me = p.getUUID();
        if (q.action() == CallWire.Action.HANGUP) { hangup(me); return null; }
        if (q.action() == CallWire.Action.DECLINE) { decline(me, q.target()); return null; }
        if (v == null) return CallWire.Status.NO_VOICE;
        if (!FriendGuard.carriesPhone(p)) return CallWire.Status.FORBIDDEN;
        if (!v.connected(me)) return CallWire.Status.NOT_CONNECTED;
        switch (q.action()) {
            case START -> {
                if (BY_PLAYER.containsKey(me)) return CallWire.Status.BUSY;
                return q.group() ? startGroup(p, q.target()) : startDirect(p, q.target());
            }
            case ACCEPT -> {
                Call c = CALLS.get(q.target());
                if (c == null || c.ringing.remove(me) == null) return CallWire.Status.ENDED;
                if (BY_PLAYER.containsKey(me)) return CallWire.Status.BUSY;
                joinCall(c, me);
                return null;
            }
            case JOIN -> {
                Call c = CALLS.get(q.target());
                if (c == null || !c.isGroup()) return CallWire.Status.ENDED;
                if (!groupMembers(c.groupChat).contains(me)) return CallWire.Status.FORBIDDEN;
                if (BY_PLAYER.containsKey(me)) return BY_PLAYER.get(me) == c ? null : CallWire.Status.BUSY;
                c.ringing.remove(me);
                joinCall(c, me);
                return null;
            }
            default -> { return CallWire.Status.FAILED; }
        }
    }

    private static Call create(UUID owner, String title) {
        Call c = new Call();
        c.owner = owner; c.title = title;
        c.group = voice.createGroup("pokicall" + c.id.toString().substring(0, 6));
        c.voiceGroup = voice.groupId(c.group);
        CALLS.put(c.id, c);
        return c;
    }

    private static CallWire.Status startDirect(ServerPlayer p, UUID target) {
        UUID me = p.getUUID();
        if (CallWire.none(target) || target.equals(me)) return CallWire.Status.FAILED;
        if (!FriendGuard.mayActOn(p, target)) return CallWire.Status.FORBIDDEN;
        ServerPlayer t = server.getPlayerList().getPlayer(target);
        if (t == null) return CallWire.Status.OFFLINE;
        if (!FriendGuard.carriesPhone(t)) return CallWire.Status.OFFLINE;
        if (!voice.connected(target)) return CallWire.Status.PEER_NOT_CONNECTED;
        if (BY_PLAYER.containsKey(target)) return CallWire.Status.BUSY;
        Call c = create(me, name(me));
        joinVoice(c, me);
        c.ringing.put(target, System.currentTimeMillis());
        send(target, state(c, CallWire.Kind.INCOMING, CallWire.Status.OK));
        var out = state(c, CallWire.Kind.STATE, CallWire.Status.OK);
        // 거는 사람 화면에는 상대 이름을 제목으로
        send(me, new CallWire.Event(out.kind(), out.status(), out.call(), out.from(), name(target), false, null, 0, out.count(), out.members()));
        return null;
    }

    private static CallWire.Status startGroup(ServerPlayer p, UUID chat) {
        UUID me = p.getUUID();
        var room = com.november.mcphone.feature.chat.group.GroupService.read(p, chat); // 멤버 아니면 예외
        Call existing = BY_CHAT.get(chat);
        if (existing != null) { existing.ringing.remove(me); joinCall(existing, me); return null; }
        Call c = create(me, room.name());
        c.groupChat = chat;
        BY_CHAT.put(chat, c);
        joinVoice(c, me);
        long now = System.currentTimeMillis();
        for (var m : room.members()) {
            UUID id = m.id();
            if (id.equals(me) || BY_PLAYER.containsKey(id)) continue;
            ServerPlayer t = server.getPlayerList().getPlayer(id);
            if (t == null || !FriendGuard.carriesPhone(t) || !voice.connected(id)) continue;
            c.ringing.put(id, now);
            send(id, state(c, CallWire.Kind.INCOMING, CallWire.Status.OK));
        }
        broadcast(c);
        return null;
    }

    private static void joinVoice(Call c, UUID player) {
        BY_PLAYER.put(player, c);
        c.members.add(player);
        voice.join(player, c.group);
    }

    private static void joinCall(Call c, UUID player) {
        joinVoice(c, player);
        if (c.started == 0 && c.members.size() >= 2) c.started = System.currentTimeMillis();
        if (c.isGroup() && c.started == 0) c.started = System.currentTimeMillis();
        broadcast(c);
    }

    private static void leaveVoice(Call c, UUID player) {
        BY_PLAYER.remove(player, c);
        c.members.remove(player);
        LEAVING.add(player);
        try { if (voice != null) voice.leave(player); } finally { LEAVING.remove(player); }
    }

    private static void end(Call c, CallWire.Status why) {
        if (CALLS.remove(c.id) == null) return;
        if (c.groupChat != null) BY_CHAT.remove(c.groupChat, c);
        var e = new CallWire.Event(CallWire.Kind.ENDED, why, c.id, c.owner, c.title, c.isGroup(), c.groupChat, c.started, 0, null);
        for (UUID m : new ArrayList<>(c.members)) { leaveVoice(c, m); send(m, e); }
        for (UUID m : c.ringing.keySet()) send(m, e);
        c.ringing.clear();
        if (c.isGroup()) groupNotice(c, 0);
    }

    public static void hangup(UUID player) {
        Call c = BY_PLAYER.get(player);
        if (c == null) return;
        if (!c.isGroup()) { end(c, CallWire.Status.ENDED); return; }
        leaveVoice(c, player);
        send(player, new CallWire.Event(CallWire.Kind.ENDED, CallWire.Status.ENDED, c.id, c.owner, c.title, true, c.groupChat, c.started, 0, null));
        if (c.members.isEmpty()) end(c, CallWire.Status.ENDED);
        else broadcast(c);
    }

    private static void decline(UUID player, UUID callId) {
        Call c = CALLS.get(callId);
        if (c == null || c.ringing.remove(player) == null) return;
        if (!c.isGroup()) end(c, CallWire.Status.DECLINED);
        else broadcast(c);
    }

    /** 서버 틱 (1초에 한 번 정도 불러도 됨): 30초 안 받으면 부재중 */
    public static void tick(MinecraftServer s) {
        server = s;
        if (CALLS.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Call c : new ArrayList<>(CALLS.values())) {
            boolean changed = false;
            for (var it = c.ringing.entrySet().iterator(); it.hasNext(); ) {
                var e = it.next();
                if (now - e.getValue() < RING_MS && s.getPlayerList().getPlayer(e.getKey()) != null) continue;
                it.remove(); changed = true;
                send(e.getKey(), new CallWire.Event(CallWire.Kind.ENDED, CallWire.Status.MISSED, c.id, c.owner, c.title, c.isGroup(), c.groupChat, 0, 0, null));
            }
            if (!changed) continue;
            if (!c.isGroup() && c.members.size() < 2 && c.ringing.isEmpty()) end(c, CallWire.Status.MISSED);
            else broadcast(c);
        }
    }

    /** 음성채팅 화면에서 직접 그룹을 나갔을 때 (다른 스레드일 수 있어서 주 스레드로) */
    public static void onVoiceLeft(UUID player, UUID voiceGroup) {
        MinecraftServer s = server;
        if (s == null) return;
        s.execute(() -> {
            if (LEAVING.contains(player)) return;
            Call c = BY_PLAYER.get(player);
            if (c != null && voiceGroup != null && voiceGroup.equals(c.voiceGroup)) hangup(player);
        });
    }

    public static void onLogout(UUID player) {
        Call c = BY_PLAYER.get(player);
        if (c != null) hangup(player);
        for (Call x : new ArrayList<>(CALLS.values()))
            if (x.ringing.remove(player) != null) {
                if (!x.isGroup() && x.members.size() < 2) end(x, CallWire.Status.MISSED);
                else broadcast(x);
            }
    }
}
