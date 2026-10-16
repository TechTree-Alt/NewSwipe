package com.alternative_studios.newswipe.suggest;

import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

/** 쓴 기록의 앞부분 찾기(사전 순 색인)가 모든 단어를 훑던 예전 방식과 같은 결과를 내는지. */
public class UsageHistoryIndexTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void prefixLookupMatchesFullScan() throws Exception {
        UsageHistory h = new UsageHistory(new File(tmp.newFolder(), "h.txt"));
        h.load();
        Random rnd = new Random(1);
        // 받침·겹받침·겹모음이 섞이도록 좁은 글자 집합에서 단어를 만든다
        String syll = "가각갃간갈갉감갑값갓강개과괘괴구궈귀그긔기나낙난날다닭달라락사삭산살앉안않하학한할핫";
        List<String> words = new ArrayList<>();
        for (int i = 0; i < 3000; i++) {
            StringBuilder b = new StringBuilder();
            int n = 1 + rnd.nextInt(4);
            for (int k = 0; k < n; k++) b.append(syll.charAt(rnd.nextInt(syll.length())));
            words.add(b.toString());
            h.record(null, b.toString(), i);
            if (rnd.nextInt(3) == 0) h.record(null, b.toString(), i);
        }
        String jamo = "ㄱㄴㄷㄹㅅㅎㅏㅗ";
        for (int i = 0; i < 2000; i++) {
            String w = words.get(rnd.nextInt(words.size()));
            String p = w.substring(0, 1 + rnd.nextInt(w.length()));
            if (rnd.nextInt(4) == 0) p = p + jamo.charAt(rnd.nextInt(jamo.length()));
            for (int min = 1; min <= 2; min++) {
                TreeSet<String> expect = new TreeSet<>();
                for (String x : new TreeSet<>(words)) {
                    if (!x.equals(p) && h.countIfLoaded(x) >= min && HangulRange.startsWith(x, p)) expect.add(x);
                }
                assertEquals(p, expect, new TreeSet<>(h.prefixIfLoaded(p, min)));
            }
        }
        h.remove(words.get(0));
        assertEquals(new TreeSet<String>(), filter(h.prefixIfLoaded(words.get(0), 1), words.get(0)));
    }

    private static TreeSet<String> filter(List<String> found, String removed) {
        TreeSet<String> out = new TreeSet<>();
        for (String w : found) if (w.equals(removed)) out.add(w);
        return out;
    }
}
