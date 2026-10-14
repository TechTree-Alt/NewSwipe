#!/usr/bin/env python3
"""말뭉치에서 용언 어간을 찾아 대화체 활용형을 만든다 (build_ko_model.py의 --extra 형식으로 쓴다).

뉴스·웹 말뭉치에는 메신저에서 흔한 꼴(먹어야지, 못하겠어, 좋겠지, 떨어졌어, 할거야)이 드물어 사전 상위에 들지 못한다.
어간은 말뭉치에 흔하므로, 어간을 말뭉치에서 찾고 활용 규칙(gen_ko_conjugations.py)으로 대화체 꼴을 만든다.
목록을 사람이 하나하나 고르지 않고, 말뭉치가 근거가 되는 어간에만 만든다.

  1) 어간 찾기: X+고가 있고, X+다·지·게·기·는/은·면/으면·지만 중 셋 이상이 말뭉치에 있는 X.
     명사 + 이다(학생이고)는 뺀다 (X가 이로 끝나고 앞부분에 의·에·을·를이 흔히 붙으면).
  2) 불규칙 가리기: 표시(없음, ㅂ, ㄷ, ㅅ, 르, ㅎ) 중 그 표시로 만든 아/어 꼴·과거형이 말뭉치에 가장 많이 있는 것.
     어느 표시로도 말뭉치에 없으면 그 어간은 쓰지 않는다 (잘못 만든 활용형이 사전에 들어가지 않게).
  3) 동사·형용사: X+는이 X+고의 5% 이상이면 동사 (형용사는 '좋는'이 없다).
  4) 점수(100만 어절당) = 어간 빈도(근거 꼴들의 빈도 합) × 어미 몫 × --scale. 말뭉치 빈도가 더 크면 그것을 쓴다
     (build_ko_model.py가 max를 고른다).

평가 (2026-10, Chatbot_data 질문·NSMC dev): 만든 꼴을 --extra로 넣으면 키 절약률이 같은 크기의 말뭉치 단어를 더 넣을 때보다
낫지 않았다 (사전에 없는 어절의 7%쯤만 맞고, 말뭉치 근거 없는 점수가 실제 단어를 밀어낸다). 그래서 기본 사전에는 넣지 않고,
어간 찾기(find_stems)만 ko_admit.py가 쓴다. 말투가 다른 말뭉치로 사전을 만들 때 다시 재 볼 수 있게 남겨 둔다.

사용법:
  python3 dict/build/gen_ko_forms.py counts.pkl OUT.txt [--scale 1.0] [--min 1.0] [--max-stems 4000]
  python3 dict/build/gen_ko_forms.py --selftest
counts.pkl은 build_ko_model.py --cache가 만든 파일 (말뭉치마다 센 단어 빈도를 쓴다).
"""
import argparse
import os
import pickle
import sys
from collections import defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_ko_conjugations as g  # noqa: E402

# 어간 근거로 보는 꼴 (그대로 붙이는 것)
EVIDENCE = ['다', '지', '게', '기', '는', '은', '면', '으면', '지만']
# ㅎ 불규칙은 색깔·모양 형용사와 그렇다 꼴뿐이다 (좋·넣·낳·닿·놓·쌓은 규칙 활용: 넣어 O, 내 X)
H_IRREGULAR = set('렇떻랗얗맣갛')
# 아/어 꼴 뒤에 붙이지 않는 어미 (있어있어), 있다·없다에 붙이지 않는 어미 (있으러, 있는다)
NO_EXIST = {('eu', '러'), ('eu', '려고'), ('ndan', ''), ('ao', '있어'), ('ao', '버렸어'), ('ao', '놓고')}

