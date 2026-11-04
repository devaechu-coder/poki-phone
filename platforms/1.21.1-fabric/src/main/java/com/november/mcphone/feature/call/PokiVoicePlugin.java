package com.november.mcphone.feature.call;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.LeaveGroupEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;

import java.util.UUID;

/**
 * Simple Voice Chat 플러그인 (fabric.mod.json 의 "voicechat" 진입점).
 * 음성채팅 모드가 없으면 이 클래스는 아예 안 불러와져서 포키폰은 그대로 돈다.
 */
public final class PokiVoicePlugin implements VoicechatPlugin {
    @Override
    public String getPluginId() { return "pokiphone"; }

    @Override
    public void registerEvents(EventRegistration reg) {
        reg.registerEvent(VoicechatServerStartedEvent.class, e -> CallService.voice = new Adapter(e.getVoicechat()));
        reg.registerEvent(LeaveGroupEvent.class, e -> {
            VoicechatConnection c = e.getConnection();
            Group g = e.getGroup();
            if (c != null && g != null) CallService.onVoiceLeft(c.getPlayer().getUuid(), g.getId());
        });
    }

    private record Adapter(VoicechatServerApi api) implements CallService.Voice {
        @Override
        public Object createGroup(String name) {
            Group.Builder b = api.groupBuilder().setName(name).setPassword(UUID.randomUUID().toString().substring(0, 12))
                    .setType(Group.Type.ISOLATED).setPersistent(false);
            try { b = b.setHidden(true); } catch (Throwable ignored) { /* 옛 음성채팅: 숨김 기능 없음 (비밀번호로만 막힘) */ }
            return b.build();
        }

        @Override
        public UUID groupId(Object group) { return ((Group) group).getId(); }

        @Override
        public boolean connected(UUID player) {
            VoicechatConnection c = api.getConnectionOf(player);
            return c != null && c.isInstalled() && c.isConnected();
        }

        @Override
        public void join(UUID player, Object group) {
            VoicechatConnection c = api.getConnectionOf(player);
            if (c != null) c.setGroup((Group) group);
        }

        @Override
        public void leave(UUID player) {
            VoicechatConnection c = api.getConnectionOf(player);
            if (c != null) c.setGroup(null);
        }
    }
}
