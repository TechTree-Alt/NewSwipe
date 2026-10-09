package com.alternative_studios.newswipe.suggest;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 단어 사전 (tools/build_*_dict.py가 만든 NSD1 파일).
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
        int n = prefix.length();
        if (n == 0 || max <= 0) return new String[0];
        char last = prefix.charAt(n - 1);
        char lo = last, hiEx = (char) (last + 1);
        int[] r = HangulRange.of(last);
        if (r != null) {
            lo = (char) r[0];
            hiEx = (char) (r[1] + 1);
        } else if (last >= 'ㄱ' && last <= 'ㅣ') {
            return new String[0];   // 모음만 있거나 맞출 수 없는 자모
        }
        char[] a = prefix.toCharArray();
        a[n - 1] = lo;
        char[] b = prefix.toCharArray();
        b[n - 1] = hiEx;
        int from = lowerBound(a, n);
        int to = lowerBound(b, n);

        int[] best = new int[max];
        int found = 0;
        for (int i = from; i < to; i++) {
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
        String[] out = new String[found];
        for (int i = 0; i < found; i++) out[i] = word(best[i]);
        return out;
    }

    public boolean contains(String word) {
        char[] k = word.toCharArray();
        int i = lowerBound(k, k.length);
        return i < count && compare(i, k, k.length) == 0;
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

    private int[] strongWords(int minScore) {
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