# 대화체 어미: (종류, 어미, 몫, 대상). 대상 'v' 동사만, 'a' 형용사만, 'va' 둘 다.
# 몫은 어간 빈도에 곱하는 대략적인 비율 (메신저에서 흔한 정도). 종류는 gen_ko_conjugations.conjugate와 같고,
# 'hear'는 전해 들은 말(간대, 먹는대, 좋대)이다.
ENDINGS = [
    ('ao', '', 0.30, 'va'), ('ao', '요', 0.25, 'va'), ('ao', '서', 0.15, 'va'), ('ao', '도', 0.08, 'va'),
    ('ao', '야', 0.06, 'va'), ('ao', '야지', 0.05, 'va'), ('ao', '야겠다', 0.02, 'va'), ('ao', '야겠어', 0.02, 'va'),
    ('ao', '야돼', 0.02, 'va'), ('ao', '야해', 0.02, 'va'), ('ao', '야겠네', 0.01, 'va'), ('ao', '도돼', 0.01, 'va'),
    ('ao', '서요', 0.01, 'va'), ('ao', '있어', 0.02, 'v'), ('ao', '버렸어', 0.01, 'v'), ('ao', '놓고', 0.01, 'v'),
    ('ao', '줘', 0.04, 'v'), ('ao', '줘요', 0.02, 'v'), ('ao', '주세요', 0.03, 'v'), ('ao', '줄게', 0.02, 'v'),
    ('ao', '줄래', 0.01, 'v'), ('ao', '봐', 0.03, 'v'), ('ao', '봐요', 0.01, 'v'), ('ao', '볼까', 0.02, 'v'),
    ('ao', '볼게', 0.02, 'v'), ('ao', '봤어', 0.02, 'v'), ('ao', '보자', 0.01, 'v'), ('ao', '보고', 0.01, 'v'),
    ('past', '어', 0.15, 'va'), ('past', '어요', 0.10, 'va'), ('past', '다', 0.10, 'va'), ('past', '는데', 0.06, 'va'),
    ('past', '고', 0.04, 'va'), ('past', '으면', 0.04, 'va'), ('past', '어서', 0.03, 'va'), ('past', '지', 0.03, 'va'),
    ('past', '네', 0.03, 'va'), ('past', '지만', 0.03, 'va'), ('past', '던', 0.02, 'va'), ('past', '나', 0.02, 'va'),
    ('past', '잖아', 0.02, 'va'), ('past', '을까', 0.01, 'va'), ('past', '나봐', 0.01, 'va'), ('past', '거든', 0.01, 'va'),
    ('past', '더니', 0.01, 'va'), ('past', '대', 0.01, 'va'), ('past', '네요', 0.01, 'va'), ('past', '습니다', 0.02, 'va'),
    ('jong', 'ㄹ게', 0.06, 'v'), ('jong', 'ㄹ게요', 0.03, 'v'), ('jong', 'ㄹ까', 0.05, 'va'), ('jong', 'ㄹ까요', 0.02, 'va'),
    ('jong', 'ㄹ래', 0.03, 'v'), ('jong', 'ㄹ래요', 0.01, 'v'), ('jong', 'ㄹ거야', 0.03, 'va'), ('jong', 'ㄹ거예요', 0.01, 'va'),
    ('jong', 'ㄹ걸', 0.01, 'va'), ('jong', 'ㄹ듯', 0.01, 'va'), ('jong', 'ㄹ텐데', 0.01, 'va'), ('jong', 'ㄹ', 0.03, 'va'),
    ('jong', 'ㄴ', 0.04, 'va'), ('jong', 'ㄴ데', 0.05, 'a'), ('jong', 'ㄴ가', 0.01, 'a'), ('jong', 'ㄴ지', 0.01, 'a'),
    ('jong', 'ㄴ거', 0.01, 'a'), ('jong', 'ㅂ니다', 0.03, 'va'), ('jong', 'ㅂ니까', 0.01, 'va'),
    ('plain', '고', 0.10, 'va'), ('plain', '지', 0.05, 'va'), ('plain', '네', 0.04, 'va'), ('plain', '네요', 0.02, 'va'),
    ('plain', '잖아', 0.03, 'va'), ('plain', '겠다', 0.03, 'va'), ('plain', '겠어', 0.04, 'va'), ('plain', '겠지', 0.03, 'va'),
    ('plain', '겠네', 0.02, 'va'), ('plain', '겠어요', 0.01, 'va'), ('plain', '더라', 0.02, 'va'), ('plain', '더라고', 0.01, 'va'),
    ('plain', '던데', 0.02, 'va'), ('plain', '거든', 0.02, 'va'), ('plain', '냐', 0.02, 'va'), ('plain', '니', 0.02, 'va'),
    ('plain', '지만', 0.03, 'va'), ('plain', '기', 0.02, 'va'), ('plain', '게', 0.04, 'va'), ('plain', '죠', 0.02, 'va'),
    ('plain', '자', 0.03, 'v'), ('plain', '지마', 0.01, 'v'), ('plain', '는', 0.08, 'v'), ('plain', '는데', 0.06, 'v'),
    ('plain', '는거', 0.01, 'v'), ('plain', '는게', 0.01, 'v'), ('plain', '기로', 0.01, 'v'), ('plain', '다', 0.03, 'a'),
    ('se', '세요', 0.03, 'va'),
    ('eu', '면', 0.05, 'va'), ('eu', '니까', 0.03, 'va'), ('eu', '려고', 0.02, 'v'), ('eu', '러', 0.02, 'v'),
    ('ndan', '', 0.02, 'v'), ('hear', '', 0.01, 'va'),
]
# ㄹ 받침 어간에서 ㄹ이 떨어지는 그대로 붙이는 어미 (ㄴ으로 시작: 노네, 노니, 노냐, 노는)
L_DROP = ('네', '는', '니', '냐')


