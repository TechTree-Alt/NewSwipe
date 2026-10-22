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

    /** NewSwipe 단모음의 글자 줄 (KeyboardLayout.D7_ROWS와 같다). */
    private static final String[][] D7 = {
            {"ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅗ", "ㅐ", "ㅔ"},
            {"ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅓ", "ㅏ", "ㅣ"},
            {"ㅍ", "ㅋ", "ㅎ", "ㅅ", "ㅜ", "ㅡ"},
    };

    @Test
    public void neighborsFollowTheLayout() {
        TypoModel eight = new TypoModel(), ns = new TypoModel();
        ns.setRows(D7, true);
        // ㅅ: 8열은 ㄱ 옆·ㅎ 위, NewSwipe는 셋째 줄에서 ㄹ 아래·ㅎ 옆
        assertEquals(TypoModel.NEIGHBOR, cost(eight, "가고", "사고", true), 1e-6);
        assertTrue(cost(ns, "가고", "사고", false) >= TypoModel.SUBSTITUTE);
        assertEquals(TypoModel.NEIGHBOR, cost(ns, "라고", "사고", true), 1e-6);
        assertTrue(cost(eight, "라고", "사고", true) > TypoModel.NEIGHBOR);   // 8열에서는 대각선
        assertEquals(TypoModel.NEIGHBOR, cost(eight, "하고", "사고", true), 1e-6);
        assertEquals(TypoModel.NEIGHBOR, cost(ns, "하고", "사고", true), 1e-6);
        // 모음은 두 자판에서 같은 자리 (ㅗ 아래 ㅓ, ㅓ 아래 ㅜ, ㅏ 아래 ㅡ)
        for (TypoModel m : new TypoModel[]{eight, ns}) {
            assertEquals(TypoModel.NEIGHBOR, cost(m, "자덩", "자동", true), 1e-6);
            assertEquals(TypoModel.NEIGHBOR, cost(m, "굴자", "글자", true), 1e-6);
            assertEquals(TypoModel.NEIGHBOR, cost(m, "이드", "이다", true), 1e-6);
        }
    }

    @Test
    public void aspirateIsSameKeyOnlyWhenSwiped() {
        TypoModel eight = new TypoModel(), ns = new TypoModel();
        ns.setRows(D7, true);
        // NewSwipe 단모음은 ㄱ을 오른쪽으로 밀어 ㅋ: 같은 키의 다른 동작
        assertEquals(TypoModel.VARIANT, cost(ns, "공", "콩", false), 1e-6);
        assertEquals(TypoModel.VARIANT, cost(ns, "자고", "차고", false), 1e-6);
        // 8열 단모음의 ㅋ·ㅊ은 셋째 줄의 다른 키
        assertTrue(cost(eight, "공", "콩", false) >= TypoModel.SUBSTITUTE);
        assertTrue(cost(eight, "자고", "차고", false) >= TypoModel.SUBSTITUTE);
        assertEquals(TypoModel.VARIANT, cost(eight, "가금", "가끔", false), 1e-6);   // 된소리는 두 자판 모두 같은 키
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
