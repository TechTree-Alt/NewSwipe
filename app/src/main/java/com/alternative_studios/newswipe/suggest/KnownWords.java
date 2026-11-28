package com.alternative_studios.newswipe.suggest;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 맞는 말로 알아볼 단어 목록. 단어 사전에는 없지만 말뭉치에 나온 말(공황, 배탈, 명언, 깜깜하고)을 자동 수정이 고치지 않게 하는
 * 데만 쓴다 (추천에는 쓰지 않는다). 단어를 담지 않고 해시로 만든 비트만 담아 작다. 목록에 없는 말을 있다고 할 확률(만들 때
 * 정한다)만큼 오타를 덜 고친다. dict/build/build_known_words.py가 만든다. 두 형식을 읽는다 (little endian):
 *
 * <ul>
 * <li>NSK2 (binary fuse 필터, 4-wise): "NSK2" | u32 단어 수 | u32 칸 묶음 길이 L (2의 거듭제곱) | u32 묶음 수 S | u64 seed |
 *     u8 지문 비트 b | 칸마다 b비트 지문 ((S+3)·L칸, 칸 j는 비트 j·b부터 낮은 비트 먼저) | 0 두 바이트.
 *     단어 해시 h = FNV(SEED1), x = fmix(h + seed), y = fmix(x + GOLDEN). 칸 h0 = ((x >>> 32) · S·L) >>> 32,
 *     h1 = (h0 + L) ^ ((x >>> 18) &amp; (L-1)), h2 = (h0 + 2L) ^ (x &amp; (L-1)), h3 = (h0 + 3L) ^ (y &amp; (L-1)).
 *     네 칸의 지문을 XOR한 값이 (y >>> 32)의 낮은 b비트와 같으면 목록에 있다 (잘못 알아볼 확률 2^-b).</li>
 * <li>NSK1 (블룸 필터, 예전 사전 팩): "NSK1" | u32 비트 수 m | u32 단어 수 | u8 해시 수 k | 비트 (m/8 바이트, 비트 i는 바이트
 *     i/8의 i%8번째). i번째 자리 = (h1 + i·h2) mod m (h1 = FNV(SEED1), h2 = FNV(SEED2) | 1, 부호 없는 나머지).</li>
 * </ul>
 * FNV: 글자(UTF-16 코드 단위)를 낮은 바이트부터 넣은 FNV-1a 64비트 (시작값 {@link #SEED1}, {@link #SEED2}).
 */
final class KnownWords {
    static final long SEED1 = 0xcbf29ce484222325L, SEED2 = 0x84222325cbf29ce4L;
    private static final long PRIME = 0x100000001b3L, GOLDEN = 0x9E3779B97F4A7C15L;
    /** 파일 크기 상한 (남이 만든 사전 팩도 읽는다). */
    static final int MAX_BYTES = 16 << 20;
    private final byte[] bits;
    /** NSK1: 비트 수 m, 해시 수 k. */
    private final long m;
    private final int k;
    /** NSK2: 묶음 길이 L, S·L, seed, 지문 비트 수 (0이면 NSK1). */
    private final int segLen;
    private final long segCountLen, seed;
    private final int fpBits;

    private KnownWords(byte[] bits, long m, int k, int segLen, long segCountLen, long seed, int fpBits) {
        this.bits = bits;
        this.m = m;
        this.k = k;
        this.segLen = segLen;
        this.segCountLen = segCountLen;
        this.seed = seed;
        this.fpBits = fpBits;
    }

    static KnownWords load(InputStream in) throws IOException {
        DataInputStream d = new DataInputStream(in);
        byte[] magic = new byte[4];
        d.readFully(magic);
        if (magic[0] != 'N' || magic[1] != 'S' || magic[2] != 'K' || (magic[3] != '1' && magic[3] != '2')) {
            throw new IOException("NSK1·NSK2가 아니다");
        }
        if (magic[3] == '1') {
            ByteBuffer b = header(d, 9);
            long m = b.getInt() & 0xFFFFFFFFL;
            b.getInt();   // 단어 수 (검토용)
            int k = b.get() & 0xFF;
            long bytes = (m + 7) / 8;
            if (m == 0 || k == 0 || k > 32 || bytes > MAX_BYTES) throw new IOException("크기가 맞지 않는다");
            byte[] bits = new byte[(int) bytes];
            d.readFully(bits);
            return new KnownWords(bits, m, k, 0, 0, 0, 0);
        }
        ByteBuffer b = header(d, 21);
        b.getInt();   // 단어 수 (검토용)
        int segLen = b.getInt();
        long segCount = b.getInt() & 0xFFFFFFFFL;
        long seed = b.getLong();
        int fpBits = b.get() & 0xFF;
        long cells = (segCount + 3) * segLen;
        long bytes = (cells * fpBits + 7) / 8 + 2;
        if (segLen <= 0 || Integer.bitCount(segLen) != 1 || segLen > (1 << 18) || segCount == 0
                || fpBits == 0 || fpBits > 16 || segCount * segLen >= (1L << 32) || bytes > MAX_BYTES) {
            throw new IOException("크기가 맞지 않는다");
        }
        byte[] bits = new byte[(int) bytes];
        d.readFully(bits);
        return new KnownWords(bits, 0, 0, segLen, segCount * segLen, seed, fpBits);
    }

    private static ByteBuffer header(DataInputStream d, int n) throws IOException {
        byte[] head = new byte[n];
        d.readFully(head);
        return ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
    }

    boolean contains(String w) {
        long h = fnv(w, SEED1);
        if (fpBits > 0) {
            long x = fmix(h + seed), y = fmix(x + GOLDEN);
            int mask = segLen - 1;
            long h0 = ((x >>> 32) * segCountLen) >>> 32;
            int f = cell(h0) ^ cell((h0 + segLen) ^ ((x >>> 18) & mask)) ^ cell((h0 + 2L * segLen) ^ (x & mask))
                    ^ cell((h0 + 3L * segLen) ^ (y & mask));
            return f == (int) ((y >>> 32) & ((1 << fpBits) - 1));
        }
        long h2 = fnv(w, SEED2) | 1;
        for (int i = 0; i < k; i++) {
            long at = Long.remainderUnsigned(h + i * h2, m);
            if ((bits[(int) (at >>> 3)] & (1 << (at & 7))) == 0) return false;
        }
        return true;
    }

    /** NSK2: 칸 j의 지문 (3바이트를 읽어 꺼낸다, 지문은 16비트 이하라 넘치지 않는다). */
    private int cell(long j) {
        long p = j * fpBits;
        int at = (int) (p >>> 3);
        int v = (bits[at] & 0xFF) | (bits[at + 1] & 0xFF) << 8 | (bits[at + 2] & 0xFF) << 16;
        return (v >>> (int) (p & 7)) & ((1 << fpBits) - 1);
    }

    /** MurmurHash3의 64비트 마무리 섞기. */
    static long fmix(long h) {
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h;
    }

    static long fnv(String w, long seed) {
        long h = seed;
        for (int i = 0; i < w.length(); i++) {
            char c = w.charAt(i);
            h = (h ^ (c & 0xFF)) * PRIME;
            h = (h ^ (c >>> 8)) * PRIME;
        }
        return h;
    }
}
