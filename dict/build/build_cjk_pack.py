#!/usr/bin/env python3
"""일본어·중국어 변환 팩(zip)을 만든다. 앱의 설정 > 실험실 > 일본어/중국어 자판에서 '사전 팩 가져오기'로 가져온다.
앱에는 변환 엔진만 들어 있고 사전은 이 팩으로만 받는다 (쓰지 않는 사용자의 앱 크기를 늘리지 않는다).

중국어 (jieba 사전 + pypinyin 병음, 둘 다 MIT):
  pip download --no-deps jieba==0.42.1 pypinyin==0.55.0 -d src
  python3 dict/build/build_cjk_pack.py zh out/zh-standard.zip --jieba dict.txt --pypinyin PYPINYIN_DIR \\
      --words 100000 --license-file LICENSES.txt --version 2026.11
  (PYPINYIN_DIR은 pinyin_dict.json·phrases_dict.json이 든 pypinyin 패키지 폴더)

일본어 (Mozc 공개 사전 dictionary_oss, BSD-3 + IPAdic 라이선스):
  python3 dict/build/build_cjk_pack.py ja out/ja-standard.zip --mozc mozc/src/data/dictionary_oss \\
      --words 120000 --license-file LICENSES.txt --version 2026.11

팩 형식은 app/src/main/java/.../cjk/CjkPack.java, 사전 형식은 cjk/ConversionDictionary.java의 설명을 따른다.
"""
import argparse
import glob
import hashlib
import io
import json
import math
import os
import re
import struct
import sys
import unicodedata
import zipfile

FORMAT = 1
MAGIC = b'NSC1'


def write_dict(entries):
    """entries: {읽기: {글자: 비용(0~255)}} → 사전 바이트."""
    keys = sorted(entries, key=lambda k: k.encode('utf-8'))
    kb, vb = io.BytesIO(), io.BytesIO()
    ko, vo = [0], [0]
    for k in keys:
        kb.write(k.encode('utf-8'))
        for text, cost in sorted(entries[k].items(), key=lambda x: (x[1], x[0])):
            t = text.encode('utf-8')
            if not t or len(t) > 255:
                continue
            vb.write(bytes([max(0, min(255, int(cost))), len(t)]))
            vb.write(t)
        ko.append(kb.tell())
        vo.append(vb.tell())
    out = io.BytesIO()
    out.write(MAGIC)
    out.write(struct.pack('>III', len(keys), kb.tell(), vb.tell()))
    out.write(struct.pack('>%di' % len(ko), *ko))
    out.write(struct.pack('>%di' % len(vo), *vo))
    out.write(kb.getvalue())
    out.write(vb.getvalue())
    return out.getvalue()


def add(entries, key, text, cost):
    d = entries.setdefault(key, {})
    if cost < d.get(text, 1 << 30):
        d[text] = cost


# ---------------------------------------------------------------- 중국어

HAN = re.compile(r'^[\u4e00-\u9fff\u3400-\u4dbf]+$')


def toneless(s):
    s = unicodedata.normalize('NFD', s.strip())
    s = ''.join(c for c in s if not unicodedata.combining(c))
    return s.replace('ü', 'v').replace('u\u0308', 'v').lower()


def build_zh(a):
    pyc = {int(k): v for k, v in json.load(open(os.path.join(a.pypinyin, 'pinyin_dict.json'))).items()}
    phr = json.load(open(os.path.join(a.pypinyin, 'phrases_dict.json')))
    words = []
    for line in open(a.jieba, encoding='utf-8'):
        parts = line.split()
        if len(parts) >= 2 and HAN.match(parts[0]):
            words.append((parts[0], int(parts[1])))
    freq = {}
    for w, f in words:
        freq[w] = max(freq.get(w, 0), f)
    fmax = max(freq.values())

    def cost(f):   # 자주 쓸수록 작다. ln 차이 1 = 비용 12
        return min(255, round(-math.log(max(f, 1) / fmax) * 12))

    syllables = set()
    for v in pyc.values():
        for s in v.split(','):
            t = toneless(s)
            if re.fullmatch(r'[a-z]{1,6}', t) and t not in ('m', 'n', 'ng', 'hm', 'hng', 'r', 'e\u0302'):
                syllables.add(t)
    entries = {}
    # 글자: 말뭉치에 나온 글자 모두, 소리가 여럿이면 둘째 소리부터 조금 덜 앞세운다
    chars = [w for w in freq if len(w) == 1]
    for c in chars:
        readings = [toneless(s) for s in pyc.get(ord(c), '').split(',') if s]
        seen = []
        for r in readings:
            if r in syllables and r not in seen:
                seen.append(r)
        for i, r in enumerate(seen):
            add(entries, r, c, cost(freq[c]) + (15 if i else 0))
    n = 0
    for w, f in sorted(((w, f) for w, f in freq.items() if len(w) > 1), key=lambda x: -x[1]):
        if n >= a.words:
            break
        if w in phr:
            py = [toneless(p[0]) for p in phr[w]]
        else:
            py = []
            for c in w:
                v = pyc.get(ord(c))
                if not v:
                    py = None
                    break
                py.append(toneless(v.split(',')[0]))
        if not py or len(py) != len(w) or any(s not in syllables for s in py):
            continue
        add(entries, "'".join(py), w, cost(f))
        n += 1
    print(f'zh: syllables {len(syllables)}, chars {len(chars)}, words {n}, keys {len(entries)}')
    return entries, {
        'name': a.name or '중국어 표준 사전', 'license': 'MIT',
        'attribution': 'jieba (Sun Junyi, MIT) 단어·빈도, pypinyin (mozillazg, MIT) 병음',
        'description': f'간체자 글자 {len(chars):,}개와 단어 {n:,}개',
    }


