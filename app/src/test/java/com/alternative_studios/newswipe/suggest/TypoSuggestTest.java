package com.alternative_studios.newswipe.suggest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** 오타가 섞인 채 치는 중의 추천: 드문 말도 찾고, 빠른 접두사 비용 계산은 예전 계산과 같다. */
public class TypoSuggestTest {
    @Test
    public void prefixScanMatchesPrefixDistance() {
        TypoModel tm = new TypoModel();
        Random r = new Random(3);
        String syl = "가각간갈감강개거걸게고공과구국그기나난내너네노누는다달대더도동되두드들디라래러로루리마만매머메모무문미바반"
                + "배버보부비사산상새서선세소수스시아안애야어언에여연오와요우워의이인일자잘재저정제조주지차채처청체초추치";
        for (int round = 0; round < 300; round++) {
            String input = word(r, syl, 2 + r.nextInt(2));
            char[] p = TypoModel.letters(input);
            TypoModel.PrefixScan scan = tm.prefixScan(p, p.length, 1.0f);
            String[] words = new String[40];
            for (int i = 0; i < words.length; i++) {
                // 앞부분이 겹치는 말이 이어지게 (입력과 같은 첫 글자, 자주 같은 둘째 글자)
                words[i] = input.charAt(0) + (r.nextBoolean() ? String.valueOf(input.charAt(1)) : "") + word(r, syl, 1 + r.nextInt(3));
            }
            Arrays.sort(words);
            for (String w : words) {
                char[] t = TypoModel.letters(w);
                float slow = tm.prefixDistance(p, p.length, t, t.length, 1.0f);
                float fast = scan.cost(t, t.length);
                if (slow <= 1.0f || fast <= 1.0f) assertEquals(input + " → " + w, slow, fast, 1e-5f);
            }
        }
    }

    private static String word(Random r, String syl, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(syl.charAt(r.nextInt(syl.length())));
        return b.toString();
    }

    @Test
    public void rareWordsFoundThroughTypos() throws Exception {
        WordDictionary d = KoreanSuggestTest.buildDict(new String[][]{
                {"제주도", "60"}, {"제주", "50"}, {"재미", "200"}, {"재미있다", "180"}, {"자주", "220"},
                {"챙길", "40"}, {"챙기다", "60"}, {"체크", "150"}, {"처음", "220"}, {"생일", "50"}, {"세일", "120"},
        });
        KoreanSuggest m = new KoreanSuggest(d, null, new TypoModel());
        assertTrue(shown(m, "재주도").contains("제주도"));   // ㅐ ↔ ㅔ
        assertTrue(shown(m, "제주더").contains("제주도"));   // ㅗ 옆의 ㅓ
        assertTrue(shown(m, "쳉길").contains("챙길"));
    }

    private static List<String> shown(KoreanSuggest m, String typed) {
        return Arrays.asList(m.suggest(typed, null, KoreanSuggest.FIELD_NORMAL, null, null, null, 3).words);
    }
}
