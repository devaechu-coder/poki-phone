package com.november.mcphone.feature.chat.group.net;

import com.november.mcphone.core.net.MCphoneNetwork;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** 两个 NeoForge 版本共用注册适配；业务、权限和缓存维护均在 shared。 */
public final class GroupNetworking {
    private GroupNetworking() {}
    public static void register(PayloadRegistrar registrar) {
        GroupServer.install((player, reply) -> MCphoneNetwork.sendToPlayer(player, new GroupResponsePacket(reply)),
                MCphoneNetwork::sendToPlayer);
        MCphoneNetwork.registerToServer(registrar, GroupRequestPacket.TYPE, GroupRequestPacket.STREAM_CODEC,
                (packet, player) -> GroupServer.handle(packet.value(), player));
        MCphoneNetwork.registerToClient(registrar, GroupResponsePacket.TYPE, GroupResponsePacket.STREAM_CODEC,
                packet -> GroupClientCache.accept(packet.value()));
    }
}
