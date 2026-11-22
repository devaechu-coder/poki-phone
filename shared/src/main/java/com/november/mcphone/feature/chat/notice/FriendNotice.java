package com.november.mcphone.feature.chat.notice;

import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * 포키: 친구 요청·수락 알림.
 * 받는 사람이 접속 중이면 채팅 한 줄 + (모드 클라이언트면) 포키톡 팝업.
 */
public final class FriendNotice {
    private FriendNotice() {}

    /** REQUEST: who 가 나에게 친구 요청 · ACCEPTED: who 와 친구가 됨 */
    public enum Kind { REQUEST, ACCEPTED }

    public record Notice(Kind kind, UUID who, String name) {}

    private static BiConsumer<ServerPlayer, Notice> sender;

    /** 플랫폼 네트워킹이 팝업 패킷 보내는 법을 꽂아 줌 (없으면 채팅만) */
    public static void install(BiConsumer<ServerPlayer, Notice> s) { sender = s; }

    public static void send(ServerPlayer target, Kind kind, UUID who, String name) {
        if (target == null) return;
        Component line = Component.literal("📱 [포키톡] ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal(name).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(kind == Kind.REQUEST
                        ? "님이 친구 요청을 보냈어요. 포키폰 › 포키톡에서 수락해 주세요."
                        : "님과 친구가 됐어요!").withStyle(ChatFormatting.GRAY));
        target.sendSystemMessage(line);
        BiConsumer<ServerPlayer, Notice> s = sender;
        if (s != null) {
            try { s.accept(target, new Notice(kind, who, name)); } catch (RuntimeException ignored) { }
        }
    }

    public static void write(Notice n, FriendlyByteBuf b) {
        b.writeEnum(n.kind());
        b.writeUUID(n.who());
        b.writeUtf(n.name(), 64);
    }

    public static Notice read(FriendlyByteBuf b) {
        return new Notice(b.readEnum(Kind.class), b.readUUID(), b.readUtf(64));
    }
}
