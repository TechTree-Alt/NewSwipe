package com.alternative_studios.newswipe.suggest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.BeforeClass;
import org.junit.Test;

/** 자동 수정의 맞춤법 오타 규칙 (KoreanCorrect.spellFix): 받침·모음을 소리대로 잘못 적은 말을 드문 사전 단어로도 고친다. */
public class SpellRulesTest {
    private static WordDictionary dict;

    @BeforeClass
    public static void setUp() throws Exception {
        dict = KoreanSuggestTest.buildDict(new String[][]{
                // 자판 오타로 고칠 때의 최소 빈도보다 드문 말 (점수가 낮다)
                {"재밌네요", "40"}, {"재미있었다", "40"}, {"웬만하면", "60"}, {"괜찮은", "90"}, {"돼서", "80"},
                {"됐다", "120"}, {"된다", "150"}, {"있습니다", "200"}, {"어차피", "100"}, {"대가", "90"},
                // 사전에 있으면 규칙을 쓰지 않는다
                {"왠지", "150"}, {"잇다", "60"},
        });
    }

    private static String fix(String w) {
        return new KoreanSuggest(dict, null, new TypoModel()).correct(w, null, KoreanSuggest.FIELD_NORMAL, null, null);
    }

    @Test
    public void fixesSpellingTypos() {
        assertEquals("재밌네요", fix("재밋네요"));       // ㅅ 받침 → ㅆ
        assertEquals("재미있었다", fix("재미잇엇다"));   // 두 군데
        assertEquals("웬만하면", fix("왠만하면"));
        assertEquals("괜찮은", fix("괜찬은"));
        assertEquals("돼서", fix("되서"));
        assertEquals("됐다", fix("됬다"));
        assertEquals("된다", fix("됀다"));
        assertEquals("있습니다", fix("있읍니다"));
        assertEquals("어차피", fix("어자피"));
        assertEquals("대가", fix("댓가"));
        assertEquals("대가는", fix("댓가는"));            // 사전 단어 + 조사
    }

    @Test
    public void keepsDictionaryWordsAndUnknownResults() {
        assertNull(fix("왠지"));
        assertNull(fix("잇다"));
        assertNull(fix("햇살이"));   // 했살이는 사전에 없다
    }

    @Test
    public void rulesCanBeTurnedOff() {
        KoreanCorrect.SPELL_RULES = false;
        try {
            assertNull(fix("재밋네요"));
        } finally {
            KoreanCorrect.SPELL_RULES = true;
        }
    }
}
