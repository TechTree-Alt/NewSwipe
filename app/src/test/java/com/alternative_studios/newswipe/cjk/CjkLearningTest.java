package com.alternative_studios.newswipe.cjk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CjkLearningTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static List<Candidate> cands(int consumed, String... texts) {
        List<Candidate> out = new ArrayList<>();
        for (String t : texts) out.add(new Candidate(t, consumed));
        return out;
    }

    private static List<String> texts(List<Candidate> l) {
        List<String> out = new ArrayList<>();
        for (Candidate c : l) out.add(c.text);
        return out;
    }

    @Test
    public void noHistoryKeepsOrder() {
        CjkLearning l = new CjkLearning(null);
        List<Candidate> c = cands(3, "機能", "昨日", "きのう");
        assertSame(c, l.reorder("きのう", c, 80));
    }

    @Test
    public void frequentPickComesFirst() {
        CjkLearning l = new CjkLearning(null);
        l.record("きのう", "昨日");
        l.record("きのう", "昨日");
        l.record("きのう", "帰納");
        List<Candidate> out = l.reorder("きのう", cands(3, "機能", "昨日", "きのう"), 80);
        assertEquals(Arrays.asList("昨日", "帰納", "機能", "きのう"), texts(out));
        assertEquals(3, out.get(0).consumed);
    }

    @Test
    public void longerReadingsArePredicted() {
        CjkLearning l = new CjkLearning(null);
        l.record("ありがとう", "有難う");
        List<Candidate> out = l.reorder("ありが", cands(3, "ありが"), 80);
        assertEquals(Arrays.asList("有難う", "ありが"), texts(out));
        assertEquals(3, out.get(0).consumed);   // 지금 친 읽기를 다 쓴다
    }

    @Test
    public void savesAndLoads() throws Exception {
        File f = new File(tmp.getRoot(), "learn.txt");
        CjkLearning l = new CjkLearning(f);
        l.record("きのう", "昨日");
        l.save();
        CjkLearning back = new CjkLearning(f);
        assertEquals("昨日", back.reorder("きのう", cands(3, "機能", "昨日"), 80).get(0).text);
    }

    @Test
    public void evictsOldestOverLimit() {
        CjkLearning l = new CjkLearning(null);
        for (int i = 0; i <= CjkLearning.MAX_ENTRIES; i++) l.record("r" + i, "t");
        List<Candidate> c = cands(2, "x");
        assertSame(c, l.reorder("r0", c, 80));   // 가장 오래된 기록이 지워졌다
    }
}
