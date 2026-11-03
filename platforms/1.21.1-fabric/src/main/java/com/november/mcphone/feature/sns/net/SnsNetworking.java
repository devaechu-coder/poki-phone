package com.november.mcphone.feature.sns.net;
import com.november.mcphone.core.net.MCphoneNetwork;
import com.november.mcphone.feature.sns.client.SnsClientCache;

/** 포키 SNS 패킷 등록 (Fabric) */
public final class SnsNetworking {
    private SnsNetworking() {}
    public static void register() {
        SnsServer.install((player, reply) -> MCphoneNetwork.sendToPlayer(player, new SnsResponsePacket(reply)));
        MCphoneNetwork.registerToServer(SnsRequestPacket.TYPE, SnsRequestPacket.STREAM_CODEC,
                (packet, player) -> SnsServer.handle(packet.value(), player));
        MCphoneNetwork.registerToClient(SnsResponsePacket.TYPE, SnsResponsePacket.STREAM_CODEC,
                packet -> SnsClientCache.accept(packet.value()));
    }
}
