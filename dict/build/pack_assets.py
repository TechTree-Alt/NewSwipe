#!/usr/bin/env python3
"""앱에 넣을 사전·이모지 파일을 작게 다시 담는다 (APK 크기를 줄이려고). 내용은 그대로다.

  python3 dict/build/pack_assets.py out_base/dict_ko.bin app/src/main/assets/dict_ko.bin
  python3 dict/build/pack_assets.py out_base/dict_ko_next.bin app/src/main/assets/dict_ko_next.bin
  python3 dict/build/pack_assets.py out_en/dict_en.bin app/src/main/assets/dict_en.bin

입력 형식을 보고 고른다:
  NSD1 (단어 사전)       → NSD2: 같은 단어·점수를 열마다 모아 둔다 (공통 접두사 길이들, 꼬리 길이들, 점수들, 꼬리 글자들)
  NSN1 (다음 단어 사전)  → NSN2: 같은 값을 열마다 모아 둔다 (앞 단어 번호 차이들, 다음 단어 수들, λ들, 다음 단어 번호 차이들, P들)
같은 종류의 값끼리 붙어 있어야 deflate가 잘 줄인다. 그 결과를 zlib(가능하면 zopfli: 같은 deflate 형식으로 5%쯤 더 작다)으로
압축해 쓴다. APK는 .bin 파일을 다시 압축하지 않고(app/build.gradle.kts의 noCompress), 앱은 zlib 머리를 보면 풀어 읽는다
(WordDictionary.open). 사전 팩에는 예전 형식(NSD1·NSN1, 압축 없음)을 그대로 넣는다: 예전 앱도 읽어야 해서.
이모지 목록은 tools/generate_emoji_data.py가 emoji_columns()로 담는다.
"""
import struct
import sys
import zlib


def compress(data):
    try:
        import zopfli.zlib
        return zopfli.zlib.compress(bytes(data), numiterations=100)
    except ImportError:   # pip install zopfli 이 없으면 조금 더 큰 zlib
        print('zopfli가 없어 zlib -9로 압축한다 (pip install zopfli)', file=sys.stderr)
        return zlib.compress(bytes(data), 9)


def varint(n, out):
    while n >= 0x80:
        out.append(n & 0x7F | 0x80)
        n >>= 7
    out.append(n)


def nsd2(data, layout=None):
    """NSD1 → NSD2: "NSD2" | u32 단어 수 n | u32 꼬리 글자 수 c | u8 글자 배치 | u8 공통 접두사 길이 ×n | u8 꼬리 길이 ×n
    | u8 점수 ×n | 꼬리 글자. 글자 배치 0 = UTF-16LE (글자마다 낮은 바이트, 높은 바이트: 한글), 1 = 낮은 바이트 c개 다음
    높은 바이트 c개 (높은 바이트가 거의 0인 영어). layout이 None이면 더 작게 압축되는 쪽을 고른다."""
    assert data[:4] == b'NSD1', '단어 사전(NSD1)이 아니다'
    n, blob = struct.unpack_from('<II', data, 4)
    raw = data[12:12 + blob]
    com, tail, score, chars = bytearray(), bytearray(), bytearray(), bytearray()
    p = 0
    for _ in range(n):
        c, t, s = raw[p], raw[p + 1], raw[p + 2]
        com.append(c)
        tail.append(t)
        score.append(s)
        chars += raw[p + 3:p + 3 + 2 * t]
        p += 3 + 2 * t
    assert p == blob
    if layout is None:
        return min((nsd2(data, k) for k in (0, 1)), key=lambda b: len(zlib.compress(b, 9)))
    if layout == 1:
        chars = chars[0::2] + chars[1::2]
    return b'NSD2' + struct.pack('<IIB', n, len(chars) // 2, layout) + bytes(com + tail + score + chars)


def nsn2(data):
    """NSN1 → NSN2: NSN1과 같은 머리 (24바이트) | varint 앞 단어 번호 차이 ×묶음 | varint 다음 단어 수 ×묶음 | u8 λ ×묶음
    | varint 묶음마다 첫 다음 단어 번호 ×묶음 | varint 나머지 다음 단어의 번호 차이 ×(쌍 - 묶음) | u8 P ×쌍."""
    assert data[:4] == b'NSN1', '다음 단어 사전(NSN1)이 아니다'
    groups, pairs = struct.unpack_from('<II', data, 16)
    p = 24

    def read():
        nonlocal p
        v = shift = 0
        while True:
            b = data[p]
            p += 1
            v |= (b & 0x7F) << shift
            shift += 7
            if b < 0x80:
                return v

    heads, counts, lam, firsts, rest = bytearray(), bytearray(), bytearray(), bytearray(), bytearray()
    for _ in range(groups):
        varint(read(), heads)
        lam.append(data[p])
        p += 1
        n = read()
        varint(n, counts)
        varint(read(), firsts)
        for _ in range(n - 1):
            varint(read(), rest)
    q = data[p:p + pairs]
    assert p + pairs == len(data)
    return b'NSN2' + data[4:24] + bytes(heads + counts + lam + firsts + rest + q)


def emoji_columns(header, rows):
    """이모지 목록: 머리 줄(#로 시작) 다음에 '#columns 줄 수', 그 뒤로 열마다(그룹, 이모지, 변형, 한국어, 영어) 모든 줄."""
    lines = [header, '#columns %d' % len(rows)]
    for col in range(5):
        lines.extend(r[col] for r in rows)
    return '\n'.join(lines).encode('utf-8') + b'\n'


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    with open(sys.argv[1], 'rb') as f:
        data = f.read()
    if data[:4] == b'NSD1':
        packed = nsd2(data)
    elif data[:4] == b'NSN1':
        packed = nsn2(data)
    else:
        sys.exit('NSD1·NSN1 파일이 아니다')
    out = compress(packed)
    with open(sys.argv[2], 'wb') as f:
        f.write(out)
    print(f'{sys.argv[1]} ({len(data):,} 바이트) → {sys.argv[2]} ({len(out):,} 바이트)')


if __name__ == '__main__':
    main()
