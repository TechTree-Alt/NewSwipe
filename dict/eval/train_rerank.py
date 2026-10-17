#!/usr/bin/env python3
"""한국어 추천 재정렬 가중치를 학습한다 (KoreanSuggest.RERANK).

SuggestEval을 DUMP=폴더로 돌리면 단어마다(W 줄) 다음 단어 예측과, 고르기와 상관없이 그 단어의 모든 접두에서 다시 정렬하기
전의 상위 후보(최대 RERANK_TOP개)의 특징(KoreanSuggest.features), 정답 위치, 그때 맞히면 아끼는 키 수를 <말뭉치>.dump에 쓴다.
이 스크립트는 그 자료에서 가중치를 바꿨을 때의 키 절약률을 그대로 계산한다: 단어마다 정답이 세 칸 안에 처음 드는 접두
(또는 예측)에서 아끼는 키를 더한다. 입력 상태(0 = 다음 단어 예측, 1/2/3 = 한/두/세 글자 이상)마다 선형 점수 w·f를
쓰고, '지금의 순위 점수만 쓰는 것'(f[0]의 가중치 1, 지금과 같은 순위)에서 시작해 좌표마다 키 절약률이 오르는 쪽으로 옮긴다.

  python3 dict/eval/train_rerank.py OUT.txt --dev DEV_DUMP... [--test TEST_DUMP...]
  python3 dict/eval/train_rerank.py --eval W.txt DUMP...       # 가중치의 키 절약률 (자료에서 계산)

OUT.txt는 상태마다 한 줄에 가중치 NF개 (SuggestEval --rerank OUT.txt로 실제로 재고, 앱의 KoreanSuggest.RERANK에 옮겨 적는다).
학습 자료(dev)와 확인 자료(test)는 나눠 쓴다. 자료에서 계산한 값은 학습의 효과(내 기록이 바뀌는 것)를 빼고 재므로, 마지막
확인은 SuggestEval로 한다.
"""
import os
import sys
import numpy as np

K = 12
PREDICT_MIN = 0.08


class Data:
    def __init__(self, paths):
        cache = None
        if len(paths) == 1 and paths[0].endswith('.npz'):
            cache = paths[0]
        if cache:
            z = np.load(cache)
            for k in z.files:
                setattr(self, k, z[k])
            return
        xs, ats, keys, states, wids, ns = [], [], [], [], [], []
        words = []   # 단어마다 키 수
        for p in paths:
            with open(p) as f:
                it = iter(f.read().split('\n'))
            for l in it:
                if l.startswith('W '):
                    words.append(int(l[2:]))
                    continue
                if not l.startswith('G '):
                    continue
                _, st, w, at, n = l.split()
                st, w, at, n = int(st), int(w), int(at), int(n)
                if at < 0:
                    continue   # 상위 후보에 정답이 없으면 어떻게 정렬해도 맞히지 못한다
                rows = np.array([next(it).split() for _ in range(n)], dtype=np.float32)
                x = np.zeros((K, rows.shape[1]), np.float32)
                x[:n] = rows
                xs.append(x)
                ats.append(at)
                keys.append(w)
                states.append(st)
                wids.append(len(words) - 1)
                ns.append(n)
        self.x = np.stack(xs)
        self.at = np.array(ats)
        self.keys = np.array(keys, np.float64)
        self.state = np.array(states)
        self.wid = np.array(wids)
        self.n = np.array(ns)
        self.words = np.array(words, np.float64)
        self.mask = np.arange(K)[None, :] < self.n[:, None]
        # 예측은 다시 정렬하기 전의 가장 높은 점수(첫 줄의 f[0])가 문턱을 넘을 때만 보인다
        self.shown = (self.state != 0) | (np.exp(self.x[:, 0, 0]) >= PREDICT_MIN)

    def save(self, path):
        np.savez(path, x=self.x, at=self.at, keys=self.keys, state=self.state, wid=self.wid, n=self.n,
                 words=self.words, mask=self.mask, shown=self.shown)


def group_values(d, w, sel=None):
    """묶음마다 '세 칸 안에 들면 아끼는 키' (sel: 다시 셀 묶음)."""
    idx = np.arange(len(d.at)) if sel is None else sel
    x = d.x[idx]
    wt = w[d.state[idx]]
    s = np.einsum('gkf,gf->gk', x, wt)
    s = s - 1e-9 * np.arange(K)[None, :]   # 같으면 원래 순서
    s[~d.mask[idx]] = -np.inf
    st = s[np.arange(len(idx)), d.at[idx]]
    rank = (s > st[:, None]).sum(1)
    return np.where((rank < 3) & d.shown[idx], d.keys[idx], 0.0)


def ksr(d, vals):
    saved = np.zeros(len(d.words))
    np.maximum.at(saved, d.wid, vals)
    return 100 * saved.sum() / (d.words + 1).sum()


def base_weights(nf):
    w = np.zeros((4, nf))
    w[:, 0] = 1
    return w


def train(d, test=None, sweeps=4):
    nf = d.x.shape[2]
    w = base_weights(nf)
    vals = group_values(d, w)
    best = ksr(d, vals)
    print(f'시작 (지금 순위): dev {best:.3f}%' + (f', test {ksr(test, group_values(test, w)):.3f}%' if test else ''))
    sd = d.x[d.mask].std(0) + 1e-6
    steps = [-2, -1, -0.5, -0.25, 0.25, 0.5, 1, 2]
    for sweep in range(sweeps):
        improved = False
        scale = 0.5 ** sweep
        for s in range(4):
            sel = np.where(d.state == s)[0]
            if len(sel) == 0:
                continue
            for j in range(nf):
                step = scale * 0.5 * abs(w[s, 0]) / sd[j]
                cand = None
                for m in steps:
                    w2 = w.copy()
                    w2[s, j] += m * step
                    v2 = vals.copy()
                    v2[sel] = group_values(d, w2, sel)
                    k2 = ksr(d, v2)
                    if k2 > best + 1e-4:
                        best, cand = k2, (w2, v2)
                if cand:
                    w, vals = cand
                    improved = True
            print(f'  {sweep + 1}회 상태 {s}까지: dev {best:.3f}%', flush=True)
        if test:
            print(f'{sweep + 1}회 끝: dev {best:.3f}%, test {ksr(test, group_values(test, w)):.3f}%', flush=True)
        if not improved:
            break
    return w


def load(paths):
    d = Data(paths)
    return d


def main():
    a = sys.argv[1:]
    if a[0] == '--eval':
        w = np.loadtxt(a[1])
        d = load(a[2:])
        print(f'{ksr(d, group_values(d, w)):.3f}% (지금 순위 {ksr(d, group_values(d, base_weights(w.shape[1]))):.3f}%)')
        return
    if a[0] == '--cache':   # 자료를 numpy로 바꿔 둔다
        load(a[2:]).save(a[1])
        return
    out = a[0]
    i = a.index('--dev')
    j = a.index('--test') if '--test' in a else len(a)
    d = load(a[i + 1:j])
    t = load(a[j + 1:]) if j < len(a) else None
    w = train(d, t)
    np.savetxt(out, w, fmt='%.6g')


if __name__ == '__main__':
    main()
