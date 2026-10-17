package com.alternative_studios.newswipe.suggest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** 어간에 붙이는 어미: 자음으로 시작하는 어미, 동사에만 붙는 '-는' 계열, 매개모음 '-으-' 계열과 불규칙·ㄹ 탈락. */
public class GrammarFormsTest {
    private boolean more, verb, medial;
    private KoreanSuggest m;

    @Before
    public void setUp() throws Exception {
        more = KoreanForms.MORE_STEM;
        verb = KoreanForms.VERB_ENDS;
        medial = KoreanForms.MEDIAL;
        KoreanForms.MORE_STEM = KoreanForms.VERB_ENDS = KoreanForms.MEDIAL = true;
        WordDictionary d = KoreanSuggestTest.buildDict(new String[][]{
                // 붙잡다 (받침 ㅂ이지만 규칙: 붙잡아)
                {"붙잡고", "150"}, {"붙잡는", "140"}, {"붙잡지", "130"}, {"붙잡아", "140"},
                // 춥다 (ㅂ 불규칙 형용사: 추워, 추우면)
                {"춥고", "150"}, {"춥다", "160"}, {"춥지", "130"}, {"추워", "150"}, {"추우면", "120"}, {"추우니까", "110"},
                // 듣다 (ㄷ 불규칙: 들어, 들으면)
                {"듣고", "150"}, {"듣는", "150"}, {"듣지", "120"}, {"들어", "150"}, {"들으면", "120"}, {"들으려고", "110"},
                // 만들다 (ㄹ 어간: 만들면, 만드니까, 만드는)
                {"만들고", "150"}, {"만드는", "150"}, {"만들지", "130"}, {"만들면", "120"}, {"만드니까", "110"}, {"만드세요", "110"},
                // 좋다 (형용사: 좋으면, 좋으러 X)
                {"좋고", "200"}, {"좋지", "180"}, {"좋다", "200"}, {"좋아", "200"}, {"좋은", "200"},
                // 없다 (-는은 붙지만 동작의 어미는 X), 갔다 (과거)
                {"없고", "200"}, {"없는", "200"}, {"없지", "180"}, {"없어", "200"},
                {"갔어", "200"}, {"갔다", "200"}, {"갔지", "150"},
        });
        m = new KoreanSuggest(d, null, new TypoModel());
    }

    @After
    public void tearDown() {
        KoreanForms.MORE_STEM = more;
        KoreanForms.VERB_ENDS = verb;
        KoreanForms.MEDIAL = medial;
    }

    private List<String> made(String typed) {
        return Arrays.asList(m.suggest(typed, null, KoreanSuggest.FIELD_NORMAL, null, null, null, 200).words);
    }

    @Test
    public void consonantEndingsAttachToAnyStem() {
        assertTrue(made("붙잡").contains("붙잡기"));
        assertTrue(made("붙잡").contains("붙잡거나"));
        assertTrue(made("춥").contains("춥도록"));
    }

    @Test
    public void neunEndingsOnlyForVerbs() {
        assertTrue(made("붙잡").contains("붙잡는데"));
        assertTrue(made("만드").contains("만드는지"));   // ㄹ 어간: ㄴ 앞에서 ㄹ이 떨어진다
        assertFalse(made("춥").contains("춥는데"));       // 형용사에는 '-는'이 없다
        assertFalse(made("좋").contains("좋는데"));
    }

    @Test
    public void medialVowelFollowsStemClass() {
        assertTrue(made("붙잡").contains("붙잡으면"));    // 받침 ㅂ이지만 사전에 '붙잡아'가 있어 규칙
        assertFalse(made("춥").contains("춥으면"));       // ㅂ 불규칙: 춥으면 X (추우면)
        assertTrue(made("추우").contains("추우면서"));    // 바뀐 꼴을 치면 같은 줄기로
        assertFalse(made("듣").contains("듣으면"));       // ㄷ 불규칙: 듣으면 X (들으면)
        assertTrue(made("들으").contains("들으니까"));
        assertTrue(made("들으").contains("들을까"));
        assertTrue(made("만들").contains("만들려고"));    // ㄹ 어간: '으' 없이, ㄴ·ㅅ 앞에서는 ㄹ이 떨어진다
        assertTrue(made("만드").contains("만드시면"));
        assertFalse(made("만드").contains("만드면"));     // 만드니까·만드세요만 보고 '만드'에 -면을 붙이지 않는다
        assertTrue(made("좋").contains("좋으면서"));
        assertFalse(made("좋").contains("좋으러"));       // 목적·의도·약속은 동사에만 (좋으러·좋을게 X)
        assertFalse(made("좋").contains("좋을게"));
    }

    @Test
    public void documentedEndings() {
        assertTrue(made("갔").contains("갔는지"));        // 과거 -었- 뒤에도 '-는'
        assertTrue(made("좋").contains("좋은데"));        // 형용사 -(으)ㄴ데
        assertFalse(made("붙잡").contains("붙잡은데"));   // 동사는 -는데
        assertFalse(made("들으").contains("들은데"));     // 바뀐 꼴은 형용사인지 모르면 -(으)ㄴ데를 붙이지 않는다
        assertTrue(made("붙잡").contains("붙잡는대"));    // 동사 -는다/-ㄴ다
        assertTrue(made("만드").contains("만든대"));      // ㄹ은 종성 ㄴ 앞에서도 떨어진다
        assertFalse(made("춥").contains("춥는대"));
        assertTrue(made("춥").contains("춥습니다"));      // -습니다는 자음 어미라 불규칙과 상관없다
        assertTrue(made("만드").contains("만듭니다"));    // ㄹ은 종성 ㅂ 앞에서도 떨어진다
        assertTrue(made("들으").contains("들읍시다"));    // -(으)ㅂ시다는 매개모음 꼴에 붙는다 (듣읍시다 X)
        assertFalse(made("듣").contains("듣읍시다"));
        assertTrue(made("좋").contains("좋구나"));
        assertTrue(made("붙잡").contains("붙잡는구나"));
        assertFalse(made("없").contains("없는다"));       // 없다: -는은 붙지만 동작의 어미는 붙지 않는다
        assertFalse(made("없").contains("없은데"));
        assertTrue(made("없").contains("없는구나"));
    }
}
