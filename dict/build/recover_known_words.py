#!/usr/bin/env python3
"""예전 형식(NSK1, 블룸 필터)의 맞는 말 목록에 든 말을 말뭉치에서 되찾는다 (새 형식 NSK2로 다시 만들 때).

  python3 dict/build/recover_known_words.py dict_ko_known.bin words.txt 말뭉치들...
  python3 dict/build/build_known_words.py dict_ko_known.bin --words words.txt --fp 0.001

목록은 build_known_words.py가 Leipzig 말뭉치(build_ko_model.py와 같은 파일)에서 고른 말이고, 그런 말은 모두 말뭉치에서
한글 음절만 이어진 덩어리(2~12글자)로 나온다. 그런 덩어리를 모두 블룸 필터에 물어 걸리는 것을 적는다. 목록의 말은 빠짐없이
걸리고, 블룸 필터가 잘못 알아보는 말(목록 밖 말의 0.1%쯤)도 함께 걸리므로 앱의 판단은 그대로다.
"""
import re
import struct
import sys

from build_known_words import MASK, SEED1, SEED2, fnv


def main():
    if len(sys.argv) < 4:
        sys.exit(__doc__)
    with open(sys.argv[1], 'rb') as f:
        data = f.read()
    magic, m, n, k = struct.unpack_from('<4sIIB', data)
    if magic != b'NSK1':
        sys.exit('NSK1 파일이 아니다')
    bits = data[13:]
    run = re.compile('[가-힣]{2,}')
    seen = set()
    for path in sys.argv[3:]:
        with open(path, encoding='utf-8', errors='replace') as f:
            for line in f:
                seen.update(w for w in run.findall(line) if len(w) <= 12)
    hits = []
    for w in seen:
        h1, h2 = fnv(w, SEED1), fnv(w, SEED2) | 1
        if all(bits[(at := ((h1 + i * h2) & MASK) % m) >> 3] >> (at & 7) & 1 for i in range(k)):
            hits.append(w)
    with open(sys.argv[2], 'w', encoding='utf-8') as f:
        f.write('\n'.join(sorted(hits)) + '\n')
    print(f'말뭉치의 덩어리 {len(seen):,}개 중 {len(hits):,}개가 걸렸다 (목록의 말 {n:,}개 + 잘못 알아본 말) → {sys.argv[2]}')


if __name__ == '__main__':
    main()
