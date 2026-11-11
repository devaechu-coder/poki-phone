package com.november.mcphone.feature.wardrobe.net;
import com.november.mcphone.core.net.MCphoneNetwork;

/** 포키 옷장 패킷 등록 (Fabric) */
public final class WardrobeNetworking {
    private WardrobeNetworking() {}
    public static void register() {
        WardrobeServer.install((player, reply) -> MCphoneNetwork.sendToPlayer(player, new WardrobeResponsePacket(reply)));
        MCphoneNetwork.registerToServer(WardrobeRequestPacket.TYPE, WardrobeRequestPacket.STREAM_CODEC,
                (packet, player) -> WardrobeServer.handle(packet.value(), player));
        MCphoneNetwork.registerToClient(WardrobeResponsePacket.TYPE, WardrobeResponsePacket.STREAM_CODEC,
                packet -> com.november.mcphone.feature.chat.client.WardrobePage.accept(packet.value()));
    }
}
