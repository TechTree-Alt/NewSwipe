package com.alternative_studios.newswipe.suggest;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

/** 내 기록의 문맥(앞 단어의 끝 글자), 어미 버릇, 최근 단어를 다른 말에도 옮겨 쓴다. */
public class UserContextTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static final String[][] WORDS = {
            {"보고", "200"}, {"먹고", "200"}, {"자고", "180"}, {"싶어", "150"}, {"싶다", "160"}, {"싶은", "160"},
            {"싶고", "120"}, {"싶지", "110"}, {"있어", "190"}, {"있는", "200"}, {"있다", "200"}, {"있고", "160"},
            {"있지", "120"}, {"없어", "150"}, {"없는", "200"}, {"없다", "200"}, {"없고", "150"}, {"없지", "110"},
            {"같아", "150"}, {"같은", "200"}, {"같다", "190"}, {"같고", "150"}, {"같지", "120"}, {"하나", "170"},
            {"하늘", "160"}, {"오늘", "200"},
    };

    private WordDictionary dict() throws Exception {
        return KoreanSuggestTest.buildDict(WORDS);
    }

    private UsageHistory history() throws Exception {
        UsageHistory h = new UsageHistory(new File(tmp.newFolder(), "h.txt"));
        h.load();
        return h;
    }

    private UserDictionary user() throws Exception {
        UserDictionary u = new UserDictionary(new File(tmp.newFolder(), "u.txt"));
        u.load();
        return u;
    }

    @Test
    public void endingOfPreviousWordCarriesToNewWords() throws Exception {
        WordDictionary d = dict();
        KoreanSuggest m = new KoreanSuggest(d, null, new TypoModel());
        UsageHistory h = history();
        UserDictionary u = user();
        // '보고 싶어', '먹고 싶어': '-고' 다음에 '싶어'를 쓴다
        WordSuggester.learnInto(d, u, h, "싶어", "보고", true, false, 1);
        WordSuggester.learnInto(d, u, h, "싶어", "먹고", true, false, 2);
        assertEquals(2, h.pairCountIfLoaded(KoreanSuggest.endKey("보고"), "싶어"));
        // 한 번도 쓰지 않은 '자고' 다음에도 예측한다
        KoreanSuggest.Result r = m.predict("자고", KoreanSuggest.FIELD_NORMAL, u, h, 3);
        assertEquals("싶어", r.words[0]);
        // 기록이 없으면 사전만으로는 예측하지 않는다
        assertEquals(0, m.predict("자고", KoreanSuggest.FIELD_NORMAL, null, null, 3).words.length);
    }

    @Test
    public void endingHabitCarriesToOtherStems() throws Exception {
        WordDictionary d = dict();
        KoreanSuggest m = new KoreanSuggest(d, null, new TypoModel());
        UsageHistory h = history();
        String before = m.suggest("있", null, KoreanSuggest.FIELD_NORMAL, null, h, null, 3).words[0];
        assertNotEquals("있어", before);
        // 다른 어간(없, 싶)에 '-어'를 즐겨 붙인다 (있다보다 있어를 앞세운다. 조사와 같은 꼬리 -는·-고·-지는 어미로 보지 않는다)
        for (int i = 0; i < 40; i++) {
            m.learnEndings("없어", h, 2 * i);
            m.learnEndings("싶어", h, 2 * i + 1);
        }
        assertEquals("있어", m.suggest("있", null, KoreanSuggest.FIELD_NORMAL, null, h, null, 3).words[0]);
    }

    @Test
    public void recentWordsWindowAndClear() throws Exception {
        UsageHistory h = history();
        for (int i = 0; i < UsageHistory.RECENT; i++) h.noteRecent("하늘");
        h.noteRecent("오늘");
        assertEquals(UsageHistory.RECENT, h.recentSize());
        assertEquals(UsageHistory.RECENT - 1, h.recentCount("하늘"));
        assertEquals(1, h.recentCount("오늘"));
        h.remove("하늘");   // 학습한 단어를 지우면 최근 단어에서도 지운다
        assertEquals(0, h.recentCount("하늘"));
        assertEquals(1, h.recentSize());
        h.clear();
        assertEquals(0, h.recentSize());
        assertEquals(0, h.recentCount("오늘"));
    }

    @Test
    public void contextStatsFollowRecords() throws Exception {
        UsageHistory h = history();
        assertArrayEquals(new int[]{0, 0}, h.contextStatsIfLoaded("~고"));
        h.recordContext("~고", "싶어", 1);
        h.recordContext("~고", "싶어", 2);
        h.recordContext("~고", "있어", 3);
        assertArrayEquals(new int[]{3, 2}, h.contextStatsIfLoaded("~고"));
        h.recordContext("~고", "싶다", 4);   // 담아 둔 값이 바뀐다
        assertArrayEquals(new int[]{4, 3}, h.contextStatsIfLoaded("~고"));
        h.remove("싶어");
        assertArrayEquals(new int[]{2, 2}, h.contextStatsIfLoaded("~고"));
        assertEquals(2, h.nextIfLoaded("~고", 1).size());
        assertEquals(0, h.nextIfLoaded("~고", 2).size());
    }

    @Test
    public void ignoredPredictionsAreShownLessAndForgiven() throws Exception {
        WordDictionary d = dict();
        KoreanSuggest m = new KoreanSuggest(d, null, new TypoModel());
        File dir = tmp.newFolder();
        UsageHistory h = new UsageHistory(new File(dir, "h.txt"));
        h.load();
        UserDictionary u = user();
        for (int i = 0; i < 3; i++) WordSuggester.learnInto(d, u, h, "싶어", "보고", true, false, i);
        assertEquals("싶어", m.predict("보고", KoreanSuggest.FIELD_NORMAL, u, h, 3).words[0]);
        // '보고' 다음에 '싶어'를 보였는데 '싶다'를 쳤다: 그 예측을 덜 보인다
        double factor = KoreanSuggest.IGNORED_FACTOR;
        KoreanSuggest.IGNORED_FACTOR = 0.5;
        try {
            double before = m.predict("보고", KoreanSuggest.FIELD_NORMAL, u, h, 3).confidence;
            WordSuggester.noteIgnoredInto(h, "보고", new String[]{"싶어"}, "싶다", 10);
            assertEquals(1, h.ignoredIfLoaded("보고", "싶어"));
            KoreanSuggest.Result r = m.predict("보고", KoreanSuggest.FIELD_NORMAL, u, h, 3);
            assertTrue(r.words.length == 0 || r.confidence < before);
        } finally {
            KoreanSuggest.IGNORED_FACTOR = factor;
        }
        // 고른 예측이나 직접 쳐도 맞힌 예측은 기록하지 않는다
        WordSuggester.noteIgnoredInto(h, "보고", new String[]{"싶어"}, "싶어", 11);
        assertEquals(1, h.ignoredIfLoaded("보고", "싶어"));
        // 저장했다 다시 읽어도 남는다 (예전 버전은 모르는 i 줄을 건너뛴다)
        h.save();
        UsageHistory h2 = new UsageHistory(new File(dir, "h.txt"));
        h2.load();
        assertEquals(1, h2.ignoredIfLoaded("보고", "싶어"));
        // 그 앞 단어 다음에 실제로 쓰면 지운다
        h2.record("보고", "싶어", 12);
        assertEquals(0, h2.ignoredIfLoaded("보고", "싶어"));
    }
}
