#!/usr/bin/env python3
"""말뭉치에 나왔지만 사전 팩에 들지 못한 용언 활용형을 고른다 (build_ko_model.py --forms-keep에 넣는다).

gen_ko_pack_forms.py가 만든 활용형은 어림한 빈도가 높은 순으로 --forms-max개만 넣으므로, 실제로 쓰지만 만들지 않았거나(아니다의
아닌지라) 어림 빈도가 낮게 나온 꼴(있는지라, 설계하였다)이 빠진다. 말뭉치에 100만 어절당 --min-freq번 이상 나온 말 중 사전에
없고, 형태소 분석기 Kiwi가 '용언 어간(또는 명사·어근 + 하다류 접미사) + 어미'로만 나누는 말을 고른다.

  python3 dict/build/attested_ko_forms.py OUT.txt --counts counts.pkl --dict out_pack/ko_rank.tsv [--min-freq 0.05] \\
      [--exclude dict/lists/ko_attested_exclude.txt]

  - counts.pkl: build_ko_model.py --cache가 만든 Leipzig 말뭉치 빈도 (리뷰는 맞춤법이 틀린 말이 많아 쓰지 않는다).
  - 표준이 아닌 어미(할께요, 있읍니다, 생각햇다, 되였다, 바람니다)는 Kiwi가 받아들여도 뺀다: 사전에 들면 자동 수정이 바른 꼴로
    고치지 못한다. --exclude에는 그 밖에 뺄 말을 적는다.
출력은 --extra 형식('100만 어절당 빈도: 단어')이다. Kiwi(kiwipiepy==0.24.0)는 사전을 만들 때만 쓴다.
"""
import argparse
import pickle
import re

HANGUL = re.compile('^[가-힣]{2,12}$')
PRED = {'VV', 'VA', 'VX', 'VCN'}          # 동사·형용사·보조 용언·아니다
DERIV = {'XSV', 'XSA'}                    # 명사·어근 + 하다·되다·스럽다 ...
# 표준이 아닌 어미 (Kiwi가 구어로 받아들이는 것)
BAD_ENDINGS = {'ᆯ께', 'ᆯ께요', '께', '께요', 'ᆯ꺼', '꺼', '엇', '읍니다', 'ᆷ니다', '어여', '여', 'ᆸ시요', 'ᆯ려고', 'ᆯ려면',
               'ᆯ려', '디', '구여', '서'}
# 표준이 아닌 꼴 (어미 분석과 상관없이)
BAD_PARTS = ('되였', '됬', '않되', '되요', '되서', '되야', '햇', '겟', '읍니', '슴니다', '음니다', '십시요', '므로써')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('out')
    ap.add_argument('--counts', required=True)
    ap.add_argument('--dict', required=True, help='사전의 ko_rank.tsv (이미 있는 말은 고르지 않는다)')
    ap.add_argument('--min-freq', type=float, default=0.05)
    ap.add_argument('--exclude', help='더 뺄 말의 목록 (한 줄에 하나, #로 시작하면 설명)')
    a = ap.parse_args()
    from kiwipiepy import Kiwi

    with open(a.counts, 'rb') as f:
        freq = pickle.load(f)[0]
    with open(a.dict, encoding='utf-8') as f:
        have = {line.split('\t')[0] for line in f}
    exclude = set()
    if a.exclude:
        with open(a.exclude, encoding='utf-8') as f:
            exclude = {line.strip() for line in f if line.strip() and not line.startswith('#')}
    cand = sorted(((fr, w) for w, fr in freq.items()
                   if fr >= a.min_freq and HANGUL.match(w) and w not in have and w not in exclude
                   and not any(p in w for p in BAD_PARTS)), reverse=True)
    kiwi = Kiwi()
    out = []
    for (fr, w), res in zip(cand, kiwi.analyze([w for _, w in cand], top_n=1)):
        toks = res[0][0]
        if not toks or toks[0].start != 0:
            continue
        tags = [t.tag for t in toks]
        if tags[0] in PRED:
            i = 1
        elif len(tags) > 1 and tags[0] in ('NNG', 'XR') and tags[1] in DERIV:
            i = 2
        else:
            continue
        ends = toks[i:]
        # -는지라·-ㄴ지라는 의존 명사 '지' + 서술격 조사로 나뉜다 (없는지라 = 없 + 는 + 지 + 이 + 라)
        for j in range(len(ends) - 1):
            if ends[j].tag == 'NNB' and ends[j].form == '지' and ends[j + 1].tag == 'VCP':
                ends = ends[:j] + ends[j + 2:]
                break
        if not ends or not all(t.tag.startswith('E') for t in ends) or any(t.form in BAD_ENDINGS for t in ends):
            continue
        out.append((fr, w))
    with open(a.out, 'w', encoding='utf-8') as f:
        f.write(f'# attested_ko_forms.py: 말뭉치에 100만 어절당 {a.min_freq}번 이상 나온, 사전에 없는 용언 활용형\n')
        for fr, w in out:
            f.write(f'{fr:.3f}: {w}\n')
    print(f'후보 {len(cand):,}개 중 활용형 {len(out):,}개 → {a.out}')


if __name__ == '__main__':
    main()
