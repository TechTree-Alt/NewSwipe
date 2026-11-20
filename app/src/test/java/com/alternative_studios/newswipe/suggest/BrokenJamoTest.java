package com.alternative_studios.newswipe.suggest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 겹모음·겹받침의 한쪽을 옆 키로 쳐서 깨진 말(고ㅔㄴ찮아요, 고ㅓㄴ심, 엊ㅅ이)을 되살리는 것. */
public class BrokenJamoTest {
    private static WordDictionary dict;

    @BeforeClass
    public static void setUp() throws Exception {
        dict = KoreanSuggestTest.buildDict(new String[][]{
                {"괜찮아요", "220"}, {"괜찮아", "230"}, {"괜찮은", "200"}, {"괜히", "150"}, {"관심", "200"}, {"관심을", "160"},
                {"관련", "190"}, {"왜", "240"}, {"뭘", "180"}, {"됐어", "180"}, {"훨씬", "170"}, {"괴로워", "120"},
                {"우리", "200"}, {"아니", "200"}, {"환장하다", "120"}, {"환장하겠다", "110"}, {"환장했다", "110"},
                {"안녕하세요", "250"}, {"안녕", "220"}, {"안녕히", "150"},
                {"없이", "220"}, {"없다", "230"}, {"없는", "230"}, {"없고", "200"}, {"읽어", "150"}, {"헐", "150"}, {"많이", "220"},
        });
        dict.warmUpChosung();
    }

    private static KoreanSuggest model() {
        return new KoreanSuggest(dict, null, new TypoModel());
    }

    private static List<String> words(String typed) {
        List<String> out = new ArrayList<>();
        for (BrokenJamo.Repair r : BrokenJamo.repairs(typed, new TypoModel())) out.add(r.word);
        return out;
    }

    @Test
    public void rebuildsCompoundVowelAndReattachesFinal() {
        // ㅗ+ㅔ(ㅐ와 소리가 같은 옆 키) → ㅙ, 따로 남은 ㄴ은 받침으로
        assertEquals("괜찮아요", words("고ㅔㄴ찮아요").get(0));
        // ㅓ·ㅡ는 ㅏ의 옆 키
        assertEquals("관심", words("고ㅓㄴ심").get(0));
        assertEquals("관심", words("고ㅡㄴ심").get(0));
        assertEquals("뭘", words("무ㅏㄹ").get(0));
        assertEquals("됐어", words("도ㅔㅆ어").get(0));
        // 대각선 키(ㅓ→ㅐ)는 바로 옆 키(ㅓ→ㅏ)보다 뒤에 온다
        List<String> w = words("도ㅓㅆ어");
        assertEquals("돴어", w.get(0));
        assertTrue(w.toString(), w.indexOf("됐어") > w.indexOf("돴어"));
        assertEquals("왜", words("오ㅔ").get(0));
    }

    @Test
    public void compoundFinalLeftApartIsJoined() {
        assertTrue(words("고ㅔㄴㅎ").contains("괞"));
    }

    @Test
    public void leavesIntentionalRepeatsAlone() {
        for (String w : new String[]{"우ㅜㅜ", "오ㅗ", "아ㅏㅏ", "ㅠㅠ", "아ㅠㅠ", "그ㅡ", "괜찮아요", "ㅋㅋ"}) {
            assertFalse(w, BrokenJamo.has(w) && !words(w).isEmpty());
        }
    }

