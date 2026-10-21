#!/usr/bin/env python3
"""자동 수정이 맞는 말로 알아볼 단어 목록(dict_ko_known.bin, 블룸 필터)을 만든다.

단어 사전에는 빈도 상위 단어만 들어가므로, 사전에 없지만 맞는 말(공황, 배탈, 명언, 깜깜하고)을 자동 수정이 가까운 사전 단어로
잘못 고친다 (공황 → 공항). 말뭉치에 여러 번 나온 말을 이 목록에 담아 두면 그런 말은 고치지 않고, 그만큼 자동 수정을 더 세게
(드문 단어로도) 할 수 있다. 앱은 이 목록을 추천에는 쓰지 않는다.

  python3 dict/build/build_known_words.py OUT_DIR/dict_ko_known.bin --counts counts.pkl --dict OUT_DIR/ko_rank.tsv \\
      [--min-freq 0.2] [--fp 0.01]

  - counts.pkl: build_ko_model.py --cache가 만든 Leipzig 말뭉치 빈도. 리뷰(NSMC)는 맞춤법이 틀린 말이 많아 쓰지 않는다.
  - 100만 어절당 --min-freq 이상 나온 한글 2~12글자 말 중 단어 사전(--dict, build_ko_model.py의 ko_rank.tsv)에 없는 것.
  - --fp: 목록에 없는 말을 있다고 할 확률. 그만큼 오타를 덜 고친다. 단어 하나에 -ln(fp)/ln(2)² 비트를 쓴다.
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


def fnv(w, seed):
    h = seed
    for ch in w:
        c = ord(ch)
        h = ((h ^ (c & 0xFF)) * PRIME) & MASK
        h = ((h ^ (c >> 8)) * PRIME) & MASK
    return h


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('out')
    ap.add_argument('--counts', required=True)
    ap.add_argument('--dict', required=True, help='단어 사전의 ko_rank.tsv (사전에 있는 말은 담지 않는다)')
    ap.add_argument('--min-freq', type=float, default=0.2)
    ap.add_argument('--fp', type=float, default=0.01)
    a = ap.parse_args()
    with open(a.counts, 'rb') as f:
        freq = pickle.load(f)[0]
    with open(a.dict, encoding='utf-8') as f:
        have = {line.split('\t')[0] for line in f}
    words = sorted(w for w, fr in freq.items() if fr >= a.min_freq and HANGUL.match(w) and w not in have)
    n = len(words)
    m = max(64, int(math.ceil(-n * math.log(a.fp) / math.log(2) ** 2)))
    m = (m + 7) // 8 * 8
    k = max(1, round(m / n * math.log(2)))
    bits = bytearray(m // 8)
    for w in words:
        h1, h2 = fnv(w, SEED1), fnv(w, SEED2) | 1
        for i in range(k):
            at = ((h1 + i * h2) & MASK) % m
            bits[at >> 3] |= 1 << (at & 7)
    with open(a.out, 'wb') as f:
        f.write(b'NSK1' + struct.pack('<IIB', m, n, k) + bytes(bits))
    print(f'단어 {n:,}개, {m // 8:,} 바이트 (해시 {k}개, 잘못 알아볼 확률 {a.fp}) → {a.out}')


if __name__ == '__main__':
    main()
