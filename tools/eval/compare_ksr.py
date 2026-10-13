#!/usr/bin/env python3
"""두 설정의 키 절약률 차이와 95% 구간 (같은 문장끼리 짝지은 부트스트랩).

  python3 tools/eval/compare_ksr.py A.tsv B.tsv   # SuggestEval --per-sentence로 만든 파일 (B - A)
"""
import sys, random
def load(p):
    return [tuple(map(int, l.split('\t'))) for l in open(p) if l.strip()]
a, b = load(sys.argv[1]), load(sys.argv[2])
assert len(a) == len(b)
n = len(a)
def ksr(rows, idx):
    k = sum(rows[i][0] for i in idx); s = sum(rows[i][1] for i in idx)
    return 100.0 * s / k
idx = list(range(n))
d0 = ksr(b, idx) - ksr(a, idx)
random.seed(0)
ds = []
for _ in range(2000):
    r = [random.randrange(n) for _ in range(n)]
    ds.append(ksr(b, r) - ksr(a, r))
ds.sort()
print(f'{d0:+.2f}%p  (95% 구간 {ds[50]:+.2f} ~ {ds[1949]:+.2f})')