    @Test
    public void suggestsFromRebuiltWord() {
        KoreanSuggest m = model();
        String[] s = m.suggest("고ㅔㄴ찮", null, KoreanSuggest.FIELD_NORMAL, null, null, null, 5).words;
        assertTrue(Arrays.toString(s), Arrays.asList(s).contains("괜찮아요"));
        // 되살린 말이 그대로 사전 단어면 맨 앞에 보인다
        s = m.suggest("고ㅓㄴ심", null, KoreanSuggest.FIELD_NORMAL, null, null, null, 5).words;
        assertEquals(Arrays.toString(s), "관심", s[0]);
        s = m.suggest("고ㅡㄴ심", null, KoreanSuggest.FIELD_NORMAL, null, null, null, 5).words;
        assertEquals(Arrays.toString(s), "관심", s[0]);
        s = m.suggest("고ㅔㄴ찮아요", null, KoreanSuggest.FIELD_NORMAL, null, null, null, 5).words;
        assertEquals(Arrays.toString(s), "괜찮아요", s[0]);
        // 사전에 없는 활용형이어도 되살린 말 자신이 보인다 (친 말을 빼는 추천에서 빠지지 않게)
        s = m.suggest("호ㅓㄴ장하겠네", null, KoreanSuggest.FIELD_NORMAL, null, null, null, 5).words;
        assertEquals(Arrays.toString(s), "환장하겠네", s[0]);
        // 친 중간에도 (고ㅔ → 괘…)
        s = m.suggest("고ㅔ", null, KoreanSuggest.FIELD_NORMAL, null, null, null, 5).words;
        assertTrue(Arrays.toString(s), Arrays.asList(s).contains("괜찮아"));
    }

    @Test
    public void autoCorrectsBrokenWord() {
        KoreanSuggest m = model();
        assertEquals("괜찮아요", m.correct("고ㅔㄴ찮아요", null, KoreanSuggest.FIELD_NORMAL, null, null));
        assertEquals("관심", m.correct("고ㅓㄴ심", null, KoreanSuggest.FIELD_NORMAL, null, null));
        assertEquals("관심", m.correct("고ㅡㄴ심", null, KoreanSuggest.FIELD_NORMAL, null, null));
        assertEquals("괜히", m.correct("고ㅔㄴ히", null, KoreanSuggest.FIELD_NORMAL, null, null));
        assertEquals("됐어", m.correct("도ㅓㅆ어", null, KoreanSuggest.FIELD_NORMAL, null, null));
        assertEquals("왜", m.correct("오ㅔ", null, KoreanSuggest.FIELD_NORMAL, null, null));
        // 사전에 없는 활용형도 낱말로 그럴듯하면 되살린 말 그대로 (환장하겠다는 사전에 있고 환장하겠네는 없다)
        assertEquals("환장하겠다", m.correct("호ㅓㄴ장하겠다", null, KoreanSuggest.FIELD_NORMAL, null, null));
        assertEquals("환장하겠네", m.correct("호ㅓㄴ장하겠네", null, KoreanSuggest.FIELD_NORMAL, null, null));
        // 아직 치는 중인 앞부분은 고치지 않는다
        assertNull(m.correct("고ㅔㄴ", null, KoreanSuggest.FIELD_NORMAL, null, null));
        assertNull(m.correct("우ㅜㅜ", null, KoreanSuggest.FIELD_NORMAL, null, null));
    }

    @Test
    public void doubleTappedVowelWhoseSecondTapSlipped() {
        // 단모음 자판의 모음 연속 탭: ㅓㅓ → ㅕ인데 둘째 탭이 옆 키(ㅜ·ㅗ)로 빠졌다
        assertEquals("안녕하세요", words("안너ㅜㅇ하세요").get(0));
        assertTrue(words("안너ㅗㅇ하세요").contains("안녕하세요"));
        KoreanSuggest m = model();
        assertEquals("안녕하세요", m.correct("안너ㅜㅇ하세요", null, KoreanSuggest.FIELD_NORMAL, null, null));
        assertEquals("안녕하세요", m.correct("안너ㅗㅇ하세요", null, KoreanSuggest.FIELD_NORMAL, null, null));
        // 첫 탭이 빠져 겹모음이 된 말(ㅜ+ㅓ = ㅝ)은 원래 오타 모델이 같은 키 계열(ㅝ↔ㅕ)로 고친다
        assertEquals("안녕하세요", m.correct("안눵하세요", null, KoreanSuggest.FIELD_NORMAL, null, null));
        // 치는 중에도
        String[] s = m.suggest("안너ㅜ", null, KoreanSuggest.FIELD_NORMAL, null, null, null, 5).words;
        assertTrue(Arrays.toString(s), Arrays.asList(s).contains("안녕하세요"));
    }

