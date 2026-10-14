package com.alternative_studios.newswipe.suggest;

import java.util.Arrays;

/**
 * 앞 단어의 끝 글자(조사·어미)로 본 다음 단어 분포 P(다음 | 앞 단어의 끝 글자).
 *
 * <p>한국어는 어절마다 조사·어미가 붙어 같은 낱말도 꼴이 많아서, 앞 단어 그대로의 단어 쌍({@link NextWords})은 흔한 꼴에만
 * 있다. '짝남이', '민수랑'처럼 단어 쌍이 없거나 사전에 없는 앞 단어도 끝 글자(이·랑·을·고·서·는 ...)를 보면 다음에 올 말의
 * 종류(을 → 용언, 는 → 의존 명사 ...)를 어느 정도 안다. 그 분포를 다음 단어 사전의 단어 쌍에서 앱이 읽을 때 만든다
 * (사전 파일에 더 넣지 않는다). 앞 단어 h의 빈도 c(h)와 P(w|h)로 c(h, w)를 어림해 끝 글자마다 더한다.
 *
 * <p>끝 글자마다 많아야 {@link #TOP}개의 다음 단어를 단어 번호 순으로 둔다 ({@link NextWords}와 같은 방식으로 찾는다).
 */
final class EndingContext {
    /** 끝 글자마다 남길 다음 단어 수. */
    static final int TOP = 48;
    /** 이보다 적은 앞 단어에서 모인 끝 글자는 믿지 않는다. */
    static final int MIN_HEADS = 4;

    private final int[] groupOf = new int[11172];   // 끝 글자(음절 번호) → 묶음 번호 + 1 (0 = 없음)
    private final int[] start;
    private final int[] next;
    private final float[] prob;
    private final int[] heads;

    private EndingContext(int[] start, int[] next, float[] prob, int[] heads, int[] syllables) {
        this.start = start;
        this.next = next;
        this.prob = prob;
        this.heads = heads;
        for (int g = 0; g < syllables.length; g++) groupOf[syllables[g]] = g + 1;
    }

    /** 다음 단어 사전에서 만든다. 단어 쌍이 없으면 null. */
    static EndingContext build(WordDictionary dict, NextWords nw) {
        if (nw == null || nw.pairs() == 0) return null;
        int groups = nw.groups();
        // 앞 단어 묶음을 끝 글자 순으로 늘어놓는다
        long[] order = new long[groups];
        int n = 0;
        for (int g = 0; g < groups; g++) {
            char c = dict.lastChar(nw.head(g));
            if (c < 0xAC00 || c > 0xD7A3) continue;
            order[n++] = ((long) (c - 0xAC00) << 32) | g;
        }
        order = Arrays.copyOf(order, n);
        Arrays.sort(order);
        double[] acc = new double[dict.size()];
        int[] touched = new int[dict.size()];
        int[] outStart = new int[1024];
        int[] outSyl = new int[1024];
        int[] outHeads = new int[1024];
        int[] outNext = new int[16 * 1024];
        float[] outProb = new float[16 * 1024];
        int ng = 0, np = 0;
        for (int i = 0; i < n; ) {
            int syl = (int) (order[i] >>> 32);
            int j = i;
            double mass = 0;
            int nt = 0;
            for (; j < n && (int) (order[j] >>> 32) == syl; j++) {
                int g = (int) order[j];
                double ch = nw.unigram(dict.score(nw.head(g)));
                mass += ch;
                for (int k = nw.from(g); k < nw.to(g); k++) {
                    int w = nw.word(k);
                    if (acc[w] == 0) touched[nt++] = w;
                    acc[w] += ch * nw.prob(k);
                }
            }
            int headCount = j - i;
            i = j;
            if (headCount < MIN_HEADS || nt == 0 || mass <= 0) {
                for (int t = 0; t < nt; t++) acc[touched[t]] = 0;
                continue;
            }
            int keep = Math.min(TOP, nt);
            int[] ids = top(acc, touched, nt, keep);
            Arrays.sort(ids);
            if (ng == outStart.length) {
                outStart = Arrays.copyOf(outStart, ng * 2);
                outSyl = Arrays.copyOf(outSyl, ng * 2);
                outHeads = Arrays.copyOf(outHeads, ng * 2);
            }
            if (np + keep > outNext.length) {
                outNext = Arrays.copyOf(outNext, Math.max(outNext.length * 2, np + keep));
                outProb = Arrays.copyOf(outProb, outNext.length);
            }
            outStart[ng] = np;
            outSyl[ng] = syl;
            outHeads[ng] = headCount;
            ng++;
            for (int id : ids) {
                outNext[np] = id;
                outProb[np] = (float) (acc[id] / mass);
                np++;
            }
            for (int t = 0; t < nt; t++) acc[touched[t]] = 0;
        }
        if (ng == 0) return null;
        int[] st = Arrays.copyOf(outStart, ng + 1);
        st[ng] = np;
        return new EndingContext(st, Arrays.copyOf(outNext, np), Arrays.copyOf(outProb, np),
                Arrays.copyOf(outHeads, ng), Arrays.copyOf(outSyl, ng));
    }

    /** ids[0..n) 중 w가 가장 큰 keep개 (순서 없음). 작은 것이 맨 위인 힙으로 고른다. */
    private static int[] top(double[] w, int[] ids, int n, int keep) {
        int[] heap = new int[keep];
        int size = 0;
        for (int t = 0; t < n; t++) {
            int id = ids[t];
            if (size < keep) {
                int i = size++;
                while (i > 0 && w[heap[(i - 1) / 2]] > w[id]) {
                    heap[i] = heap[(i - 1) / 2];
                    i = (i - 1) / 2;
                }
                heap[i] = id;
            } else if (w[id] > w[heap[0]]) {
                int i = 0;
                while (true) {   // 맨 위를 바꾸고 아래로 내린다
                    int c = 2 * i + 1;
                    if (c >= keep) break;
                    if (c + 1 < keep && w[heap[c + 1]] < w[heap[c]]) c++;
                    if (w[heap[c]] >= w[id]) break;
                    heap[i] = heap[c];
                    i = c;
                }
                heap[i] = id;
            }
        }
        return heap;
    }

    /** 앞 단어 끝 글자의 묶음 번호. 없으면 -1. */
    int group(String prev) {
        if (prev == null || prev.isEmpty()) return -1;
        char c = prev.charAt(prev.length() - 1);
        if (c < 0xAC00 || c > 0xD7A3) return -1;
        return groupOf[c - 0xAC00] - 1;
    }

    /** 이 끝 글자로 모인 앞 단어 수 (많을수록 분포를 믿는다). */
    int heads(int g) {
        return heads[g];
    }

    int from(int g) {
        return start[g];
    }

    int to(int g) {
        return start[g + 1];
    }

    int word(int i) {
        return next[i];
    }

    double prob(int i) {
        return prob[i];
    }

    /** 묶음 g 안에서 단어 w의 자리. 없으면 -1. */
    int find(int g, int w) {
        int lo = start[g], hi = start[g + 1] - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (next[mid] < w) lo = mid + 1;
            else if (next[mid] > w) hi = mid - 1;
            else return mid;
        }
        return -1;
    }

    /** 묶음 g 안에서 번호가 w 이상인 첫 자리. */
    int lowerBound(int g, int w) {
        int lo = start[g], hi = start[g + 1];
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (next[mid] < w) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    /** 담고 있는 (끝 글자, 다음 단어) 수. */
    int size() {
        return next.length;
    }
}
