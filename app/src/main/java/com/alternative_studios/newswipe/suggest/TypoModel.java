package com.alternative_studios.newswipe.suggest;

/**
 * 한글 오타의 '그럴듯함'을 비용으로 매긴다 (작을수록 흔한 실수). 글자를 호환 자모(ㄱ, ㅏ, ㄺ, ㅘ 등 키 하나로 넣는 단위)로
 * 풀어서, 바꾸기·빠뜨리기·더 치기·맞바꾸기를 가중치를 둔 편집 거리로 잰다.
 * <ul>
 *   <li>같은 키의 다른 동작으로 나오는 글자 (ㄱ↔ㄲ·ㅋ, ㅗ↔ㅛ·ㅘ, ㄹ↔ㄺ)와 소리가 같아 헷갈리는 모음 (ㅐ↔ㅔ, ㅚ↔ㅙ↔ㅞ): 가장 싸다.</li>
 *   <li>자판에서 바로 옆 키: 싸다. 자판 배열은 {@link #setRows}로 받는다 (기본은 8열 단모음).</li>
 *   <li>그 밖의 바꾸기, 빠뜨리기, 더 치기: 비싸다. 같은 키를 한 번 더 친 것은 덜 비싸다.</li>
 * </ul>
 * 화면 스레드에서만 쓴다 (계산용 버퍼를 다시 쓴다).
 */
public final class TypoModel {
    public static final float VARIANT = 0.35f, NEIGHBOR = 0.6f, SUBSTITUTE = 1.1f, CROSS = 1.4f;
    public static final float INSERT = 0.9f, REPEAT = 0.5f, DELETE = 0.9f, TRANSPOSE = 0.7f;
    private static final float INF = 1e9f;

    private static final String CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
    private static final String JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ";
    private static final String JONG = "\0ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ";
    /** 같은 키의 다른 동작(아래·오른쪽으로 밀기, 겹받침·겹모음 밀기)이나 같은 소리로 헷갈리는 짝. */
    private static final String[] VARIANTS = {
            "ㄱㄲㅋ", "ㄷㄸㅌ", "ㅂㅃㅍ", "ㅅㅆ", "ㅈㅉㅊ",
            "ㄱㄳ", "ㄴㄵㄶ", "ㄹㄺㄻㄼㄽㄾㄿㅀ", "ㅂㅄ",
            "ㅏㅑㅘ", "ㅓㅕㅝ", "ㅗㅛㅘㅚㅙ", "ㅜㅠㅝㅟㅞ", "ㅐㅒㅔㅖㅙㅞ", "ㅚㅙㅞㅟ", "ㅡㅢ", "ㅣㅢㅚㅟ",
    };
    /** 한 키에서 (밀기로) 나오는 글자들. 이웃한 키끼리는 이 글자들도 서로 이웃으로 본다 (ㅓ 옆의 ㅗ → ㅕ 옆의 ㅛ). */
    private static final String[] KEY_FAMILY = {
            "ㄱㄲ", "ㄷㄸ", "ㅂㅃ", "ㅅㅆ", "ㅈㅉ", "ㅏㅑ", "ㅓㅕ", "ㅗㅛㅘㅚㅙ", "ㅜㅠㅝㅟㅞ", "ㅐㅒ", "ㅔㅖ", "ㅡㅢ",
    };
    private static final String[][] DEFAULT_ROWS = {
            {"ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅅ", "ㅗ", "ㅐ", "ㅔ"},
            {"ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅎ", "ㅓ", "ㅏ", "ㅣ"},
            {"ㅋ", "ㅌ", "ㅊ", "ㅍ", "ㅜ", "ㅡ"},
    };

    /** 호환 자모 0x3131~0x3163 쌍의 비용 표 (51×51). */
    private final float[] table = new float[51 * 51];
    private float[] r0 = new float[66], r1 = new float[66], r2 = new float[66];

    public TypoModel() {
        setRows(DEFAULT_ROWS);
    }

    /** 자판의 글자 줄 (줄마다 키 글자). 같은 줄의 양옆 키와, 위·아래 줄에서 칸이 하나 안쪽으로 차이 나는 키를 이웃으로 본다. */
    public synchronized void setRows(String[][] rows) {
        int n = 51;
        for (int a = 0; a < n; a++) {
            for (int b = 0; b < n; b++) {
                char x = (char) (0x3131 + a), y = (char) (0x3131 + b);
                table[a * n + b] = a == b ? 0f : isVowel(x) != isVowel(y) ? CROSS : SUBSTITUTE;
            }
        }
        if (rows != null) {
            for (int r = 0; r < rows.length; r++) {
                for (int c = 0; c < rows[r].length; c++) {
                    for (int r2 = Math.max(0, r - 1); r2 <= Math.min(rows.length - 1, r + 1); r2++) {
                        for (int c2 = c - 1; c2 <= c + 1; c2++) {
                            if (c2 < 0 || c2 >= rows[r2].length || (r2 == r && c2 == c)) continue;
                            String fa = family(rows[r][c]), fb = family(rows[r2][c2]);
                            for (int i = 0; i < fa.length(); i++) {
                                for (int j = 0; j < fb.length(); j++) {
                                    set(String.valueOf(fa.charAt(i)), String.valueOf(fb.charAt(j)), NEIGHBOR);
                                }
                            }
                        }
                    }
                }
            }
        }
        for (String g : VARIANTS) {
            for (int i = 0; i < g.length(); i++) {
                for (int j = 0; j < g.length(); j++) {
                    if (i != j) set(String.valueOf(g.charAt(i)), String.valueOf(g.charAt(j)), VARIANT);
                }
            }
        }
    }

