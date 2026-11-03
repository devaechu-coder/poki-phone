package com.november.mcphone.core.script.net;

import com.november.mcphone.core.net.Wire;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 客户端 → 服务端的一次脚本调用（施工方案 §15.3 的 {@code script_rpc}）。
 *
 * <h2>这个 record 是平台无关的，包装才不是</h2>
 *
 * 三个目标的登记门面分两族：1.20.1-forge 收的是
 * {@code registerToServer(Class<T>, encoder, decoder, handler)}，<b>可以直接用这个 record</b>；
 * 1.21.1 的两支收的是 {@code CustomPacketPayload.Type<T>} 加 {@code StreamCodec}，
 * 要在 {@code layers/version/1.20.5+/} 包一层 —— 那一层的包装类<b>持有</b>这个 record，
 * 不复制它的字段，于是 {@link #encode} / {@link #decode} 全仓只有一份。
 *
 * <p>现有的 {@code PurchaseAppPacket} 是把 record 与 encode/decode <b>整个复制两份</b>的。
 * 那不是历史包袱 —— 那个类必须 {@code implements CustomPacketPayload}，而
 * {@code CustomPacketPayload} 命中 shared 的判据表，进不来。本步把"要实现接口的那三行"
 * 与"其余"分开，就不必复制了。
 *
 * <h2>编解码不许碰要注册表的类型</h2>
 *
 * 这里只有 varint / varlong / utf / byteArray（§10.4.1）。<b>不许出现 ItemStack、Component、Holder</b> ——
 * 这一份跑在 {@code FriendlyByteBuf} 上，拿不到 {@code registryAccess()}，
 * 而 1.21.1 那支的 codec 参数是 {@code RegistryFriendlyByteBuf}，喂窄的进去是合法的
 * （{@code RegistryFriendlyByteBuf extends FriendlyByteBuf}），反过来不行。
 *
 * @param protocol       {@link ScriptProtocol#PROTOCOL}。对不上回 VERSION_MISMATCH，<b>不断线</b>
 * @param requestId      客户端生成（单调递增 + 随机高位），幂等键的一部分（§15.6）
 * @param connectionEpoch 服务端在握手时下发的连接标签，客户端原样回填（§15.9）
 * @param appId          哪个 App
 * @param deployRev      客户端以为的部署版本
 * @param actionId       {@code actions} 表上的属性名（§15.2）
 * @param params         参数，≤ {@link ScriptProtocol#PARAMS_MAX}
 * @param frontendDigest 界面摘要。<b>这不是安全边界</b>（§13.3）：客户端能伪造它，
 *                       服务端只用它回显"界面被改过"，<b>不参与任何授权判断</b>
 */
public record ScriptRpc(
        int protocol,
        long requestId,
        long connectionEpoch,
        String appId,
        String deployRev,
        String actionId,
        byte[] params,
        String frontendDigest) {

    public static void encode(ScriptRpc m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.protocol());
        buf.writeVarLong(m.requestId());
        buf.writeVarLong(m.connectionEpoch());
        buf.writeUtf(m.appId(), ScriptProtocol.ID_MAX);
        buf.writeUtf(m.deployRev(), ScriptProtocol.ID_MAX);
        buf.writeUtf(m.actionId(), ScriptProtocol.ID_MAX);
        Wire.writeBytes(buf, m.params(), ScriptProtocol.PARAMS_MAX);
        buf.writeUtf(m.frontendDigest(), ScriptProtocol.DIGEST_MAX);
    }

    public static ScriptRpc decode(FriendlyByteBuf buf) {
        return new ScriptRpc(
                buf.readVarInt(),
                buf.readVarLong(),
                buf.readVarLong(),
                buf.readUtf(ScriptProtocol.ID_MAX),
                buf.readUtf(ScriptProtocol.ID_MAX),
                buf.readUtf(ScriptProtocol.ID_MAX),
                Wire.readBytes(buf, ScriptProtocol.PARAMS_MAX),
                buf.readUtf(ScriptProtocol.DIGEST_MAX));
    }
}
