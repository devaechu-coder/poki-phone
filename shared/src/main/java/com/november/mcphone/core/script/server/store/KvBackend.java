package com.november.mcphone.core.script.server.store;

/**
 * {@code ctx.store.*} 的后端（施工方案 §16.5、§17.3）：<b>每玩家的 KV</b>。
 *
 * <p>服务端侧落在 {@code PhonePlayerData.scriptKv()}（shared 档），
 * 客户端侧落在 {@link LocalStore}（local 档）。<b>脚本看到的方法名两边一样</b> ——
 * 档位是"这段脚本跑在哪一侧"决定的，不是方法名里带的（勘误 E17 的口径）。
 *
 * <p>实现要在写入时做配额（{@link StoreQuota}），超了<b>显式抛</b>，不静默截断。
 *
 * <p><b>守卫计数不走这里</b>：那是 {@link ScriptGuards}，脚本写不到（§19.2、§20.2）。
 */
public interface KvBackend {

    /** 没有返回 null。 */
    String getString(String appId, String key);

    /** 超配额抛 {@link StoreQuota.QuotaExceeded}。 */
    void setString(String appId, String key, String value);

    void remove(String appId, String key);

    /** 这个 App 有哪些 key。条数受 {@link StoreQuota} 限，不会很大。 */
    java.util.List<String> keys(String appId);
}
