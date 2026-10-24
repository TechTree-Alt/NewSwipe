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
                // 소리대로 적은 말의 규칙으로 고칠 말
                {"갑니다", "90"}, {"드립니다", "90"}, {"그럴까요", "70"}, {"되네요", "60"}, {"들어오세요", "80"},
                {"좋아", "200"}, {"괜찮아", "150"},
                // 연음 규칙이 만들 수 있는 사전의 명사 + 조사, '-ㅆ어' 꼴: 바늘·힘내서를 이것들로 고치면 안 된다
                {"반을", "100"}, {"힘냈어", "100"}, {"분", "100"},
                // 끝난 말(되니) + 요는 사전 단어 + 조사로 보지 않는다. 명사(언니: 격조사 꼴이 있다) + 요는 맞는 말
                {"되니", "150"}, {"되나요", "250"}, {"언니", "150"}, {"언니가", "120"}, {"언니를", "120"}, {"언니는", "100"},
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
    public void fixesSpellingBySound() {
        assertEquals("갑니다", fix("감니다"));           // ㅂ니다를 소리대로 ㅁ니다로
        assertEquals("드립니다", fix("드림니다"));
        assertEquals("그럴까요", fix("그럴가요"));       // ㄹ 받침 뒤의 까
        assertEquals("되네요", fix("되네오"));           // 끝의 요 → 오
        assertEquals("들어오세요", fix("드러오세요"));   // 연음
        assertEquals("좋아", fix("조아"));               // ㅎ 받침
        assertEquals("괜찮아", fix("괜차나"));           // 연음 + ㅎ 받침
    }

    @Test
    public void yoAfterFinishedFormIsNotAWord() {
        assertEquals("되나요", fix("되니요"));   // 되니 + 요는 틀린 말: 자판에서 가까운 되나요로 고친다
        assertNull(fix("언니요"));              // 명사 + 요
    }

    @Test
    public void soundRulesKeepCollidingWords() {
        assertNull(fix("바늘"));     // 반+을: 두 글자 말은 받침을 옮기지 않는다
        assertNull(fix("힘내서"));   // 힘냈어: '-서'는 '-ㅆ어'로 옮기지 않는다
        assertNull(fix("부네요"));   // 분+에요: 소리 규칙으로는 조사가 붙은 말로 고치지 않는다 (사전에 '분'이 없어도)
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
