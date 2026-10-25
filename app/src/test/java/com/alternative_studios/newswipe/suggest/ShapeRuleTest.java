package com.alternative_studios.newswipe.suggest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

/** 자동 수정의 판단 (KoreanCorrect.SHAPE_RULE): 빈도·오타 비용·낱말다움(음절 모형)을 함께 본다. */
public class ShapeRuleTest {
    private static WordDictionary dict;

    @BeforeClass
    public static void setUp() throws Exception {
        dict = KoreanSuggestTest.buildDict(new String[][]{
                // 드문 활용형: 예전의 최소 빈도로는 고칠 수 없다
                {"사용하세요", "110"}, {"사용하고", "150"}, {"사용해요", "120"}, {"사랑하세요", "110"},
                // 명사와 그 조사 꼴 (명사로 알아본다). 작은 사전이라 음절 모형이 자댕과 자동의 차이를 실제 사전(로그 확률 29 차이)보다
                // 작게 보므로(9) 자동을 실제보다 흔하게 둔다
                {"자동", "200"}, {"자동이", "90"}, {"자동을", "90"}, {"자동으로", "90"},
                {"지역", "200"}, {"지역이", "180"}, {"지역을", "180"}, {"지역에", "180"},
                // 음절 모형이 배울 흔한 말들
                {"사람", "220"}, {"사이", "200"}, {"하세요", "220"}, {"하고", "230"}, {"입니다", "230"}, {"자리", "200"},
                {"미안", "150"}, {"역사", "180"}, {"미소", "150"}, {"동네", "170"}, {"용기", "150"},
        });
    }

    private static String fix(String w) {
        return new KoreanSuggest(dict, null, new TypoModel()).correct(w, null, KoreanSuggest.FIELD_NORMAL, null, null);
    }

    @Test
    public void cheapTypoIntoRareFormIsFixed() {
        assertEquals("사용하세요", fix("사옹하세요"));   // ㅛ를 ㅗ로: 같은 키의 다른 동작
    }

    @Test
    public void nounWithParticleIsFixed() {
        assertEquals("자동입니다", fix("자댕입니다"));   // 자댕은 낯선 음절 이음이다
    }

    @Test
    public void oldFloorRuleKeepsRareForms() {
        KoreanCorrect.SHAPE_RULE = false;
        try {
            assertNull(fix("사옹하세요"));   // 예전 판단: 고칠 말이 최소 빈도보다 드물다
        } finally {
            KoreanCorrect.SHAPE_RULE = true;
        }
    }

    @Test
    public void syllableModelPrefersWordLikeStrings() {
        SyllableModel m = new SyllableModel(dict);
        assertTrue(m.logProb("자동") > m.logProb("자댕"));
        assertTrue(m.logProb("사용하세요") > m.logProb("사옹하세요"));
    }

    @Test
    public void middleVowelEditOnlyInsideTheWord() {
        assertTrue(KoreanCorrect.middleVowelEdit("키버드", "키보드"));
        assertTrue(KoreanCorrect.middleVowelEdit("바이얼린", "바이올린"));
        assertTrue(!KoreanCorrect.middleVowelEdit("예민함", "예민한"));    // 끝 글자 (어미·조사)
        assertTrue(!KoreanCorrect.middleVowelEdit("직장운", "직장은"));    // 끝 글자
        assertTrue(!KoreanCorrect.middleVowelEdit("지망에서", "지방에서"));  // 자음
        assertTrue(!KoreanCorrect.middleVowelEdit("키버드", "키보드가"));   // 길이가 다르다
        assertTrue(!KoreanCorrect.middleVowelEdit("키버도", "키보드"));    // 두 글자
    }

    @Test
    public void particleTypoAfterNoun() throws Exception {
        WordDictionary d = KoreanSuggestTest.buildDict(new String[][]{
                {"생선", "150"}, {"생선을", "90"}, {"생선이", "90"}, {"생선의", "80"}, {"생선에", "80"}, {"생선과", "80"},
                {"사람", "220"}, {"사람을", "200"}, {"사람은", "205"}, {"사람이", "200"}, {"사람의", "190"}, {"사람에게", "180"},
                {"하고", "230"}, {"입니다", "230"},
        });
        KoreanSuggest ks = new KoreanSuggest(d, null, new TypoModel());
        assertEquals("생선을", ks.correct("생선응", null, KoreanSuggest.FIELD_NORMAL, null, null));   // 을·은 중 사전에 있는 꼴
        assertNull(ks.correct("사람응", null, KoreanSuggest.FIELD_NORMAL, null, null));          // 사람을·사람은 둘 다 흔하다
        assertNull(ks.correct("생선돼", null, KoreanSuggest.FIELD_NORMAL, null, null));          // 용언 조각은 조사 오타로 보지 않는다
    }

