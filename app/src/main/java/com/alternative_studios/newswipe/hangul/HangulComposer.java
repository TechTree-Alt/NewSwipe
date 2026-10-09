package com.alternative_studios.newswipe.hangul;

/**
 * 두벌식 한글 조합기 (단모음 자판용).
 *
 * <p>입력은 자모 단위로 들어온다. 쌍자음(ㄲㄸㅃㅆㅉ)과 이중모음(ㅑㅕㅛㅠㅒㅖ)은 키보드에서
 * 아래로 스와이프해 직접 들어오므로, 자음을 두 번 눌렀다고 쌍자음으로 바꾸지 않는다.
 * 그래서 "학교"를 빠르게 쳐도 "하꾜"가 되지 않는다.
 *
 * <p>겹받침(ㄳ ㄵ ㄶ ㄺ ㄻ ㄼ ㄽ ㄾ ㄿ ㅀ ㅄ)도 키보드에서 스와이프로 한 번에 들어온다.
 * 받침 자리에 들어가며(받침이 될 수 없는 자리에서는 겹받침 글자 그대로 입력되며), 백스페이스는 겹받침 전체를 한 번에 지운다.
 * 겹모음(ㅘ ㅙ ㅝ ㅞ ㅟ ㅢ)도 스와이프로 한 번에 들어오지만, 두 모음을 차례로 친 것과 같게 조합해서
 * 백스페이스는 한 모음씩 지운다.
 *
 * <p>모음은 같은 키를 연속으로 탭하면(ㅏ+ㅏ) 이중모음(ㅑ)으로 바꿀 수 있다.
 * 모음끼리는 이런 연속 입력이 다른 글자와 충돌하지 않기 때문이다.
 *
 * <p>자음 연속 탭(ㄱ+ㄱ → ㄲ)은 설정에서 켰을 때만 쓴다. 이때는 두 탭 사이가 정해진 시간보다
 * 짧을 때만 쌍자음으로 보아, 보통 속도로 친 "학교"(ㄱ 받침 + ㄱ 초성)는 그대로 두 자음이 된다.
 *
 * <p>안드로이드 의존성이 없는 순수 자바 클래스라 단위 테스트로 검증한다.
 */
public final class HangulComposer {

    static final String CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
    static final String JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ";
    /** 종성 표. 0번은 받침 없음. */
    static final String JONG = "\0ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ";

    /** 겹모음: 앞 모음, 뒤 모음, 결과. */
    private static final String[] VOWEL_PAIRS = {
            "ㅗㅏㅘ", "ㅗㅐㅙ", "ㅗㅣㅚ", "ㅜㅓㅝ", "ㅜㅔㅞ", "ㅜㅣㅟ", "ㅡㅣㅢ",
            "ㅘㅣㅙ", "ㅝㅣㅞ",
    };
    /** 겹받침: 앞 자음, 뒤 자음, 결과. */
    private static final String[] FINAL_PAIRS = {
            "ㄱㅅㄳ", "ㄴㅈㄵ", "ㄴㅎㄶ", "ㄹㄱㄺ", "ㄹㅁㄻ", "ㄹㅂㄼ",
            "ㄹㅅㄽ", "ㄹㅌㄾ", "ㄹㅍㄿ", "ㄹㅎㅀ", "ㅂㅅㅄ",
    };
    /** 단모음 → 연속 탭/스와이프 시 이중모음. */
    private static final String DOUBLE_VOWEL_FROM = "ㅏㅓㅗㅜㅐㅔ";
    private static final String DOUBLE_VOWEL_TO = "ㅑㅕㅛㅠㅒㅖ";
    /** 자음 → 스와이프 시 쌍자음. */
    private static final String DOUBLE_CONSONANT_FROM = "ㄱㄷㅂㅅㅈ";
    private static final String DOUBLE_CONSONANT_TO = "ㄲㄸㅃㅆㅉ";

