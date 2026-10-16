package com.alternative_studios.newswipe.suggest;

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
import java.util.List;
import java.util.Map;

/**
 * 사용자가 실제로 쓴 한국어 단어와 단어 쌍(앞 단어 → 다음 단어, 앞 단어의 끝 글자 → 다음 단어)의 횟수. 추천 순서를 내
 * 말버릇에 맞추는 데 쓴다. 최근에 쓴 단어 몇 개는 메모리에만 둔다 (지금 대화의 주제어).
 * 기본 사전에 있는 단어도 센다 ({@link UserDictionary}는 사전에 없는 단어만 '아는 단어'로 기억한다).
 * 기기 안의 파일에만 저장하며 백업에도 넣지 않는다. 학습한 단어를 모두 지우면 함께 지운다.
 *
 * <p>화면 스레드는 *IfLoaded 메서드로 메모리에 올라온 것만 보고 기다리지 않는다. 파일은 백그라운드 스레드에서 읽고 쓴다.
 */
public final class UsageHistory {
    public static final int MAX_WORDS = 4000, MAX_PAIRS = 6000;
    private static final int MAX_COUNT = 100000;

    private static final class Entry {
        int count;
        long last;

        Entry(int count, long last) {
            this.count = count;
            this.last = last;
        }
    }

    private static UsageHistory shared;

    /** 키보드 서비스와 설정 화면이 같은 프로세스에서 하나를 같이 쓴다. */
    public static synchronized UsageHistory shared(File filesDir) {
        if (shared == null) shared = new UsageHistory(new File(filesDir, "usage.txt"));
        return shared;
    }

    private final File file;
    private final Object fileLock = new Object();
    /** 단어 → 횟수. 사전 순이라 입력 중인 앞부분으로 찾을 때 모두 훑지 않고 구간만 본다. */
    private java.util.TreeMap<String, Entry> words = new java.util.TreeMap<>();
    private Map<String, Map<String, Entry>> pairs = new HashMap<>();
    /** 앞 단어(문맥)마다 {쓴 횟수 합, 다른 단어 수} (contextStatsIfLoaded가 키마다 모두 세지 않게). 단어 쌍이 바뀌면 비운다. */
    private final Map<String, int[]> ctxStats = new HashMap<>();
    private int pairCount;
    /**
     * 내가 쓴 어미: "종류:어미" → 그 어미를 붙인 앞 꼴 → 횟수 (보여+줘서, 했+는데). 앞 꼴이 둘 이상인 어미만 다른 말에도 붙인다
     * ({@link KoreanSuggest}의 END_*).
     */
    private Map<String, Map<String, Entry>> endings = new HashMap<>();
    private int endingCount;
    public static final int MAX_ENDINGS = 3000;
    /** "종류:어미" → 쓴 횟수(앞 꼴을 모두 더해), "종류" → 그 종류의 합. 어미 기록이 바뀌면 비운다. */
    private Map<String, Integer> endingTotals;
    /** 최근에 쓴 단어 (메모리에만, 많아야 RECENT개, 오래된 것부터)와 그 횟수. */
    public static final int RECENT = 32;
    private final java.util.ArrayDeque<String> recent = new java.util.ArrayDeque<>();
    private final Map<String, Integer> recentCount = new HashMap<>();
    /** endingsIfLoaded의 결과 (종류마다). 어미 기록이 바뀌면 비운다 (키마다 모두 훑지 않게). */
    private final Object[] endingCache = new Object[3];
    private int cacheMinBases = -1, cacheMax = -1;
    private long total;
    private boolean loaded, dirty;
    private int sinceSave;
    private int generation;

    UsageHistory(File file) {
        this.file = file;
    }

    private static final class Data {
        final java.util.TreeMap<String, Entry> words = new java.util.TreeMap<>();
        final Map<String, Map<String, Entry>> pairs = new HashMap<>();
        int pairCount;
        final Map<String, Map<String, Entry>> endings = new HashMap<>();
        int endingCount;
    }

