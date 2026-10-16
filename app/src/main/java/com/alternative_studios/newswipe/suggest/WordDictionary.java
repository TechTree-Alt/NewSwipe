package com.alternative_studios.newswipe.suggest;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 단어 사전 (dict/build/build_*_dict.py가 만든 NSD1 파일).
 * 단어는 유니코드 순으로 정렬되어 있어 접두사에 맞는 단어가 한 구간에 모인다.
 * 메모리에는 글자 배열 하나, 시작 위치 배열, 점수 배열만 둔다.
 */
public final class WordDictionary {
    private final char[] chars;
    private final int[] offsets;   // 단어 i = chars[offsets[i] .. offsets[i+1])
    private final byte[] scores;
    private final int count;
    /** correct()가 살펴볼 단어 (점수가 min 이상인 것, 사전 순서 그대로). 처음 쓸 때 만든다. */
    private static final class Strong {
        final int min;
        final int[] words;

        Strong(int min, int[] words) {
            this.min = min;
            this.words = words;
        }
    }

    private volatile Strong strong;

    private WordDictionary(char[] chars, int[] offsets, byte[] scores) {
        this.chars = chars;
        this.offsets = offsets;
        this.scores = scores;
        this.count = scores.length;
    }

    public static WordDictionary load(InputStream in) throws IOException {
        DataInputStream d = new DataInputStream(new java.io.BufferedInputStream(in, 16 * 1024));
        byte[] magic = new byte[4];
        d.readFully(magic);
        if (magic[0] != 'N' || magic[1] != 'S' || magic[2] != 'D' || magic[3] != '1') {
            throw new IOException("bad dictionary");
        }
        int n = Integer.reverseBytes(d.readInt());
        int blob = Integer.reverseBytes(d.readInt());
        if (n < 0 || blob < 0 || blob > (16 << 20) || n > blob / 3) throw new IOException("bad dictionary");
        byte[] raw = new byte[blob];
        d.readFully(raw);

        // 먼저 풀어 쓴 전체 길이를 세어 딱 맞는 배열을 한 번만 잡는다 (늘리고 다시 자르는 복사가 없다).
        long total = 0;
        for (int i = 0, q = 0; i < n; i++) {
            if (q + 3 > blob) throw new IOException("bad dictionary");
            int tail = raw[q + 1] & 0xFF;
            total += (raw[q] & 0xFF) + tail;
            q += 3 + 2 * tail;
            if (q > blob) throw new IOException("bad dictionary");
        }
        if (total > (32 << 20)) throw new IOException("bad dictionary");
        char[] buf = new char[(int) total];
        int[] offs = new int[n + 1];
        byte[] sc = new byte[n];
        int len = 0, p = 0, prevStart = 0, prevLen = 0;
        for (int i = 0; i < n; i++) {
            int common = raw[p++] & 0xFF;
            int tail = raw[p++] & 0xFF;
            sc[i] = raw[p++];
            if (common > prevLen) throw new IOException("bad dictionary");
            offs[i] = len;
            System.arraycopy(buf, prevStart, buf, len, common);
            len += common;
            for (int k = 0; k < tail; k++, p += 2) {
                buf[len++] = (char) ((raw[p] & 0xFF) | ((raw[p + 1] & 0xFF) << 8));
            }
            prevStart = offs[i];
            prevLen = len - offs[i];
        }
        offs[n] = len;
        return new WordDictionary(buf, offs, sc);
    }

    public int size() { return count; }

    public String word(int i) { return new String(chars, offsets[i], offsets[i + 1] - offsets[i]); }

    public int score(int i) { return scores[i] & 0xFF; }