    private char cho, jung, jong;
    /** 현재 음절 안에서의 이전 상태들 (백스페이스로 한 자모씩 되돌리기). */
    private long[] history = new long[8];
    private int historySize;
    private final StringBuilder commit = new StringBuilder();
    /** 직전 입력이 '탭'으로 넣은 모음이면 그 모음, 아니면 0. */
    private char lastTapVowel;
    private boolean doubleTapVowel = true;
    /** 직전 입력이 '탭'으로 넣은 홑자음이면 그 자음, 아니면 0. */
    private char lastTapConsonant;
    private long lastTapConsonantTime;
    private boolean doubleTapConsonant;
    private int doubleTapConsonantMs = 200;

    public void setDoubleTapVowel(boolean enabled) {
        doubleTapVowel = enabled;
    }

    /**
     * 같은 자음을 빠르게 두 번 탭하면 쌍자음(ㄱㄱ → ㄲ)으로 바꾼다.
     *
     * @param windowMs 두 탭 사이가 이 시간(ms) 이하일 때만 쌍자음으로 본다.
     */
    public void setDoubleTapConsonant(boolean enabled, int windowMs) {
        doubleTapConsonant = enabled;
        doubleTapConsonantMs = windowMs;
        lastTapConsonant = 0;
    }

    public static boolean isJamo(char c) {
        return isConsonant(c) || isVowel(c);
    }

    public static boolean isConsonant(char c) {
        return c >= 'ㄱ' && c <= 'ㅎ';
    }

    public static boolean isVowel(char c) {
        return c >= 'ㅏ' && c <= 'ㅣ';
    }

    /** 겹받침 하나로 입력되는 자모(ㄳ, ㄺ …)인지. */
    public static boolean isCompoundFinal(char c) {
        return splitFinal(c) != 0;
    }

    /** {@code first}로 시작하는 겹받침들 (예: ㄹ → "ㄺㄻㄼㄽㄾㄿㅀ"). 없으면 빈 문자열. */
    public static String compoundFinalsStartingWith(char first) {
        StringBuilder sb = new StringBuilder();
        for (String t : FINAL_PAIRS) {
            if (t.charAt(0) == first) sb.append(t.charAt(2));
        }
        return sb.toString();
    }

    /** 겹받침의 앞 자음 (ㄺ → ㄹ). 겹받침이 아니면 0. */
    public static char compoundFirst(char f) {
        int split = splitFinal(f);
        return split == 0 ? 0 : (char) (split >> 16);
    }

    /** 스와이프(또는 Shift)했을 때 들어갈 자모. 없으면 0. */
    public static char doubled(char c) {
        int i = DOUBLE_CONSONANT_FROM.indexOf(c);
        if (i >= 0) return DOUBLE_CONSONANT_TO.charAt(i);
        i = DOUBLE_VOWEL_FROM.indexOf(c);
        if (i >= 0) return DOUBLE_VOWEL_TO.charAt(i);
        return 0;
    }

    /**
     * 자모 하나를 입력한다.
     *
     * @param tap 키를 그냥 탭했으면 true. 스와이프·Shift·길게 누르기면 false.
     */
    public void input(char c, boolean tap) {
        input(c, tap, -1);
    }

