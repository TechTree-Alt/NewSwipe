#!/usr/bin/env python3
"""자동 수정이 맞는 말로 알아볼 단어 목록(dict_ko_known.bin)을 만든다.

단어 사전에는 빈도 상위 단어만 들어가므로, 사전에 없지만 맞는 말(공황, 배탈, 명언, 깜깜하고)을 자동 수정이 가까운 사전 단어로
잘못 고친다 (공황 → 공항). 말뭉치에 여러 번 나온 말을 이 목록에 담아 두면 그런 말은 고치지 않고, 그만큼 자동 수정을 더 세게
(드문 단어로도) 할 수 있다. 앱은 이 목록을 추천에는 쓰지 않는다.

  python3 dict/build/build_known_words.py OUT_DIR/dict_ko_known.bin --counts counts.pkl --dict OUT_DIR/ko_rank.tsv \\
      [--min-freq 0.2] [--fp 0.01] [--exclude dict/lists/ko_known_exclude.txt]
  python3 dict/build/build_known_words.py OUT_DIR/dict_ko_known.bin --words 단어목록.txt [--fp 0.001]

  - counts.pkl: build_ko_model.py --cache가 만든 Leipzig 말뭉치 빈도. 리뷰(NSMC)는 맞춤법이 틀린 말이 많아 쓰지 않는다.
  - 100만 어절당 --min-freq 이상 나온 한글 2~12글자 말 중 단어 사전(--dict, build_ko_model.py의 ko_rank.tsv)에 없는 것.
  - --words: 고른 말을 직접 준다 (한 줄에 하나). --counts·--dict·--min-freq·--exclude는 쓰지 않는다.
  - --fp: 목록에 없는 말을 있다고 할 확률. 그만큼 오타를 덜 고친다.
  - --exclude: 넣지 않을 말 (말뭉치에 나오지만 대화에서는 거의 쓰지 않아 흔한 말의 오타로 보는 편이 나은 말: 배일 → 매일).
  - --bloom: 예전 형식(NSK1, 블룸 필터)으로 쓴다. 같은 --fp에서 기본 형식(NSK2)보다 30%쯤 크다.

기본 형식 NSK2는 binary fuse 필터(4-wise, Graf·Lemire 2022)다: 단어마다 칸 네 개의 지문을 XOR한 값이 그 단어의 지문과 같다.
지문이 b비트면 잘못 알아볼 확률은 2^-b이고, 단어 하나에 b × 1.07비트쯤 쓴다 (블룸 필터는 b × 1.44비트).
형식과 해시는 app/src/main/java/.../suggest/KnownWords.java의 설명과 같다.
"""
import argparse
import math
import pickle
import re
import struct

SEED1, SEED2 = 0xcbf29ce484222325, 0x84222325cbf29ce4
PRIME = 0x100000001b3
MASK = (1 << 64) - 1
HANGUL = re.compile('^[가-힣]{2,12}$')
GOLDEN = 0x9E3779B97F4A7C15
ARITY = 4


def fnv(w, seed):
    h = seed
    for ch in w:
        c = ord(ch)
        h = ((h ^ (c & 0xFF)) * PRIME) & MASK
        h = ((h ^ (c >> 8)) * PRIME) & MASK
    return h


def fmix(h):
    """MurmurHash3의 64비트 마무리 섞기."""
    h ^= h >> 33
    h = (h * 0xff51afd7ed558ccd) & MASK
    h ^= h >> 33
    h = (h * 0xc4ceb9fe1a85ec53) & MASK
    h ^= h >> 33
    return h


def fuse_cells(h, seed, seg_len, seg_count_len):
    """단어 해시 h의 칸 네 개와 지문 해시 (KnownWords.contains와 같다)."""
    x = fmix((h + seed) & MASK)
    y = fmix((x + GOLDEN) & MASK)
    m = seg_len - 1
    h0 = ((x >> 32) * seg_count_len) >> 32
    return (h0, (h0 + seg_len) ^ ((x >> 18) & m), (h0 + 2 * seg_len) ^ (x & m), (h0 + 3 * seg_len) ^ (y & m)), y >> 32


