package com.november.mcphone.feature.chat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 포키: PokiName 플러그인 한글닉을 폰에서도 보여 준다.
 *
 * 같은 서버 폴더의 plugins/PokiName/nicknames.yml 을 읽기만 한다 (플러그인과 직접 연결 X).
 * 파일이 바뀌었는지는 5초에 한 번만 본다. 파일이 없으면 마크닉 그대로.
 */
public final class PokiNames {
    private PokiNames() {}

    private static final Path FILE = Path.of("plugins", "PokiName", "nicknames.yml");
    private static final long CHECK_MS = 5_000L;
    private static Map<UUID, String> nicks = Map.of();
    private static long lastCheck, lastModified = -1;

    /** 한글닉이 있으면 한글닉, 없으면 fallback */
    public static String display(UUID id, String fallback) {
        if (id == null) return fallback;
        String n = current().get(id);
        return n == null || n.isBlank() ? fallback : n;
    }

    private static synchronized Map<UUID, String> current() {
        long now = System.currentTimeMillis();
        if (now - lastCheck < CHECK_MS) return nicks;
        lastCheck = now;
        try {
            if (!Files.isRegularFile(FILE)) { nicks = Map.of(); lastModified = -1; return nicks; }
            long mod = Files.getLastModifiedTime(FILE).toMillis();
            if (mod == lastModified) return nicks;
            nicks = parse(Files.readAllLines(FILE, StandardCharsets.UTF_8));
            lastModified = mod;
        } catch (Exception e) {
            com.november.mcphone.MCphone.LOGGER.warn("[포키폰] PokiName 한글닉 파일을 못 읽음: {}", e.toString());
        }
        return nicks;
    }

    /** players: / <uuid>: / nick: 값  — PokiName 이 쓰는 모양만 읽는다 */
    static Map<UUID, String> parse(Iterable<String> lines) {
        Map<UUID, String> out = new HashMap<>();
        boolean inPlayers = false;
        UUID current = null;
        for (String raw : lines) {
            if (raw.isBlank() || raw.trim().startsWith("#")) continue;
            int indent = 0;
            while (indent < raw.length() && raw.charAt(indent) == ' ') indent++;
            String line = raw.trim();
            if (indent == 0) { inPlayers = line.startsWith("players:"); current = null; continue; }
            if (!inPlayers) continue;
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String key = unquote(line.substring(0, colon).trim());
            String value = line.substring(colon + 1).trim();
            if (value.isEmpty()) {
                try { current = UUID.fromString(key); } catch (IllegalArgumentException e) { current = null; }
            } else if (current != null && key.equals("nick")) {
                String nick = strip(unquote(value));
                if (!nick.isBlank()) out.put(current, nick.length() > 32 ? nick.substring(0, 32) : nick);
            }
        }
        return out;
    }

    private static String unquote(String v) {
        if (v.length() >= 2 && v.startsWith("'") && v.endsWith("'")) return v.substring(1, v.length() - 1).replace("''", "'");
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            String s = v.substring(1, v.length() - 1);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '\\' && i + 1 < s.length()) {
                    char n = s.charAt(++i);
                    if (n == 'u' && i + 4 < s.length()) {
                        try { sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16)); i += 4; continue; }
                        catch (NumberFormatException ignored) {}
                    }
                    sb.append(switch (n) { case 'n' -> ' '; case 't' -> ' '; default -> n; });
                } else sb.append(c);
            }
            return sb.toString();
        }
        return v;
    }

    /** &a, §a, &#RRGGBB 같은 색 코드 빼기 */
    private static String strip(String s) {
        return s.replaceAll("[&§]#[0-9a-fA-F]{6}", "").replaceAll("[&§][0-9a-fk-orxA-FK-ORX]", "").trim();
    }
}