    /**
     * 자모 하나를 입력한다.
     *
     * @param tap    키를 그냥 탭했으면 true. 스와이프·Shift·길게 누르기면 false.
     * @param timeMs 탭한 시각(ms, 단조 증가하는 시계). 자음 연속 탭 판단에 쓴다. 모르면 -1.
     */
    public void input(char c, boolean tap, long timeMs) {
        if (!isConsonant(c)) lastTapConsonant = 0;
        if (isVowel(c)) {
            if (tap && doubleTapVowel && c == lastTapVowel && jung == c && jong == 0
                    && doubled(c) != 0 && historySize > 0) {
                // 같은 모음을 연속 탭: 직전 모음을 되돌리고 이중모음을 넣는다.
                restore(pop());
                lastTapVowel = 0;
                inputVowel(doubled(c));
                return;
            }
            String parts = splitVowel(c);
            if (parts != null) {
                // 밀어서 들어온 겹모음(ㅘ ㅙ ㅝ ㅞ ㅟ ㅢ)은 앞 모음 → 뒤 모음 순으로 친 것처럼 넣는다.
                // 그래야 백스페이스가 겹받침과 달리 한 모음씩 지운다 (ㅝ → ㅜ → 초성).
                inputVowel(parts.charAt(0));
                inputVowel(parts.charAt(1));
            } else {
                inputVowel(c);
            }
            lastTapVowel = tap ? c : 0;
        } else if (isConsonant(c)) {
            lastTapVowel = 0;
            if (tap && doubleTapConsonant && timeMs >= 0 && c == lastTapConsonant
                    && timeMs - lastTapConsonantTime <= doubleTapConsonantMs
                    && doubled(c) != 0 && historySize > 0) {
                // 같은 자음을 빠르게 두 번 탭: 직전 자음을 되돌리고 쌍자음을 넣는다.
                // 직전 자음이 받침이었으면 쌍자음도 받침(ㄲ ㅆ)으로, 받침이 될 수 없으면(ㄸ ㅃ ㅉ) 다음 글자 초성으로 간다.
                restore(pop());
                lastTapConsonant = 0;
                inputConsonant(doubled(c));
                return;
            }
            inputConsonant(c);
            lastTapConsonant = tap && !isCompoundFinal(c) ? c : 0;
            lastTapConsonantTime = timeMs;
        } else {
            flush();
            commit.append(c);
            lastTapVowel = 0;
        }
    }

    /**
     * 겹받침(ㄳ, ㄺ …)을 한 번에 입력한다. 받침 자리에 넣을 수 있으면 받침으로 넣고,
     * 아니면(받침이 이미 있거나 앞 글자가 없음) 조합 중인 글자를 확정하고 겹받침 모양 그대로 넣는다.
     */
    private void inputCompoundFinal(char c) {
        if (cho != 0 && jung != 0 && jong == 0) {
            push();
            jong = c;
            return;
        }
        flush();
        commit.append(c);
    }

    private void inputConsonant(char c) {
        if (isCompoundFinal(c)) {
            inputCompoundFinal(c);
            return;
        }
        if (cho == 0 && jung == 0) {            // 빈 상태
            push();
            cho = c;
        } else if (jung == 0) {                 // 초성만 있음 → 새 글자
            startNew(c, (char) 0);
        } else if (cho == 0) {                  // 모음만 있음 → 새 글자
            startNew(c, (char) 0);
        } else if (jong == 0) {                 // 초성+중성 → 받침 시도
            if (JONG.indexOf(c) > 0) {
                push();
                jong = c;
            } else {
                startNew(c, (char) 0);
            }
        } else {                                // 받침 있음 → 겹받침 시도
            char pair = combine(FINAL_PAIRS, jong, c);
            if (pair != 0) {
                push();
                jong = pair;
            } else {
                startNew(c, (char) 0);
            }
        }
    }

    private void inputVowel(char v) {
        if (cho == 0 && jung == 0) {            // 빈 상태 → 모음 단독
            push();
            jung = v;
        } else if (jung == 0) {                 // 초성만 → 초성+중성
            push();
            jung = v;
        } else if (jong == 0) {                 // 겹모음 시도
            char pair = combine(VOWEL_PAIRS, jung, v);
            if (pair != 0) {
                push();
                jung = pair;
            } else {
                commitCurrent();
                push();
                jung = v;
            }
        } else {                                // 받침이 다음 글자 초성으로 넘어감
            char moved;
            int split = splitFinal(jong);
            if (split != 0) {
                jong = (char) (split >> 16);
                moved = (char) (split & 0xFFFF);
            } else {
                moved = jong;
                jong = 0;
            }
            startNew(moved, v);
        }
    }

