package com.alternative_studios.newswipe.suggest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/** 내가 쓴 어미(보여+줘서)를 다른 말(보내+줘서)에도 붙인다. 둘 이상의 말에 붙여 쓴 어미만. */
public class LearnedEndingTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void endingUsedWithTwoVerbsIsOfferedForAThird() throws Exception {
        WordDictionary dict = KoreanSuggestTest.buildDict(new String[][]{
                {"보여", "150"}, {"보여서", "120"}, {"보여요", "110"}, {"알려", "150"}, {"알려서", "120"}, {"알려요", "110"},
                {"보내", "160"}, {"보내서", "130"}, {"보내요", "120"}, {"가방", "150"}, {"가방이", "120"}, {"가고", "200"},
                {"가는", "200"}, {"가서", "180"},
        });
        KoreanSuggest m = new KoreanSuggest(dict, null, new TypoModel());
        UsageHistory h = new UsageHistory(new File(tmp.newFolder(), "h.txt"));
        h.load();
        m.learnEndings("보여줘서", h, 1);
        assertFalse(offered(m, h, "보내줘ㅅ", "보내줘서"));   // 한 말에서만 본 어미는 아직 붙이지 않는다
        m.learnEndings("알려줘서", h, 2);
        assertTrue(h.endingsIfLoaded(KoreanForms.END_AO, 2, 30).contains("줘서"));
        assertTrue(offered(m, h, "보내줘ㅅ", "보내줘서"));
        // 조사(가방+이)나 어간과 같은 아/어 꼴 뒤의 어간 어미(가+는)는 어미로 배우지 않는다
        m.learnEndings("가는", h, 3);
        m.learnEndings("가방이", h, 4);
        assertFalse(h.endingsIfLoaded(KoreanForms.END_AO, 1, 30).contains("는"));
        assertFalse(h.endingsIfLoaded(KoreanForms.END_STEM, 1, 30).contains("방이"));
    }

    private static boolean offered(KoreanSuggest m, UsageHistory h, String typed, String want) {
        List<String> w = Arrays.asList(m.suggest(typed, null, KoreanSuggest.FIELD_NORMAL, null, h, null, 3).words);
        return w.contains(want);
    }
}