    @Test
    public void adjectiveDoesNotTakeVerbOnlyEndings() throws Exception {
        WordDictionary d = KoreanSuggestTest.buildDict(new String[][]{
                {"좋고", "200"}, {"좋은", "200"}, {"좋지", "180"}, {"좋다", "220"},
                {"먹고", "200"}, {"먹는", "200"}, {"먹지", "180"}, {"먹다", "180"},
        });
        KoreanSuggest ks = new KoreanSuggest(d, null, new TypoModel());
        assertTrue(ks.plausible("먹자"));            // 동사 + 청유
        assertTrue(!ks.plausible("좋자"));           // 형용사에는 -자가 붙지 않는다
        assertTrue(!ks.plausible("좋는"));
    }

    @Test
    public void stemTypoUsesFamilyFrequency() throws Exception {
        // 궁금하네 하나는 드물어도 궁금… 활용형이 많고, 궁근…으로 시작하는 말은 없다
        WordDictionary d = KoreanSuggestTest.buildDict(new String[][]{
                {"궁금하네", "60"}, {"궁금해", "200"}, {"궁금한", "200"}, {"궁금하다", "190"}, {"궁금해요", "190"}, {"궁금하면", "150"},
                {"사람", "220"}, {"하고", "230"}, {"하네", "200"}, {"입니다", "230"},
        });
        KoreanSuggest ks = new KoreanSuggest(d, null, new TypoModel());
        ks.setKnownWords(KnownWords.load(new java.io.ByteArrayInputStream(KnownWordsTest.hex(KnownWordsTest.FILE))));
        assertEquals("궁금하네", ks.correct("궁근하네", null, KoreanSuggest.FIELD_NORMAL, null, null));
    }
    @Test
    public void syllableModelCountsPairs() {
        SyllableModel m = new SyllableModel(dict);
        assertTrue(m.pairCount('자', '동') > 0);
        assertEquals(0, m.pairCount('자', '댕'));
        assertTrue(m.pairCount('다', (char) 0) > 0);   // 입니다로 끝난다
        assertTrue(m.unseenPair("자댕"));
        assertTrue(!m.unseenPair("자동"));
    }

    private static KoreanSuggest withKnown(String[][] words) throws Exception {
        KoreanSuggest ks = new KoreanSuggest(KoreanSuggestTest.buildDict(words), null, new TypoModel());
        ks.setKnownWords(KnownWords.load(new java.io.ByteArrayInputStream(KnownWordsTest.hex(KnownWordsTest.FILE))));
        return ks;
    }

    @Test
    public void rareFormWithCommonEndingIsFixed() throws Exception {
        // 확인하겠습니다는 아주 드물지만 끝(니다)은 흔하고, 친 말의 끝 이음(니드)은 사전에 없다
        java.util.List<String[]> w = new java.util.ArrayList<>();
        String heads = "가나다라마바사아자차카타파하고노도로모보소오조초";
        for (int i = 0; i < heads.length(); i++) w.add(new String[]{heads.charAt(i) + "습니다", "150"});
        w.add(new String[]{"확인하겠습니다", "40"});
        w.add(new String[]{"확인", "200"});
        w.add(new String[]{"중지된", "45"});
        w.add(new String[]{"중지", "150"});
        w.add(new String[]{"된", "200"});
        KoreanSuggest ks = withKnown(w.toArray(new String[0][]));
        assertEquals("확인하겠습니다", ks.correct("확인하겠습니드", null, KoreanSuggest.FIELD_NORMAL, null, null));
        assertEquals("중지된", ks.correct("중지돤", null, KoreanSuggest.FIELD_NORMAL, null, null));   // 지돤은 낯선 이음이다
    }

    @Test
    public void nominalFormDoesNotTakeParticles() throws Exception {
        // 사름(사르다의 명사형) + 은이 아니라 사람은의 오타다
        KoreanSuggest ks = withKnown(new String[][]{
                {"사름", "0"}, {"사르고", "100"}, {"사르면", "90"}, {"사람", "220"}, {"사람은", "205"}, {"사람이", "200"},
                {"사람을", "200"}, {"하고", "230"}, {"입니다", "230"},
                {"느림", "150"}, {"느리고", "160"}, {"느리면", "150"}, {"느낌", "200"}, {"느낌이", "190"},
        });
        assertEquals("사람은", ks.correct("사름은", null, KoreanSuggest.FIELD_NORMAL, null, null));
        assertNull(ks.correct("느림이", null, KoreanSuggest.FIELD_NORMAL, null, null));   // 흔한 명사형은 명사로도 쓴다
    }
}