# ---------------------------------------------------------------- 일본어

KANA = re.compile(r'^[\u3041-\u3096\u30fc]+$')


def kata2hira(s):
    return ''.join(chr(ord(c) - 0x60) if '\u30a1' <= c <= '\u30f6' else c for c in s)


# Mozc의 단어 비용은 품사 연결 비용과 함께 쓰도록 매겨져, 어간·접미사 같은 조각(出ん, 者, 古-)이 아주 싸다.
# 앱은 연결 비용 없이 단어 비용만 쓰므로 이런 조각은 비용을 올려 문장을 잘게 자르지 않게 한다.
FRAGMENT = ('接頭詞', '接尾', '非自立', '体言接続特殊', '仮定縮約', '文語', 'ガル接続', '未然ウ接続', '未然特殊')
FRAGMENT_COST = 3000
FUNCTION = ('助詞', '助動詞')


def load_pos(path):
    pos = {}
    if path and os.path.isfile(path):
        for line in open(path, encoding='utf-8'):
            p = line.rstrip('\n').split(' ', 1)
            if len(p) == 2:
                pos[int(p[0])] = p[1]
    return pos


def build_ja(a):
    pos = load_pos(os.path.join(a.mozc, 'id.def'))
    if not pos:
        print('id.def가 없어 품사 보정을 하지 않습니다', file=sys.stderr)
    best = {}
    functional = set()
    for f in sorted(glob.glob(os.path.join(a.mozc, 'dictionary*.txt'))):
        for line in open(f, encoding='utf-8'):
            p = line.rstrip('\n').split('\t')
            if len(p) < 5:
                continue
            reading, c, surface = p[0], int(p[3]), p[4]
            tag = pos.get(int(p[1]), '')
            if any(f in tag for f in FRAGMENT):
                c += FRAGMENT_COST
            if not KANA.match(reading) or not surface or '\t' in surface:
                continue
            if kata2hira(surface) == reading and surface != reading:   # 가타카나 그대로는 앱이 만든다
                continue
            if re.search(r'[A-Za-z0-9０-９]', surface):   # 숫자·로마자 표기(2, TOKYO)는 뺀다
                continue
            k = (reading, surface)
            if surface == reading and tag.split(',')[0] in FUNCTION:
                functional.add(k)
            if c < best.get(k, 1 << 30):
                best[k] = c
    ranked = sorted(best.items(), key=lambda x: x[1])
    # 가나 표기 단어는 가장 흔한 것(조사·어미 등)만 남긴다. 많이 넣으면 문장을 가나 조각으로 잘게 나눈다.
    kana_kept, chosen = 0, []
    for (r, s), c in ranked:
        if s == r and (r, s) not in functional:
            if kana_kept >= a.kana_words:
                continue
            kana_kept += 1
        chosen.append(((r, s), c))
        if len(chosen) >= a.words:
            break
    lo = chosen[0][1]
    hi = chosen[-1][1]
    span = max(1, hi - lo)
    entries = {}
    for (r, s), c in chosen:
        add(entries, r, s, round((c - lo) * 250 / span))
    print(f'ja: entries {len(chosen)}, keys {len(entries)}')
    return entries, {
        'name': a.name or '일본어 표준 사전', 'license': 'BSD-3-Clause, IPAdic',
        'attribution': 'Mozc 공개 사전 (Google, BSD-3; IPAdic, NAIST; 沖縄辞書, Public Domain)',
        'description': f'단어 {len(chosen):,}개',
    }


def prop(v):
    return v.replace('\\', '\\\\').replace('\n', '\\n').replace('\r', '')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('lang', choices=['zh', 'ja'])
    ap.add_argument('out')
    ap.add_argument('--jieba')
    ap.add_argument('--pypinyin')
    ap.add_argument('--mozc')
    ap.add_argument('--words', type=int, default=100000)
    ap.add_argument('--kana-words', type=int, default=300, help='일본어: 남길 가나 표기 단어 수')
    ap.add_argument('--name')
    ap.add_argument('--version', default='')
    ap.add_argument('--license-file', help='데이터 라이선스 전문 (팩에 LICENSE.txt로 넣는다)')
    a = ap.parse_args()
    if a.lang == 'zh':
        if not a.jieba or not a.pypinyin:
            sys.exit('--jieba 와 --pypinyin 이 필요합니다')
        entries, meta = build_zh(a)
    else:
        if not a.mozc:
            sys.exit('--mozc 가 필요합니다')
        entries, meta = build_ja(a)
    data = write_dict(entries)
    lines = [f'format={FORMAT}', f'lang={a.lang}', f'name={prop(meta["name"])}', f'version={prop(a.version)}',
             f'description={prop(meta["description"])}', f'license={prop(meta["license"])}',
             f'attribution={prop(meta["attribution"])}', f'sha256.convert.bin={hashlib.sha256(data).hexdigest()}']
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    with zipfile.ZipFile(a.out, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        z.writestr('manifest.properties', '\n'.join(lines) + '\n')
        z.writestr('convert.bin', data)
        if a.license_file:
            z.write(a.license_file, 'LICENSE.txt')
    print(f'wrote {a.out}: {os.path.getsize(a.out):,} bytes (convert.bin {len(data):,} bytes)')


if __name__ == '__main__':
    main()
