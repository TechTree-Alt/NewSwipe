package com.alternative_studios.newswipe.suggest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;

public class UsageHistoryTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void countsWordsAndPairsAndPersists() throws Exception {
        File f = new File(tmp.getRoot(), "h.txt");
        UsageHistory h = new UsageHistory(f);
        h.load();
        h.record(null, "안녕", 1);
        h.record("안녕", "하세요", 2);
        h.record("안녕", "하세요", 3);
        h.record("안녕", "친구", 4);
        assertEquals(1, h.countIfLoaded("안녕"));
        assertEquals(2, h.countIfLoaded("하세요"));
        assertEquals(4, h.totalIfLoaded());
        assertEquals(2, h.pairCountIfLoaded("안녕", "하세요"));
        assertEquals(3, h.contextCountIfLoaded("안녕"));
        List<java.util.Map.Entry<String, Integer>> next = h.nextIfLoaded("안녕");
        assertEquals("하세요", next.get(0).getKey());
        assertEquals(2, (int) next.get(0).getValue());
        h.save();   // 바뀐 것이 10개가 안 되어도 저장은 된다

        UsageHistory again = new UsageHistory(f);
        assertEquals(0, again.countIfLoaded("하세요"));   // 읽기 전에는 모른다 (기다리지 않는다)
        again.load();
        assertEquals(2, again.countIfLoaded("하세요"));
        assertEquals(2, again.pairCountIfLoaded("안녕", "하세요"));
    }

    @Test
    public void snapshotRoundTripsThroughReplaceAll() throws Exception {
        UsageHistory a = new UsageHistory(new File(tmp.getRoot(), "a.txt"));
        a.load();
        a.record(null, "안녕", 1);
        a.record("안녕", "하세요", 2);
        a.recordIgnored("안녕", "친구", 3);
        List<String[]> rows = a.snapshot();
        rows.add(new String[]{"u", "깨진\t줄", "1", "1"});   // 탭이 든 칸은 버린다
        rows.add(new String[]{"x", "모름", "1", "1"});

        File f = new File(tmp.getRoot(), "b.txt");
        UsageHistory b = new UsageHistory(f);
        b.load();
        b.record(null, "지워질말", 1);
        assertEquals(2, b.replaceAll(rows));
        assertEquals(0, b.countIfLoaded("지워질말"));   // 전부 바꾼다
        assertEquals(1, b.countIfLoaded("하세요"));
        assertEquals(1, b.pairCountIfLoaded("안녕", "하세요"));
        assertEquals(1, b.ignoredIfLoaded("안녕", "친구"));
        assertEquals(2, b.totalIfLoaded());

        UsageHistory again = new UsageHistory(f);   // 파일에도 썼다
        again.load();
        assertEquals(1, again.pairCountIfLoaded("안녕", "하세요"));
    }

    @Test
    public void recordsBeforeLoadAreMerged() throws Exception {
        File f = new File(tmp.getRoot(), "h.txt");
        UsageHistory a = new UsageHistory(f);
        a.load();
        a.record(null, "하늘", 1);
        a.save();
        UsageHistory b = new UsageHistory(f);
        b.record("파란", "하늘", 2);
        b.load();
        assertEquals(2, b.countIfLoaded("하늘"));
        assertEquals(1, b.pairCountIfLoaded("파란", "하늘"));
    }

    @Test
    public void prefixMatchesPartialSyllable() throws Exception {
        UsageHistory h = new UsageHistory(new File(tmp.getRoot(), "h.txt"));
        h.load();
        h.record(null, "하늘", 1);
        h.record(null, "하늘", 2);
        h.record(null, "바다", 3);
        assertEquals(List.of("하늘"), h.prefixIfLoaded("ㅎ", 1));
        assertEquals(List.of("하늘"), h.prefixIfLoaded("한", 1));   // 받침 ㄴ이 다음 글자 첫소리일 수 있다
        assertTrue(h.prefixIfLoaded("하늘", 1).isEmpty());          // 입력한 그대로
        assertTrue(h.prefixIfLoaded("바", 2).isEmpty());            // 한 번만 쓴 말
    }

    @Test
    public void removeAndClear() throws Exception {
        File f = new File(tmp.getRoot(), "h.txt");
        UsageHistory h = new UsageHistory(f);
        h.load();
        h.record("파란", "하늘", 1);
        h.record("하늘", "아래", 2);
        h.remove("하늘");
        assertEquals(0, h.countIfLoaded("하늘"));
        assertEquals(0, h.pairCountIfLoaded("파란", "하늘"));
        assertEquals(0, h.contextCountIfLoaded("하늘"));
        assertEquals(1, h.countIfLoaded("아래"));
        h.clear();
        assertTrue(h.isLoaded());
        assertEquals(0, h.size());
        assertFalse(f.exists());
    }

    @Test
    public void trimsOldestWhenFull() throws Exception {
        UsageHistory h = new UsageHistory(new File(tmp.getRoot(), "h.txt"));
        h.load();
        for (int i = 0; i <= UsageHistory.MAX_WORDS; i++) h.record(null, "단어" + i, i);
        assertTrue(h.size() <= UsageHistory.MAX_WORDS);
        assertEquals(0, h.countIfLoaded("단어0"));   // 가장 오래된 것부터
        assertEquals(1, h.countIfLoaded("단어" + UsageHistory.MAX_WORDS));
        for (int i = 0; i <= UsageHistory.MAX_PAIRS; i++) h.record("앞" + i, "뒤", i);
        assertTrue(h.pairSize() <= UsageHistory.MAX_PAIRS);
    }
}
