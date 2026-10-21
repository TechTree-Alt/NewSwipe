#!/usr/bin/env python3
"""한국어 사전 팩(zip)을 만든다. 앱의 설정 > 단어 추천 > 한국어 사전 > '사전 팩 가져오기'로 가져온다.

  python3 dict/build/make_dict_pack.py MODEL_DIR OUT.zip --name "표준 사전" --version 2026.10 \\
      --license "CC BY 4.0" --attribution "Leipzig Corpora Collection ..." [--description ...] [--license-file F]

MODEL_DIR은 dict/build/build_ko_model.py의 결과 폴더다 (dict_ko.bin 필수, dict_ko_next.bin 선택).
팩에 넣는 데이터의 라이선스와 출처를 정확히 적는다 (앱은 가져온 팩의 출처를 설정 화면에 보여 준다).
팩 형식은 app/src/main/java/.../suggest/DictionaryPack.java의 설명을 따른다.
"""
import argparse
import hashlib
import os
import sys
import zipfile

FORMAT = 1


def sha256(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 16), b''):
            h.update(chunk)
    return h.hexdigest()


def prop(value):
    """java.util.Properties가 읽는 값으로 (줄바꿈·역슬래시를 이스케이프한다). 한글은 UTF-8 그대로 둔다."""
    return value.replace('\\', '\\\\').replace('\n', '\\n').replace('\r', '')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('model_dir')
    ap.add_argument('out')
    ap.add_argument('--name', required=True)
    ap.add_argument('--version', default='')
    ap.add_argument('--description', default='')
    ap.add_argument('--license', required=True, help='예: CC BY 4.0')
    ap.add_argument('--attribution', required=True, help='데이터 출처 (화면에 보인다)')
    ap.add_argument('--license-file', help='라이선스 전문 (팩에 LICENSE.txt로 넣는다)')
    a = ap.parse_args()

    dict_path = os.path.join(a.model_dir, 'dict_ko.bin')
    next_path = os.path.join(a.model_dir, 'dict_ko_next.bin')
    if not os.path.isfile(dict_path):
        sys.exit(f'{dict_path} 이 없습니다')
    with open(dict_path, 'rb') as f:
        if f.read(4) != b'NSD1':
            sys.exit('dict_ko.bin 형식이 아닙니다')
    lines = [
        f'format={FORMAT}', 'lang=ko',
        f'name={prop(a.name)}', f'version={prop(a.version)}', f'description={prop(a.description)}',
        f'license={prop(a.license)}', f'attribution={prop(a.attribution)}',
        f'sha256.dict_ko.bin={sha256(dict_path)}',
    ]
    if os.path.isfile(next_path):
        lines.append(f'sha256.dict_ko_next.bin={sha256(next_path)}')
    with zipfile.ZipFile(a.out, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        z.writestr('manifest.properties', '\n'.join(lines) + '\n')
        z.write(dict_path, 'dict_ko.bin')
        if os.path.isfile(next_path):
            z.write(next_path, 'dict_ko_next.bin')
        if a.license_file:
            z.write(a.license_file, 'LICENSE.txt')
    print(f'wrote {a.out}: {os.path.getsize(a.out):,} bytes')


if __name__ == '__main__':
    main()
