package com.alternative_studios.newswipe.cjk;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 변환 후보에서 고른 글자의 기록 (읽기 → 고른 글자 → 횟수). 같은 읽기를 치면 자주 고른 글자를 후보 맨 앞에 놓고,
 * 친 읽기로 시작하는 더 긴 읽기에서 자주 고른 글자도 그 뒤에 놓는다 (예측). 언어(ja·zh)마다 파일 하나에 저장한다.
 *
 * <p>안드로이드에 기대지 않는다. 파일은 부르는 쪽이 정한 때에 {@link #save()}로 쓴다.
 */
public final class CjkLearning {
    /** 기억하는 (읽기, 글자) 쌍의 상한. 넘으면 오래전에 쓴 것부터 지운다. */
    public static final int MAX_ENTRIES = 3000;
    /** 예측으로 앞에 놓는 후보 수. */
    static final int MAX_PREDICT = 3;
    private static final int MAX_COUNT = 10000;

    private static final class Entry {
        int count;
        long last;

        Entry(int count, long last) {
            this.count = count;
            this.last = last;
        }
    }

    private final File file;
    /** 읽기 → 글자 → 기록. 읽기 순이라 앞부분이 같은 읽기를 구간으로 찾는다. */
    private final TreeMap<String, Map<String, Entry>> byReading = new TreeMap<>();
    private int size;
    private long clock;
    private boolean dirty;

    /** file이 null이면 메모리에만 둔다 (시험용). */
    public CjkLearning(File file) {
        this.file = file;
        load();
    }

    /** reading을 text로 바꿨다. */
    public synchronized void record(String reading, String text) {
        if (reading == null || text == null || reading.isEmpty() || text.isEmpty()) return;
        Map<String, Entry> m = byReading.computeIfAbsent(reading, k -> new HashMap<>());
        Entry e = m.get(text);
        if (e == null) {
            m.put(text, new Entry(1, ++clock));
            size++;
            if (size > MAX_ENTRIES) evictOldest();
        } else {
            e.count = Math.min(MAX_COUNT, e.count + 1);
            e.last = ++clock;
        }
        dirty = true;
    }

    /** 이 읽기에서 고른 기록을 모두 지운다. */
    public synchronized void clear() {
        byReading.clear();
        size = 0;
        dirty = true;
    }

    /**
     * cands를 기록대로 다시 늘어놓는다: reading을 그대로 바꾼 적 있는 글자(자주·최근 고른 것부터), reading으로 시작하는 더 긴
     * 읽기에서 고른 글자 몇 개(예측, 읽기 전체를 쓴다), 그다음 원래 순서의 나머지. 기록이 없으면 cands를 그대로 돌려준다.
     */
    public synchronized List<Candidate> reorder(String reading, List<Candidate> cands, int max) {
        if (reading.isEmpty() || byReading.isEmpty()) return cands;
        List<Candidate> front = new ArrayList<>();
        Set<String> used = new HashSet<>();
        Map<String, Entry> exact = byReading.get(reading);
        if (exact != null) {
            for (String t : sorted(exact)) {
                if (used.add(t)) front.add(new Candidate(t, reading.length()));
            }
        }
        // 더 긴 읽기에서 고른 글자 (예측): 횟수가 많은 것부터
        List<Object[]> longer = new ArrayList<>();
        for (Map.Entry<String, Map<String, Entry>> r : byReading.tailMap(reading, false).entrySet()) {
            if (!r.getKey().startsWith(reading)) break;
            for (Map.Entry<String, Entry> x : r.getValue().entrySet()) longer.add(new Object[]{x.getKey(), x.getValue()});
        }
        longer.sort((a, b) -> compare((Entry) a[1], (Entry) b[1]));
        int predicted = 0;
        for (Object[] x : longer) {
            if (predicted >= MAX_PREDICT) break;
            if (used.add((String) x[0])) {
                front.add(new Candidate((String) x[0], reading.length()));
                predicted++;
            }
        }
        if (front.isEmpty()) return cands;
        List<Candidate> out = new ArrayList<>(front);
        for (Candidate c : cands) {
            if (out.size() >= max) break;
            if (used.add(c.text)) out.add(c);
        }
        return out;
    }

    private static List<String> sorted(Map<String, Entry> m) {
        List<Map.Entry<String, Entry>> l = new ArrayList<>(m.entrySet());
        l.sort((a, b) -> compare(a.getValue(), b.getValue()));
        List<String> out = new ArrayList<>(l.size());
        for (Map.Entry<String, Entry> e : l) out.add(e.getKey());
        return out;
    }

    /** 자주 고른 것부터, 같으면 최근에 고른 것부터. */
    private static int compare(Entry a, Entry b) {
        if (a.count != b.count) return Integer.compare(b.count, a.count);
        return Long.compare(b.last, a.last);
    }

    private void evictOldest() {
        String oldR = null, oldT = null;
        long oldest = Long.MAX_VALUE;
        for (Map.Entry<String, Map<String, Entry>> r : byReading.entrySet()) {
            for (Map.Entry<String, Entry> x : r.getValue().entrySet()) {
                if (x.getValue().last < oldest) {
                    oldest = x.getValue().last;
                    oldR = r.getKey();
                    oldT = x.getKey();
                }
            }
        }
        if (oldR == null) return;
        Map<String, Entry> m = byReading.get(oldR);
        m.remove(oldT);
        if (m.isEmpty()) byReading.remove(oldR);
        size--;
    }

    // ---------------------------------------------------------------- 파일 (한 줄에 읽기\t글자\t횟수, 오래된 것부터)

    private void load() {
        if (file == null || !file.isFile()) return;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split("\t");
                if (f.length != 3 || f[0].isEmpty() || f[1].isEmpty()) continue;
                int n;
                try {
                    n = Math.max(1, Math.min(MAX_COUNT, Integer.parseInt(f[2])));
                } catch (NumberFormatException e) {
                    continue;
                }
                Map<String, Entry> m = byReading.computeIfAbsent(f[0], k -> new HashMap<>());
                if (m.put(f[1], new Entry(n, ++clock)) == null) size++;
            }
        } catch (IOException ignored) {
            // 읽지 못하면 빈 기록으로 시작한다
        }
        while (size > MAX_ENTRIES) evictOldest();
    }

    /** 바뀐 것이 있으면 파일에 쓴다. */
    public void save() {
        List<String[]> rows = new ArrayList<>();
        synchronized (this) {
            if (file == null || !dirty) return;
            dirty = false;
            List<Object[]> all = new ArrayList<>();
            for (Map.Entry<String, Map<String, Entry>> r : byReading.entrySet()) {
                for (Map.Entry<String, Entry> x : r.getValue().entrySet()) all.add(new Object[]{r.getKey(), x.getKey(), x.getValue()});
            }
            all.sort((a, b) -> Long.compare(((Entry) a[2]).last, ((Entry) b[2]).last));
            for (Object[] a : all) rows.add(new String[]{(String) a[0], (String) a[1], String.valueOf(((Entry) a[2]).count)});
        }
        File tmp = new File(file.getPath() + ".tmp");
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8))) {
            for (String[] r : rows) {
                if (r[0].indexOf('\t') >= 0 || r[1].indexOf('\t') >= 0 || r[1].indexOf('\n') >= 0) continue;
                w.write(r[0]);
                w.write('\t');
                w.write(r[1]);
                w.write('\t');
                w.write(r[2]);
                w.write('\n');
            }
        } catch (IOException e) {
            synchronized (this) {
                dirty = true;
            }
            return;
        }
        if (!tmp.renameTo(file)) {
            synchronized (this) {
                dirty = true;
            }
        }
    }
}