    private Data readFile() {
        Data d = new Data();
        if (!file.exists()) return d;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split("\t");
                try {
                    if (p.length == 4 && p[0].equals("u") && !p[1].isEmpty() && d.words.size() < MAX_WORDS) {
                        d.words.put(p[1], new Entry(clampCount(Integer.parseInt(p[2])), Long.parseLong(p[3])));
                    } else if (p.length == 5 && p[0].equals("e") && !p[1].isEmpty() && !p[2].isEmpty()
                            && d.endingCount < MAX_ENDINGS) {
                        Map<String, Entry> m = d.endings.computeIfAbsent(p[1], k -> new HashMap<>());
                        if (m.put(p[2], new Entry(clampCount(Integer.parseInt(p[3])), Long.parseLong(p[4]))) == null) {
                            d.endingCount++;
                        }
                    } else if (p.length == 5 && p[0].equals("p") && !p[1].isEmpty() && !p[2].isEmpty()
                            && d.pairCount < MAX_PAIRS) {
                        Map<String, Entry> m = d.pairs.computeIfAbsent(p[1], k -> new HashMap<>());
                        if (m.put(p[2], new Entry(clampCount(Integer.parseInt(p[3])), Long.parseLong(p[4]))) == null) {
                            d.pairCount++;
                        }
                    }
                } catch (NumberFormatException ignored) {
                    // 깨진 줄은 건너뛴다.
                }
            }
        } catch (IOException ignored) {
            // 읽은 데까지만 쓴다.
        }
        return d;
    }

    private static int clampCount(int c) {
        return Math.max(1, Math.min(c, MAX_COUNT));
    }

    /** 파일을 읽어 메모리에 올린다. 백그라운드 스레드에서 부른다. */
    public void load() {
        int gen;
        synchronized (this) {
            if (loaded) return;
            gen = generation;
        }
        Data d;
        synchronized (fileLock) {
            d = readFile();
        }
        synchronized (this) {
            if (loaded || gen != generation) return;
            // 읽는 동안 들어온 기록(record)을 합친다.
            for (Map.Entry<String, Entry> me : words.entrySet()) merge(d.words, me.getKey(), me.getValue());
            for (Map.Entry<String, Map<String, Entry>> pe : pairs.entrySet()) {
                Map<String, Entry> m = d.pairs.computeIfAbsent(pe.getKey(), k -> new HashMap<>());
                for (Map.Entry<String, Entry> me : pe.getValue().entrySet()) {
                    if (merge(m, me.getKey(), me.getValue())) d.pairCount++;
                }
            }
            for (Map.Entry<String, Map<String, Entry>> ee : endings.entrySet()) {
                Map<String, Entry> m = d.endings.computeIfAbsent(ee.getKey(), k -> new HashMap<>());
                for (Map.Entry<String, Entry> me : ee.getValue().entrySet()) {
                    if (merge(m, me.getKey(), me.getValue())) d.endingCount++;
                }
            }
            endings = d.endings;
            endingCount = d.endingCount;
            java.util.Arrays.fill(endingCache, null);
            endingTotals = null;
            words = d.words;
            pairs = d.pairs;
            ctxStats.clear();
            pairCount = d.pairCount;
            total = 0;
            for (Entry e : words.values()) total += e.count;
            loaded = true;
            trimIfNeeded();
        }
    }

    /** 새 항목이면 true. */
    private static boolean merge(Map<String, Entry> into, String key, Entry e) {
        Entry old = into.get(key);
        if (old == null) {
            into.put(key, new Entry(e.count, e.last));
            return true;
        }
        old.count = clampCount(old.count + e.count);
        old.last = Math.max(old.last, e.last);
        return false;
    }

    public synchronized boolean isLoaded() {
        return loaded;
    }

    /**
     * 단어를 한 번 썼다고 기록한다 (prev: 바로 앞 단어, 없으면 null). 읽기 전이어도 기록해 두었다가 읽을 때 합친다.
     * 파일은 건드리지 않는다 (needsSave()이면 백그라운드에서 save()).
     */
    public synchronized void record(String prev, String word, long now) {
        if (word == null || word.isEmpty()) return;
        Entry e = words.get(word);
        if (e == null) words.put(word, new Entry(1, now));
        else {
            e.count = clampCount(e.count + 1);
            e.last = now;
        }
        total++;
        if (prev != null && !prev.isEmpty()) {
            ctxStats.remove(prev);
            Map<String, Entry> m = pairs.computeIfAbsent(prev, k -> new HashMap<>());
            Entry pe = m.get(word);
            if (pe == null) {
                m.put(word, new Entry(1, now));
                pairCount++;
            } else {
                pe.count = clampCount(pe.count + 1);
                pe.last = now;
            }
        }
        dirty = true;
        sinceSave++;
        if (loaded) trimIfNeeded();
    }

    /** 최근 단어에 넣는다 (record와 함께 부른다. 파일에는 쓰지 않는다). */
    public synchronized void noteRecent(String word) {
        if (word == null || word.isEmpty()) return;
        recent.addLast(word);
        recentCount.merge(word, 1, Integer::sum);
        while (recent.size() > RECENT) {
            String old = recent.removeFirst();
            recentCount.computeIfPresent(old, (k, v) -> v > 1 ? v - 1 : null);
        }
    }

    /** 화면 스레드용: 최근 단어 수. */
    public synchronized int recentSize() {
        return recent.size();
    }

    /** 화면 스레드용: 최근 단어 중 word의 수. */
    public synchronized int recentCount(String word) {
        Integer c = recentCount.get(word);
        return c == null ? 0 : c;
    }

    private void clearRecent() {
        recent.clear();
        recentCount.clear();
    }

    /**
     * 앞 단어 대신 앞 문맥 key(앞 단어의 끝 글자 {@link KoreanSuggest#endKey} 등 낱말이 될 수 없는 꼴) 다음에 word를 썼다고
     * 기록한다. 단어 쌍과 같은 곳에 같은 한도로 둔다. 단어 횟수는 세지 않는다.
     */
    public synchronized void recordContext(String key, String word, long now) {
        if (key == null || key.isEmpty() || word == null || word.isEmpty()) return;
        ctxStats.remove(key);
        Map<String, Entry> m = pairs.computeIfAbsent(key, k -> new HashMap<>());
        Entry pe = m.get(word);
        if (pe == null) {
            m.put(word, new Entry(1, now));
            pairCount++;
        } else {
            pe.count = clampCount(pe.count + 1);
            pe.last = now;
        }
        dirty = true;
        if (loaded) trimIfNeeded();
    }

    /** 어미를 한 번 썼다고 기록한다 (kind: 어미 종류, base: 어미 앞의 꼴, tail: 어미). */
    public synchronized void recordEnding(int kind, String base, String tail, long now) {
        if (base == null || base.isEmpty() || tail == null || tail.isEmpty()) return;
        Map<String, Entry> m = endings.computeIfAbsent(kind + ":" + tail, k -> new HashMap<>());
        Entry e = m.get(base);
        if (e == null) {
            m.put(base, new Entry(1, now));
            endingCount++;
        } else {
            e.count = clampCount(e.count + 1);
            e.last = now;
        }
        dirty = true;
        java.util.Arrays.fill(endingCache, null);
        endingTotals = null;
        if (loaded && endingCount > MAX_ENDINGS) endingCount -= trimNested(endings, MAX_ENDINGS / 10);
    }

    /** 화면 스레드용: 어미를 쓴 횟수 ("종류:어미"와 "종류" → 횟수, 읽지 않았으면 null). 받은 것을 고치지 않는다. */
    public synchronized Map<String, Integer> endingTotalsIfLoaded() {
        if (!loaded) return null;
        if (endingTotals == null) {
            Map<String, Integer> t = new HashMap<>();
            for (Map.Entry<String, Map<String, Entry>> ee : endings.entrySet()) {
                int n = 0;
                for (Entry e : ee.getValue().values()) n += e.count;
                t.put(ee.getKey(), n);
                t.merge(ee.getKey().substring(0, ee.getKey().indexOf(':')), n, Integer::sum);
            }
            endingTotals = t;
        }
        return endingTotals;
    }

    /**
     * 화면 스레드용: kind 종류의 어미 중 앞 꼴 minBases개 이상에 붙여 쓴 것 (많이 쓴 순, 최대 max개). 읽지 않았으면 빈 목록.
     * 한 낱말에서만 본 꼬리(가방이 → 가 + 방이)는 어미가 아닐 수 있어 둘 이상의 앞 꼴을 요구한다.
     */
    public synchronized List<String> endingsIfLoaded(int kind, int minBases, int max) {
        List<String> out = new ArrayList<>();
        if (!loaded) return out;
        boolean cacheable = kind >= 0 && kind < endingCache.length;
        if (cacheable && minBases == cacheMinBases && max == cacheMax && endingCache[kind] != null) {
            @SuppressWarnings("unchecked") List<String> c = (List<String>) endingCache[kind];
            return new ArrayList<>(c);
        }
        if (cacheable && (minBases != cacheMinBases || max != cacheMax)) {
            java.util.Arrays.fill(endingCache, null);
            cacheMinBases = minBases;
            cacheMax = max;
        }
        String pre = kind + ":";
        List<Object[]> all = new ArrayList<>();
        for (Map.Entry<String, Map<String, Entry>> ee : endings.entrySet()) {
            if (!ee.getKey().startsWith(pre) || ee.getValue().size() < minBases) continue;
            int n = 0;
            for (Entry e : ee.getValue().values()) n += e.count;
            all.add(new Object[]{ee.getKey().substring(pre.length()), n});
        }
        all.sort((a, b) -> Integer.compare((Integer) b[1], (Integer) a[1]));
        for (int i = 0; i < all.size() && i < max; i++) out.add((String) all.get(i)[0]);
        if (cacheable) endingCache[kind] = new ArrayList<>(out);
        return out;
    }

    /** 한도를 넘으면 가장 오래 안 쓴 것부터 10%를 덜어 낸다 (한 번만 쓴 것이 먼저). */
    private void trimIfNeeded() {
        if (words.size() > MAX_WORDS) {
            List<Map.Entry<String, Entry>> all = new ArrayList<>(words.entrySet());
            all.sort(UsageHistory::older);
            for (int i = 0; i < MAX_WORDS / 10 && i < all.size(); i++) {
                Entry e = words.remove(all.get(i).getKey());
                if (e != null) total -= e.count;
            }
        }
        if (pairCount > MAX_PAIRS) {
            pairCount -= trimNested(pairs, MAX_PAIRS / 10);
            ctxStats.clear();
        }
    }

    /** 앞 키 → (뒤 키 → 횟수) 꼴의 기록에서 가장 오래 안 쓴 것부터(한 번만 쓴 것이 먼저) n개를 덜어 내고 덜어 낸 수를 돌려준다. */
    private static int trimNested(Map<String, Map<String, Entry>> map, int n) {
        List<String[]> keys = new ArrayList<>();
        List<Entry> vals = new ArrayList<>();
        for (Map.Entry<String, Map<String, Entry>> outer : map.entrySet()) {
            for (Map.Entry<String, Entry> me : outer.getValue().entrySet()) {
                keys.add(new String[]{outer.getKey(), me.getKey()});
                vals.add(me.getValue());
            }
        }
        Integer[] order = new Integer[keys.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> olderEntry(vals.get(a), vals.get(b)));
        int removed = 0;
        for (int i = 0; i < n && i < order.length; i++) {
            String[] k = keys.get(order[i]);
            Map<String, Entry> m = map.get(k[0]);
            if (m != null && m.remove(k[1]) != null) {
                removed++;
                if (m.isEmpty()) map.remove(k[0]);
            }
        }
        return removed;
    }

    private static int older(Map.Entry<String, Entry> a, Map.Entry<String, Entry> b) {
        return olderEntry(a.getValue(), b.getValue());
    }

    private static int olderEntry(Entry a, Entry b) {
        int ca = a.count >= 2 ? 1 : 0, cb = b.count >= 2 ? 1 : 0;
        if (ca != cb) return ca - cb;
        return Long.compare(a.last, b.last);
    }

    /** 화면 스레드용: 단어를 쓴 횟수 (읽지 않았으면 0). */
    public synchronized int countIfLoaded(String word) {
        if (!loaded) return 0;
        Entry e = words.get(word);
        return e == null ? 0 : e.count;
    }

    /** 화면 스레드용: 모든 단어를 쓴 횟수의 합. */
    public synchronized long totalIfLoaded() {
        return loaded ? total : 0;
    }

    /** 화면 스레드용: prev 다음에 word를 쓴 횟수. */
    public synchronized int pairCountIfLoaded(String prev, String word) {
        if (!loaded || prev == null) return 0;
        Map<String, Entry> m = pairs.get(prev);
        Entry e = m == null ? null : m.get(word);
        return e == null ? 0 : e.count;
    }

    /** 화면 스레드용: prev 다음에 어떤 단어든 쓴 횟수의 합. */
    public synchronized int contextCountIfLoaded(String prev) {
        if (!loaded || prev == null) return 0;
        Map<String, Entry> m = pairs.get(prev);
        if (m == null) return 0;
        int n = 0;
        for (Entry e : m.values()) n += e.count;
        return n;
    }

    /** 화면 스레드용: prev 다음에 쓴 횟수의 합과 다른 단어 수 {합, 종류} (없으면 {0, 0}). */
    public synchronized int[] contextStatsIfLoaded(String prev) {
        if (!loaded || prev == null) return new int[2];
        int[] st = ctxStats.get(prev);
        if (st == null) {
            Map<String, Entry> m = pairs.get(prev);
            st = new int[2];
            if (m != null) {
                for (Entry e : m.values()) st[0] += e.count;
                st[1] = m.size();
            }
            if (ctxStats.size() >= 256) ctxStats.clear();
            ctxStats.put(prev, st);
        }
        return st.clone();
    }

    /** 화면 스레드용: prev 다음에 쓴 단어와 횟수 (많이 쓴 순, 같으면 최근 순). */
    public synchronized List<Map.Entry<String, Integer>> nextIfLoaded(String prev) {
        return nextIfLoaded(prev, 1);
    }

    /** nextIfLoaded 중 minCount번 이상 쓴 것만. */
    public synchronized List<Map.Entry<String, Integer>> nextIfLoaded(String prev, int minCount) {
        List<Map.Entry<String, Integer>> out = new ArrayList<>();
        if (!loaded || prev == null) return out;
        Map<String, Entry> m = pairs.get(prev);
        if (m == null) return out;
        List<Map.Entry<String, Entry>> all = new ArrayList<>();
        for (Map.Entry<String, Entry> me : m.entrySet()) if (me.getValue().count >= minCount) all.add(me);
        all.sort((a, b) -> {
            int c = Integer.compare(b.getValue().count, a.getValue().count);
            return c != 0 ? c : Long.compare(b.getValue().last, a.getValue().last);
        });
        for (Map.Entry<String, Entry> me : all) {
            out.add(new java.util.AbstractMap.SimpleImmutableEntry<>(me.getKey(), me.getValue().count));
        }
        return out;
    }

    /** 화면 스레드용: 입력 중인 prefix로 시작할 수 있는 단어들 (prefix와 똑같은 단어는 뺀다, {@link HangulRange#startsWith}). */
    public synchronized List<String> prefixIfLoaded(String prefix, int minCount) {
        List<String> out = new ArrayList<>();
        if (!loaded) return out;
        List<String[]> bounds = HangulRange.bounds(prefix);
        for (String[] b : bounds) {
            for (Map.Entry<String, Entry> me : words.subMap(b[0], true, b[1], false).entrySet()) {
                String w = me.getKey();
                if (w.equals(prefix) || me.getValue().count < minCount) continue;
                if (bounds.size() > 1 && out.contains(w)) continue;   // 두 구간이 겹칠 때
                out.add(w);
            }
        }
        return out;
    }

    /** 단어와 그 단어가 들어간 단어 쌍을 지운다 (학습한 단어를 지울 때). */
    public void remove(String word) {
        ensureLoaded();
        synchronized (this) {
            boolean changed = false;
            Entry e = words.remove(word);
            if (e != null) {
                total -= e.count;
                changed = true;
            }
            ctxStats.clear();
            if (recentCount.remove(word) != null) recent.removeIf(word::equals);
            Map<String, Entry> m = pairs.remove(word);
            if (m != null) {
                pairCount -= m.size();
                changed = true;
            }
            for (java.util.Iterator<Map<String, Entry>> it = pairs.values().iterator(); it.hasNext(); ) {
                Map<String, Entry> pm = it.next();
                if (pm.remove(word) != null) {
                    pairCount--;
                    changed = true;
                    if (pm.isEmpty()) it.remove();
                }
            }
            if (!changed) return;
            dirty = true;
            sinceSave = Math.max(sinceSave, 10);
        }
        save();
    }

    private void ensureLoaded() {
        boolean need;
        synchronized (this) {
            need = !loaded;
        }
        if (need) load();
    }

    public synchronized boolean needsSave() {
        return dirty && sinceSave >= 10;
    }

    /** 바뀐 것이 있으면 파일에 쓴다 (임시 파일에 쓴 뒤 바꿔치기). 백그라운드 스레드에서 부른다. */
    public void save() {
        List<String> lines;
        int gen;
        synchronized (this) {
            if (!dirty || !loaded) return;
            gen = generation;
            lines = new ArrayList<>(words.size() + pairCount);
            for (Map.Entry<String, Entry> me : words.entrySet()) {
                lines.add("u\t" + me.getKey() + "\t" + me.getValue().count + "\t" + me.getValue().last);
            }
            for (Map.Entry<String, Map<String, Entry>> ee : endings.entrySet()) {
                for (Map.Entry<String, Entry> me : ee.getValue().entrySet()) {
                    lines.add("e\t" + ee.getKey() + "\t" + me.getKey() + "\t" + me.getValue().count + "\t"
                            + me.getValue().last);
                }
            }
            for (Map.Entry<String, Map<String, Entry>> pe : pairs.entrySet()) {
                for (Map.Entry<String, Entry> me : pe.getValue().entrySet()) {
                    lines.add("p\t" + pe.getKey() + "\t" + me.getKey() + "\t" + me.getValue().count + "\t"
                            + me.getValue().last);
                }
            }
            dirty = false;
            sinceSave = 0;
        }
        boolean ok;
        synchronized (fileLock) {
            synchronized (this) {
                if (gen != generation) return;   // 그사이 모두 지우기가 있었다
            }
            ok = writeFile(lines);
        }
        if (!ok) {
            synchronized (this) {
                dirty = true;
            }
        }
    }

    private boolean writeFile(List<String> lines) {
        File tmp = new File(file.getPath() + ".tmp");
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8))) {
            for (String l : lines) {
                w.write(l);
                w.write('\n');
            }
        } catch (IOException e) {
            tmp.delete();
            return false;
        }
        return tmp.renameTo(file) || (file.delete() && tmp.renameTo(file));
    }

    /** 메모리에서 내린다 (저장하지 않은 것은 먼저 저장한다). 백그라운드 스레드에서 부른다. */
    public void release() {
        save();
        synchronized (this) {
            if (dirty) return;
            generation++;
            clearRecent();
            words = new java.util.TreeMap<>();
            endings = new HashMap<>();
            endingCount = 0;
            java.util.Arrays.fill(endingCache, null);
            endingTotals = null;
            pairs = new HashMap<>();
            ctxStats.clear();
            pairCount = 0;
            total = 0;
            loaded = false;
        }
    }

    /** 모두 지운다 (파일도). 지운 뒤에는 '빈 기록을 읽은 상태'다. */
    public void clear() {
        synchronized (fileLock) {
            synchronized (this) {
                generation++;
                clearRecent();
                words = new java.util.TreeMap<>();
                endings = new HashMap<>();
                endingCount = 0;
                java.util.Arrays.fill(endingCache, null);
                endingTotals = null;
                pairs = new HashMap<>();
                ctxStats.clear();
                pairCount = 0;
                total = 0;
                loaded = true;
                dirty = false;
                sinceSave = 0;
            }
            file.delete();
            new File(file.getPath() + ".tmp").delete();
        }
    }

    public synchronized int size() {
        return words.size();
    }

    public synchronized int pairSize() {
        return pairCount;
    }
}
