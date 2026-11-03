package com.november.mcphone.feature.chat.profile;

import com.november.mcphone.util.TextSanitizer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 服务端个人介绍账本；修改者由已认证连接确定，不采用请求中的目标身份。 */
public final class ProfileBook {
    public static final int MAX_INTRO = 160, MAX_PROFILES = 65536;
    private final Map<UUID, String> introductions = new HashMap<>();
    public String get(UUID id) { return introductions.getOrDefault(id, ""); }
    public boolean update(UUID actor, UUID target, String text) {
        if (!actor.equals(target)) throw new SecurityException("只能修改自己的个人介绍");
        if (text == null || text.length() > MAX_INTRO) throw new IllegalArgumentException("个人介绍过长");
        String clean = TextSanitizer.sanitize(text, MAX_INTRO).strip();
        if (get(actor).equals(clean)) return false;
        if (clean.isEmpty()) introductions.remove(actor);
        else {
            if (!introductions.containsKey(actor) && introductions.size() >= MAX_PROFILES)
                throw new IllegalStateException("资料容量已满");
            introductions.put(actor, clean);
        }
        return true;
    }
    public Map<UUID, String> snapshot() { return Map.copyOf(introductions); }
    public static ProfileBook restore(Map<UUID, String> values) {
        if (values.size() > MAX_PROFILES) throw new IllegalArgumentException("资料容量超限");
        ProfileBook result = new ProfileBook();
        values.forEach((id, text) -> result.update(id, id, text));
        return result;
    }
}