    /** 현재 글자를 확정하고 새 글자를 (초성[, 중성])으로 시작한다. */
    private void startNew(char newCho, char newJung) {
        commitCurrent();
        push();             // 빈 상태
        cho = newCho;
        if (newJung != 0) {
            push();         // 초성만 있는 상태
            jung = newJung;
        }
    }

    /**
     * 백스페이스. 조합 중인 글자에서 자모 하나를 지웠으면 true,
     * 조합 중인 글자가 없어서 편집기에서 지워야 하면 false.
     */
    public boolean backspace() {
        lastTapVowel = 0;
        lastTapConsonant = 0;
        if (isEmpty()) return false;
        if (historySize > 0) {
            restore(pop());
        } else {
            cho = jung = jong = 0;
        }
        return true;
    }

    /** 조합 중인 글자를 확정 버퍼로 옮긴다. */
    public void flush() {
        commitCurrent();
        lastTapVowel = 0;
        lastTapConsonant = 0;
    }

    /** 조합 상태를 버린다 (커서 이동 등). 확정 버퍼도 비운다. */
    public void reset() {
        cho = jung = jong = 0;
        historySize = 0;
        lastTapVowel = 0;
        lastTapConsonant = 0;
        commit.setLength(0);
    }

    public boolean isEmpty() {
        return cho == 0 && jung == 0 && jong == 0;
    }

    /** 확정된 문자열을 꺼낸다 (꺼낸 뒤 비워짐). */
    public String takeCommit() {
        if (commit.length() == 0) return "";
        String s = commit.toString();
        commit.setLength(0);
        return s;
    }

    /** 현재 조합 중인 문자열. */
    public String getComposing() {
        if (cho != 0 && jung != 0) {
            int c = CHO.indexOf(cho), v = JUNG.indexOf(jung), f = jong == 0 ? 0 : JONG.indexOf(jong);
            return String.valueOf((char) (0xAC00 + (c * 21 + v) * 28 + f));
        }
        if (cho != 0) return String.valueOf(cho);
        if (jung != 0) return String.valueOf(jung);
        return "";
    }

    private void commitCurrent() {
        commit.append(getComposing());
        cho = jung = jong = 0;
        historySize = 0;
    }

    private static char combine(String[] table, char a, char b) {
        for (String t : table) {
            if (t.charAt(0) == a && t.charAt(1) == b) return t.charAt(2);
        }
        return 0;
    }

    /** 겹모음을 "앞 모음 뒤 모음"으로 나눈다 (ㅝ → "ㅜㅓ"). 두 가지로 나뉘는 ㅙ ㅞ는 표의 앞쪽(ㅗㅐ, ㅜㅔ). 겹모음이 아니면 null. */
    private static String splitVowel(char v) {
        for (String t : VOWEL_PAIRS) {
            if (t.charAt(2) == v) return t.substring(0, 2);
        }
        return null;
    }

    /** 겹받침을 (앞 << 16 | 뒤)로 나눈다. 겹받침이 아니면 0. */
    private static int splitFinal(char f) {
        for (String t : FINAL_PAIRS) {
            if (t.charAt(2) == f) return (t.charAt(0) << 16) | t.charAt(1);
        }
        return 0;
    }

    private void push() {
        if (historySize == history.length) {
            long[] n = new long[history.length * 2];
            System.arraycopy(history, 0, n, 0, historySize);
            history = n;
        }
        history[historySize++] = ((long) cho << 32) | ((long) jung << 16) | jong;
    }

    private long pop() {
        return history[--historySize];
    }

    private void restore(long s) {
        cho = (char) ((s >> 32) & 0xFFFF);
        jung = (char) ((s >> 16) & 0xFFFF);
        jong = (char) (s & 0xFFFF);
    }
}
