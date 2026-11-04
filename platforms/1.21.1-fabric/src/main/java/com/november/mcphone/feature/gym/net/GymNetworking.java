package com.november.mcphone.feature.gym.net;
import com.november.mcphone.core.net.MCphoneNetwork;

/** 포키 체육관 앱 패킷 등록 (Fabric) */
public final class GymNetworking {
    private GymNetworking() {}
    public static void register() {
        GymServer.install((player, reply) -> MCphoneNetwork.sendToPlayer(player, new GymResponsePacket(reply)));
        MCphoneNetwork.registerToServer(GymRequestPacket.TYPE, GymRequestPacket.STREAM_CODEC,
                (packet, player) -> GymServer.handle(packet.value(), player));
        MCphoneNetwork.registerToClient(GymResponsePacket.TYPE, GymResponsePacket.STREAM_CODEC,
                packet -> com.november.mcphone.feature.chat.client.GymPage.accept(packet.value()));
    }
}
