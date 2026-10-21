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
}
