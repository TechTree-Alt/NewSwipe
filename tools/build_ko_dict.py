#!/usr/bin/env python3
"""한국어 단어 추천용 시험 사전을 만든다.

입력: Leipzig Corpora Collection (CC BY 4.0)의 한국어 단어 빈도 파일 (*-words.txt: 순번 \t 단어 \t 빈도)
출력: 빈도 순위 TSV(검토용)와, 앱에 넣을 압축 이진 사전(dict_ko.bin)

사용법:
  python3 tools/build_ko_dict.py OUT_DIR TOP_N [--extra 목록.txt ...] words1.txt [words2.txt ...]

  --extra: 보완 목록 (여러 번 지정 가능). 뉴스·웹 말뭉치에 대화체가 거의 없어서
           직접 고른 구어체 목록(ko_colloquial.txt)과 활용형 목록(ko_conjugated.txt)으로 보완한다.

처리 순서:
  1) 한글 음절만으로 된 단어만 남긴다 (숫자, 영문, 기호가 섞인 것은 제외).
  2) 말뭉치마다 '100만 토큰당 빈도'로 바꿔 평균을 낸다 (뉴스와 웹의 규모 차이를 맞춘다).
  3) 한 글자 단어는 허용 목록만 남긴다 (조사·어미 조각이 대부분이라 추천에 도움이 안 된다).
  4) 욕설·비속어 차단 목록이 들어간 단어는 뺀다.
  5) 상위 TOP_N개를 점수(양자화한 로그 빈도 1바이트)와 함께 저장한다.

이진 형식 (little endian):
  magic "NSD1" | u32 단어 수 | u32 문자열 영역 크기
  단어마다: u8 공통 접두사 길이(앞 단어와 UTF-16 코드 단위 기준) | u8 이어붙일 글자 수 | u8 점수 | 글자들(UTF-16LE)
  가나다순(유니코드 순)으로 정렬되어 있어 이진 탐색과 접두사 범위 조회가 된다.
"""
import math
import os
import re
import struct
import sys
from collections import defaultdict

HANGUL = re.compile(r'^[가-힣]+$')

# 한 글자로도 추천할 가치가 있는 단어(자주 쓰는 명사·부사·감탄사).
ONE_CHAR_OK = set('나너내네저제우그이것수때중안못잘더또네예응아오음글말집일년월일밤낮물불길꿈별달해곧')

# 추천에서 빼는 욕설·비속어 조각. 이 문자열이 들어간 단어는 제외한다.
# 뉴스·일상어에도 쓰이는 말(성폭력, 자위대, 한남동, 꺼져, 닥쳐 등)이 걸리지 않도록 뜻이 분명한 것만 둔다.
BLOCKLIST = [
    '씨발', '시발', '씨팔', '시팔', '병신', '개새끼', '좆', '존나', '지랄', '느금', '니미', '엿먹',
    '미친놈', '미친년', '또라이', '썅', '쌍놈', '쌍년', '창녀', '김치녀', '일베충', '섹스', '야동', '포르노',
]


def read_words(path):
    rows = []
    total = 0
    with open(path, encoding='utf-8') as f:
        for line in f:
            p = line.rstrip('\n').split('\t')
            if len(p) != 3:
                continue
            try:
                c = int(p[2])
            except ValueError:
                continue
            total += c
            rows.append((p[1], c))
    return rows, total


def read_extra(path):
    """'점수: 단어 단어 ...' 형식의 보완 목록을 읽는다."""
    extra = {}
    with open(path, encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            score, _, words = line.partition(':')
            for w in words.split():
                if HANGUL.match(w):
                    extra[w] = max(extra.get(w, 0), float(score))
    return extra


def main():
    args = sys.argv[1:]
    extra = {}
    while '--extra' in args:   # 여러 번 줄 수 있다 (구어체 목록, 활용형 목록 등)
        i = args.index('--extra')
        for w, sc in read_extra(args[i + 1]).items():
            extra[w] = max(extra.get(w, 0), sc)
        del args[i:i + 2]
    out_dir, top_n = args[0], int(args[1])
    paths = args[2:]
    os.makedirs(out_dir, exist_ok=True)

    per_million = defaultdict(float)
    for path in paths:
        rows, total = read_words(path)
        print(f'{os.path.basename(path)}: {len(rows):,} types, {total:,} tokens')
        for w, c in rows:
            if HANGUL.match(w):
                per_million[w] += c * 1_000_000 / total / len(paths)

    # 구어체 보완 목록: 이미 있는 단어는 점수가 더 클 때만 올린다.
    for w, sc in extra.items():
        per_million[w] = max(per_million.get(w, 0.0), sc)
    if extra:
        print(f'extra entries: {len(extra):,}')

    kept = []
    dropped_one = dropped_block = 0
    for w, f in per_million.items():
        if len(w) == 1 and w not in ONE_CHAR_OK and w not in extra:
            dropped_one += 1
            continue
        if len(w) > 12:
            continue
        if any(b in w for b in BLOCKLIST):
            dropped_block += 1
            continue
        kept.append((w, f))
    kept.sort(key=lambda x: -x[1])
    kept = kept[:top_n]
    print(f'kept {len(kept):,} (dropped one-char {dropped_one:,}, blocklist {dropped_block:,}), '
          f'min freq/million {kept[-1][1]:.2f}')

    # 검토용 TSV (빈도순)
    with open(os.path.join(out_dir, 'ko_rank.tsv'), 'w', encoding='utf-8') as f:
        for w, fr in kept:
            f.write(f'{w}\t{fr:.2f}\n')

    # 점수: 로그 빈도를 1~255로 양자화
    top, bottom = math.log(kept[0][1] + 1), math.log(kept[-1][1] + 1)
    def score(fr):
        return 1 + int(round(254 * (math.log(fr + 1) - bottom) / (top - bottom)))

    words = sorted(kept, key=lambda x: x[0])
    blob = bytearray()
    prev = ''
    for w, fr in words:
        common = 0
        while common < len(prev) and common < len(w) and prev[common] == w[common] and common < 255:
            common += 1
        tail = w[common:]
        blob += struct.pack('<BBB', common, len(tail), score(fr))
        blob += tail.encode('utf-16-le')
        prev = w
    path = os.path.join(out_dir, 'dict_ko.bin')
    with open(path, 'wb') as f:
        f.write(b'NSD1' + struct.pack('<II', len(words), len(blob)) + bytes(blob))
    print(f'wrote {path}: {os.path.getsize(path):,} bytes')


if __name__ == '__main__':
    main()
