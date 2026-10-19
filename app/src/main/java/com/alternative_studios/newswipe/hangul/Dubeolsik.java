package com.alternative_studios.newswipe.hangul;

/**
 * 외장 키보드(블루투스·USB)의 글자 키를 표준 두벌식 자모로 바꾼다 (KS X 5002).
 * 키 자리는 쿼티 글자로 받는다: 외장 키보드는 한글 각인이 있어도 쿼티 자리로 키를 보낸다.
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
}
