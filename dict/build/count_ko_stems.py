#!/usr/bin/env python3
"""말뭉치의 용언 어간을 형태소 분석기 Kiwi(kiwipiepy, Apache License 2.0)로 세어 어간 목록을 만든다.

활용형 생성기(gen_ko_conjugations.py)의 입력인 dict/lists/ko_stems.txt를 늘리는 데 쓴다. 앱에는 Kiwi가 들어가지 않는다
(사전을 만들 때만 쓴다).

사용법:
  pip install kiwipiepy==0.24.0
  python3 dict/build/count_ko_stems.py OUT.tsv kor_news_2022_1M-sentences.txt kor-kr_web_2020_1M-sentences.txt ...
  (Leipzig 문장 파일: 순번 \t 문장. 300만 문장에 4코어로 20분쯤 걸린다.)

출력 (빈도순 TSV): 어간 \t 품사(v 동사 / a 형용사) \t 불규칙 표시(b d s r h 또는 빈칸) \t 100만 어절당 빈도 \t 불규칙으로 분석된 비율
  - 어간은 '-다'를 뺀 꼴이고, 명사 + 하다·되다·스럽다(사랑하, 걱정되, 자연스럽)처럼 접미사로 만든 용언도 한 어간으로 센다.
  - 빈도는 말뭉치마다 (어간 수 / 어절 수 × 100만)을 낸 뒤 평균한다 (build_ko_dict.py의 단어 빈도와 같은 방식).
  - 불규칙 표시: Kiwi가 불규칙 활용으로 분석한 비율(VV-I·VA-I)이 IRREGULAR_MIN 이상이고, 어간 끝이 그 불규칙을 따를 수
    있는 꼴(ㅂ·ㄷ·ㅅ·ㅎ 받침)일 때만 붙인다. 르 어간은 따로 정한다 (RU_EU_DROP). ㄹ 탈락·ㅡ 탈락·하다는 생성기가 스스로 알아본다.
"""
import argparse
import os
import sys
from collections import Counter, defaultdict

IRREGULAR_MIN = 0.2      # 불규칙으로 분석된 비율이 이보다 작으면 규칙 활용으로 본다 (분석 실수를 거른다)
# Kiwi는 르 불규칙(모르 → 몰라)을 불규칙으로 표시하지 않는다. 르 어간은 르 불규칙으로 보되, Kiwi의 결합 규칙
# (models/cong/base/combiningRule.txt)에 ㅡ 탈락으로 적힌 어간(따르 → 따라)과 러 불규칙(이르 → 이르러, 푸르 → 푸르러)은 뺀다.
RU_EU_DROP = ('따르', '치르', '다다르', '우러르', '들르')
RU_REO = {'이르', '푸르', '누르르', '노르'}
SUFFIX_VERBS = {'XSV', 'XSA', 'XSA-I', 'XSV-I'}   # 명사 + 하다·되다·스럽다 ...
JONG = ['', 'ㄱ', 'ㄲ', 'ㄳ', 'ㄴ', 'ㄵ', 'ㄶ', 'ㄷ', 'ㄹ', 'ㄺ', 'ㄻ', 'ㄼ', 'ㄽ', 'ㄾ', 'ㄿ', 'ㅀ', 'ㅁ', 'ㅂ',
        'ㅄ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ']


def hangul(s):
    return bool(s) and all('가' <= c <= '힣' for c in s)


def irregular_flag(stem):
    """어간 끝 꼴로 정해지는 불규칙 표시 (그 불규칙을 따를 수 없는 꼴이면 None)."""
    if stem.endswith('르'):
        return None if stem.endswith(RU_EU_DROP) else 'r'
    t = JONG[(ord(stem[-1]) - 0xAC00) % 28]
    return {'ㅂ': 'b', 'ㄷ': 'd', 'ㅅ': 's', 'ㅎ': 'h'}.get(t)


def sentences(path, seen):
    """문장을 하나씩 내며 어절 수를 seen[0]에 더한다."""
    with open(path, encoding='utf-8', errors='replace') as f:
        for line in f:
            p = line.rstrip('\n').split('\t')
            if len(p) >= 2:
                seen[0] += len(p[-1].split())
                yield p[-1]


def count(path, kiwi):
    """{(어간, 품사): [횟수, 불규칙 횟수]}, 어절 수."""
    stems = defaultdict(lambda: [0, 0])
    seen = [0]
    for i, toks in enumerate(kiwi.tokenize(sentences(path, seen))):
        prev = None
        for t in toks:
            tag = t.tag
            base = tag.split('-')[0]
            irr = tag.endswith('-I')
            stem = None
            if base in ('VV', 'VA'):
                stem, pos = t.form, 'v' if base == 'VV' else 'a'
            elif tag in SUFFIX_VERBS and prev is not None and prev.tag in ('NNG', 'XR') \
                    and prev.end == t.start and hangul(prev.form):
                stem, pos = prev.form + t.form, 'v' if base == 'XSV' else 'a'
            if stem and hangul(stem):
                s = stems[(stem, pos)]
                s[0] += 1
                s[1] += irr
            prev = t
        if i % 200000 == 0 and i:
            print(f'  {os.path.basename(path)}: {i:,} 문장', file=sys.stderr)
    return stems, seen[0]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('out')
    ap.add_argument('corpora', nargs='+')
    ap.add_argument('--workers', type=int, default=os.cpu_count())
    a = ap.parse_args()
    from kiwipiepy import Kiwi
    kiwi = Kiwi(num_workers=a.workers)
    per_million = Counter()
    irr = Counter()
    total = Counter()
    for path in a.corpora:
        stems, words = count(path, kiwi)
        print(f'{os.path.basename(path)}: 어절 {words:,}, 어간 {len(stems):,}', file=sys.stderr)
        for k, (n, i) in stems.items():
            per_million[k] += n * 1_000_000 / words / len(a.corpora)
            irr[k] += i
            total[k] += n
    with open(a.out, 'w', encoding='utf-8') as f:
        for (stem, pos), fr in per_million.most_common():
            if stem in RU_REO:
                continue   # 활용형 생성기가 러 불규칙을 모른다
            ratio = irr[(stem, pos)] / total[(stem, pos)]
            flag = irregular_flag(stem) if ratio >= IRREGULAR_MIN or stem.endswith('르') else None
            f.write(f'{stem}\t{pos}\t{flag or ""}\t{fr:.3f}\t{ratio:.2f}\n')
    print(f'어간 {len(per_million):,}개 → {a.out}', file=sys.stderr)


if __name__ == '__main__':
    main()
