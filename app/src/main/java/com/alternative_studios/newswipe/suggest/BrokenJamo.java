package com.alternative_studios.newswipe.suggest;

import java.util.ArrayList;
import java.util.List;

/**
 * 겹모음·겹받침의 한쪽을 옆 키로 잘못 쳐서 글자가 깨진 말을 되살린다. ㄱㅗ 다음에 ㅐ 대신 ㅔ를 치면 ㅗ+ㅔ는 겹모음이 아니라
 * 조합기가 '고'를 끝내고 ㅔ를 따로 둔다. 그 뒤의 받침도 붙을 곳이 없어 따로 남아 '괜찮아요'가 '고ㅔㄴ찮아요'가 된다
 * (관심 → 고ㅓㄴ심·고ㅡㄴ심). 이런 말은 사전 단어와 자모 배열이 달라 추천·자동 수정이 찾지 못한다.
 *
 * <p>받침 없는 글자 바로 뒤에 모음 자모가 하나 따로 있으면, 두 모음을 자판에서 키 하나쯤 떨어진 겹모음(ㅘ ㅙ ㅚ ㅝ ㅞ ㅟ ㅢ)으로
 * 바꿔 보고, 따로 남은 받침 자음을 그 글자에 다시 붙인다 (고ㅔㄴ찮아요 → 괜찮아요). 단모음 자판에서 모음 연속 탭을 쓰면 같은 모음을
 * 두 번 쳐 만드는 이중모음(ㅑ ㅕ ㅛ ㅠ ㅒ ㅖ)도 본다 (안너ㅜㅇ하세요 → 안녕하세요). 비용은 {@link TypoModel}의 바꾸기 비용
 * (두 모음 중 어느 쪽이 틀려도 된다)이고 합이 옆 키 하나({@link TypoModel#NEIGHBOR}) 이하인 것만 쓴다.
 *
 * <p>겹받침도 같다: ㅇㅓ 다음에 ㅂ 대신 옆 키 ㅈ을 치면 ㅈ+ㅅ은 겹받침이 아니라 ㅅ이 따로 남아 '없이'가 '엊ㅅ이'가 된다.
 * 받침 있는 글자 바로 뒤에 자음 자모가 하나 따로 있으면, 두 자음을 키 하나쯤 떨어진 겹받침(ㄳ ㄵ ㄶ ㄺ ㄻ ㄼ ㄽ ㄾ ㄿ ㅀ ㅄ)으로
 * 바꿔 본다 (엊ㅅ이 → 없이, 업ㄱ이 → 없이).
 *
 * <p>같은 모음을 되풀이한 말(우ㅜㅜ, 오ㅗ)과 모음 자모가 둘 이상 이어진 말(아ㅏㅏ)은 일부러 친 것으로 보고 건드리지 않는다.
 * 자음도 받침과 같은 자음(만ㄴ), 자음 자모가 둘 이상 이어진 말(헐ㅋㅋ), ㅋ(헐ㅋ: 웃음)은 건드리지 않는다.
 */
final class BrokenJamo {
    private BrokenJamo() {
    }

    private static final String JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ";
    private static final String JONG = "\0ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ";
    /** 조합기가 두 모음을 이어 만드는 겹모음 {앞 모음, 뒤 모음, 겹모음} (HangulComposer와 같다). */
    private static final String[] COMBOS = {
            "ㅗㅏㅘ", "ㅗㅐㅙ", "ㅗㅣㅚ", "ㅜㅓㅝ", "ㅜㅔㅞ", "ㅜㅣㅟ", "ㅡㅣㅢ", "ㅘㅣㅙ", "ㅝㅣㅞ",
    };
    /**
     * 같은 모음을 두 번 탭해 만드는 이중모음 {앞, 뒤, 이중모음} (단모음 자판의 '모음 연속 탭', HangulComposer와 같다). 둘째 탭이
     * 옆 키로 빠지면 (ㅓ 다음에 ㅜ) 겹모음이 되지 않아 글자가 깨진다: 안녕하세요 → 안너ㅜㅇ하세요.
     */
    private static final String[] DOUBLED = {"ㅏㅏㅑ", "ㅓㅓㅕ", "ㅗㅗㅛ", "ㅜㅜㅠ", "ㅐㅐㅒ", "ㅔㅔㅖ"};
    private static final String[] COMBOS_DOUBLED;

