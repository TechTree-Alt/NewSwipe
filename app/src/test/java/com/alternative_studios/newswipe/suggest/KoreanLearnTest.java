package com.alternative_studios.newswipe.suggest;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;

/** 사전에 없는 말을 학습할 때 조사를 떼는 방식과, 한 번 쓴 말을 추천하는 방식. */
public class KoreanLearnTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static WordDictionary dict;
    private UserDictionary user;
    private UsageHistory usage;
    private long now;

    @BeforeClass
    public static void setUpDict() throws Exception {
        dict = KoreanSuggestTest.buildDict(new String[][]{
                {"친구", "200"}, {"친구를", "150"}, {"고양이", "180"}, {"감사합니다", "255"}, {"감사", "200"}, {"길", "150"},
        });
    }

    @Before
    public void setUp() throws Exception {
        File dir = tmp.newFolder();
        user = new UserDictionary(new File(dir, "u.txt"));
        usage = new UsageHistory(new File(dir, "h.txt"));
        user.load();
        usage.load();
    }

    private void learn(String word) {
        WordSuggester.learnInto(dict, user, usage, word, null, true, false, ++now);
    }

    @Test
    public void splitsCopulaAndParticlesByFinalConsonant() {
        assertArrayEquals(new String[]{"짝남", "이야"}, WordSuggester.splitParticle("짝남이야"));
        assertArrayEquals(new String[]{"짝남", "한테"}, WordSuggester.splitParticle("짝남한테"));
        assertArrayEquals(new String[]{"뉴진스", "가"}, WordSuggester.splitParticle("뉴진스가"));
        assertArrayEquals(new String[]{"고민", "임"}, WordSuggester.splitParticle("고민임"));
        assertNull(WordSuggester.splitParticle("짝남야"));   // 받침 뒤에 '야'는 조사가 아니다
    }

    @Test
    public void ambiguousParticleNeedsAnotherForm() {
        learn("길냥이");   // '이'는 낱말의 끝 글자일 수 있다: 길냥 + 이로 떼지 않는다
        learn("길냥이");
        assertTrue(user.isKnownIfLoaded("길냥이", true));
        assertFalse(user.isKnownIfLoaded("길냥", true));
        learn("냥이");     // 한 글자 앞부분(냥)이어도 학습한다
        learn("냥이");
        assertTrue(user.isKnownIfLoaded("냥이", true));
    }

    @Test
    public void particleFormsOfOneWordAddUp() {
        learn("짝남이");    // 처음에는 짝남이 그대로
        learn("짝남한테");  // 한테는 떼어 짝남. 짝남이를 쓴 적이 있으니 두 번째로 쳐서 바로 아는 말이 된다
        assertTrue(user.isKnownIfLoaded("짝남", true));
        assertTrue(WordSuggester.userKnows(user, "짝남이야"));   // 자동 수정하지 않는다
        learn("친구가");    // 사전 단어 + 조사는 학습한 단어에 넣지 않는다
        assertFalse(user.isKnownIfLoaded("친구", true));
    }

    @Test
    public void wordUsedOnceIsSuggestedUnlessItLooksLikeATypo() {
        KoreanSuggest m = new KoreanSuggest(dict, null, new TypoModel());
        learn("뉴진스");
        assertFalse(m.noteLearned("뉴진스"));
        assertTrue(Arrays.asList(m.suggest("뉴진", null, KoreanSuggest.FIELD_NORMAL, user, usage, null, 3).words)
                .contains("뉴진스"));
        learn("감사합니댜");   // 감사합니다의 오타: 한 번 쓴 것으로는 추천하지 않는다
        assertTrue(m.noteLearned("감사합니댜"));
        assertFalse(Arrays.asList(m.suggest("감사합니", null, KoreanSuggest.FIELD_NORMAL, user, usage, null, 3).words)
                .contains("감사합니댜"));
        assertEquals(1, usage.countIfLoaded("감사합니댜"));
    }
}
