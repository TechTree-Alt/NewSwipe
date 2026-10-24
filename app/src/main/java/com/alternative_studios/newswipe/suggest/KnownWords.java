package com.alternative_studios.newswipe.suggest;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 맞는 말로 알아볼 단어 목록 (블룸 필터). 단어 사전에는 없지만 말뭉치에 나온 말(공황, 배탈, 명언, 깜깜하고)을 자동 수정이
 * 고치지 않게 하는 데만 쓴다 (추천에는 쓰지 않는다). 단어를 담지 않고 비트만 담아 작다 (단어 하나에 10비트쯤). 목록에 없는 말을
 * 있다고 할 확률(만들 때 정한다, 1%쯤)만큼 오타를 덜 고친다. dict/build/build_known_words.py가 만든다.
 *
 * <p>형식 (little endian): "NSK1" | u32 비트 수 m | u32 단어 수 | u8 해시 수 k | 비트 (m/8 바이트, 비트 i는 바이트 i/8의 i%8번째).
 * 해시: 글자(UTF-16 코드 단위)를 낮은 바이트부터 넣은 FNV-1a 64비트 두 개(시작값 {@link #SEED1}, {@link #SEED2}),
 * i번째 자리 = (h1 + i·h2) mod m (h2는 홀수로 만든다, 부호 없는 나머지).
 */
final class KnownWords {
    static final long SEED1 = 0xcbf29ce484222325L, SEED2 = 0x84222325cbf29ce4L;
    private static final long PRIME = 0x100000001b3L;
    /** 파일 크기 상한 (남이 만든 사전 팩도 읽는다). */
    static final int MAX_BYTES = 16 << 20;
    private final byte[] bits;
    private final long m;
    private final int k;

    private KnownWords(byte[] bits, long m, int k) {
        this.bits = bits;
        this.m = m;
        this.k = k;
    }

    static KnownWords load(InputStream in) throws IOException {
        DataInputStream d = new DataInputStream(in);
        byte[] head = new byte[13];
        d.readFully(head);
        ByteBuffer b = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        if (b.get() != 'N' || b.get() != 'S' || b.get() != 'K' || b.get() != '1') throw new IOException("NSK1이 아니다");
        long m = b.getInt() & 0xFFFFFFFFL;
        b.getInt();   // 단어 수 (검토용)
        int k = b.get() & 0xFF;
        long bytes = (m + 7) / 8;
        if (m == 0 || k == 0 || k > 32 || bytes > MAX_BYTES) throw new IOException("크기가 맞지 않는다");
        byte[] bits = new byte[(int) bytes];
        d.readFully(bits);
        return new KnownWords(bits, m, k);
    }

    boolean contains(String w) {
        long h1 = fnv(w, SEED1), h2 = fnv(w, SEED2) | 1;
        for (int i = 0; i < k; i++) {
            long at = Long.remainderUnsigned(h1 + i * h2, m);
            if ((bits[(int) (at >>> 3)] & (1 << (at & 7))) == 0) return false;
        }
        return true;
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
