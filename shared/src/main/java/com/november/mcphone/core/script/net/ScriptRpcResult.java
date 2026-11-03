package com.november.mcphone.core.script.net;

import com.november.mcphone.core.net.Wire;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;

/**
 * 服务端 → 客户端的一次调用结果（施工方案 §15.3 的 {@code script_rpc_result}）。
 *
 * <p>平台无关的那一半，说明见 {@link ScriptRpc}。
 *
 * @param requestId     对应哪一次 {@link ScriptRpc}
 * @param code          见 {@link ScriptErrorCode}。线上是序号，解码侧认不出<b>不抛</b>
 * @param data          结果，≤ {@link ScriptProtocol#DATA_MAX}
 * @param messageKey    <b>本地化键，不是文本</b>（§15.3）：服务端发键，客户端查自己的语言文件。
 *                      收文本就等于让后端往客户端推任意字符串，配合界面就是钓鱼面
 * @param messageArgs   本地化参数
 * @param retryAfterMs  {@link ScriptErrorCode#RATE_LIMITED} 时的退避毫秒数，其余为 0
 * @param stateRevision 业务状态版本（§20.6），客户端据此判断本地缓存过没过期
 */
public record ScriptRpcResult(
        long requestId,
        ScriptErrorCode code,
        byte[] data,
        String messageKey,
        List<String> messageArgs,
        long retryAfterMs,
        long stateRevision) {

    public ScriptRpcResult {
        if (code == null) code = ScriptErrorCode.INTERNAL;
        if (data == null) data = new byte[0];
        if (messageKey == null) messageKey = "";
        messageArgs = messageArgs == null ? List.of() : List.copyOf(messageArgs);
    }

    /** 成功，不带文案。 */
    public static ScriptRpcResult ok(long requestId, byte[] data, long stateRevision) {
        return new ScriptRpcResult(requestId, ScriptErrorCode.OK, data, "", List.of(), 0, stateRevision);
    }

    /** 失败，用这个码的默认文案键。 */
    public static ScriptRpcResult fail(long requestId, ScriptErrorCode code) {
        return new ScriptRpcResult(requestId, code, new byte[0], code.defaultMessageKey(), List.of(), 0, 0);
    }

    /** 限流，带退避。 */
    public static ScriptRpcResult rateLimited(long requestId, long retryAfterMs, String messageKey) {
        return new ScriptRpcResult(requestId, ScriptErrorCode.RATE_LIMITED, new byte[0],
                messageKey, List.of(), retryAfterMs, 0);
    }

    public static void encode(ScriptRpcResult m, FriendlyByteBuf buf) {
        buf.writeVarLong(m.requestId());
        buf.writeVarInt(m.code().toWire());
        Wire.writeBytes(buf, m.data(), ScriptProtocol.DATA_MAX);
        buf.writeUtf(m.messageKey(), ScriptProtocol.KEY_MAX);
        Wire.writeList(buf, m.messageArgs(), ScriptProtocol.ARGS_MAX,
                (s, b) -> b.writeUtf(s, ScriptProtocol.ARG_LEN_MAX));
        buf.writeVarLong(m.retryAfterMs());
        buf.writeVarLong(m.stateRevision());
    }

    public static ScriptRpcResult decode(FriendlyByteBuf buf) {
        long requestId = buf.readVarLong();
        // 认不出的序号归 INTERNAL，不抛：抛在这里是 netty 解码异常 = 当场断线（见 ScriptErrorCode）
        ScriptErrorCode code = ScriptErrorCode.fromWire(buf.readVarInt());
        byte[] data = Wire.readBytes(buf, ScriptProtocol.DATA_MAX);
        String key = buf.readUtf(ScriptProtocol.KEY_MAX);
        List<String> args = Wire.readList(buf, ScriptProtocol.ARGS_MAX,
                b -> b.readUtf(ScriptProtocol.ARG_LEN_MAX));
        return new ScriptRpcResult(requestId, code, data, key, args, buf.readVarLong(), buf.readVarLong());
    }
}
