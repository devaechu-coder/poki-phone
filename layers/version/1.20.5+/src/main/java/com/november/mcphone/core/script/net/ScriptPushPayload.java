package com.november.mcphone.core.script.net;

import com.november.mcphone.MCphone;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * {@code script_push} 的 1.20.5+ 包装（S2C）。
 *
 * <p><b>它只持有 {@link ScriptPush}，不复制它的字段。</b>
 * 1.20.1 那一支的登记门面收的是 {@code (Class, encoder, decoder, handler)}，
 * 可以直接用那个 record；只有这一支要 {@code CustomPacketPayload}，而那个接口
 * 命中 shared 的判据表，进不了 shared。把"要实现接口的这三行"单独拿出来，
 * {@code encode/decode} 就不必像 {@code PurchaseAppPacket} 那样复制两份。
 *
 * <p>{@code STREAM_CODEC} 声明成 {@code StreamCodec<FriendlyByteBuf, …>} 而不是
 * {@code RegistryFriendlyByteBuf}：后者是前者的子类，喂给门面那个
 * {@code ? super RegistryFriendlyByteBuf} 的参数是合法的（{@code PurchaseAppPacket} 已经这么用了）。
 * 代价是编解码<b>拿不到 registryAccess()</b> —— 所以线上不许出现 ItemStack / Component / Holder。
 */
public record ScriptPushPayload(ScriptPush msg) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ScriptPushPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MCphone.MODID, "script_push"));

    public static final StreamCodec<FriendlyByteBuf, ScriptPushPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> ScriptPush.encode(p.msg(), buf),
                    buf -> new ScriptPushPayload(ScriptPush.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
