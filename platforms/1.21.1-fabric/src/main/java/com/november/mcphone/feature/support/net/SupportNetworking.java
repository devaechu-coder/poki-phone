package com.november.mcphone.feature.support.net;
import com.november.mcphone.core.net.MCphoneNetwork;

/** 포키 고객센터 패킷 등록 (Fabric) */
public final class SupportNetworking {
    private SupportNetworking() {}
    public static void register() {
        SupportServer.install((player, reply) -> MCphoneNetwork.sendToPlayer(player, new SupportResponsePacket(reply)));
        MCphoneNetwork.registerToServer(SupportRequestPacket.TYPE, SupportRequestPacket.STREAM_CODEC,
                (packet, player) -> SupportServer.handle(packet.value(), player));
        MCphoneNetwork.registerToClient(SupportResponsePacket.TYPE, SupportResponsePacket.STREAM_CODEC,
                packet -> com.november.mcphone.feature.chat.client.SupportPage.accept(packet.value()));
    }
}
