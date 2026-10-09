#!/usr/bin/env python3
"""영어 단어 추천용 시험 사전을 만든다.

입력: SymSpell의 en-80k.txt (단어 \t 빈도; Google Books Ngram(CC BY 3.0)과 SCOWL 기반 헌스펠 사전의 교집합,
      저장소는 MIT)와, 직접 고른 구어체·축약형 목록(tools/en_colloquial.txt).
출력: 빈도 순위 TSV(검토용)와 앱에 넣을 이진 사전(dict_en.bin). 형식은 build_ko_dict.py와 같다.

사용법:
  python3 tools/build_en_dict.py OUT_DIR TOP_N en-80k.txt [en_colloquial.txt]
"""
import math
import os
import re
import struct
import sys

WORD = re.compile(r"^[a-z]+(?:'[a-z]+)?$")

# 일반적인 입력 보조에서 추천하지 않는 단어 조각. 뜻이 분명한 욕설·선정적 단어만 둔다.
BLOCKLIST = [
    'fuck', 'shit', 'bitch', 'cunt', 'nigg', 'faggot', 'whore', 'slut', 'dick', 'cock', 'pussy', 'porn', 'cum',
    'bastard', 'asshole', 'retard',
]

# 두 글자 이하 단어 중 남길 것 (나머지는 대부분 약어·조각이라 추천에 도움이 안 된다).
SHORT_OK = {'a', 'i', 'an', 'am', 'as', 'at', 'be', 'by', 'do', 'go', 'he', 'if', 'in', 'is', 'it', 'me', 'my',
            'no', 'of', 'oh', 'ok', 'on', 'or', 'so', 'to', 'up', 'us', 'we', 'hi'}


def read_extra(path):
    extra = {}
    if not path:
        return extra
    with open(path, encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            score, _, words = line.partition(':')
            for w in words.split():
                extra[w.lower()] = max(extra.get(w.lower(), 0), float(score))
    return extra


def main():
    out_dir, top_n, src = sys.argv[1], int(sys.argv[2]), sys.argv[3]
    extra = read_extra(sys.argv[4] if len(sys.argv) > 4 else None)
    os.makedirs(out_dir, exist_ok=True)

    rows = {}
    total = 0
    with open(src, encoding='utf-8') as f:
        for line in f:
            p = line.split()
            if len(p) != 2:
                continue
            total += int(p[1])
            rows[p[0].lower()] = rows.get(p[0].lower(), 0) + int(p[1])
    per_million = {w: c * 1_000_000 / total for w, c in rows.items()}

    for w, s in extra.items():
        per_million[w] = max(per_million.get(w, 0), s)

    kept = []
    for w, f in per_million.items():
        if not WORD.match(w):
            continue
        if len(w) <= 2 and w not in SHORT_OK and w not in extra:
            continue
        if any(b in w for b in BLOCKLIST):
            continue
        kept.append((w, f))
    kept.sort(key=lambda x: -x[1])
    kept = kept[:top_n]
    print(f'kept {len(kept):,}, min freq/million {kept[-1][1]:.2f}, extra entries {len(extra)}')

    with open(os.path.join(out_dir, 'en_rank.tsv'), 'w', encoding='utf-8') as f:
        for w, fr in kept:
            f.write(f'{w}\t{fr:.2f}\n')

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
    path = os.path.join(out_dir, 'dict_en.bin')
    with open(path, 'wb') as f:
        f.write(b'NSD1' + struct.pack('<II', len(words), len(blob)) + bytes(blob))
    print(f'wrote {path}: {os.path.getsize(path):,} bytes')


if __name__ == '__main__':
    main()
