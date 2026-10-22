package com.alternative_studios.newswipe.suggest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

/** 추천란에서 고른 단어를 기억해 자동 수정하기. */
public class CorrectionMemoryTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void correctsAfterThreePicks() {
        File f = new File(tmp.getRoot(), "p.txt");
        CorrectionMemory m = new CorrectionMemory(f);
        assertNull(m.lookupIfLoaded("굴자"));   // 아직 읽지 않았다
        m.load();
        m.record("굴자", "글자", 1);
        m.record("굴자", "글자", 2);
        assertNull(m.lookupIfLoaded("굴자"));
        m.record("굴자", "글자", 3);
        assertEquals("글자", m.lookupIfLoaded("굴자"));
        // 다시 읽어도 남는다
        CorrectionMemory again = new CorrectionMemory(f);
        again.load();
        assertEquals("글자", again.lookupIfLoaded("굴자"));
        assertTrue(again.forget("굴자"));
        assertNull(again.lookupIfLoaded("굴자"));
        assertFalse(again.forget("굴자"));
    }

    @Test
    public void otherWordStartsOver() {
        CorrectionMemory m = new CorrectionMemory(new File(tmp.getRoot(), "p.txt"));
        m.load();
        m.record("배일", "매일", 1);
        m.record("배일", "매일", 2);
        m.record("배일", "내일", 3);
        m.record("배일", "내일", 4);
        assertNull(m.lookupIfLoaded("배일"));
        m.record("배일", "내일", 5);
        assertEquals("내일", m.lookupIfLoaded("배일"));
        m.clear();
        assertNull(m.lookupIfLoaded("배일"));
    }

    @Test
    public void countsOnlyTypoFixes() throws Exception {
        WordDictionary dict = KoreanSuggestTest.buildDict(new String[][]{
                {"글자", "200"}, {"글자가", "150"}, {"이야기", "200"}, {"사람", "200"},
        });
        TypoModel typo = new TypoModel();
        assertTrue(WordSuggester.pickCounts(dict, null, typo, "굴자", "글자"));
        assertFalse(WordSuggester.pickCounts(dict, null, typo, "글자", "글자가"));   // 사전 단어 / 이어 쓴 말
        assertFalse(WordSuggester.pickCounts(dict, null, typo, "글", "글자"));       // 이어 쓴 말
        assertFalse(WordSuggester.pickCounts(dict, null, typo, "굴자", "사람"));     // 오타로 볼 수 없는 다른 말
        assertFalse(WordSuggester.pickCounts(dict, null, typo, "ㄱㅈ", "글자"));     // 초성
    }
}
