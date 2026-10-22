package com.alternative_studios.newswipe.suggest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 자판 거리: 대각선 키는 마지막에 보고, 입력한 된소리는 일부러 친 것으로 본다. */
public class KeyProximityTest {
    private static float cost(TypoModel m, String typed, String word, boolean direct) {
        char[] p = TypoModel.letters(typed), t = TypoModel.letters(word);
        return direct ? m.distanceDirect(p, p.length, t, t.length, 9f) : m.distance(p, p.length, t, t.length, 9f);
    }

    @Test
    public void diagonalKeysAreOnlyInTheFullTable() {
        TypoModel m = new TypoModel();   // 8열 단모음
        assertEquals(TypoModel.NEIGHBOR, cost(m, "배일", "매일", false), 1e-6);   // ㅂ 아래 ㅁ
        assertEquals(TypoModel.NEIGHBOR, cost(m, "배일", "매일", true), 1e-6);
        assertEquals(TypoModel.NEIGHBOR, cost(m, "배일", "내일", false), 1e-6);   // ㅂ 대각선 ㄴ
        assertTrue(cost(m, "배일", "내일", true) > TypoModel.NEIGHBOR);
    }

    @Test
    public void typedTenseConsonantIsDeliberate() {
        TypoModel m = new TypoModel();
        assertEquals(TypoModel.NEIGHBOR, cost(m, "다끔", "가끔", false), 1e-6);   // ㄷ 옆 ㄱ
        assertTrue(cost(m, "다끔", "다음", false) >= TypoModel.SUBSTITUTE);         // 입력한 ㄲ을 바꾸는 것은 비싸다
        assertEquals(TypoModel.VARIANT, cost(m, "가금", "가끔", false), 1e-6);     // 예사소리를 친 것을 된소리로는 싸다
        assertTrue(cost(m, "까끔", "가끔", false) >= TypoModel.SUBSTITUTE);
    }

    @Test
    public void correctionPrefersStraightNeighbors() throws Exception {
        // 내일이 매일보다 흔해도, 대각선 키(ㄴ)가 아닌 바로 아래 키(ㅁ)로 고친다
        WordDictionary dict = KoreanSuggestTest.buildDict(new String[][]{
                {"내일", "250"}, {"매일", "240"}, {"사람", "220"}, {"하고", "230"}, {"일이", "200"},
        });
        KoreanSuggest ks = new KoreanSuggest(dict, null, new TypoModel());
        assertEquals("매일", ks.correct("배일", null, KoreanSuggest.FIELD_NORMAL, null, null));
        KoreanCorrect.DIAGONAL_LAST = false;
        try {
            assertEquals(null, ks.correct("배일", null, KoreanSuggest.FIELD_NORMAL, null, null));   // 예전: 둘이 겨뤄 고치지 않았다
        } finally {
            KoreanCorrect.DIAGONAL_LAST = true;
        }
    }
}
