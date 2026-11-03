package com.november.mcphone.feature.chat.profile.net;
import com.november.mcphone.core.net.MCphoneNetwork;
import com.november.mcphone.feature.chat.client.profile.ProfileClientCache;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** 加载器注册适配，不维护个人资料业务。 */
public final class ProfileNetworking {
    private ProfileNetworking() {}
    public static void register(PayloadRegistrar registrar) {
        ProfileServer.install((player, reply) -> MCphoneNetwork.sendToPlayer(player, new ProfileResponsePacket(reply)));
        MCphoneNetwork.registerToServer(registrar, ProfileRequestPacket.TYPE, ProfileRequestPacket.STREAM_CODEC,
                (packet, player) -> ProfileServer.handle(packet.value(), player));
        MCphoneNetwork.registerToClient(registrar, ProfileResponsePacket.TYPE, ProfileResponsePacket.STREAM_CODEC,
                packet -> ProfileClientCache.accept(packet.value()));
    }
}
