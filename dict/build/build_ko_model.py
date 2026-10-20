#!/usr/bin/env python3
"""한국어 단어 추천용 사전(dict_ko.bin)과 다음 단어 사전(dict_ko_next.bin)을 문장 말뭉치에서 함께 만든다.

입력: Leipzig Corpora Collection (CC BY 4.0)의 한국어 문장 파일 (*-sentences.txt: 순번 \t 문장)
출력 (OUT_DIR):
  dict_ko.bin       단어 사전 (build_ko_dict.py와 같은 NSD1 형식)
  dict_ko_next.bin  다음 단어 사전 (NSN1 형식, 아래)
  ko_rank.tsv       검토용 단어 빈도 순위
  ko_next.tsv       검토용 단어 쌍 목록 (앞 단어, 다음 단어, P(다음|앞), 고른 기준값)
  ko_test.txt       평가용으로 떼어 둔 문장 (--test-from과 이름이 맞는 말뭉치에서만)

사용법:
  python3 dict/build/build_ko_model.py OUT_DIR [--cache counts.pkl] [--mix 다른것.pkl:0.5 --mix-exclude 목록.txt] \
      --top 단어수 --pairs 단어쌍수 --extra dict/lists/ko_colloquial.txt --extra dict/lists/ko_conjugated.txt \
      --phrases dict/lists/ko_phrases.txt kor_news_2022_1M-sentences.txt kor-kr_web_2020_1M-sentences.txt kor-kr_web_2015_1M-sentences.txt
  말뭉치는 https://downloads.wortschatz-leipzig.de/corpora/ 의 같은 이름 tar.gz에 들어 있다. 세는 데 5분쯤 걸리고,
  --cache가 있으면 사전 크기·단어 쌍 수를 바꿔 다시 만들 때 1~2분. 앱의 기본 사전(5만 단어, 단어 쌍 없음)과
  표준 사전 팩(8만 단어 + 40만 쌍)을 만든 정확한 명령은 BUILDING.md의 '한국어 사전과 사전 팩'에 있다.
  dict/eval/run_eval.sh로 성능을 확인한다. 평가 문장(ko_test.txt)은 웹 말뭉치에서 떼어 둔 문장이다.

처리 순서:
  1) 문장마다 순번을 HOLDOUT(기본 50)으로 나눠 떨어지면 평가용으로 떼어 내고 세지 않는다.
  2) 띄어쓰기로 어절을 나누고 앞뒤의 문장 부호·괄호를 뗀다. 한글 음절만 남는 어절만 단어로 센다.
     문장 부호가 붙어 있던 자리, 숫자·영문이 섞인 어절에서는 앞뒤 단어 관계를 끊는다.
  3) 단어 사전: 말뭉치마다 '100만 어절당 빈도'의 평균 (build_ko_dict.py와 같은 방식, 보완 목록도 같다).
  4) 단어 쌍: 사전에 든 두 단어가 이어 나온 횟수를 같은 기준(100만 어절당)으로 더한다.
     구어체 문장 목록(--phrases)의 단어 쌍은 PHRASE_WEIGHT만큼 더한다 (말뭉치에 대화체가 적어서).
  5) 단어 쌍 고르기: 앞 단어 h, 다음 단어 w마다
       P_bi = c(h,w) / c(h),  λ(h) = n(h) / (n(h) + t(h))   (Witten-Bell: n = 앞 단어 횟수, t = 서로 다른 다음 단어 수)
       기준값 = c(h,w) × ln((λ P_bi + (1-λ) P_uni(w)) / P_uni(w))
     즉 단어 쌍을 넣었을 때 말뭉치를 더 잘 맞히게 되는 정도(Stolcke 가지치기와 같은 생각)가 큰 순으로 PAIRS개.
     자주 나오기만 하고 다음 단어 예측에 도움이 안 되는 쌍(아무 단어 뒤에나 오는 '수', '것' 등)은 덜 고른다.

NSN1 형식 (little endian):
  magic "NSN1" | u32 단어 사전의 단어 수(맞지 않으면 앱이 쓰지 않는다)
  | f32 ln(top+1) | f32 ln(bottom+1)  (단어 사전 점수를 빈도로 되돌리는 기준, build_ko_dict.score_scale)
  | u32 앞 단어 수 | u32 단어 쌍 수
  앞 단어마다 (번호 순): varint 앞 단어 번호 차이 | u8 λ×255 | varint 다음 단어 수
      다음 단어마다 (번호 순): varint 다음 단어 번호 차이 (첫 것은 번호 그대로)
  그 뒤에 단어 쌍마다 (같은 순서로) u8 round(-ln P_bi × 4) (255까지)
  단어 번호 = 사전(가나다순)에서의 위치.
"""
import argparse
import math
import os
import pickle
import struct
import sys
import zlib
from collections import Counter, defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_ko_dict as kd  # noqa: E402

