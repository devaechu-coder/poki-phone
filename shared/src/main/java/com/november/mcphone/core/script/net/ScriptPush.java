package com.november.mcphone.core.script.net;

import com.november.mcphone.core.net.Wire;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 服务端 → 客户端的主动推送（施工方案 §15.3 的 {@code script_push}）。
 *
 * <p>平台无关的那一半，说明见 {@link ScriptRpc}。
 *
 * <h2>宿主也走这个包，所以 appId 与 topic 都要认</h2>
 *
 * 握手（§15.7）不另开第四个包，走这里的保留 topic。而 {@code topic} 在 §16.5 里是
 * <b>脚本说了算</b>的字符串（{@code ctx.notify(topic, data)}）—— 只按 topic 分派的话，
 * 任何一个普通 App 都能伪造一次握手，把 serverId 与部署表整个改写。
 *
 * <p>所以客户端分派宿主消息时<b>两个条件都要查</b>：
 * {@code appId.equals(ScriptProtocol.HOST_APP_ID)} 且
 * {@code topic.startsWith(ScriptProtocol.HOST_TOPIC_PREFIX)}。见 {@link #isHost()}。
 *
 * @param appId    哪个 App 发的；宿主自己发的是 {@link ScriptProtocol#HOST_APP_ID}
 * @param topic    分类。脚本发的必须以自己的 appId 开头（S13 落实）
 * @param data     内容，≤ {@link ScriptProtocol#DATA_MAX}
 * @param revision 单调递增，客户端据此判乱序与丢失
 */
public record ScriptPush(String appId, String topic, byte[] data, long revision) {

    /**
     * 这条是不是宿主自己发的。<b>两个条件缺一不可</b>，理由见类注释。
     *
     * <p>只查 topic 前缀是不够的：脚本能选 topic。只查 appId 也不够：
     * 宿主将来可能用同一个 appId 发别的东西，而握手的分派要认得出是哪一类。
     */
    public boolean isHost() {
        return ScriptProtocol.HOST_APP_ID.equals(appId)
                && topic != null && topic.startsWith(ScriptProtocol.HOST_TOPIC_PREFIX);
    }

    public static void encode(ScriptPush m, FriendlyByteBuf buf) {
        buf.writeUtf(m.appId(), ScriptProtocol.ID_MAX);
        buf.writeUtf(m.topic(), ScriptProtocol.TOPIC_MAX);
        Wire.writeBytes(buf, m.data(), ScriptProtocol.DATA_MAX);
        buf.writeVarLong(m.revision());
    }

    public static ScriptPush decode(FriendlyByteBuf buf) {
        return new ScriptPush(
                buf.readUtf(ScriptProtocol.ID_MAX),
                buf.readUtf(ScriptProtocol.TOPIC_MAX),
                Wire.readBytes(buf, ScriptProtocol.DATA_MAX),
                buf.readVarLong());
    }
}