def form(stem, flag, kind, tail, pos):
    """어간 + 어미. 만들 수 없으면 None."""
    _, _, l_stem = g.classify(stem, flag)
    if kind == 'plain':
        if l_stem and tail.startswith(L_DROP):
            return g.drop_final(stem) + tail
        return stem + tail
    if kind == 'eu' and tail == '러' and l_stem:
        return stem + tail   # 놀러, 만들러
    if kind == 'hear':
        if pos == 'v':
            return g.conjugate(stem, flag, 'ndan', '', pos)[:-1] + '대'   # 간대, 먹는대
        return stem + '대'   # 좋대
    if kind == 'jong' and len(tail) > 1 and tail[0] == 'ㄹ' and tail[1:] in ('거야', '거예요', '걸', '듯', '텐데'):
        return g.conjugate(stem, flag, 'jong', 'ㄹ', pos) + tail[1:]   # 할거야, 먹을걸
    if kind == 'jong' and tail[0] == 'ㄴ' and tail[1:] in ('가', '지', '거'):
        return g.conjugate(stem, flag, 'jong', 'ㄴ', pos) + tail[1:]   # 좋은가, 큰지, 예쁜거
    return g.conjugate(stem, flag, kind, tail, pos)


def safe_form(stem, flag, kind, tail, pos):
    try:
        w = form(stem, flag, kind, tail, pos)
    except (AssertionError, KeyError, ValueError, IndexError):
        return None
    return w if w and all(g.is_hangul(c) for c in w) else None


def check_forms(stem, flag):
    """이 표시로 만든 꼴 중 말뭉치로 확인할 것: 아/어서, 아/어도, 과거 다·고·는데·어."""
    out = []
    for kind, tail in (('ao', '서'), ('ao', '도'), ('ao', '요'), ('past', '다'), ('past', '고'), ('past', '는데'), ('past', '어')):
        w = safe_form(stem, flag, kind, tail, 'v')
        if w:
            out.append(w)
    return out


def flag_candidates(stem):
    jong = g.final_of(stem)
    out = [None]
    if jong == 'ㅂ':
        out.append('b')
    if jong == 'ㄷ':
        out.append('d')
    if jong == 'ㅅ':
        out.append('s')
    if jong == 'ㅎ' and stem[-1] in H_IRREGULAR:
        out.append('h')
    if stem[-1] == '르' and len(stem) >= 2:
        out.append('r')
    return out