    static {
        COMBOS_DOUBLED = java.util.Arrays.copyOf(COMBOS, COMBOS.length + DOUBLED.length);
        System.arraycopy(DOUBLED, 0, COMBOS_DOUBLED, COMBOS.length, DOUBLED.length);
    }

    /** 받침 둘이 이어 만드는 겹받침 {앞, 뒤, 겹받침}. */
    private static final String[] FINALS = {
            "ㄱㅅㄳ", "ㄴㅈㄵ", "ㄴㅎㄶ", "ㄹㄱㄺ", "ㄹㅁㄻ", "ㄹㅂㄼ", "ㄹㅅㄽ", "ㄹㅌㄾ", "ㄹㅍㄿ", "ㄹㅎㅀ", "ㅂㅅㅄ",
    };
    /** 대각선 키로만 설명되는 바꾸기에 더하는 비용 (바로 옆 키를 먼저 본다). */
    private static final float DIAGONAL = 0.1f;
    /** 받아들이는 비용의 상한: 옆 키 하나 (대각선 키면 그만큼 더). */
    static final float MAX_COST = TypoModel.NEIGHBOR + DIAGONAL + 1e-4f;
    /** 되살린 말을 이만큼까지 돌려준다. */
    private static final int MAX_REPAIRS = 4;

    /** 되살린 말과 그 비용. */
    static final class Repair {
        final String word;
        final float cost;

        Repair(String word, float cost) {
            this.word = word;
            this.cost = cost;
        }

        @Override
        public String toString() {
            return word + "(" + cost + ")";
        }
    }

    private static boolean isVowelJamo(char c) {
        return c >= 'ㅏ' && c <= 'ㅣ';
    }

    private static boolean isConsonantJamo(char c) {
        return c >= 'ㄱ' && c <= 'ㅎ';
    }

    /** word에 겹모음·겹받침이 깨진 자리가 있는지 (값싸게 먼저 본다). */
    static boolean has(String word) {
        int n = word.length();
        for (int i = 0; i + 1 < n; i++) if (spot(word, i) != NONE) return true;
        return false;
    }

    private static final int NONE = 0, VOWEL = 1, FINAL = 2;

    /**
     * i번째 글자가 깨진 자리인지: 받침 없는 음절 뒤에 모음 자모가 하나만 따로 있으면 VOWEL, 홑받침 음절 뒤에 자음 자모가 하나만
     * 따로 있으면 FINAL, 아니면 NONE.
     */
    private static int spot(String w, int i) {
        char c = w.charAt(i);
        if (c < 0xAC00 || c > 0xD7A3) return NONE;
        int s = c - 0xAC00, jong = s % 28;
        char x = w.charAt(i + 1);
        char after = i + 2 < w.length() ? w.charAt(i + 2) : 0;
        if (jong == 0) {
            if (!isVowelJamo(x)) return NONE;
            if (isVowelJamo(after)) return NONE;                                      // 아ㅏㅏ: 일부러 늘인 말
            return JUNG.charAt((s / 28) % 21) != x ? VOWEL : NONE;                    // 우ㅜ·오ㅗ: 같은 모음 되풀이
        }
        if (!isConsonantJamo(x) || x == 'ㅋ' || singleFinal(jong) == 0) return NONE;   // 헐ㅋ: 웃음
        if (isConsonantJamo(after) || isVowelJamo(after)) return NONE;               // 헐ㅋㅋ·ㅎㅎ
        return JONG.charAt(jong) != x ? FINAL : NONE;                                 // 만ㄴ: 같은 자음 되풀이
    }

    /** 받침 번호 jong이 홑받침이면 그 자음, 겹받침이면 0. */
    private static char singleFinal(int jong) {
        char f = JONG.charAt(jong);
        for (String p : FINALS) if (p.charAt(2) == f) return 0;
        return f;
    }

