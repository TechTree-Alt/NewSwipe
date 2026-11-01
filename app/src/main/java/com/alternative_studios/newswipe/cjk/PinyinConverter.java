package com.alternative_studios.newswipe.cjk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 병음(성조 없이, ü는 v) → 한자 후보. 음절표는 따로 들고 있지 않고 사전에서 얻는다: '가 없는 키(글자 하나의 읽기)가 곧 음절이다.
 * 입력은 소문자 a~z와 음절을 직접 나누는 '로 이루어진다. 마지막 음절은 덜 쳐도(예: nih → ni'hao) 이어지는 음절로 넓혀 찾는다.
 */
public final class PinyinConverter implements Converter {
    static final byte SEP = '\'';
    /** 단어 하나를 지날 때마다 더하는 비용 (긴 단어를 앞세운다). */
    static final int WORD_PENALTY = 40;
    /** 덜 친 마지막 음절을 넓혀 찾은 단어에 더하는 비용. */
    static final int PARTIAL_PENALTY = 25;
    private static final int MAX_SYLLABLE = 6, MAX_WORD_SYLLABLES = 8;
    /** 간이 병음(简拼)으로 쓰는 성모: 이것만 쳐도 그 성모로 시작하는 음절로 본다 (nh → 你好, bj → 北京). */
    private static final Set<String> INITIALS = new HashSet<>(java.util.Arrays.asList(
            "b", "p", "m", "f", "d", "t", "n", "l", "g", "k", "h", "j", "q", "x", "zh", "ch", "sh", "r", "z", "c", "s",
            "y", "w"));
    /** 한 번 바꿀 때 사전을 찾는 횟수의 상한 (성모만 길게 쳐도 느려지지 않게). */
    private static final int MAX_LOOKUPS = 8000;
    private int lookups;
    /** 이번 변환에서 위치마다 찾아 둔 음절 (같은 위치를 여러 번 지나므로 한 번만 만든다). */
    private List<Object[]>[] sylCache;

    private final ConversionDictionary dict;
    private final Set<String> syllables = new HashSet<>();
    /** 음절의 모든 앞부분 (덜 친 음절을 알아보는 데 쓴다). */
    private final java.util.Map<String, List<String>> byPrefix = new java.util.HashMap<>();

    public PinyinConverter(ConversionDictionary dict) {
        this.dict = dict;
        for (int i = 0; i < dict.size(); i++) {
            String k = dict.key(i);
            if (k.indexOf('\'') < 0 && k.length() <= MAX_SYLLABLE && isLetters(k)) syllables.add(k);
        }
        for (String s : syllables) {
            for (int len = 1; len < s.length(); len++) {
                byPrefix.computeIfAbsent(s.substring(0, len), x -> new ArrayList<>()).add(s);
            }
        }
    }