PHRASE_WEIGHT = 2.0   # 구어체 문장 목록의 한 쌍 = 100만 어절당 2회 (말뭉치에서 100번쯤 본 것과 비슷)
MIN_PAIR_RAW = 3      # 말뭉치에서 이보다 적게 본 쌍은 우연일 수 있어 고르지 않는다 (문장 목록의 쌍은 예외)
Q_SCALE = 4.0   # 확률은 0.25 nat 단위로 (순위를 매기기에 충분하고 압축이 잘 된다)
SUPER_TOP = 90000   # 단어 쌍을 셀 때의 넓은 단어 목록 크기 (--top을 바꿔 다시 만들 때 다시 세지 않게)


def is_hangul(ch):
    return '가' <= ch <= '힣'


def tokenize(sentence):
    """[(어절 핵심 또는 None, 앞에서 끊김, 뒤에서 끊김)]. 핵심이 None이면 단어가 아니다(관계도 끊는다)."""
    out = []
    for raw in sentence.split():
        i, j = 0, len(raw)
        while i < j and not is_hangul(raw[i]):
            i += 1
        while j > i and not is_hangul(raw[j - 1]):
            j -= 1
        core = raw[i:j]
        if not core or not all(is_hangul(c) for c in core):
            out.append((None, True, True))
            continue
        out.append((core, i > 0, j < len(raw)))
    return out


def read_sentences(path, holdout, keep_test):
    """(학습 문장 목록 생성기, 평가 문장 목록)."""
    test = []
    def gen():
        with open(path, encoding='utf-8') as f:
            for line in f:
                p = line.rstrip('\n').split('\t', 1)
                if len(p) != 2:
                    continue
                try:
                    sid = int(p[0])
                except ValueError:
                    continue
                if holdout and sid % holdout == 0:
                    if keep_test:
                        test.append(p[1])
                    continue
                yield p[1]
    return gen, test


