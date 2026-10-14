#!/usr/bin/env python3
"""
JSON 말뭉치의 구조(키 이름, 자료형, 목록 길이)만 보여 준다. 문자열 값은 길이만 찍어, 원문을 보지 않고도 구조를 알 수 있다.
공개할 수 없는 말뭉치의 형식을 확인할 때 쓴다 (nikl_extract.py가 대화를 찾지 못할 때 등).

  python3 dict/eval/json_schema.py 파일.json|파일.zip|폴더 [--depth 6]
"""
import argparse
import json
import os
import sys
import zipfile


def first_json(path):
    if os.path.isdir(path):
        for root, _, files in os.walk(path):
            for f in sorted(files):
                if f.lower().endswith(('.json', '.zip')):
                    return first_json(os.path.join(root, f))
        return None, None
    if path.lower().endswith('.zip'):
        with zipfile.ZipFile(path) as z:
            for name in sorted(z.namelist()):
                if name.lower().endswith('.json'):
                    return path + '!' + name, z.read(name)
        return None, None
    with open(path, 'rb') as f:
        return path, f.read()


def show(o, depth, indent=''):
    if isinstance(o, dict):
        for k, v in o.items():
            print(f'{indent}{k}: {kind(v)}')
            if depth > 0 and isinstance(v, (dict, list)):
                show(v, depth - 1, indent + '  ')
    elif isinstance(o, list) and o:
        print(f'{indent}[0]: {kind(o[0])}')
        if depth > 0 and isinstance(o[0], (dict, list)):
            show(o[0], depth - 1, indent + '  ')


def kind(v):
    if isinstance(v, dict):
        return f'객체 (키 {len(v)}개)'
    if isinstance(v, list):
        return f'목록 ({len(v)}개)'
    if isinstance(v, str):
        return f'문자열 (길이 {len(v)})'
    return type(v).__name__


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('path')
    ap.add_argument('--depth', type=int, default=6)
    a = ap.parse_args()
    name, data = first_json(a.path)
    if data is None:
        sys.exit('JSON 파일을 찾지 못했습니다')
    print(f'파일: {os.path.basename(name)}')
    show(json.loads(data.decode('utf-8-sig')), a.depth)


if __name__ == '__main__':
    main()
