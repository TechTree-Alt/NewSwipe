package com.alternative_studios.newswipe.hangul;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DubeolsikTest {

    /** 쿼티로 친 글을 두벌식으로 조합한다. 대문자는 Shift를 누른 것으로 본다. */
    private static String type(String keys) {
        HangulComposer h = new HangulComposer();
        StringBuilder out = new StringBuilder();
        for (char k : keys.toCharArray()) {
            char j = Dubeolsik.jamo(k, Character.isUpperCase(k));
            if (j == 0) {
                h.flush();
                out.append(h.takeCommit()).append(k);
            } else {
                h.input(j, false);
                out.append(h.takeCommit());
            }
        }
        return out + h.getComposing();
    }

    @Test
    public void plainKeys() {
        assertEquals('ㅂ', Dubeolsik.jamo('q', false));
        assertEquals('ㅁ', Dubeolsik.jamo('a', false));
        assertEquals('ㅋ', Dubeolsik.jamo('z', false));
        assertEquals('ㅔ', Dubeolsik.jamo('p', false));
        assertEquals('ㅡ', Dubeolsik.jamo('m', false));
    }

    @Test
    public void shiftedKeys() {
        assertEquals('ㅃ', Dubeolsik.jamo('q', true));
        assertEquals('ㅉ', Dubeolsik.jamo('w', true));
        assertEquals('ㄸ', Dubeolsik.jamo('e', true));
        assertEquals('ㄲ', Dubeolsik.jamo('r', true));
        assertEquals('ㅆ', Dubeolsik.jamo('t', true));
        assertEquals('ㅒ', Dubeolsik.jamo('o', true));
        assertEquals('ㅖ', Dubeolsik.jamo('p', true));
        // 쌍자음·이중모음이 없는 자리는 Shift를 눌러도 같다.
        assertEquals('ㅁ', Dubeolsik.jamo('A', true));
    }

    @Test
    public void notLetter() {
        assertEquals(0, Dubeolsik.jamo('1', false));
        assertEquals(0, Dubeolsik.jamo(' ', false));
    }

    @Test
    public void composesWords() {
        assertEquals("안녕하세요", type("dkssudgktpdy"));
        assertEquals("학교", type("gkrry"));
        assertEquals("까닭", type("Rkekfr"));
        assertEquals("있어 봐", type("dlTdj qhk"));
    }

    @Test
    public void screenRowsAndShift() {
        String[][] rows = Dubeolsik.rows();
        assertEquals("ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔ", String.join("", rows[0]));
        assertEquals("ㅁㄴㅇㄹㅎㅗㅓㅏㅣ", String.join("", rows[1]));
        assertEquals("ㅋㅌㅊㅍㅠㅜㅡ", String.join("", rows[2]));
        assertEquals('ㅃ', Dubeolsik.shifted('ㅂ'));
        assertEquals('ㅆ', Dubeolsik.shifted('ㅅ'));
        assertEquals('ㅒ', Dubeolsik.shifted('ㅐ'));
        assertEquals('ㅖ', Dubeolsik.shifted('ㅔ'));
        assertEquals('ㅗ', Dubeolsik.shifted('ㅗ'));   // 단모음 자판의 Shift(ㅗ → ㅛ)와 다르다
        assertEquals('ㅏ', Dubeolsik.shifted('ㅏ'));
        assertEquals('ㅋ', Dubeolsik.shifted('ㅋ'));
        assertEquals('1', Dubeolsik.shifted('1'));
    }

    /** 화면 두벌식 자판은 모음 연속 탭을 끈 채로 조합한다: 같은 모음을 두 번 친 그대로 둔다. */
    @Test
    public void repeatedVowelTapStaysWithoutDoubleTap() {
        HangulComposer h = new HangulComposer();
        h.setDoubleTapVowel(false);
        for (char c : "ㅇㅗㅗ".toCharArray()) h.input(c, true);
        h.flush();
        assertEquals("오ㅗ", h.takeCommit());
        // ㅗ 다음 ㅏ는 그대로 겹모음 (두벌식으로 '과'를 치는 법)
        for (char c : "ㄱㅗㅏ".toCharArray()) h.input(c, true);
        h.flush();
        assertEquals("과", h.takeCommit());
    }
}
