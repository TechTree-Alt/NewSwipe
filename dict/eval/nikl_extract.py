#!/usr/bin/env python3
"""
국립국어원 '모두의 말뭉치' 메신저 말뭉치(JSON)에서 평가용 문장을 뽑는다. 이용 약정상 원문은 저장소나 외부에 두지 않으므로,
run_private_eval.sh가 임시 폴더에 뽑아 평가한 뒤 지운다. 화면에는 문장 수 같은 집계만 찍는다.

  python3 dict/eval/nikl_extract.py 말뭉치_폴더_또는_zip 출력_폴더 [--field form] [--max 20000]

- 폴더 안(하위 폴더 포함)의 *.json과 *.zip 안의 *.json을 모두 읽는다.
- 'utterance' 목록을 가진 항목(대화 하나)마다 발화의 form(또는 --field로 고른 키)을 한 줄씩 꺼낸다.
  국립국어원 메신저 말뭉치: 최상위 document 목록의 각 항목이 대화이고, metadata.speaker에 화자마다 id·device(스마트폰 등)·
  keyboard(2벌식(쿼티), 천지인 등)가 있다. 발화의 speaker_id로 화자를 찾아 --device·--keyboard로 거를 수 있다
  (값에 그 글자가 들어 있으면 고른다. 예: --device 스마트폰 --keyboard 2벌식).
- 화면에는 문장 수, 자판·기기별 발화 수, form과 original_form이 다른 발화의 비율 같은 집계만 찍는다.
- 가명 처리 표시(&name& 같은 것)는 '#'로 바꾼다: 평가기는 한글이 아닌 토큰에서 앞 단어를 끊는다.
- 대화 id의 해시로 절반씩 나눈다: dev.txt(조정에 쓸 것), test.txt(조정에 쓰지 않을 것). 같은 대화는 한쪽에만 들어간다.
- --max: 나눈 쪽마다 최대 문장 수 (해시로 고르게 고른다).
- --numbered: '순번 \t 문장' 꼴로 쓴다 (dict/build/build_ko_model.py가 읽는 Leipzig 문장 파일과 같은 꼴).
  사전을 만들 때는 dev.txt만 쓰고 test.txt는 평가에만 쓴다.
"""
import argparse
import hashlib
import json
import os
import re
import sys
import zipfile
from collections import Counter

TAG = re.compile(r'&[^&\s]{1,40}&')
HANGUL = re.compile(r'[가-힣]')


def stable(s):
    return int(hashlib.sha1(s.encode('utf-8')).hexdigest()[:8], 16)


def json_sources(path):
    if os.path.isfile(path):
        paths = [path]
    else:
        paths = []
        for root, _, files in os.walk(path):
            for f in sorted(files):
                paths.append(os.path.join(root, f))
    for p in paths:
        low = p.lower()
        if low.endswith('.json'):
            with open(p, 'rb') as f:
                yield p, f.read()
        elif low.endswith('.zip'):
            with zipfile.ZipFile(p) as z:
                for name in sorted(z.namelist()):
                    if name.lower().endswith('.json'):
                        yield p + '!' + name, z.read(name)


def dialogues(obj, src):
    """'utterance' 목록을 가진 dict를 찾아 (대화 id, 발화 dict 목록, 화자 id → 화자 정보)를 낸다."""
    stack = [obj]
    n = 0
    while stack:
        o = stack.pop()
        if isinstance(o, dict):
            utt = o.get('utterance')
            if isinstance(utt, list):
                n += 1
                meta = o.get('metadata') if isinstance(o.get('metadata'), dict) else {}
                speakers = {}
                for sp in meta.get('speaker') or []:
                    if isinstance(sp, dict) and 'id' in sp:
                        speakers[str(sp['id'])] = sp
                yield str(o.get('id') or f'{src}#{n}'), utt, speakers
                continue
            stack.extend(o.values())
        elif isinstance(o, list):
            stack.extend(o)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('corpus')
    ap.add_argument('out_dir')
    ap.add_argument('--field', default='form', help="발화에서 꺼낼 키 (form, original_form 등)")
    ap.add_argument('--max', type=int, default=20000, help='나눈 쪽마다 최대 문장 수 (0이면 모두)')
    ap.add_argument('--numbered', action='store_true', help="'순번\t문장' 꼴로 쓴다 (사전 만들기용)")
    ap.add_argument('--device', default='', help='화자의 기기에 이 글자가 든 발화만 (예: 스마트폰)')
    ap.add_argument('--keyboard', default='', help='화자의 자판에 이 글자가 든 발화만 (예: 2벌식)')
    a = ap.parse_args()

    splits = {'dev': [], 'test': []}
    files = docs = skipped = 0
    by_keyboard, by_device = Counter(), Counter()
    total = differ = dropped = 0
    for src, data in json_sources(a.corpus):
        files += 1
        try:
            obj = json.loads(data.decode('utf-8-sig'))
        except (ValueError, UnicodeDecodeError):
            skipped += 1
            continue
        for did, utts, speakers in dialogues(obj, src):
            docs += 1
            side = 'dev' if stable(did) % 2 == 0 else 'test'
            for u in utts:
                text = u.get(a.field) if isinstance(u, dict) else None
                if not isinstance(text, str):
                    continue
                sp = speakers.get(str(u.get('speaker_id')), {})
                device, keyboard = str(sp.get('device', '?')), str(sp.get('keyboard', '?'))
                total += 1
                by_device[device] += 1
                by_keyboard[keyboard] += 1
                if isinstance(u.get('original_form'), str) and isinstance(u.get('form'), str) \
                        and u['original_form'] != u['form']:
                    differ += 1
                if a.device not in device or a.keyboard not in keyboard:
                    dropped += 1
                    continue
                text = ' '.join(TAG.sub(' # ', text).split())
                if HANGUL.search(text):
                    splits[side].append(text)
    os.makedirs(a.out_dir, exist_ok=True)
    for side, lines in splits.items():
        if a.max and len(lines) > a.max:
            lines = sorted(lines, key=stable)[:a.max]
        with open(os.path.join(a.out_dir, side + '.txt'), 'w', encoding='utf-8') as f:
            if a.numbered:
                f.write(''.join(f'{i + 1}\t{t}\n' for i, t in enumerate(lines)))
            else:
                f.write('\n'.join(lines) + '\n')
        print(f'{side}: 문장 {len(lines):,}개', file=sys.stderr)
    print(f'JSON {files:,}개, 대화 {docs:,}개, 발화 {total:,}개 (읽지 못한 파일 {skipped}개)', file=sys.stderr)
    if a.device or a.keyboard:
        print(f'기기·자판으로 거른 발화 {dropped:,}개', file=sys.stderr)
    print('기기별 발화: ' + ', '.join(f'{k} {v:,}' for k, v in by_device.most_common(8)), file=sys.stderr)
    print('자판별 발화: ' + ', '.join(f'{k} {v:,}' for k, v in by_keyboard.most_common(8)), file=sys.stderr)
    if total:
        print(f'form과 original_form이 다른 발화: {100 * differ / total:.1f}%', file=sys.stderr)
    if docs == 0:
        print("대화를 찾지 못했습니다. JSON에 'utterance' 목록이 있는지 확인하세요.", file=sys.stderr)
        sys.exit(1)


if __name__ == '__main__':
    main()