    private static String family(String key) {
        if (key == null || key.length() != 1) return "";
        for (String f : KEY_FAMILY) if (f.charAt(0) == key.charAt(0)) return f;
        return key;
    }

    private void set(String a, String b, float cost) {
        if (a == null || b == null || a.length() != 1 || b.length() != 1) return;
        int x = a.charAt(0) - 0x3131, y = b.charAt(0) - 0x3131;
        if (x < 0 || x >= 51 || y < 0 || y >= 51) return;
        table[x * 51 + y] = Math.min(table[x * 51 + y], cost);
        table[y * 51 + x] = Math.min(table[y * 51 + x], cost);
    }

    private static boolean isVowel(char c) {
        return c >= 'ㅏ' && c <= 'ㅣ';
    }

    /** 입력한 글자 a를 b로 바꾸는 비용. */
    public float substitute(char a, char b) {
        if (a == b) return 0f;
        int x = a - 0x3131, y = b - 0x3131;
        if (x < 0 || x >= 51 || y < 0 || y >= 51) return CROSS;
        return table[x * 51 + y];
    }

    /** 글자를 키 하나로 넣는 단위(호환 자모)로 푼다. 한글 음절이 아닌 글자는 그대로 둔다. */
    public static char[] letters(String s) {
        char[] buf = new char[s.length() * 3];
        int n = letters(s.toCharArray(), 0, s.length(), buf);
        return java.util.Arrays.copyOf(buf, n);
    }

    /** src[from..to)를 out에 풀어 쓰고 길이를 돌려준다. out은 (to-from)*3칸 이상. */
    public static int letters(char[] src, int from, int to, char[] out) {
        int n = 0;
        for (int i = from; i < to; i++) {
            char c = src[i];
            if (c >= 0xAC00 && c <= 0xD7A3) {
                int s = c - 0xAC00;
                out[n++] = CHO.charAt(s / 588);
                out[n++] = JUNG.charAt((s / 28) % 21);
                if (s % 28 != 0) out[n++] = JONG.charAt(s % 28);
            } else {
                out[n++] = c;
            }
        }
        return n;
    }

    /** 입력 p 전체를 단어 t 전체로 고치는 비용. limit을 넘을 것이 확실하면 그보다 큰 값을 돌려준다. */
    public float distance(char[] p, int pn, char[] t, int tn, float limit) {
        return align(p, pn, t, tn, limit, false);
    }

    /** 입력 p 전체를 단어 t의 어떤 앞부분으로 고치는 가장 작은 비용 (아직 치는 중인 단어용). */
    public float prefixDistance(char[] p, int pn, char[] t, int tn, float limit) {
        return align(p, pn, t, tn, limit, true);
    }

    private static final int BAND = 3;

    private synchronized float align(char[] p, int pn, char[] t, int tn, float limit, boolean prefix) {
        if (!prefix && Math.abs(pn - tn) > BAND) return INF;
        if (tn + 1 > r0.length) {
            r0 = new float[tn + 1];
            r1 = new float[tn + 1];
            r2 = new float[tn + 1];
        }
        float[] prev2 = r2, prev = r1, cur = r0;   // 행 i-2, i-1, i
        for (int j = 0; j <= tn; j++) prev[j] = j <= BAND ? j * DELETE : INF;
        float best = prefix && pn == 0 ? 0f : INF;
        for (int i = 1; i <= pn; i++) {
            char pc = p[i - 1];
            float ins = i > 1 && p[i - 2] == pc ? REPEAT : INSERT;
            float rowMin = INF;
            int jFrom = Math.max(0, i - BAND), jTo = Math.min(tn, i + BAND);
            for (int j = 0; j < jFrom; j++) cur[j] = INF;
            for (int j = jFrom; j <= jTo; j++) {
                float v = prev[j] + ins;   // 입력에 글자가 더 들어갔다
                if (j > 0) {
                    v = Math.min(v, cur[j - 1] + DELETE);   // 입력에서 글자가 빠졌다
                    v = Math.min(v, prev[j - 1] + substitute(pc, t[j - 1]));
                    if (i > 1 && j > 1 && pc == t[j - 2] && p[i - 2] == t[j - 1] && pc != p[i - 2]) {
                        v = Math.min(v, prev2[j - 2] + TRANSPOSE);
                    }
                }
                cur[j] = v;
                if (v < rowMin) rowMin = v;
            }
            for (int j = jTo + 1; j <= tn; j++) cur[j] = INF;
            if (rowMin > limit) return INF;
            if (i == pn) {
                if (prefix) {
                    for (int j = jFrom; j <= jTo; j++) best = Math.min(best, cur[j]);
                } else {
                    best = cur[tn];
                }
            }
            float[] tmp = prev2;
            prev2 = prev;
            prev = cur;
            cur = tmp;
        }
        return best;
    }
}
