package com.alternative_studios.newswipe.suggest;

/**
 * 덜 만들어진 마지막 글자가 앞으로 될 수 있는 글자들의 범위.
 * 완성 음절은 코드값이 (초성, 중성, 종성) 순으로 이어져 있어서 가능한 글자들이 한 구간에 모인다.
 */
final class HangulRange {
    private static final int BASE = 0xAC00, END = 0xD7A3;
    // 호환 자모 자음 → 초성 번호 (ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ)
    private static final int[] INITIAL_OF_COMPAT = new int[0x3164 - 0x3131];

    static {
        java.util.Arrays.fill(INITIAL_OF_COMPAT, -1);
        String initials = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
        for (int i = 0; i < initials.length(); i++) INITIAL_OF_COMPAT[initials.charAt(i) - 0x3131] = i;
    }

    private HangulRange() { }

    /** {처음, 끝}(둘 다 포함). 한글이 아니면 null. */
    static int[] of(char c) {
        if (c >= 0x3131 && c < 0x3164) {
            int l = INITIAL_OF_COMPAT[c - 0x3131];
            if (l < 0) return null;
            int a = BASE + l * 588;
            return new int[]{a, a + 587};
        }
        if (c < BASE || c > END) return null;
        int s = c - BASE;
        int t = s % 28, v = (s / 28) % 21, l = s / 588;
        int vEnd = v;
        if (t == 0) {
            // 받침 없는 글자는 받침이 붙을 수도, 모음이 합쳐질 수도 있다 (ㅗ→ㅘㅙㅚ, ㅜ→ㅝㅞㅟ, ㅡ→ㅢ).
            if (v == 8) vEnd = 11;
            else if (v == 13) vEnd = 16;
            else if (v == 18) vEnd = 19;
            int a = BASE + (l * 21 + v) * 28;
            return new int[]{a, BASE + (l * 21 + vEnd) * 28 + 27};
        }
        int tEnd = t;
        switch (t) {
            case 1: tEnd = 3; break;     // ㄱ → ㄳ
            case 4: tEnd = 6; break;     // ㄴ → ㄵㄶ
            case 8: tEnd = 15; break;    // ㄹ → ㄺㄻㄼㄽㄾㄿㅀ
            case 17: tEnd = 18; break;   // ㅂ → ㅄ
            default: break;
        }
        int a = BASE + (l * 21 + v) * 28;
        return new int[]{a + t, a + tEnd};
    }
}