    /** key보다 작지 않은 첫 단어의 위치. */
    private int lowerBound(char[] key, int keyLen) {
        int lo = 0, hi = count;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (compare(mid, key, keyLen) < 0) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    private int compare(int i, char[] key, int keyLen) {
        int s = offsets[i], e = offsets[i + 1];
        int n = Math.min(e - s, keyLen);
        for (int k = 0; k < n; k++) {
            int c = chars[s + k] - key[k];
            if (c != 0) return c;
        }
        return (e - s) - keyLen;
    }

    /**
     * prefix로 시작하는 단어 중 점수가 높은 것을 최대 max개 돌려준다 (점수 내림차순, 같으면 짧은 것 먼저).
     * 한글은 마지막 글자가 덜 만들어진 상태(자음 하나, 받침 없는 음절 등)도 맞춘다.
     * prefix와 똑같은 단어는 뺀다.
     */
    public String[] lookup(String prefix, int max) {
        int[] ids = lookupIds(prefix, max);
        String[] out = new String[ids.length];
        for (int i = 0; i < ids.length; i++) out[i] = word(ids[i]);
        return out;
    }

    /**
     * lookup()과 같지만 단어 번호를 돌려준다. 마지막 글자의 받침이 다음 글자의 첫소리일 수 있으면
     * ('안녕핫'은 '안녕하세요'를 치는 중일 수 있다) 그렇게 읽은 단어도 함께 찾는다.
     */
    public int[] lookupIds(String prefix, int max) {
        int n = prefix.length();
        if (n == 0 || max <= 0) return new int[0];
        // 첫 키(ㄱ, 가 ...)는 구간이 커서(수천 단어) 매번 훑으면 아깝다. 한 글자 입력의 결과는 기억해 둔다.
        if (n == 1) {
            String key = max + ":" + prefix;
            int[] hit;
            synchronized (firstKey) {
                hit = firstKey.get(key);
            }
            if (hit == null) {
                hit = scanIds(prefix, max);
                synchronized (firstKey) {
                    if (firstKey.size() > 512) firstKey.clear();
                    firstKey.put(key, hit);
                }
            }
            return hit.clone();
        }
        return scanIds(prefix, max);
    }

    /** 한 글자 입력의 lookupIds 결과 ("max:글자" → 단어 번호). */
    private final java.util.Map<String, int[]> firstKey = new java.util.HashMap<>();

    private int[] scanIds(String prefix, int max) {
        int n = prefix.length();
        int[] rg = new int[4];
        int nr = ranges(prefix, rg);
        int[] best = new int[max];
        int found = 0;
        for (int r = 0; r < nr; r++) {
            for (int i = rg[2 * r]; i < rg[2 * r + 1]; i++) {
                int len = offsets[i + 1] - offsets[i];
                if (len == n && equalsPrefix(i, prefix)) continue;
                // 삽입 정렬로 상위 max개만 유지
                int sc = scores[i] & 0xFF;
                int pos = found;
                while (pos > 0 && better(i, sc, best[pos - 1])) pos--;
                if (pos >= max) continue;
                int upper = Math.min(found, max - 1);
                for (int k = upper; k > pos; k--) best[k] = best[k - 1];
                best[pos] = i;
                if (found < max) found++;
            }
        }
        return found == max ? best : java.util.Arrays.copyOf(best, found);
    }

    /**
     * prefix로 시작할 수 있는 단어들의 번호 구간을 out에 {처음, 끝(제외)} 쌍으로 넣고 구간 수(0~2)를 돌려준다.
     * 마지막 글자가 덜 만들어진 상태도 맞추고, 받침이 다음 글자 첫소리일 수 있는 구간도 넣는다. out은 4칸 이상.
     */
    public int ranges(String prefix, int[] out) {
        int nr = 0;
        if (range(prefix, out, 0)) nr++;
        String moved = HangulRange.movedFinal(prefix);
        if (moved != null && range(moved, out, 2 * nr)) nr++;
        return nr;
    }

    private boolean range(String prefix, int[] out, int at) {
        int n = prefix.length();
        char last = prefix.charAt(n - 1);
        char lo = last, hiEx = (char) (last + 1);
        int[] r = HangulRange.of(last);
        if (r != null) {
            lo = (char) r[0];
            hiEx = (char) (r[1] + 1);
        } else if (last >= 'ㄱ' && last <= 'ㅣ') {
            return false;   // 모음만 있거나 맞출 수 없는 자모
        }
        char[] a = prefix.toCharArray();
        a[n - 1] = lo;
        char[] b = prefix.toCharArray();
        b[n - 1] = hiEx;
        int from = lowerBound(a, n), to = lowerBound(b, n);
        if (from >= to) return false;
        out[at] = from;
        out[at + 1] = to;
        return true;
    }

    /** prefix로 (글자 그대로) 시작하는 단어들의 번호 구간 {처음, 끝(제외)}. prefix와 같은 단어도 들어간다. */
    public int[] prefixRange(String prefix) {
        char[] a = prefix.toCharArray();
        char[] b = java.util.Arrays.copyOf(a, a.length + 1);
        b[a.length] = '\uFFFF';
        return new int[]{lowerBound(a, a.length), lowerBound(b, b.length)};
    }

    public boolean contains(String word) {
        return indexOf(word) >= 0;
    }

    /** 단어의 번호. 없으면 -1. */
    public int indexOf(String word) {
        if (word.isEmpty()) return -1;
        char[] k = word.toCharArray();
        int i = lowerBound(k, k.length);
        return i < count && compare(i, k, k.length) == 0 ? i : -1;
    }

    /** 단어 i의 길이 (UTF-16 단위). */
    public int length(int i) {
        return offsets[i + 1] - offsets[i];
    }

    /** 단어 i의 마지막 글자. */
    public char lastChar(int i) {
        return chars[offsets[i + 1] - 1];
    }

    // ---------------------------------------------------------------- 초성 검색

    /** (초성 9개를 5비트씩 << 17) | 단어 번호. 초성 순으로 정렬. 처음 쓸 때(백그라운드의 warmUpChosung) 만든다. */
    private volatile long[] chosung;
    private static final int CHO_SLOTS = 9;

    /** 초성 검색 색인을 만들어 둔다 (두 글자 이상인 한글 단어만). 백그라운드 스레드에서 부른다. */
    public void warmUpChosung() {
        if (chosung != null || count >= (1 << 17)) return;
        long[] keys = new long[count];
        int k = 0;
        for (int i = 0; i < count; i++) {
            int s = offsets[i], e = offsets[i + 1];
            if (e - s < 2) continue;
            long key = 0;
            boolean ok = true;
            for (int j = 0; j < CHO_SLOTS; j++) {
                int code = 0;
                if (s + j < e) {
                    char c = chars[s + j];
                    if (c < 0xAC00 || c > 0xD7A3) {
                        ok = false;
                        break;
                    }
                    code = (c - 0xAC00) / 588 + 1;
                }
                key = (key << 5) | code;
            }
            if (ok) keys[k++] = (key << 17) | i;
        }
        long[] idx = java.util.Arrays.copyOf(keys, k);
        java.util.Arrays.sort(idx);
        chosung = idx;
    }

    public boolean hasChosung() {
        return chosung != null;
    }

    /**
     * 초성만 친 말(ㅅㄱㅎㅅㅇ)로 시작하는 단어의 번호. 초성 수와 글자 수가 같은 단어를 먼저, 그다음 긴 단어를 점수순으로.
     * 색인이 없거나 초성이 아닌 글자가 있으면 빈 배열.
     */
    public int[] chosungIds(String initials, int max) {
        long[] idx = chosung;
        int n = initials.length();
        if (idx == null || n == 0 || n > CHO_SLOTS || max <= 0) return new int[0];
        long key = 0;
        for (int j = 0; j < CHO_SLOTS; j++) {
            int code = 0;
            if (j < n) {
                int[] r = HangulRange.of(initials.charAt(j));
                char c = initials.charAt(j);
                if (r == null || c < 0x3131 || c >= 0x3164) return new int[0];
                code = (r[0] - 0xAC00) / 588 + 1;
            }
            key = (key << 5) | code;
        }
        long lo = key << 17;
        long hi = (key + (1L << (5 * (CHO_SLOTS - n)))) << 17;
        int from = lowerBound(idx, lo), to = lowerBound(idx, hi);
        int[] best = new int[max];
        int[] rank = new int[max];
        int found = 0;
        for (int p = from; p < to; p++) {
            int i = (int) (idx[p] & 0x1FFFF);
            // 글자 수가 초성 수와 같으면 점수에 256을 더해 먼저 보인다.
            int sc = (scores[i] & 0xFF) + (length(i) == n ? 256 : 0);
            int pos = found;
            while (pos > 0 && sc > rank[pos - 1]) pos--;
            if (pos >= max) continue;
            int upper = Math.min(found, max - 1);
            for (int q = upper; q > pos; q--) {
                best[q] = best[q - 1];
                rank[q] = rank[q - 1];
            }
            best[pos] = i;
            rank[pos] = sc;
            if (found < max) found++;
        }
        return found == max ? best : java.util.Arrays.copyOf(best, found);
    }

    private static int lowerBound(long[] a, long key) {
        int lo = 0, hi = a.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (a[mid] < key) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    // ---------------------------------------------------------------- 오타가 섞인 접두사

    /**
     * 앞부분에 오타가 하나쯤 있는 접두사로 찾는다 ('안녕하새' → 안녕하세요). 첫 자음은 맞게 쳤다고 보고,
     * 그 자음으로 시작하는 단어 중 점수가 minScore 이상인 것의 앞부분과 typo 비용으로 견준다.
     * out에 단어 번호를, cost에 고친 비용을 넣고 찾은 수를 돌려준다 (비용이 maxCost 이하인 것, 점수순 상위 max개).
     */
    public int fuzzyPrefix(String prefix, TypoModel typo, float maxCost, int minScore, int max, int[] out, float[] cost) {
        if (prefix.length() < 2 || max <= 0) return 0;
        char first = prefix.charAt(0);
        if (first < 0xAC00 || first > 0xD7A3) return 0;
        int l = (first - 0xAC00) / 588;
        char[] a = {(char) (0xAC00 + l * 588)}, b = {(char) (0xAC00 + (l + 1) * 588)};
        int from = lowerBound(a, 1), to = lowerBound(b, 1);
        char[] p = TypoModel.letters(prefix);
        char[] t = new char[64];
        int found = 0;
        int[] rank = new int[max];
        for (int i = from; i < to; i++) {
            int sc = scores[i] & 0xFF;
            if (sc < minScore) continue;
            if (found == max && sc <= rank[max - 1]) continue;
            int s = offsets[i], e = offsets[i + 1];
            if (e - s > 21) continue;
            int tn = TypoModel.letters(chars, s, e, t);
            float c = typo.prefixDistance(p, p.length, t, tn, maxCost);
            if (c > maxCost || c <= 0f) continue;   // 0이면 이미 맞는 접두사다 (lookup이 찾는다)
            int pos = found;
            while (pos > 0 && sc > rank[pos - 1]) pos--;
            if (pos >= max) continue;
            int upper = Math.min(found, max - 1);
            for (int q = upper; q > pos; q--) {
                out[q] = out[q - 1];
                rank[q] = rank[q - 1];
                cost[q] = cost[q - 1];
            }
            out[pos] = i;
            rank[pos] = sc;
            cost[pos] = c;
            if (found < max) found++;
        }
        return found;
    }

    /**
     * 사전에 없는 word를 한 글자(한글은 자모 하나) 고쳐서 만들 수 있는 가장 흔한 단어를 찾는다.
     * 바꾸기·빠뜨리기·더하기·이웃한 두 글자 맞바꾸기를 한 번으로 센다. 점수가 minScore 미만인 단어는 쓰지 않는다.
     * 없으면 null.
     */
    public String correct(String word, int minScore) {
        int[] target = new int[word.length() * 3];
        int tn = decompose(word.toCharArray(), 0, word.length(), target);
        int[] cand = new int[64 * 3];
        int bestIdx = -1, bestScore = 0;
        // 점수가 minScore 이상인 단어는 전체의 2% 남짓이라, 그 목록만 훑는다 (순서가 같아 결과도 같다).
        int[] idx = strongWords(minScore);
        for (int i : idx) {
            int sc = scores[i] & 0xFF;
            if (sc < minScore || sc < bestScore) continue;
            int s = offsets[i], e = offsets[i + 1];
            int diff = (e - s) - word.length();
            if (diff > 1 || diff < -1) continue;
            if (cand.length < (e - s) * 3) cand = new int[(e - s) * 3];
            int cn = decompose(chars, s, e, cand);
            if (!withinOne(target, tn, cand, cn)) continue;
            if (sc > bestScore || (sc == bestScore && bestIdx >= 0 && e - s < offsets[bestIdx + 1] - offsets[bestIdx])) {
                bestIdx = i;
                bestScore = sc;
            }
        }
        return bestIdx < 0 ? null : word(bestIdx);
    }

    int[] strongWords(int minScore) {
        Strong s = strong;   // 기준과 목록을 한 객체로 묶어, 다른 스레드가 만들어 넣어도 어긋나지 않는다
        if (s != null && s.min == minScore) return s.words;
        int n = 0;
        for (int i = 0; i < count; i++) if ((scores[i] & 0xFF) >= minScore) n++;
        int[] words = new int[n];
        for (int i = 0, k = 0; i < count; i++) if ((scores[i] & 0xFF) >= minScore) words[k++] = i;
        strong = new Strong(minScore, words);
        return words;
    }

    /** 음절을 초성·중성·종성으로 풀어 쓴다 (한글이 아닌 글자는 그대로). */
    static int decompose(char[] src, int from, int to, int[] out) {
        int n = 0;
        for (int i = from; i < to; i++) {
            char c = src[i];
            if (c >= 0xAC00 && c <= 0xD7A3) {
                int s = c - 0xAC00;
                out[n++] = 0x1100 + s / 588;
                out[n++] = 0x1161 + (s / 28) % 21;
                if (s % 28 != 0) out[n++] = 0x11A7 + s % 28;
            } else {
                out[n++] = c;
            }
        }
        return n;
    }

    /** a와 b가 정확히 한 번의 편집(바꾸기·빠뜨리기·더하기·맞바꾸기)으로 같아지는지. 같은 것은 false. */
    static boolean withinOne(int[] a, int an, int[] b, int bn) {
        if (an - bn > 1 || bn - an > 1) return false;
        int p = 0;
        int min = Math.min(an, bn);
        while (p < min && a[p] == b[p]) p++;
        if (p == an && p == bn) return false;
        if (an == bn) {
            if (tailEquals(a, p + 1, an, b, p + 1, bn)) return true;   // 바꾸기
            return p + 1 < an && a[p] == b[p + 1] && a[p + 1] == b[p] && tailEquals(a, p + 2, an, b, p + 2, bn);
        }
        if (an > bn) return tailEquals(a, p + 1, an, b, p, bn);
        return tailEquals(a, p, an, b, p + 1, bn);
    }

    private static boolean tailEquals(int[] a, int ai, int an, int[] b, int bi, int bn) {
        if (an - ai != bn - bi) return false;
        for (; ai < an; ai++, bi++) if (a[ai] != b[bi]) return false;
        return true;
    }

    private boolean equalsPrefix(int i, String prefix) {
        int s = offsets[i];
        for (int k = 0; k < prefix.length(); k++) if (chars[s + k] != prefix.charAt(k)) return false;
        return true;
    }

    private boolean better(int i, int sc, int j) {
        int sj = scores[j] & 0xFF;
        if (sc != sj) return sc > sj;
        return (offsets[i + 1] - offsets[i]) < (offsets[j + 1] - offsets[j]);
    }
}
