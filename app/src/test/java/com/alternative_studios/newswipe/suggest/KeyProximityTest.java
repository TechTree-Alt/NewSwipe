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

    /** 두벌식 자판의 글자 줄과 줄마다의 시작 위치 (KeyboardLayout과 같다: 둘째 줄은 반 칸, 셋째 줄은 Shift 1.5칸 뒤). */
    private static TypoModel dubeolsik() {
        TypoModel m = new TypoModel();
        m.setRows(com.alternative_studios.newswipe.hangul.Dubeolsik.rows(), new float[]{0f, 0.5f, 1.5f}, false, true);
        return m;
    }

    @Test
    public void dubeolsikStaggeredRows() {
        TypoModel m = dubeolsik(), eight = new TypoModel();
        // ㅁ은 ㅂ·ㅈ 사이 아래: 둘 다 바로 위 키다 (8열 단모음에서 ㅈ은 대각선)
        assertEquals(TypoModel.NEIGHBOR, cost(m, "바다", "마다", true), 1e-6);
        assertEquals(TypoModel.NEIGHBOR, cost(m, "자다", "마다", true), 1e-6);
        assertTrue(cost(eight, "자다", "마다", true) > TypoModel.NEIGHBOR);
        // ㅂ과 ㄴ은 1.5칸 떨어져 이웃이 아니다 (8열 단모음에서는 대각선 이웃)
        assertTrue(cost(m, "배일", "내일", false) >= TypoModel.SUBSTITUTE);
        assertEquals(TypoModel.NEIGHBOR, cost(eight, "배일", "내일", false), 1e-6);
        // ㅋ은 ㄴ 바로 아래, ㅁ·ㅇ은 대각선
        assertEquals(TypoModel.NEIGHBOR, cost(m, "나다", "카다", true), 1e-6);
        assertEquals(TypoModel.NEIGHBOR, cost(m, "마다", "카다", false), 1e-6);
        assertTrue(cost(m, "마다", "카다", true) > TypoModel.NEIGHBOR);
        assertTrue(cost(m, "라다", "카다", false) >= TypoModel.SUBSTITUTE);   // 두 칸 떨어진 키
        // 같은 줄은 바로 옆 키만: ㅅ 옆에 ㅛ, ㄱ
        assertEquals(TypoModel.NEIGHBOR, cost(m, "고", "소", true), 1e-6);
        assertTrue(cost(m, "도", "소", false) >= TypoModel.SUBSTITUTE);
        // 모음: ㅐ 아래 ㅏ·ㅣ (8열 단모음에서 ㅣ는 대각선), ㅜ 옆 ㅠ·ㅡ, ㅗ 위 ㅛ·ㅅ 쪽은 자음이라 비싸다
        assertEquals(TypoModel.NEIGHBOR, cost(m, "새", "시", true), 1e-6);
        assertTrue(cost(eight, "새", "시", true) > TypoModel.NEIGHBOR);
        assertEquals(TypoModel.NEIGHBOR, cost(m, "구", "그", true), 1e-6);
        assertEquals(TypoModel.NEIGHBOR, cost(m, "구", "규", true), 1e-6);
    }

    @Test
    public void dubeolsikIotizedVowelsAreTheirOwnKeys() {
        TypoModel m = dubeolsik(), eight = new TypoModel();
        // ㅑ는 ㅏ 키를 민 것이 아니라 옆 키: 같은 키의 다른 동작(VARIANT)이 아니라 자판 거리
        assertEquals(TypoModel.NEIGHBOR, cost(m, "갸", "가", true), 1e-6);
        assertEquals(TypoModel.VARIANT, cost(eight, "갸", "가", true), 1e-6);
        assertEquals(TypoModel.NEIGHBOR, cost(m, "교", "고", true), 1e-6);
        // ㅑ의 이웃은 제 자리 (ㅕ·ㅐ·ㅓ·ㅏ)에서 잰다: ㅏ 아래의 ㅡ는 ㅑ와 이웃이 아니다
        assertEquals(TypoModel.NEIGHBOR, cost(m, "갸", "개", true), 1e-6);
        assertEquals(TypoModel.NEIGHBOR, cost(m, "갸", "겨", true), 1e-6);
        assertTrue(cost(m, "갸", "그", false) >= TypoModel.SUBSTITUTE);
        assertEquals(TypoModel.NEIGHBOR, cost(eight, "갸", "그", false), 1e-6);   // 8열: ㅏ 키의 아래 밀기라 ㅏ의 이웃
        // Shift로 같은 키에서 나오는 글자, 소리가 같은 모음은 그대로 싸다
        assertEquals(TypoModel.VARIANT, cost(m, "가금", "가끔", false), 1e-6);
        assertEquals(TypoModel.VARIANT, cost(m, "개", "걔", false), 1e-6);
        assertEquals(TypoModel.VARIANT, cost(m, "게", "개", false), 1e-6);
        // ㅋ·ㅊ은 따로 있는 키 (NewSwipe 단모음처럼 밀어서 넣지 않는다)
        assertTrue(cost(m, "공", "콩", false) >= TypoModel.SUBSTITUTE);
    }

    @Test
    public void dubeolsikCorrection() throws Exception {
        WordDictionary dict = KoreanSuggestTest.buildDict(new String[][]{
                {"내일", "250"}, {"매일", "240"}, {"사람", "220"}, {"하고", "230"}, {"일이", "200"},
        });
        // ㅂ의 이웃은 ㅁ뿐이라 '배일'은 매일로 고친다
        assertEquals("매일", new KoreanSuggest(dict, null, dubeolsik())
                .correct("배일", null, KoreanSuggest.FIELD_NORMAL, null, null));
        // 두벌식의 ㅈ은 ㅁ·ㄴ 사이 위라 둘 다 바로 위·아래 키: 하나로 정할 수 없어 고치지 않는다
        // (8열 단모음은 ㅈ 바로 아래가 ㄴ이라 내일로 고친다)
        assertEquals(null, new KoreanSuggest(dict, null, dubeolsik())
                .correct("재일", null, KoreanSuggest.FIELD_NORMAL, null, null));
        assertEquals("내일", new KoreanSuggest(dict, null, new TypoModel())
                .correct("재일", null, KoreanSuggest.FIELD_NORMAL, null, null));
    }

    /** 반듯한 격자(줄 시작 위치 0)는 줄 시작 위치를 주지 않은 것과 같은 표를 만든다. */
    @Test
    public void zeroOffsetsMatchTheGridTable() {
        TypoModel a = new TypoModel(), b = new TypoModel();
        String[][] rows = {
                {"ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅅ", "ㅗ", "ㅐ", "ㅔ"},
                {"ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅎ", "ㅓ", "ㅏ", "ㅣ"},
                {"", "ㅋ", "ㅌ", "ㅊ", "ㅍ", "ㅜ", "ㅡ"},
        };
        b.setRows(rows, new float[]{0f, 0f, 0f}, false, false);
        for (char x = 0x3131; x < 0x3131 + 51; x++) {
            for (char y = 0x3131; y < 0x3131 + 51; y++) {
                assertEquals(a.substitute(x, y), b.substitute(x, y), 0f);
                assertEquals(a.substituteDirect(x, y), b.substituteDirect(x, y), 0f);
            }
        }
    }
}
