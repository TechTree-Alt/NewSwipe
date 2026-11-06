package com.alternative_studios.newswipe.hangul;

/**
 * 표준 두벌식 배열 (KS X 5002). 외장 키보드(블루투스·USB)의 글자 키를 자모로 바꾸고, 실험실 '두벌식 자판'의 화면 자판도
 * 이 배열을 쓴다. 키 자리는 쿼티 글자로 받는다: 외장 키보드는 한글 각인이 있어도 쿼티 자리로 키를 보낸다.
 *
 * <p>안드로이드 의존성이 없는 순수 자바 클래스라 단위 테스트로 검증한다.
 */
public final class Dubeolsik {

    private Dubeolsik() {
    }

    /** a~z 자리의 자모. */
    private static final String PLAIN = "ㅁㅠㅊㅇㄷㄹㅎㅗㅑㅓㅏㅣㅡㅜㅐㅔㅂㄱㄴㅅㅕㅍㅈㅌㅛㅋ";
    /** Shift를 누른 a~z 자리의 자모 (쌍자음 ㅃㅉㄸㄲㅆ, 이중모음 ㅒㅖ). 나머지는 Shift 없이와 같다. */
    private static final String SHIFTED = "ㅁㅠㅊㅇㄸㄹㅎㅗㅑㅓㅏㅣㅡㅜㅒㅖㅃㄲㄴㅆㅕㅍㅉㅌㅛㅋ";

    /**
     * @param letter 쿼티 글자 키 (대소문자 상관없음)
     * @param shift  Shift를 눌렀는지
     * @return 그 자리의 자모. 글자 키가 아니면 0.
     */
    public static char jamo(char letter, boolean shift) {
        char c = Character.toLowerCase(letter);
        if (c < 'a' || c > 'z') return 0;
        return (shift ? SHIFTED : PLAIN).charAt(c - 'a');
    }

    /** 화면 자판의 글자 줄 (쿼티 자리 순서). */
    private static final String[] QWERTY_ROWS = {"qwertyuiop", "asdfghjkl", "zxcvbnm"};

    /** 화면 두벌식 자판의 글자 줄: ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔ / ㅁㄴㅇㄹㅎㅗㅓㅏㅣ / ㅋㅌㅊㅍㅠㅜㅡ. 고치지 않는다. */
    public static String[][] rows() {
        return ROWS;
    }

    private static final String[][] ROWS = new String[QWERTY_ROWS.length][];

    static {
        for (int r = 0; r < QWERTY_ROWS.length; r++) {
            ROWS[r] = new String[QWERTY_ROWS[r].length()];
            for (int i = 0; i < ROWS[r].length; i++) ROWS[r][i] = String.valueOf(jamo(QWERTY_ROWS[r].charAt(i), false));
        }
    }

    /**
     * 두벌식 자판에서 이 자모 키를 Shift와 함께 누르면 들어가는 자모: ㅂㅈㄷㄱㅅ → ㅃㅉㄸㄲㅆ, ㅐㅔ → ㅒㅖ.
     * 나머지(ㅗ·ㅏ 등)는 Shift를 눌러도 같다 (단모음 자판처럼 ㅗ → ㅛ가 되지 않는다). 두벌식 키가 아니면 그대로 돌려준다.
     */
    public static char shifted(char jamo) {
        int i = PLAIN.indexOf(jamo);
        return i < 0 ? jamo : SHIFTED.charAt(i);
    }
}