    private static boolean isLetters(String s) {
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) < 'a' || s.charAt(i) > 'z') return false;
        return !s.isEmpty();
    }

    int syllableCount() {
        return syllables.size();
    }

    /** 입력 i에서 시작하는 음절들: {음절, 끝 위치(뒤의 '까지 넘긴), 덜 친 것인지(1/0)}. */
    private List<Object[]> syllablesAt(String in, int i) {
        List<Object[]> out = new ArrayList<>();
        int n = in.length();
        for (int len = 1; len <= MAX_SYLLABLE && i + len <= n; len++) {
            String s = in.substring(i, i + len);
            if (s.indexOf('\'') >= 0) break;
            String norm = s.endsWith("ue") && (s.equals("lue") || s.equals("nue")) ? s.charAt(0) + "ve" : s;
            int end = i + len;
            int next = end < n && in.charAt(end) == '\'' ? end + 1 : end;
            if (syllables.contains(norm)) out.add(new Object[]{norm, next, 0});
            // 마지막 조각은 덜 친 음절로, 가운데의 성모만 친 조각은 간이 병음으로 보고 그 음절들로 넓힌다.
            // 성모 바로 뒤에 모음이 오면 음절을 이어 친 것이다 (nihao의 h는 hao의 시작이지 간이 병음이 아니다).
            boolean initialOnly = INITIALS.contains(norm) && !syllables.contains(norm)
                    && (end >= n || "aeiouv".indexOf(in.charAt(end)) < 0);
            if (end == n || initialOnly) {
                List<String> more = byPrefix.get(norm);
                if (more != null) for (String m : more) out.add(new Object[]{m, next, 1});
            }
        }
        return out;
    }

    /** i에서 시작하는 사전 단어들을 lattice에 더한다. */
    private void addWords(Lattice lat, String in, int i) {
        walk(lat, in, i, i, "", 0, false);
    }

    private void walk(Lattice lat, String in, int start, int at, String key, int depth, boolean partial) {
        if (depth >= MAX_WORD_SYLLABLES || at >= in.length() && depth > 0) return;
        if (sylCache[at] == null) sylCache[at] = syllablesAt(in, at);
        for (Object[] s : sylCache[at]) {
            String k = key.isEmpty() ? (String) s[0] : key + "'" + s[0];
            if (++lookups > MAX_LOOKUPS) return;
            byte[] kb = ConversionDictionary.utf8(k);
            int lb = dict.lowerBound(kb);
            if (!dict.startsWithUnit(lb, kb, SEP)) continue;   // 이 음절로 이어지는 단어가 없다
            int end = (Integer) s[1];
            boolean part = partial || (Integer) s[2] == 1;
            if (dict.isKey(lb, kb)) {
                lat.add(new Lattice.Edge(start, end, lb, dict.bestCost(lb) + (part ? PARTIAL_PENALTY : 0), null, part));
            }
            walk(lat, in, start, end, k, depth + 1, part);
        }
    }

    @Override
    public List<Candidate> convert(String in, int max) {
        List<Candidate> out = new ArrayList<>();
        int n = in.length();
        if (n == 0) return out;
        lookups = 0;
        @SuppressWarnings("unchecked")
        List<Object[]>[] cache = new List[n + 1];
        sylCache = cache;
        Lattice lat = new Lattice(n);
        // 처음의 '는 건너뛴다 (단어가 0에서 시작하도록 0→1 빈 칸을 잇는다).
        boolean[] seen = new boolean[n + 1];
        java.util.ArrayDeque<Integer> todo = new java.util.ArrayDeque<>();
        int first = 0;
        while (first < n && in.charAt(first) == '\'') first++;
        if (first > 0) lat.add(new Lattice.Edge(0, first, -1, 0, ""));
        todo.add(first);
        seen[first] = true;
        while (!todo.isEmpty()) {
            int i = todo.poll();
            if (i >= n) continue;
            addWords(lat, in, i);
            for (Lattice.Edge e : lat.edgesFrom(i)) {
                if (!seen[e.to]) {
                    seen[e.to] = true;
                    todo.add(e.to);
                }
            }
        }
        Set<String> used = new HashSet<>();
        List<Lattice.Edge> path = lat.bestPath(WORD_PENALTY);
        if (path != null) {
            int words = 0;
            for (Lattice.Edge e : path) if (e.dictIndex >= 0) words++;
            if (words >= 2) add(out, used, Lattice.textOf(path, dict), n);
        }
        // 처음에서 시작하는 단어: 많이 덮는 것부터, 같으면 자주 쓰는 것부터.
        List<Lattice.Edge> starts = new ArrayList<>(lat.edgesFrom(first));
        starts.removeIf(e -> e.dictIndex < 0);
        // 같은 길이면 끝까지 친 음절로 찾은 단어를 덜 친 음절로 넓혀 찾은 단어보다 앞세운다 (e → 饿, 그 뒤에 er → 而).
        starts.sort((a, b) -> a.to != b.to ? b.to - a.to : a.partial != b.partial ? (a.partial ? 1 : -1) : a.cost - b.cost);
        List<ConversionDictionary.Entry> es = new ArrayList<>();
        for (Lattice.Edge e : starts) {
            if (out.size() >= max) break;
            es.clear();
            dict.entries(e.dictIndex, es, max);
            for (ConversionDictionary.Entry x : es) {
                if (out.size() >= max) break;
                add(out, used, x.text, e.to);
            }
        }
        if (out.size() < max) add(out, used, in.replace("'", ""), n);   // 그대로 (영어 낱말 등)
        return out;
    }

    private static void add(List<Candidate> out, Set<String> used, String text, int consumed) {
        if (text.isEmpty() || !used.add(text)) return;
        out.add(new Candidate(text, consumed));
    }

    /** 화면에 보일 병음: 음절 사이에 '를 넣는다 (사용자가 넣은 '는 그대로). 나눌 수 없는 부분은 그대로 둔다. */
    public String display(String in) {
        StringBuilder b = new StringBuilder();
        String[] chunks = in.split("'", -1);
        for (int c = 0; c < chunks.length; c++) {
            if (c > 0) b.append('\'');
            String s = chunks[c];
            boolean[] ok = splittable(s);
            int i = 0;
            while (i < s.length()) {
                int take = 0;
                for (int len = Math.min(MAX_SYLLABLE, s.length() - i); len >= 1; len--) {
                    if (syllables.contains(s.substring(i, i + len)) && ok[i + len]) {
                        take = len;
                        break;
                    }
                }
                if (take == 0) take = byPrefix.containsKey(s.substring(i)) ? s.length() - i : 1;
                if (i > 0) b.append('\'');
                b.append(s, i, i + take);
                i += take;
            }
        }
        return b.toString();
    }

    /** ok[pos]: pos부터 끝까지 음절로 나뉘는지 (끝은 덜 친 음절이어도 된다). 긴 음절을 앞세워 'xian'을 'xi'an'으로 나누지 않는다. */
    private boolean[] splittable(String s) {
        int n = s.length();
        boolean[] ok = new boolean[n + 1];
        ok[n] = true;
        for (int pos = n - 1; pos >= 0; pos--) {
            if (byPrefix.containsKey(s.substring(pos))) {
                ok[pos] = true;
                continue;
            }
            for (int len = 1; len <= MAX_SYLLABLE && pos + len <= n && !ok[pos]; len++) {
                ok[pos] = syllables.contains(s.substring(pos, pos + len)) && ok[pos + len];
            }
        }
        return ok;
    }
}
