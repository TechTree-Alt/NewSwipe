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

    /**
     * 모음을 연속으로 탭해 이중모음을 만드는지 (ㅓ+ㅓ → ㅕ, 설정 '모음 연속 탭', 키보드가 알려 준다). 그러면 '안너'는 ㅓ를 한 번
     * 더 눌러 '안녀'가 되는 중일 수 있다 ({@link #doubledVowel}).
     */
    static volatile boolean doubleTapVowel = true;

    /**
     * 단어 w가 입력 중인 p로 시작할 수 있는지 (마지막 글자가 덜 만들어졌거나, 받침이 다음 글자의 첫소리일 수 있거나, 모음을
     * 한 번 더 탭해 이중모음이 될 수 있는 것까지). w가 p와 똑같아도 true.
     */
    static boolean startsWith(String w, String p) {
        if (p.isEmpty()) return false;
        if (matches(w, p)) return true;
        String m = alternate(p);
        return m != null && matches(w, m);
    }

    /**
     * p의 마지막 글자가 될 수 있는 다른 꼴: 받침을 다음 글자로 넘긴 꼴({@link #movedFinal}, 받침이 있을 때)이나 모음을 이중모음으로
     * 바꾼 꼴({@link #doubledVowel}, 받침이 없을 때). 둘은 함께 생기지 않는다. 없으면 null.
     */
    static String alternate(String p) {
        String m = movedFinal(p);
        return m != null ? m : doubledVowel(p);
    }

    /** 단어 w가 p를 이중모음으로 바꾼 꼴로만 맞는지 ('안너' 다음의 '안녕하세요': 점수를 낮춘다). */
    static boolean onlyDoubled(String w, String p) {
        if (p.isEmpty() || matches(w, p)) return false;
        String d = doubledVowel(p);
        return d != null && matches(w, d);
    }

    /** 단모음 중성 번호 → 연속 탭으로 바뀌는 이중모음 번호 (ㅏ→ㅑ, ㅐ→ㅒ, ㅓ→ㅕ, ㅔ→ㅖ, ㅗ→ㅛ, ㅜ→ㅠ). 없으면 -1. */
    private static final int[] DOUBLED = new int[21];

    static {
        java.util.Arrays.fill(DOUBLED, -1);
        DOUBLED[0] = 2;
        DOUBLED[1] = 3;
        DOUBLED[4] = 6;
        DOUBLED[5] = 7;
        DOUBLED[8] = 12;
        DOUBLED[13] = 17;
    }

    /**
     * 마지막 글자가 받침 없는 단모음(ㅏㅐㅓㅔㅗㅜ)이고 모음 연속 탭을 쓰면, 그 모음을 이중모음으로 바꾼 접두사 ('안너' → '안녀',
     * '고' → '교'). 같은 모음을 한 번 더 누르는 중일 수 있다. 아니면 null.
     */
    static String doubledVowel(String prefix) {
        int n = prefix.length();
        if (n == 0 || !doubleTapVowel) return null;
        char c = prefix.charAt(n - 1);
        if (c < BASE || c > END) return null;
        int s = c - BASE, t = s % 28, v = (s / 28) % 21;
        if (t != 0 || DOUBLED[v] < 0) return null;
        return prefix.substring(0, n - 1) + (char) (c + (DOUBLED[v] - v) * 28);
    }

    private static boolean matches(String w, String p) {
        int n = p.length();
        if (w.length() < n || !w.regionMatches(0, p, 0, n - 1)) return false;
        char last = p.charAt(n - 1), c = w.charAt(n - 1);
        int[] r = of(last);
        if (r != null) return c >= r[0] && c <= r[1];
        return !(last >= 'ㄱ' && last <= 'ㅣ') && c == last;
    }

    /**
     * 입력 중인 p로 시작할 수 있는 단어({@link #startsWith})가 사전 순으로 놓이는 구간들 {처음(포함), 끝(제외)}. 마지막 글자가
     * 될 수 있는 글자들의 구간과, 다른 꼴({@link #alternate})의 구간 (0~2개, 겹칠 수 있다). 정렬된 단어 목록에서 찾을 때 쓴다.
     */
    static java.util.List<String[]> bounds(String p) {
        java.util.List<String[]> out = new java.util.ArrayList<>(2);
        if (p.isEmpty()) return out;
        addBounds(p, out);
        String m = alternate(p);
        if (m != null) addBounds(m, out);
        return out;
    }

    private static void addBounds(String p, java.util.List<String[]> out) {
        int n = p.length();
        char last = p.charAt(n - 1);
        int[] r = of(last);
        if (r == null && last >= 'ㄱ' && last <= 'ㅣ') return;   // 모음만 있거나 맞출 수 없는 자모
        String head = p.substring(0, n - 1);
        out.add(new String[]{head + (char) (r != null ? r[0] : last), head + (char) ((r != null ? r[1] : last) + 1)});
    }

    /** 종성 번호 → 호환 자모 (0번은 받침 없음). */
    private static final String FINAL_COMPAT = "\0ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ";
    /** 겹받침 번호 → {남는 앞 받침 번호, 다음 글자로 넘어갈 자음}. */
    private static final int[][] SPLIT_FINAL = {
            {3, 1, 'ㅅ'}, {5, 4, 'ㅈ'}, {6, 4, 'ㅎ'}, {9, 8, 'ㄱ'}, {10, 8, 'ㅁ'}, {11, 8, 'ㅂ'},
            {12, 8, 'ㅅ'}, {13, 8, 'ㅌ'}, {14, 8, 'ㅍ'}, {15, 8, 'ㅎ'}, {18, 17, 'ㅅ'},
    };

    /**
     * 마지막 글자의 받침이 사실은 다음 글자의 첫소리일 수 있을 때(두벌식은 '하'+ㅅ을 먼저 '핫'으로 보인다),
     * 그 받침을 다음 글자의 첫소리로 넘긴 접두사 ('안녕핫' → '안녕하ㅅ', '닭' → '달ㄱ'). 받침이 없으면 null.
     */
    static String movedFinal(String prefix) {
        int n = prefix.length();
        if (n == 0) return null;
        char c = prefix.charAt(n - 1);
        if (c < BASE || c > END) return null;
        int s = c - BASE, t = s % 28;
        if (t == 0) return null;
        int keep = 0;
        char moved = FINAL_COMPAT.charAt(t);
        for (int[] sp : SPLIT_FINAL) {
            if (sp[0] == t) {
                keep = sp[1];
                moved = (char) sp[2];
                break;
            }
        }
        if (moved >= 0x3131 && moved < 0x3164 && INITIAL_OF_COMPAT[moved - 0x3131] < 0) return null;
        return prefix.substring(0, n - 1) + (char) (c - t + keep) + moved;
    }

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
