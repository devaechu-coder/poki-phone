package com.november.mcphone.core.script.server;

import java.util.UUID;

/**
 * 求值时能看到的玩家信息（施工方案 §15.5：worker 只拿快照，不持有可变世界引用）。
 *
 * <p><b>这是一份拷贝，取的那一刻就开始过时。</b>任何要"现在是不是还成立"的判断
 * 都必须回主线程重查（§15.5 第二条）。
 *
 * <p>字段按 §16.5 的 {@code ctx.player} 定。留空接口等于什么都没定，
 * 而 S13 到货时再补字段就要改已经合并的签名。
 */
public record PlayerSnapshot(UUID uuid, String name, String dimension,
                             String gameMode, long onlineSinceEpochMs) {
}
