#!/usr/bin/env python3
"""count_ko_stems.py가 센 어간 중 ko_stems.txt에 없는 것을 골라 3단계 어간 목록(ko_stems_kiwi.txt)을 만든다.

사용법:
  python3 dict/build/select_ko_stems.py kiwi_stems.tsv dict/lists/ko_stems.txt dict/lists/ko_stems_kiwi.txt \
      --okt verb.txt adjective.txt --min 1.0
  (verb.txt·adjective.txt는 github.com/open-korean-text/open-korean-text의
   src/main/resources/org/openkoreantext/processor/util/verb/verb.txt, adjective/adjective.txt)
  python3 dict/build/select_ko_stems.py kiwi_stems.tsv dict/lists/ko_stems.txt --check   # 손으로 고른 목록 검사

고르기:
  - 100만 어절당 빈도가 --min 이상인 어간 (1.0: Chatbot_data·NSMC dev에서 0.5·2.0보다 나았다).
  - 형태소 분석이 틀린 어간(드문 말을 용언으로 잘못 나눈 것)을 거르려고, --free보다 드문 어간은 open-korean-text의 동사·형용사
    목록(--okt, Apache License 2.0)에도 있어야 한다. 명사 + 하다·되다·스럽다처럼 접미사로 만든 어간은 목록에 없어도 된다.
  - 한 글자 어간은 --free 이상이고 목록에도 있어야 한다 (분석 실수가 많다).
  - 같은 어간이 동사·형용사 둘 다로 분석되면 더 많은 쪽 (2배가 안 되면 동사·형용사에 같이 붙는 어미만 쓰도록 형용사로 둔다).
--check: ko_stems.txt의 불규칙 표시·품사가 Kiwi의 분석과 다른 어간을 찍는다 (빈도가 --min 이상인 것만).
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_ko_conjugations as g  # noqa: E402

SUFFIXES = ('하', '되', '스럽', '롭', '시키', '당하', '받')
PER_LINE = 40


def read_kiwi(path):
    """{어간: {품사: (표시, 빈도, 불규칙 비율)}}"""
    out = {}
    with open(path, encoding='utf-8') as f:
        for line in f:
            stem, pos, flag, fr, ratio = line.rstrip('\n').split('\t')
            out.setdefault(stem, {})[pos] = (flag or None, float(fr), float(ratio))
    return out


def read_stems(path):
    """손으로 고른 목록의 {어간: (품사, 표시)}."""
    out = {}
    with open(path, encoding='utf-8') as f:
        for line in f:
            if line.startswith('#') or ':' not in line:
                continue
            kind, _, tokens = line.partition(':')
            for t in tokens.split():
                stem, flag = g.parse_stem(t)
                out.setdefault(stem, (kind.strip()[0], flag))
    return out


def read_list(path):
    with open(path, encoding='utf-8') as f:
        return {w.strip() for w in f if w.strip() and not w.startswith('#')}


def check(kiwi, have, min_freq):
    bad = 0
    for stem, (pos, flag) in sorted(have.items()):
        k = kiwi.get(stem)
        if not k:
            continue
        kpos = max(k, key=lambda p: k[p][1])
        kflag, fr, ratio = k[kpos]
        if fr < min_freq:
            continue
        notes = []
        if (kflag or None) != (flag or None) and g.final_of(stem) != 'ㄹ':
            notes.append(f'표시 {flag or "-"} → Kiwi {kflag or "-"} (불규칙 비율 {ratio:.2f})')
        if kpos != pos and k[kpos][1] > 2 * k.get(pos, (None, 0, 0))[1]:
            notes.append(f'품사 {pos} → Kiwi {kpos}')
        if notes:
            bad += 1
            print(f'{stem}\t{fr:.1f}\t' + '; '.join(notes))
    print(f'다른 것 {bad}개', file=sys.stderr)


# 한 글자 어간 중 생성기가 바르게 활용하지 못하거나(푸다 → 퍼: 우 불규칙) 분석 실수·옛말인 것
ONE_BAD = {'푸', '허', '앗', '외'}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('kiwi')
    ap.add_argument('stems')
    ap.add_argument('out', nargs='?')
    ap.add_argument('--okt', nargs=2, metavar=('VERB', 'ADJ'))
    ap.add_argument('--min', type=float, default=1.0)
    ap.add_argument('--free', type=float, default=20.0)
    ap.add_argument('--free-one', type=float, help='한 글자 어간의 최소 빈도 (목록에 있어야 한다, 기본은 --free)')
    ap.add_argument('--check', action='store_true')
    a = ap.parse_args()
    kiwi = read_kiwi(a.kiwi)
    have = read_stems(a.stems)
    if a.check:
        check(kiwi, have, a.min)
        return
    okt = {'v': read_list(a.okt[0]), 'a': read_list(a.okt[1])} if a.okt else {'v': set(), 'a': set()}
    chosen = {'v': [], 'a': []}
    for stem, k in kiwi.items():
        if stem in have:
            continue
        v, adj = k.get('v', (None, 0, 0)), k.get('a', (None, 0, 0))
        pos = 'v' if v[1] >= 2 * adj[1] else 'a'
        flag, fr, _ = k[pos] if pos in k else k['v']
        fr = v[1] + adj[1]
        if fr < a.min:
            continue
        listed = stem in okt['v'] or stem in okt['a']
        suffixed = len(stem) >= 2 and stem.endswith(SUFFIXES)
        if len(stem) == 1 and (fr < (a.free if a.free_one is None else a.free_one) or not listed or stem in ONE_BAD):
            continue
        if fr < a.free and not listed and not suffixed:
            continue
        if g.final_of(stem) == 'ㅆ' and not stem.endswith(('있', '없')):
            continue
        chosen[pos].append((fr, stem, flag))
    with open(a.out, 'w', encoding='utf-8') as f:
        f.write('# 말뭉치에서 센 용언 어간 (3단계). select_ko_stems.py가 만든다 (직접 고치지 않는다).\n'
                '# Leipzig 말뭉치(뉴스 2022, 웹 2020·2015)를 Kiwi(kiwipiepy 0.24.0, Apache License 2.0)로 분석해\n'
                '# count_ko_stems.py가 센 어간 중 ko_stems.txt에 없는 것. 형식: 어간[/불규칙 표시]=100만 어절당 빈도.\n'
                '# 드문 어간은 open-korean-text(Apache License 2.0)의 동사·형용사 목록에 있는 것만 골랐다.\n'
                '# 쓰는 곳: build_ko_model.py --admit-stems (리뷰를 섞을 때 어간 + 어미로 풀리는 말을 들인다).\n'
                '# gen_ko_conjugations.py로 활용형을 만들어 --extra로 넣을 수도 있지만 기본 사전에서는 낫지 않았다 (TIER3_SCALE).\n')
        for pos, kind in (('v', 'v3'), ('a', 'a3')):
            rows = sorted(chosen[pos], key=lambda x: (-x[0], x[1]))
            for i in range(0, len(rows), PER_LINE):
                toks = [f'{s}{"/" + fl if fl else ""}={fr:.1f}' for fr, s, fl in rows[i:i + PER_LINE]]
                f.write(f'{kind}: ' + ' '.join(toks) + '\n')
    print(f'동사 {len(chosen["v"]):,}개, 형용사 {len(chosen["a"]):,}개 → {a.out}', file=sys.stderr)


if __name__ == '__main__':
    main()
