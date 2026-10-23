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

    @Test
    public void correctsToLearnedWord() throws Exception {
        WordDictionary dict = KoreanSuggestTest.buildDict(new String[][]{
                {"자동", "200"}, {"수정", "200"}, {"사람", "200"}, {"하고", "200"},
        });
        KoreanSuggest ks = new KoreanSuggest(dict, null, new TypoModel());
        UserDictionary user = new UserDictionary(new File(tmp.getRoot(), "w.txt"));
        user.load();
        TypoModel typo = new TypoModel();
        assertNull(WordSuggester.userFix(dict, ks, user, typo, "자덩수정"));   // 아직 모르는 말
        user.add("자동수정", true, false, 1);
        user.add("자동수정", true, false, 2);                                 // 두 번 쓰면 아는 말
        assertEquals("자동수정", WordSuggester.userFix(dict, ks, user, typo, "자덩수정"));   // ㅗ 아래 ㅓ
        assertEquals("자동수정을", WordSuggester.userFix(dict, ks, user, typo, "자덩수정을"));   // 조사는 다시 붙인다
        assertNull(WordSuggester.userFix(dict, ks, user, typo, "자동수정"));   // 그대로인 말
        assertNull(WordSuggester.userFix(dict, ks, user, typo, "자댕소정"));   // 키 두 개
        user.add("민수", true, true, 3);
        assertNull(WordSuggester.userFix(dict, ks, user, typo, "민우"));       // 두 글자 말(이름)은 고치지 않는다
        user.add("자던수정", true, true, 4);                                 // 자덩수정과 키 하나(ㅇ 옆 ㄴ) 다르다
        assertNull(WordSuggester.userFix(dict, ks, user, typo, "자덩수정"));   // 비슷한 학습한 말이 둘이면 고르지 않는다
    }
}
