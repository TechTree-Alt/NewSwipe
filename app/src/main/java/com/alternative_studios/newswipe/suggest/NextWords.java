package com.alternative_studios.newswipe.suggest;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 다음 단어 사전 (dict/build/build_ko_model.py가 만든 NSN1 파일): 앞 단어마다 그 뒤에 자주 오는 단어와 확률 P(다음|앞).
 * 단어는 단어 사전({@link WordDictionary})의 번호로 가리키므로, 같은 빌드에서 만든 단어 사전과 함께 써야 한다.
 * 단어 사전 점수를 확률로 되돌리는 기준도 담고 있다 ({@link #unigram}).
 */
public final class NextWords {
    private final int[] prev;      // 앞 단어 번호 (오름차순)
    private final int[] start;     // 앞 단어 g의 다음 단어 = next[start[g] .. start[g+1])
    private final byte[] lambda;   // 앞 단어마다 다음 단어 사전을 믿는 정도 (Witten-Bell, ×255)
    private final int[] next;      // 다음 단어 번호 (앞 단어 안에서 오름차순)
    private final byte[] q;        // round(-ln P × 4)
    private final double[] uni = new double[256];   // 단어 사전 점수 → 단어 확률

    private NextWords(int[] prev, int[] start, byte[] lambda, int[] next, byte[] q, float lnTop, float lnBottom) {
        this.prev = prev;
        this.start = start;
        this.lambda = lambda;
        this.next = next;
        this.q = q;
        fillUnigram(uni, lnTop, lnBottom);
    }

    /** 단어 사전 점수(1~255)를 단어 확률로 되돌리는 표 (build_ko_dict.write_nsd1의 양자화를 거꾸로). */
    static void fillUnigram(double[] out, double lnTop, double lnBottom) {
        for (int s = 0; s < 256; s++) {
            double ln = lnBottom + (Math.max(s, 1) - 1) * (lnTop - lnBottom) / 254.0;
            out[s] = Math.max(Math.exp(ln) - 1, 0.01) / 1e6;
        }
    }

    /**
     * @param dictSize 함께 쓸 단어 사전의 단어 수. 파일이 다른 사전으로 만들어졌으면 IOException.
     */
    public static NextWords load(InputStream in, int dictSize) throws IOException {
        DataInputStream d = new DataInputStream(new java.io.BufferedInputStream(in, 16 * 1024));
        byte[] magic = new byte[4];
        d.readFully(magic);
        if (magic[0] != 'N' || magic[1] != 'S' || magic[2] != 'N' || magic[3] != '1') throw new IOException("bad next");
        int words = Integer.reverseBytes(d.readInt());
        float top = Float.intBitsToFloat(Integer.reverseBytes(d.readInt()));
        float bottom = Float.intBitsToFloat(Integer.reverseBytes(d.readInt()));
        int groups = Integer.reverseBytes(d.readInt());
        int pairs = Integer.reverseBytes(d.readInt());
        if (words != dictSize) throw new IOException("next: dictionary mismatch");
        if (groups < 0 || pairs < 0 || groups > words || pairs > (4 << 20) || !(top > bottom)) {
            throw new IOException("bad next");
        }
        int[] prev = new int[groups];
        int[] start = new int[groups + 1];
        byte[] lambda = new byte[groups];
        int[] next = new int[pairs];
        int h = 0, k = 0;
        for (int g = 0; g < groups; g++) {
            h += varint(d);
            if (h < 0 || h >= words || (g > 0 && h <= prev[g - 1])) throw new IOException("bad next");
            prev[g] = h;
            lambda[g] = d.readByte();
            int n = varint(d);
            if (n <= 0 || k + n > pairs) throw new IOException("bad next");
            start[g] = k;
            int w = 0;
            for (int i = 0; i < n; i++) {
                w += varint(d);
                if (w < 0 || w >= words || (i > 0 && w <= next[k - 1])) throw new IOException("bad next");
                next[k++] = w;
            }
        }
        if (k != pairs) throw new IOException("bad next");
        start[groups] = k;
        byte[] q = new byte[pairs];
        d.readFully(q);
        return new NextWords(prev, start, lambda, next, q, top, bottom);
    }

    private static int varint(DataInputStream d) throws IOException {
        int v = 0;
        for (int shift = 0; shift < 32; shift += 7) {
            int b = d.readUnsignedByte();
            v |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return v;
        }
        throw new IOException("bad next");
    }

    /** 단어 사전 점수의 단어 확률 P(w). */
    public double unigram(int score) {
        return uni[score & 0xFF];
    }

    /** 앞 단어의 묶음 번호. 다음 단어 정보가 없으면 -1. */
    public int group(int prevId) {
        int lo = 0, hi = prev.length - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (prev[mid] < prevId) lo = mid + 1;
            else if (prev[mid] > prevId) hi = mid - 1;
            else return mid;
        }
        return -1;
    }

    /** 앞 단어 묶음 수. */
    public int groups() {
        return prev.length;
    }

    /** 묶음 g의 앞 단어 번호. */
    public int head(int g) {
        return prev[g];
    }

    public double lambda(int g) {
        return (lambda[g] & 0xFF) / 255.0;
    }

    public int from(int g) {
        return start[g];
    }

    public int to(int g) {
        return start[g + 1];
    }

    public int word(int i) {
        return next[i];
    }

    /** 단어 쌍 i의 P(다음|앞). */
    public double prob(int i) {
        return Math.exp(-(q[i] & 0xFF) / 4.0);
    }

    /** 묶음 g 안에서 단어 w의 단어 쌍 번호. 없으면 -1. */
    public int find(int g, int w) {
        int lo = start[g], hi = start[g + 1] - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (next[mid] < w) lo = mid + 1;
            else if (next[mid] > w) hi = mid - 1;
            else return mid;
        }
        return -1;
    }

    /** 묶음 g 안에서 번호가 from 이상인 첫 단어 쌍 번호 (구간 [from, to)의 단어를 훑을 때). */
    public int lowerBound(int g, int w) {
        int lo = start[g], hi = start[g + 1];
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (next[mid] < w) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    public int pairs() {
        return next.length;
    }
}