def fuse_filter(words, bits, size_factor=None, max_tries=64):
    """(NSK2 바이트, 설명). 칸 배치가 풀리지 않으면 다음 seed로 다시 한다."""
    keys = sorted({fnv(w, SEED1) for w in words})
    n = len(keys)
    seg_len = 1 << max(1, min(18, int(math.floor(math.log(max(n, 2)) / math.log(2.91) - 0.5))))
    if size_factor is None:
        size_factor = max(1.075, 0.77 + 0.305 * math.log(600000) / math.log(max(n, 2)))
    capacity = int(round(n * size_factor))
    seg_count = max(1, (capacity + seg_len - 1) // seg_len - (ARITY - 1))
    length = (seg_count + ARITY - 1) * seg_len
    seg_count_len = seg_count * seg_len
    fmask = (1 << bits) - 1
    for seed in range(max_tries):
        cells = []
        fps = []
        count = [0] * length
        xor = [0] * length
        for i, h in enumerate(keys):
            c, f = fuse_cells(h, seed, seg_len, seg_count_len)
            cells.append(c)
            fps.append(f & fmask)
            for j in c:
                count[j] += 1
                xor[j] ^= i
        stack = []
        queue = [j for j in range(length) if count[j] == 1]
        while queue:
            j = queue.pop()
            if count[j] != 1:
                continue
            i = xor[j]
            stack.append((i, j))
            for t in cells[i]:
                count[t] -= 1
                xor[t] ^= i
                if count[t] == 1:
                    queue.append(t)
        if len(stack) != n:
            continue
        value = [0] * length
        for i, j in reversed(stack):
            v = fps[i]
            for t in cells[i]:
                if t != j:
                    v ^= value[t]
            value[j] = v
        packed = bytearray((length * bits + 7) // 8 + 2)   # 끝의 2바이트: 3바이트씩 읽을 때 넘치지 않게
        for j, v in enumerate(value):
            p = j * bits
            word = v << (p & 7)
            k = p >> 3
            while word:
                packed[k] |= word & 0xFF
                word >>= 8
                k += 1
        head = b'NSK2' + struct.pack('<IIIQB', n, seg_len, seg_count, seed, bits)
        return head + bytes(packed), f'칸 {length:,}개 (단어의 {length / max(n, 1):.3f}배), seed {seed}'
    raise RuntimeError('칸 배치가 풀리지 않는다 (size_factor를 키운다)')


def bloom_filter(words, fp):
    n = len(words)
    m = max(64, int(math.ceil(-n * math.log(fp) / math.log(2) ** 2)))
    m = (m + 7) // 8 * 8
    k = max(1, round(m / n * math.log(2)))
    out = bytearray(m // 8)
    for w in words:
        h1, h2 = fnv(w, SEED1), fnv(w, SEED2) | 1
        for i in range(k):
            at = ((h1 + i * h2) & MASK) % m
            out[at >> 3] |= 1 << (at & 7)
    return b'NSK1' + struct.pack('<IIB', m, n, k) + bytes(out), f'해시 {k}개'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('out')
    ap.add_argument('--counts')
    ap.add_argument('--dict', help='단어 사전의 ko_rank.tsv (사전에 있는 말은 담지 않는다)')
    ap.add_argument('--words', help='담을 말의 목록 (한 줄에 하나). 주면 --counts·--dict를 쓰지 않는다')
    ap.add_argument('--min-freq', type=float, default=0.2)
    ap.add_argument('--fp', type=float, default=0.01)
    ap.add_argument('--exclude', help='넣지 않을 말의 목록 (한 줄에 하나, #로 시작하면 설명)')
    ap.add_argument('--bloom', action='store_true', help='예전 형식(NSK1, 블룸 필터)으로 쓴다')
    a = ap.parse_args()
    if a.words:
        with open(a.words, encoding='utf-8') as f:
            words = sorted({line.strip() for line in f if line.strip()})
    else:
        if not a.counts or not a.dict:
            ap.error('--words 또는 --counts와 --dict가 있어야 한다')
        with open(a.counts, 'rb') as f:
            freq = pickle.load(f)[0]
        with open(a.dict, encoding='utf-8') as f:
            have = {line.split('\t')[0] for line in f}
        exclude = set()
        if a.exclude:
            with open(a.exclude, encoding='utf-8') as f:
                exclude = {line.strip() for line in f if line.strip() and not line.startswith('#')}
        words = sorted(w for w, fr in freq.items()
                       if fr >= a.min_freq and HANGUL.match(w) and w not in have and w not in exclude)
    if a.bloom:
        data, info = bloom_filter(words, a.fp)
    else:
        bits = max(1, min(16, int(math.ceil(-math.log2(a.fp)))))   # 2^-bits ≤ fp
        try:   # 칸을 조금 덜 써 본다 (40만 단어면 첫 seed에 풀린다). 안 풀리면 논문의 기준 크기로
            data, info = fuse_filter(words, bits, size_factor=1.07, max_tries=8)
        except RuntimeError:
            data, info = fuse_filter(words, bits)
    with open(a.out, 'wb') as f:
        f.write(data)
    print(f'단어 {len(words):,}개, {len(data):,} 바이트 ({info}, 잘못 알아볼 확률 {a.fp}) → {a.out}')


if __name__ == '__main__':
    main()
