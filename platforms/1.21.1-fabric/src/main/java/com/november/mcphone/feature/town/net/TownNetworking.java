package com.november.mcphone.feature.town.net;
import com.november.mcphone.core.net.MCphoneNetwork;

/** 포키 마을 앱 패킷 등록 (Fabric) */
public final class TownNetworking {
    private TownNetworking() {}
    public static void register() {
        TownServer.install(
                (player, reply) -> MCphoneNetwork.sendToPlayer(player, new TownResponsePacket(reply)),
                // 예전 버전 클라이언트(패킷 모름)에는 안 보냄 → Towny 채팅 메시지만 받음
                (player, notice) -> {
                    if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, TownNoticePacket.TYPE))
                        MCphoneNetwork.sendToPlayer(player, new TownNoticePacket(notice));
                });
        MCphoneNetwork.registerToServer(TownRequestPacket.TYPE, TownRequestPacket.STREAM_CODEC,
                (packet, player) -> TownServer.handle(packet.value(), player));
        MCphoneNetwork.registerToClient(TownResponsePacket.TYPE, TownResponsePacket.STREAM_CODEC,
                packet -> com.november.mcphone.feature.chat.client.TownPage.accept(packet.value()));
        MCphoneNetwork.registerToClient(TownNoticePacket.TYPE, TownNoticePacket.STREAM_CODEC,
                packet -> com.november.mcphone.feature.chat.client.TownPage.notice(packet.value()));
        // 버킷 플러그인은 모드보다 늦게 켜짐 → 첫 접속 때 PokiTown 알림 연결
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> TownServer.hook(server));
    }
}
