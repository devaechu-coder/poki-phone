package com.november.mcphone.feature.sns;

import java.util.List;
import java.util.Locale;

/**
 * 욕설·도배 거르기. 띄어쓰기·기호·숫자를 끼워 넣어 피하는 걸 막으려고 한글/영문만 남겨서 본다.
 * 완벽하진 않으니 나머지는 신고 → 운영진 검토로 처리.
 */
public final class SnsFilter {
    private SnsFilter() {}

    private static final List<String> WORDS = List.of(
            "시발", "씨발", "씨빨", "시빨", "씨바", "시바새", "ㅅㅂ", "ㅆㅂ", "십새", "씹새",
            "병신", "븅신", "빙신", "ㅂㅅ", "ㅄ",
            "좆", "존나", "졸라", "ㅈㄴ", "ㅈ같",
            "개새", "개세끼", "개색", "개쌔", "ㅅㄲ",
            "니애미", "니엄마", "느금", "느개비", "ㄴㄱㅁ",
            "지랄", "ㅈㄹ", "미친놈", "미친년", "미친새", "또라이", "닥쳐",
            "엠창", "염병", "육시럴", "썅", "쌍년", "쌍놈", "창녀", "섹스", "야동",
            "fuck", "shit", "bitch", "sibal", "ssibal", "tlqkf", "qudtls");

    /** 걸리면 true */
    public static boolean profane(String text) {
        if (text == null || text.isEmpty()) return false;
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c)) sb.append(Character.toLowerCase(c));
        }
        String s = sb.toString().toLowerCase(Locale.ROOT);
        for (String w : WORDS) if (s.contains(w)) return true;
        return false;
    }

    /** 같은 글자를 잔뜩 반복하는 도배 (예: ㅋㅋㅋ… 40개 넘게, 같은 글자 20연속) */
    public static boolean spammy(String text) {
        if (text == null) return false;
        int run = 1;
        for (int i = 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == text.charAt(i - 1) && !Character.isWhitespace(c)) { if (++run >= 20) return true; }
            else run = 1;
        }
        return false;
    }
}
