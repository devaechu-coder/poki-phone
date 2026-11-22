package com.november.mcphone.feature.chat.notice;
import com.november.mcphone.core.net.MCphoneNetwork;

/** 포키 친구 알림 패킷 등록 (Fabric) */
public final class FriendNoticeNetworking {
    private FriendNoticeNetworking() {}
    public static void register() {
        // 예전 버전 클라이언트(패킷 모름)에는 안 보냄 → 채팅 한 줄만 받음
        FriendNotice.install((player, notice) -> {
            if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, FriendNoticePacket.TYPE))
                MCphoneNetwork.sendToPlayer(player, new FriendNoticePacket(notice));
        });
        MCphoneNetwork.registerToClient(FriendNoticePacket.TYPE, FriendNoticePacket.STREAM_CODEC,
                packet -> com.november.mcphone.feature.chat.client.FriendNoticeClient.accept(packet.value()));
    }
}
