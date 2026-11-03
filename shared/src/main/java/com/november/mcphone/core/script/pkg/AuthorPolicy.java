package com.november.mcphone.core.script.pkg;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 服务端的作者名单（施工方案 §12.7）。来自 {@code mcphone-server.toml} 的 {@code [authors]}。
 *
 * <pre>
 * [authors]
 * blocked = ["9579-ce63-18c2-0b3d"]
 * allowed = []            # 非空 = 白名单模式
 * </pre>
 *
 * <h2>allowed 非空就是白名单模式</h2>
 *
 * 空表不是"谁都不许"，是"不设白名单"。反过来写会让服主第一次配置时把自己的服务器锁死。
 *
 * <h2>它管不了玩家本地装的纯前端 App</h2>
 *
 * 那是玩家自己的机器，而 {@code deploy:"client"} 的包本来就没有特权（§13）。
 * 服务端这张名单管的是<b>要在这台服务器上部署后端</b>的那些。
 */
public final class AuthorPolicy {

    private final Set<String> blocked = new LinkedHashSet<>();
    private final Set<String> allowed = new LinkedHashSet<>();

    public AuthorPolicy block(String fingerprint) {
        blocked.add(fingerprint);
        return this;
    }

    public AuthorPolicy allow(String fingerprint) {
        allowed.add(fingerprint);
        return this;
    }

    /** 在不在白名单模式。 */
    public boolean whitelistMode() {
        return !allowed.isEmpty();
    }

    /** 这个作者的包能不能在这台服务器上部署。 */
    public boolean permits(String fingerprint) {
        if (fingerprint == null) return !whitelistMode();       // 未签名的包在白名单模式下过不去
        if (blocked.contains(fingerprint)) return false;
        return !whitelistMode() || allowed.contains(fingerprint);
    }

    /** 拒绝时给服主与玩家看的本地化键。<b>不是文本</b>。 */
    public String denyKey(String fingerprint) {
        if (fingerprint != null && blocked.contains(fingerprint)) return "mcphone.sig.server_blocked";
        return "mcphone.sig.server_not_allowed";
    }

    public Set<String> blockedSet() {
        return Set.copyOf(blocked);
    }

    public Set<String> allowedSet() {
        return Set.copyOf(allowed);
    }
}
