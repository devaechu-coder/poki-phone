package com.november.mcphone.feature.chat;

/**
 * 포키냥 이모티콘. 메시지에는 "[[포키냥:12]]" 같은 글자만 오가고, 그림은 폰 모드 안에 들어 있다
 * (assets/mcphone/textures/poki_sticker/N.png). 서버 저장·업로드 없음.
 * 1~8 글자 있는 것, 9~17 그림만, 18~32 코블몬×포키냥 (글자 있음).
 * 번호는 절대 바꾸지 않는다 (예전 메시지가 그 번호로 저장돼 있음). 그림만 바꾸는 건 괜찮음.
 */
public final class PokiSticker {
    private PokiSticker() {}
    public static final int COUNT = 32;
    private static final String HEAD = "[[포키냥:", TAIL = "]]";

    public static String token(int n) { return HEAD + n + TAIL; }

    /** 이모티콘 글이면 번호(1~COUNT), 아니면 -1 */
    public static int parse(String text) {
        if (text == null || !text.startsWith(HEAD) || !text.endsWith(TAIL)) return -1;
        try {
            int n = Integer.parseInt(text.substring(HEAD.length(), text.length() - TAIL.length()));
            return n >= 1 && n <= COUNT ? n : -1;
        } catch (NumberFormatException e) { return -1; }
    }
}