def find_stems(freq, max_stems, min_mass):
    """[(어간, 표시, 품사, 어간 빈도)]를 어간 빈도 내림차순으로."""
    stems = []
    for w, f_go in freq.items():
        if len(w) < 2 or w[-1] != '고' or len(w) > 7:
            continue
        stem = w[:-1]
        if not all(g.is_hangul(c) for c in stem):
            continue
        # 과거·추측(했고, 밝혔고, 하겠고)과 하다의 아/어 꼴(해)은 어간이 아니다
        last = stem[-1]
        if g.final_of(stem) == 'ㅆ' and last not in '있밌' or last in '겠해돼다' or stem == '라' or stem.endswith('이라'):
            continue   # (없다고, 것이라고처럼 말을 옮기는 꼴도 뺀다. 있·재밌은 어간이다)
        if last == '라' and sum(freq.get(stem[:-1] + p, 0) for p in ('의', '를', '에', '가')) > f_go:
            continue   # 명사 + 라고 (결과라고)
        # 명사 (엄마고, 전자고): 격조사가 붙은 꼴이 X+고보다 흔하면 뺀다 (을·이는 용언에도 붙어 보지 않는다: 있을, 많이)
        case = sum(freq.get(stem + p, 0) for p in ('의', '를', '에', '가'))
        if case > 0.5 * f_go:
            continue
        ev = {stem + e for e in EVIDENCE if freq.get(stem + e, 0) > 0}
        if g.final_of(stem) == 'ㄹ':   # ㄹ 받침 어간: 노는, 논
            ev |= {x for x in (g.drop_final(stem) + '는',) if freq.get(x, 0) > 0}
        if len(ev) < 3:
            continue
        # 한 꼴이 유난히 흔하면(그리고) 어간 빈도를 부풀리지 않게 근거 꼴마다 중앙값의 세 배까지만 센다
        fs = sorted([f_go] + [freq[x] for x in ev])
        cap = 3 * fs[len(fs) // 2]
        mass = sum(min(x, cap) for x in fs)
        if mass < min_mass:
            continue
        # 명사 + 이다 (학생이고): 앞부분에 격조사가 흔히 붙으면 뺀다
        if stem[-1] == '이' and len(stem) >= 2:
            noun = stem[:-1]
            case = sum(freq.get(noun + p, 0) for p in ('의', '에', '을', '를', '은', '는'))
            if case > 0.5 * f_go:
                continue
        best, best_ev, regular = None, 0.0, 0.0
        for fl in flag_candidates(stem):
            e = sum(freq.get(x, 0) for x in set(check_forms(stem, fl)))
            if fl is None:
                regular = e
            if e > best_ev:
                best, best_ev = fl, e
        if best_ev < max(0.3, 0.02 * mass):   # 아/어 꼴·과거형이 말뭉치에 충분히 있어야 활용을 믿는다
            continue
        if best is not None and regular >= 0.1 * best_ev:   # 규칙 활용도 흔하면 규칙으로 (집어 / 지워)
            best, best_ev = None, regular
        pos = 'v' if freq.get(safe_form(stem, best, 'plain', '는', 'v') or '', 0) >= 0.05 * f_go else 'a'   # 노는
        stems.append((stem, best, pos, mass + best_ev))
    stems.sort(key=lambda x: -x[3])
    return stems[:max_stems]


def generate(freq, scale, min_score, max_stems, min_mass):
    out = {}
    stems = find_stems(freq, max_stems, min_mass)
    for stem, flag, pos, mass in stems:
        for kind, tail, share, target in ENDINGS:
            if target != 'va' and target != pos:
                continue
            if stem in ('있', '없', '계시') and (kind, tail) in NO_EXIST or (kind, tail) == ('ao', '있어') and stem == '있':
                continue
            w = safe_form(stem, flag, kind, tail, pos)
            if not w or len(w) > 12:
                continue
            sc = mass * share * scale
            if sc >= min_score:
                out[w] = max(out.get(w, 0.0), sc)
    return out, stems


def selftest():
    freq = defaultdict(float)
    for w in ('먹고 먹다 먹지 먹는 먹은 먹어서 먹었다 먹었고 춥고 춥다 춥지 춥게 추워서 추웠다 '
              '듣고 듣다 듣지 듣는 들어서 들었다 놀고 놀다 놀지 노는 놀아서 놀았다 학생이고 학생이다 학생이지 학생의 학생을').split():
        freq[w] = 10.0
    out, stems = generate(freq, 1.0, 0.0, 100, 0)
    got = {s: (f, p) for s, f, p, _ in stems}
    assert got['먹'] == (None, 'v'), got
    assert got['춥'] == ('b', 'a'), got
    assert got['듣'] == ('d', 'v'), got
    assert '학생이' not in got, got
    for w in ('먹어야지', '먹었어', '먹을게', '먹을거야', '먹는대', '추워', '추웠어', '추운데', '춥대', '들어봐', '들을까',
              '놀러', '노는데', '노네', '놀아야지', '먹으러', '먹지마'):
        assert w in out, w
    for w in ('먹는데요다', '춥어', '듣어', '놀는', '추울게'):
        assert w not in out, w
    print('selftest ok')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('cache', nargs='?')
    ap.add_argument('out', nargs='?')
    ap.add_argument('--scale', type=float, default=1.0, help='어간 빈도 × 어미 몫에 곱하는 값')
    ap.add_argument('--min', type=float, default=1.0, help='이보다 낮은 점수(100만 어절당)의 꼴은 쓰지 않는다')
    ap.add_argument('--max-stems', type=int, default=4000)
    ap.add_argument('--min-mass', type=float, default=5.0, help='어간 빈도(근거 꼴들의 100만 어절당 빈도 합)의 최소')
    ap.add_argument('--selftest', action='store_true')
    a = ap.parse_args()
    if a.selftest:
        selftest()
        return
    with open(a.cache, 'rb') as f:
        freq = pickle.load(f)[0]
    out, stems = generate(freq, a.scale, a.min, a.max_stems, a.min_mass)
    by_score = defaultdict(list)
    for w, sc in out.items():
        by_score[max(1, int(round(sc)))].append(w)
    with open(a.out, 'w', encoding='utf-8') as f:
        f.write('# gen_ko_forms.py가 말뭉치의 어간으로 만든 대화체 활용형 (직접 고치지 않는다)\n')
        for sc in sorted(by_score, reverse=True):
            f.write(f'{sc}: ' + ' '.join(sorted(by_score[sc])) + '\n')
    flags = defaultdict(int)
    for _, fl, pos, _ in stems:
        flags[(fl, pos)] += 1
    print(f'stems {len(stems):,} {dict(flags)}, forms {len(out):,} → {a.out}')


if __name__ == '__main__':
    main()