def read_phrases(path):
    out = []
    with open(path, encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith('#'):
                out.append(line)
    return out


def varint(n):
    b = bytearray()
    while True:
        x = n & 0x7F
        n >>= 7
        if n:
            b.append(x | 0x80)
        else:
            b.append(x)
            return bytes(b)


def load_cache(path):
    if not os.path.exists(path):
        return None
    with open(path, 'rb') as f:
        return pickle.load(f)


def read_fragments(path):
    if not path:
        return []
    out = []
    with open(path, encoding='utf-8') as f:
        for line in f:
            if not line.startswith('#'):
                out += line.split()
    return out


def mix_caches(base, other, weight, exclude=(), known_only=False, admit=False, admit_stems=None):
    """두 말뭉치의 센 결과를 (1-weight):weight로 섞는다. 단어 목록은 둘을 합친 것이 된다 (횟수 자체는 더한다).
    exclude의 조각이 들어간 단어는 other에서 빼고 섞는다 (그 단어가 든 단어 쌍도).
    known_only면 base의 넓은 단어 목록에 없는 단어도 other에서 뺀다 (other는 이미 있는 단어의 빈도만 바꾼다).
    admit이면 base에 없는 단어 중 base가 아는 조각으로 풀리는 말만 들인다 (ko_admit.py, admit_stems = 어간 목록 파일)."""
    pm_a, voc_a, pw_a, pr_a, cw_a, cr_a, tests = base
    pm_b, voc_b, pw_b, pr_b, cw_b, cr_b, _ = other
    if exclude or known_only or admit:
        known = set(voc_a) if known_only or admit else None
        if admit:
            import ko_admit
            ok = ko_admit.Admit(pm_a, kd.ONE_CHAR_OK, admit_stems)
            new = {w for w in pm_b if w not in known and ok(w)}
            print(f'mix: admitted {len(new):,} new words from the mixed corpus')
            known |= new
        bad = {i for i, w in enumerate(voc_b)
               if any(x in w for x in exclude) or (known is not None and w not in known)}
        sn = len(voc_b)
        pm_b = {w: f for w, f in pm_b.items()
                if not any(x in w for x in exclude) and (known is None or w in known)}
        pw_b = {c: v for c, v in pw_b.items() if c // sn not in bad and c % sn not in bad}
        cw_b = [0.0 if i in bad else v for i, v in enumerate(cw_b)]
        print(f'mix: {len(bad):,} words excluded from the mixed corpus')
    pm = defaultdict(float)
    for w, f in pm_a.items():
        pm[w] += (1 - weight) * f
    for w, f in pm_b.items():
        pm[w] += weight * f
    vocab = sorted(set(voc_a) | set(voc_b))
    wid = {w: i for i, w in enumerate(vocab)}
    n = len(vocab)
    pw, pr = defaultdict(float), Counter()
    cw, cr = [0.0] * n, [0] * n
    for voc, pws, prs, cws, crs, k in ((voc_a, pw_a, pr_a, cw_a, cr_a, 1 - weight), (voc_b, pw_b, pr_b, cw_b, cr_b, weight)):
        sn = len(voc)
        remap = [wid[w] for w in voc]
        for code, c in pws.items():
            h, w = divmod(code, sn)
            nc = remap[h] * n + remap[w]
            pw[nc] += k * c
            pr[nc] += prs.get(code, 0)
        for i in range(sn):
            cw[remap[i]] += k * cws[i]
            cr[remap[i]] += crs[i]
    print(f'mixed: vocab {n:,}, pairs {len(pw):,} (weight {weight})')
    return dict(pm), vocab, dict(pw), dict(pr), cw, cr, tests


def add_forms(kept, per_million, forms, n, cap):
    """말뭉치 단어(kept) 뒤에 사전에 없는 활용형을 어림한 빈도가 높은 순으로 n개 더한다. 빈도는 어림한 빈도와 말뭉치 빈도 중
    큰 것이고, cap이 있으면 말뭉치 단어 중 가장 드문 것의 cap배를 넘지 않게 누른다 (만든 꼴이 말뭉치 단어보다 앞서지 않게)."""
    have = {w for w, _ in kept}
    bottom = kept[-1][1]
    cand = []
    for w, est in forms.items():
        if w in have or not 2 <= len(w) <= 12 or any(b in w for b in kd.BLOCKLIST):
            continue
        cand.append((max(est, per_million.get(w, 0.0)), w))
    cand.sort(key=lambda x: (-x[0], x[1]))
    added = [(w, min(f, bottom * cap) if cap else f) for f, w in cand[:n]]
    print(f'forms: {len(cand):,} candidates, added {len(added):,} '
          f'(est freq {cand[0][0] if cand else 0:.2f} .. {cand[min(n, len(cand)) - 1][0] if cand else 0:.4f})')
    out = kept + added
    out.sort(key=lambda x: (-x[1], x[0]))
    return out


def count(a, extra, phrases):
    """말뭉치를 세어 (단어 빈도, 넓은 단어 목록, 단어 쌍 횟수, 앞 단어 횟수, 평가 문장)을 돌려준다.
    단어 쌍은 사전 크기를 바꿔 보며 다시 쓸 수 있게 SUPER_TOP개의 넓은 목록으로 센다."""
    per_million = defaultdict(float)
    corpora = []
    tests = []
    for path in a.sentences:
        gen, test = read_sentences(path, a.holdout, a.test_from in os.path.basename(path))
        counts = Counter()
        total = 0
        for s in gen():
            for core, _, _ in tokenize(s):
                total += 1
                if core:
                    counts[core] += 1
        tests += test
        corpora.append((path, gen, total))
        print(f'{os.path.basename(path)}: {len(counts):,} types, {total:,} tokens, test {len(test):,}')
        for w, c in counts.items():
            per_million[w] += c * 1_000_000 / total / len(a.sentences)
        del counts
    pm = defaultdict(float, per_million)
    for w, sc in extra.items():
        pm[w] = max(pm.get(w, 0.0), sc)
    for ph in phrases:
        for core, _, _ in tokenize(ph):
            if core:
                pm.setdefault(core, 50.0)
    super_vocab = sorted(w for w, _ in kd.select(pm, extra, max(SUPER_TOP, a.top)))
    del pm
    wid = {w: i for i, w in enumerate(super_vocab)}
    n = len(super_vocab)
    pair_w = defaultdict(float)   # 코드(h*n+w) → 100만 어절당 횟수
    pair_raw = Counter()
    ctx_w = [0.0] * n             # 앞 단어 h 뒤에 (아무 어절이든) 이어 나온 횟수
    ctx_raw = [0] * n
    for path, gen, total in corpora:
        unit = 1_000_000 / total / len(a.sentences)
        for s in gen():
            toks = tokenize(s)
            for k in range(len(toks) - 1):
                h, _, hb = toks[k]
                if h is None or hb:
                    continue
                hi = wid.get(h)
                if hi is None:
                    continue
                w, wb, _ = toks[k + 1]
                if wb:
                    continue
                ctx_w[hi] += unit
                ctx_raw[hi] += 1
                wi = wid.get(w) if w else None
                if wi is None:
                    continue
                code = hi * n + wi
                pair_w[code] += unit
                pair_raw[code] += 1
        print(f'pairs after {os.path.basename(path)}: {len(pair_w):,}')
    return dict(per_million), super_vocab, dict(pair_w), dict(pair_raw), ctx_w, ctx_raw, tests


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('out_dir')
    ap.add_argument('sentences', nargs='+')
    ap.add_argument('--top', type=int, default=40000)
    ap.add_argument('--pairs', type=int, default=100000)
    ap.add_argument('--min-pair', type=int, default=MIN_PAIR_RAW, help='말뭉치에서 이보다 적게 본 단어 쌍은 고르지 않는다')
    ap.add_argument('--extra', action='append', default=[])
    ap.add_argument('--phrases')
    ap.add_argument('--holdout', type=int, default=50)
    ap.add_argument('--test-from', default='web')
    ap.add_argument('--cache', help='센 결과를 이 파일에 저장하고, 있으면 다시 쓴다')
    ap.add_argument('--mix', action='append', default=[], metavar='CACHE:WEIGHT',
                    help='다른 말뭉치를 센 결과(--cache로 만든 파일)를 이 비중으로 섞는다 (예: nsmc.pkl:0.3). '
                         '단어 빈도와 단어 쌍 빈도가 (1-비중)×기본 + 비중×이것이 된다')
    ap.add_argument('--mix-exclude', help='섞는 말뭉치에서 이 목록의 조각이 들어간 단어는 빼고 섞는다 (주제어를 빼고 말투만 가져온다)')
    ap.add_argument('--mix-known-only', action='store_true',
                    help='섞는 말뭉치는 기본 말뭉치에도 있는 단어의 빈도·단어 쌍만 바꾸고 새 단어를 들이지 않는다 '
                         '(리뷰의 붙여 쓴 어절·고유명사·리뷰 용어가 사전에 들어오지 않게)')
    ap.add_argument('--mix-admit', action='store_true',
                    help='--mix-known-only처럼 섞되, 기본 말뭉치가 아는 조각으로 풀리는 새 단어(대화체 활용형, 띄어 쓰지 않은 '
                         '두 어절)는 들인다 (ko_admit.py: 고유명사·맞춤법이 틀린 말은 들이지 않는다)')
    ap.add_argument('--forms', help='말뭉치 단어 --top개 뒤에 더 넣을 활용형 목록 (gen_ko_pack_forms.py의 출력, --extra 형식). '
                         '말뭉치 단어를 밀어내지 않는다')
    ap.add_argument('--forms-max', type=int, default=0, help='--forms에서 어림한 빈도가 높은 순으로 이만큼 넣는다')
    ap.add_argument('--forms-cap', type=float, default=0,
                    help='넣는 활용형의 빈도를 말뭉치 단어 중 가장 드문 것의 이 배수로 누른다 (0이면 누르지 않는다)')
    ap.add_argument('--admit-stems', help='--mix-admit에서 어간 + 어미로 풀리는 말을 가릴 때 더 쓸 어간 목록 '
                         '(dict/lists/ko_stems_kiwi.txt: 형태소 분석기로 센 어간)')
    a = ap.parse_args()
    os.makedirs(a.out_dir, exist_ok=True)

    extra = {}
    for path in a.extra:
        for w, sc in kd.read_extra(path).items():
            extra[w] = max(extra.get(w, 0), sc)
    phrases = read_phrases(a.phrases) if a.phrases else []

    cache = load_cache(a.cache) if a.cache else None
    if cache is None:
        cache = count(a, extra, phrases)
        if a.cache:
            with open(a.cache, 'wb') as f:
                pickle.dump(cache, f, protocol=pickle.HIGHEST_PROTOCOL)
    for spec in a.mix:
        path, _, weight = spec.rpartition(':')
        cache = mix_caches(cache, load_cache(path), float(weight), read_fragments(a.mix_exclude), a.mix_known_only,
                           a.mix_admit, a.admit_stems)
    per_million_raw, super_vocab, pair_w, pair_raw, ctx_w, ctx_raw, tests = cache

    per_million = defaultdict(float, per_million_raw)
    phrase_words = set()
    for ph in phrases:
        for core, _, _ in tokenize(ph):
            if core:
                phrase_words.add(core)
    # 문장 목록의 단어가 사전에 빠지지 않게 보완 목록처럼 넣는다 (이미 더 흔하면 그대로).
    for w in phrase_words:
        extra.setdefault(w, 50.0)
    for w, sc in extra.items():
        per_million[w] = max(per_million.get(w, 0.0), sc)

    kept = kd.select(per_million, extra, a.top)
    if a.forms:
        kept = add_forms(kept, per_million, kd.read_extra(a.forms), a.forms_max, a.forms_cap)
    del per_million
    kd.write_nsd1(os.path.join(a.out_dir, 'dict_ko.bin'), kept)
    with open(os.path.join(a.out_dir, 'ko_rank.tsv'), 'w', encoding='utf-8') as f:
        for w, fr in kept:
            f.write(f'{w}\t{fr:.2f}\n')
    top, bottom = kd.score_scale(kept)
    vocab = sorted(w for w, _ in kept)
    wid = {w: i for i, w in enumerate(vocab)}
    # 앱이 보는 빈도: 양자화한 점수에서 되돌린 값 (앱과 똑같이 계산해야 기준값이 맞는다)
    def dequant(fr):
        s = 1 + int(round(254 * (math.log(fr + 1) - bottom) / (top - bottom)))
        return (math.exp(bottom + (s - 1) * (top - bottom) / 254) - 1) / 1e6
    p_uni = [0.0] * len(vocab)
    for w, fr in kept:
        p_uni[wid[w]] = dequant(fr)
    n = len(vocab)

    # 세어 둔 단어 쌍(넓은 단어 목록 기준 번호)을 이번 사전의 번호로 옮긴다.
    sn = len(super_vocab)
    remap = [wid.get(w, -1) for w in super_vocab]
    pw, pr = defaultdict(float), Counter()
    for code, c in pair_w.items():
        h, w = divmod(code, sn)
        h, w = remap[h], remap[w]
        if h >= 0 and w >= 0:
            pw[h * n + w] += c
            pr[h * n + w] += pair_raw.get(code, 0)
    cw, cr = [0.0] * n, [0] * n
    for i, w in enumerate(super_vocab):
        if remap[i] >= 0:
            cw[remap[i]] = ctx_w[i]
            cr[remap[i]] = ctx_raw[i]
    pair_w, pair_raw, ctx_w, ctx_raw = pw, pr, cw, cr

    phrase_pairs = set()
    for ph in phrases:
        toks = tokenize(ph)
        for k in range(len(toks) - 1):
            h, _, hb = toks[k]
            w, wb, _ = toks[k + 1]
            if not h or not w or hb or wb:
                continue
            hi, wi = wid.get(h), wid.get(w)
            if hi is None or wi is None:
                continue
            code = hi * n + wi
            pair_w[code] += PHRASE_WEIGHT
            ctx_w[hi] += PHRASE_WEIGHT
            phrase_pairs.add(code)

    distinct = Counter()
    for code, c in pair_raw.items():
        distinct[code // n] += 1
    lam = [0.0] * n
    for h in range(n):
        if ctx_raw[h] or ctx_w[h]:
            t = distinct[h] + 1
            r = max(ctx_raw[h], 1)
            lam[h] = r / (r + t)

    scored = []
    for code, c in pair_w.items():
        if pair_raw[code] < a.min_pair and code not in phrase_pairs:
            continue
        h, w = divmod(code, n)
        pb = c / ctx_w[h]
        pu = p_uni[w]
        l = lam[h]
        gain = c * math.log((l * pb + (1 - l) * pu) / pu)
        if gain > 0 and pb > pu:
            scored.append((gain, h, w, pb))
    scored.sort(reverse=True)
    chosen = scored[:a.pairs]
    print(f'candidate pairs {len(scored):,}, chosen {len(chosen):,}, '
          f'min gain {chosen[-1][0]:.4f}' if chosen else 'no pairs')

    groups = defaultdict(list)
    for gain, h, w, pb in chosen:
        groups[h].append((w, pb))
    blob = bytearray()   # 구조: 앞 단어와 다음 단어 번호
    probs = bytearray()  # 확률: 단어 쌍마다 1바이트 (구조와 따로 두어야 압축이 잘 된다)
    prev_h = 0
    for h in sorted(groups):
        items = sorted(groups[h])
        blob += varint(h - prev_h)
        blob.append(max(0, min(255, int(round(lam[h] * 255)))))
        blob += varint(len(items))
        prev_w = 0
        for w, pb in items:
            blob += varint(w - prev_w)
            probs.append(max(0, min(255, int(round(-math.log(pb) * Q_SCALE)))))
            prev_w = w
        prev_h = h
    blob += probs
    head = b'NSN1' + struct.pack('<IffII', n, top, bottom, len(groups), len(chosen))
    path = os.path.join(a.out_dir, 'dict_ko_next.bin')
    with open(path, 'wb') as f:
        f.write(head + bytes(blob))
    raw = open(path, 'rb').read()
    print(f'wrote {path}: {len(raw):,} bytes (deflate ≈ {len(zlib.compress(raw, 9)):,}), groups {len(groups):,}')
    dk = open(os.path.join(a.out_dir, 'dict_ko.bin'), 'rb').read()
    print(f'dict_ko.bin: {len(dk):,} bytes (deflate ≈ {len(zlib.compress(dk, 9)):,})')

    with open(os.path.join(a.out_dir, 'ko_next.tsv'), 'w', encoding='utf-8') as f:
        for gain, h, w, pb in chosen:
            f.write(f'{vocab[h]}\t{vocab[w]}\t{pb:.4f}\t{gain:.3f}\n')
    if tests:
        with open(os.path.join(a.out_dir, 'ko_test.txt'), 'w', encoding='utf-8') as f:
            for s in tests:
                f.write(s + '\n')


if __name__ == '__main__':
    main()
