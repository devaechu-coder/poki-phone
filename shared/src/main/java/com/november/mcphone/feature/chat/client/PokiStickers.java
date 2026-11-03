package com.november.mcphone.feature.chat.client;

import com.november.mcphone.feature.chat.PokiSticker;

import java.io.InputStream;
import java.util.UUID;

/** 포키냥 이모티콘 그림: 모드 안의 PNG 를 채팅 사진 캐시에 고정 id 로 넣어 두고 같은 방식으로 그린다. */
public final class PokiStickers {
    private PokiStickers() {}
    private static final long HI = 0x506F6B694E79616EL; // "PokiNyan"
    private static final byte[][] PNG = new byte[PokiSticker.COUNT + 1][];

    public static UUID id(int n) { return new UUID(HI, n); }

    public static boolean isSticker(UUID id) {
        return id != null && id.getMostSignificantBits() == HI
                && id.getLeastSignificantBits() >= 1 && id.getLeastSignificantBits() <= PokiSticker.COUNT;
    }

    /** 그림 바이트. 못 읽으면 null */
    public static synchronized byte[] png(UUID id) {
        if (!isSticker(id)) return null;
        int n = (int) id.getLeastSignificantBits();
        if (PNG[n] == null) {
            try (InputStream in = PokiStickers.class.getResourceAsStream("/assets/mcphone/textures/poki_sticker/" + n + ".png")) {
                if (in != null) PNG[n] = in.readAllBytes();
            } catch (Exception e) {
                com.november.mcphone.MCphone.LOGGER.warn("[포키폰] 이모티콘 {} 을 못 읽음: {}", n, e.toString());
            }
        }
        return PNG[n];
    }
}
