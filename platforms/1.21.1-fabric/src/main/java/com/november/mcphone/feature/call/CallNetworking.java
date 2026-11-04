package com.november.mcphone.feature.call;

import com.november.mcphone.core.net.MCphoneNetwork;
import com.november.mcphone.feature.call.net.CallEventPacket;
import com.november.mcphone.feature.call.net.CallRequestPacket;

/** 포키폰 통화 패킷 등록 (Fabric) */
public final class CallNetworking {
    private CallNetworking() {}
    public static void register() {
        CallService.install((player, e) -> MCphoneNetwork.sendToPlayer(player, new CallEventPacket(e)));
        MCphoneNetwork.registerToServer(CallRequestPacket.TYPE, CallRequestPacket.STREAM_CODEC,
                (packet, player) -> CallService.handle(packet.value(), player));
        MCphoneNetwork.registerToClient(CallEventPacket.TYPE, CallEventPacket.STREAM_CODEC,
                packet -> com.november.mcphone.feature.chat.client.CallClient.accept(packet.value()));
    }
}