    /**
     * word의 깨진 겹모음·겹받침을 되살린 말들 (비용이 싼 순서, 많아야 {@link #MAX_REPAIRS}개). 깨진 자리가 없거나 키 하나쯤으로
     * 설명되지 않으면 빈 목록.
     */
    static List<Repair> repairs(String word, TypoModel typo) {
        List<Repair> out = new ArrayList<>();
        if (!has(word)) return out;
        expand(word, 0, new StringBuilder(), 0f, typo, out);
        out.sort((a, b) -> Float.compare(a.cost, b.cost));
        // 같은 말이 다른 길로 나오면 싼 것만 남긴다
        List<Repair> uniq = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Repair r : out) {
            if (seen.add(r.word)) uniq.add(r);
            if (uniq.size() >= MAX_REPAIRS) break;
        }
        return uniq;
    }

    /** word[from..]를 sb 뒤에 이어 붙이며 깨진 자리마다 고칠 수 있는 겹모음·겹받침을 모두 펼친다 (비용 합이 MAX_COST 이하인 것만). */
    private static void expand(String w, int from, StringBuilder sb, float cost, TypoModel typo, List<Repair> out) {
        int n = w.length();
        int i = from;
        int kind = NONE;
        while (i + 1 < n && (kind = spot(w, i)) == NONE) i++;
        if (i + 1 >= n) {
            if (cost > 0f) out.add(new Repair(sb + w.substring(from), cost));
            return;
        }
        int keep = sb.length();
        sb.append(w, from, i);
        int s = w.charAt(i) - 0xAC00;
        if (kind == FINAL) {
            // 홑받침 + 따로 남은 자음 → 겹받침
            char a = JONG.charAt(s % 28), b = w.charAt(i + 1);
            int base = s - s % 28;
            boolean any = false;
            for (String f : FINALS) {
                float c = cost(typo, a, f.charAt(0)) + cost(typo, b, f.charAt(1));
                if (cost + c > MAX_COST) continue;
                sb.append((char) (0xAC00 + base + JONG.indexOf(f.charAt(2))));
                expand(w, i + 2, sb, cost + c, typo, out);
                sb.setLength(sb.length() - 1);
                any = true;
            }
            if (!any) {
                sb.append(w, i, i + 2);
                expand(w, i + 2, sb, cost, typo, out);
            }
            sb.setLength(keep);
            return;
        }
        int cho = s / 588;
        char v1 = JUNG.charAt((s / 28) % 21), v2 = w.charAt(i + 1);
        // 따로 남은 받침 (하나, 또는 겹받침이 되는 둘)
        int j = i + 2;
        int jong = 0, used = 0;
        if (j < n && isConsonantJamo(w.charAt(j)) && JONG.indexOf(w.charAt(j)) > 0) {
            char a = w.charAt(j);
            jong = JONG.indexOf(a);
            used = 1;
            if (j + 1 < n && isConsonantJamo(w.charAt(j + 1))) {
                for (String f : FINALS) {
                    if (f.charAt(0) == a && f.charAt(1) == w.charAt(j + 1)) {
                        jong = JONG.indexOf(f.charAt(2));
                        used = 2;
                    }
                }
            }
        }
        boolean any = false;
        // 모음 연속 탭은 ㅑ·ㅕ 키가 따로 없는 자판(단모음)에서 켰을 때만 본다 (두벌식은 ㅕ를 제 키로 친다)
        String[] combos = HangulRange.doubleTapVowel && !typo.iotizedKeys() ? COMBOS_DOUBLED : COMBOS;
        for (String combo : combos) {
            float c = cost(typo, v1, combo.charAt(0)) + cost(typo, v2, combo.charAt(1));
            if (cost + c > MAX_COST) continue;
            int jung = JUNG.indexOf(combo.charAt(2));
            sb.append((char) (0xAC00 + cho * 588 + jung * 28 + jong));
            expand(w, j + used, sb, cost + c, typo, out);
            sb.setLength(sb.length() - 1);
            any = true;
        }
        if (!any) {
            // 이 자리는 고칠 수 없다: 그대로 두고 뒤에서 더 찾는다
            sb.append(w, i, i + 2);
            expand(w, i + 2, sb, cost, typo, out);
        }
        sb.setLength(keep);
    }

    /** 친 자모 typed를 want로 보는 비용 (바로 옆 키가 아닌 대각선 키는 조금 더). */
    private static float cost(TypoModel typo, char typed, char want) {
        if (typed == want) return 0f;
        float d = typo.substituteDirect(typed, want);
        if (d <= TypoModel.NEIGHBOR + 1e-4f) return d;
        float t = typo.substitute(typed, want);
        return t <= TypoModel.NEIGHBOR + 1e-4f ? t + DIAGONAL : d;
    }
}