    @Test
    public void doubleTapOnlyOnLayoutsWithoutIotizedKeys() {
        // 두벌식은 ㅕ를 제 키로 친다: ㅓ 다음 ㅜ를 ㅓㅓ로 보지 않는다
        TypoModel dubeolsik = new TypoModel();
        dubeolsik.setRows(com.alternative_studios.newswipe.hangul.Dubeolsik.rows(), new float[]{0f, 0.5f, 1.5f}, false, true);
        for (BrokenJamo.Repair r : BrokenJamo.repairs("안너ㅜㅇ하세요", dubeolsik)) assertFalse(r.word, r.word.equals("안녕하세요"));
        // 모음 연속 탭을 끄면 보지 않는다
        boolean saved = HangulRange.doubleTapVowel;
        HangulRange.doubleTapVowel = false;
        try {
            assertFalse(words("안너ㅜㅇ하세요").contains("안녕하세요"));
        } finally {
            HangulRange.doubleTapVowel = saved;
        }
    }

    @Test
    public void rebuildsCompoundFinal() {
        // ㅂ 대신 옆 키 ㅈ: ㅈ+ㅅ은 겹받침이 아니라 ㅅ이 따로 남는다
        assertEquals("없이", words("엊ㅅ이").get(0));
        assertEquals("없다", words("엊ㅅ다").get(0));
        // 뒤 자음이 빠진 것 (ㅅ 대신 옆 키 ㄱ), ㄱ 대신 옆 키 ㄷ (ㄺ)
        assertTrue(words("업ㄱ이").contains("없이"));
        assertTrue(words("일ㄷ어").contains("읽어"));
        KoreanSuggest m = model();
        assertEquals("없이", m.correct("엊ㅅ이", null, KoreanSuggest.FIELD_NORMAL, null, null));
        assertEquals("없다", m.correct("엊ㅅ다", null, KoreanSuggest.FIELD_NORMAL, null, null));
        assertEquals("없이", m.correct("업ㄱ이", null, KoreanSuggest.FIELD_NORMAL, null, null));
        assertEquals("읽어", m.correct("일ㄷ어", null, KoreanSuggest.FIELD_NORMAL, null, null));
        // 치는 중에도 (엊ㅅ → 없…)
        String[] s = m.suggest("엊ㅅ", null, KoreanSuggest.FIELD_NORMAL, null, null, null, 5).words;
        assertTrue(Arrays.toString(s), Arrays.asList(s).contains("없는"));
    }

    @Test
    public void leavesLooseConsonantsTypedOnPurposeAlone() {
        // 웃음(헐ㅋ, 헐ㅋㅋ), 이어진 자음(했음ㅎㅎ), 받침과 같은 자음(만ㄴ)
        for (String w : new String[]{"헐ㅋ", "헐ㅋㅋ", "했음ㅎㅎ", "만ㄴ이", "없이"}) assertFalse(w, BrokenJamo.has(w));
        KoreanSuggest m = model();
        assertNull(m.correct("헐ㅋㅋ", null, KoreanSuggest.FIELD_NORMAL, null, null));
        assertNull(m.correct("만ㄴ이", null, KoreanSuggest.FIELD_NORMAL, null, null));
    }

    @Test
    public void offWhenDisabled() {
        boolean saved = KoreanSuggest.BROKEN_JAMO;
        KoreanSuggest.BROKEN_JAMO = false;
        try {
            assertNull(model().correct("고ㅓㄴ심", null, KoreanSuggest.FIELD_NORMAL, null, null));
        } finally {
            KoreanSuggest.BROKEN_JAMO = saved;
        }
    }
}
