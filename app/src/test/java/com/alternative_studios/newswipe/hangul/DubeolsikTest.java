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
}
