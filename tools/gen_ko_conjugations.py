#!/usr/bin/env python3
"""한국어 용언 활용형 생성기.

tools/ko_stems.txt의 어간 목록에 자주 쓰는 어미를 붙여, 대화체(해요체, 반말, 합쇼체) 활용형을 만든다.
결과는 build_ko_dict.py의 --extra 형식('점수: 단어 단어 ...')으로 쓴다.

사용법:
  python3 tools/gen_ko_conjugations.py tools/ko_stems.txt OUT.txt   # 생성
  python3 tools/gen_ko_conjugations.py --selftest                   # 알려진 정답과 대조

지원하는 활용 규칙:
  - 모음조화(아/어), 모음 축약(가+아→가, 보+아→봐, 주+어→줘, 마시+어→마셔, 되+어→돼, 하+여→해)
  - ㅡ 탈락(쓰→써, 아프→아파, 바쁘→바빠), ㄹ 탈락(살→사니/삽니다/사세요)
  - ㅂ·ㄷ·ㅅ·ㅎ·르 불규칙 (어간 뒤 /b /d /s /h /r 표시)
틀린 활용형이 섞이지 않게, 모르는 규칙은 만들지 않고 건너뛴다.
"""
import sys

CHO = 'ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ'
JUNG = 'ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ'
JONG = ['', 'ㄱ', 'ㄲ', 'ㄳ', 'ㄴ', 'ㄵ', 'ㄶ', 'ㄷ', 'ㄹ', 'ㄺ', 'ㄻ', 'ㄼ', 'ㄽ', 'ㄾ', 'ㄿ', 'ㅀ', 'ㅁ', 'ㅂ',
        'ㅄ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ']
POSITIVE = set('ㅏㅗㅑㅘ')   # 양성 모음 (아 계열)


def dec(ch):
    c = ord(ch) - 0xAC00
    return c // 588, (c % 588) // 28, c % 28


def com(l, v, t):
    return chr(0xAC00 + (l * 21 + v) * 28 + t)


def parts(s):
    l, v, t = dec(s[-1])
    return CHO[l], JUNG[v], JONG[t]


def build(prefix, cho, jung, jong=''):
    return prefix + com(CHO.index(cho), JUNG.index(jung), JONG.index(jong))


def is_hangul(ch):
    return 0xAC00 <= ord(ch) <= 0xD7A3


def final_of(s):
    return JONG[dec(s[-1])[2]]


def with_final(s, jong):
    """마지막 음절(받침 없는)에 받침을 붙인다."""
    l, v, t = dec(s[-1])
    assert t == 0, s
    return s[:-1] + com(l, v, JONG.index(jong))


def drop_final(s):
    l, v, t = dec(s[-1])
    return s[:-1] + com(l, v, 0)


def harmony(stem):
    """어간 마지막 모음이 양성이면 '아', 아니면 '어'."""
    return '아' if parts(stem)[1] in POSITIVE else '어'


def ao_base(stem, flag):
    """'-아/어' 앞의 형태를 돌려준다 (끝 음절에 아/어 소리가 들어 있다). 모르는 경우 None."""
    cho, jung, jong = parts(stem)
    if flag == 'b':   # ㅂ 불규칙: 돕/곱 → 와, 나머지 → 워
        assert jong == 'ㅂ', stem
        return drop_final(stem) + ('와' if stem[-1] in '돕곱' else '워')
    if flag == 'd':   # ㄷ 불규칙: ㄷ → ㄹ
        assert jong == 'ㄷ', stem
        alt = stem[:-1] + com(dec(stem[-1])[0], dec(stem[-1])[1], JONG.index('ㄹ'))
        return alt + harmony(stem)
    if flag == 's':   # ㅅ 불규칙: ㅅ 탈락, 축약 없음
        assert jong == 'ㅅ', stem
        return drop_final(stem) + harmony(stem)
    if flag == 'r':   # 르 불규칙: ㄹ 겹침
        assert stem[-1] == '르' and len(stem) >= 2, stem
        prefix = stem[:-1]
        return with_final(prefix, 'ㄹ') + ('라' if parts(prefix)[1] in POSITIVE else '러')
    if flag == 'h':   # ㅎ 불규칙: ㅎ 탈락 + ㅐ
        assert jong == 'ㅎ', stem
        new_v = {'ㅏ': 'ㅐ', 'ㅓ': 'ㅐ', 'ㅑ': 'ㅒ', 'ㅕ': 'ㅖ'}[jung]
        return stem[:-1] + build('', cho, new_v)
    if flag:
        raise ValueError(f'알 수 없는 표시: {stem}/{flag}')
    if stem[-1] == '하':
        return stem[:-1] + '해'
    if jong:   # 받침 있는 규칙 어간 (ㄹ, ㅎ 받침 포함)
        return stem + harmony(stem)
    if jung == 'ㅡ':   # ㅡ 탈락: 앞 음절 모음으로 아/어 결정, 한 음절이면 어
        prev_positive = len(stem) > 1 and parts(stem[:-1])[1] in POSITIVE
        return stem[:-1] + build('', cho, 'ㅏ' if prev_positive else 'ㅓ')
    # 받침 없는 어간의 모음 축약
    if jung in ('ㅏ', 'ㅓ', 'ㅐ', 'ㅔ', 'ㅕ', 'ㅘ', 'ㅝ', 'ㅙ', 'ㅞ'):
        return stem
    if jung == 'ㅗ':
        return stem[:-1] + build('', cho, 'ㅘ')
    if jung == 'ㅜ':
        return stem[:-1] + build('', cho, 'ㅝ')
    if jung == 'ㅣ':
        return stem[:-1] + build('', cho, 'ㅕ')
    if jung == 'ㅚ':
        return stem[:-1] + build('', cho, 'ㅙ')
    return stem + harmony(stem)   # ㅟ 등: 줄이지 않는다 (바뀌어, 쉬어)


def classify(stem, flag):
    """으-계열 어미(면, ㄹ…, ㄴ…, 세요)를 붙일 때 쓸 형태.

    돌려주는 값: (alt_stem, needs_eu, l_stem)
      alt_stem: 모음으로 시작하는 어미 앞의 어간
      needs_eu: True면 '으'를 끼운다 (먹으면, 들으면)
      l_stem  : ㄹ 받침 어간 (ㄹ 탈락 규칙 적용)
    """
    jong = final_of(stem)
    if flag == 'b':
        return drop_final(stem) + '우', False, False
    if flag == 'd':
        alt = stem[:-1] + com(dec(stem[-1])[0], dec(stem[-1])[1], JONG.index('ㄹ'))
        return alt, True, False
    if flag == 's':
        return drop_final(stem), True, False
    if flag == 'h':
        return drop_final(stem), False, False
    if flag in ('r', None, ''):
        if jong == 'ㄹ':
            return stem, False, True
        if jong:
            return stem, True, False
        return stem, False, False
    raise ValueError(flag)


def attach_jong(alt, jong):
    return with_final(alt, jong)


def eu_syllable(jong):
    return com(CHO.index('ㅇ'), JUNG.index('ㅡ'), JONG.index(jong))


# ---------------------------------------------------------------------------------------------
# 어미 목록. (이름, 종류, 어미, 점수, 대상) 대상: 'va' 동사·형용사, 'v' 동사만, 'a' 형용사만
# 점수는 백만 단어당 빈도로 취급한다. 'full'은 자주 쓰는 어간에, 'lite'는 덜 쓰는 어간에 쓴다.
# ---------------------------------------------------------------------------------------------
# 종류: ao(아/어 뒤) / past(았/었 뒤) / help(아/어 + 보조 동사) / jong(ㄹ,ㄴ,ㅂ 받침 어미) /
#       se(세요) / eu(면, 니까, 려고) / plain(그대로 붙임) / ndan(동사 현재 -ㄴ다/는다)
ENDINGS = [
    # 아/어
    ('ao', '', 160, 'va', 'lite'), ('ao', '요', 160, 'va', 'lite'), ('ao', '서', 100, 'va', 'lite'),
    ('ao', '도', 50, 'va', 'full'), ('ao', '야', 50, 'va', 'full'),
    # 과거
    ('past', '어', 150, 'va', 'lite'), ('past', '어요', 150, 'va', 'lite'), ('past', '습니다', 90, 'va', 'lite'),
    ('past', '다', 60, 'va', 'full'), ('past', '는데', 50, 'va', 'full'), ('past', '지만', 40, 'va', 'full'),
    ('past', '네', 40, 'va', 'full'), ('past', '네요', 40, 'va', 'full'), ('past', '죠', 40, 'va', 'full'),
    ('past', '으면', 40, 'va', 'full'),
    # 아/어 + 보조 동사 (동사만)
    ('help', '줘', 70, 'v', 'full'), ('help', '주세요', 70, 'v', 'full'), ('help', '줄게', 40, 'v', 'full'),
    ('help', '줄게요', 40, 'v', 'full'), ('help', '봐', 40, 'v', 'full'), ('help', '봐요', 40, 'v', 'full'),
    ('help', '볼게', 40, 'v', 'full'), ('help', '볼게요', 40, 'v', 'full'), ('help', '보자', 30, 'v', 'full'),
    ('help', '볼까요', 30, 'v', 'full'),
    # ㄹ·ㄴ·ㅂ 받침이 붙는 어미
    ('jong', 'ㄹ게', 90, 'v', 'lite'), ('jong', 'ㄹ게요', 90, 'v', 'lite'), ('jong', 'ㄹ까', 70, 'va', 'full'),
    ('jong', 'ㄹ까요', 70, 'va', 'lite'), ('jong', 'ㄹ래', 40, 'v', 'full'), ('jong', 'ㄹ래요', 40, 'v', 'full'),
    ('jong', 'ㄹ', 60, 'va', 'lite'), ('jong', 'ㄴ', 70, 'va', 'lite'), ('jong', 'ㄴ데', 60, 'a', 'full'),
    ('jong', 'ㅂ니다', 100, 'va', 'lite'), ('jong', 'ㅂ니까', 40, 'va', 'full'),
    ('se', '세요', 90, 'va', 'lite'),
    ('eu', '면', 70, 'va', 'lite'), ('eu', '니까', 70, 'va', 'full'), ('eu', '려고', 40, 'v', 'full'),
    ('ndan', '', 40, 'v', 'full'),
    # 그대로 붙이는 어미
    ('plain', '고', 120, 'va', 'lite'), ('plain', '지', 70, 'va', 'full'), ('plain', '지만', 70, 'va', 'full'),
    ('plain', '네', 60, 'va', 'full'), ('plain', '네요', 60, 'va', 'full'), ('plain', '군요', 40, 'va', 'full'),
    ('plain', '구나', 40, 'va', 'full'), ('plain', '죠', 40, 'va', 'full'), ('plain', '잖아', 40, 'va', 'full'),
    ('plain', '잖아요', 40, 'va', 'full'), ('plain', '다', 50, 'va', 'full'), ('plain', '겠다', 40, 'va', 'full'),
    ('plain', '겠어요', 40, 'va', 'full'), ('plain', '는', 90, 'v', 'lite'), ('plain', '는데', 80, 'v', 'full'),
    ('plain', '자', 40, 'v', 'full'),
]
# ㄹ 받침 어간이 ㄴ으로 시작하는 어미 앞에서 ㄹ을 버리는 plain 어미
L_DROP_PLAIN = {'네', '네요', '는', '는데'}


def conjugate(stem, flag, kind, ending, pos):
    """어간+어미 하나를 활용한 결과. 만들 수 없으면 None."""
    base_kind, tail = kind, ending
    ao = ao_base(stem, flag)
    if base_kind == 'ao':
        return ao + tail
    if base_kind == 'past':
        return with_final(ao, 'ㅆ') + tail
    if base_kind == 'help':
        return ao + tail
    alt, needs_eu, l_stem = classify(stem, flag)
    if base_kind == 'jong':
        mark, rest = tail[0], tail[1:]
        if mark == 'ㅂ':   # 합쇼체: 받침 있으면 습니다/습니까
            if l_stem:
                return with_final(drop_final(stem), 'ㅂ') + rest
            if final_of(stem):
                return stem + '습' + rest
            return with_final(alt, 'ㅂ') + rest
        if l_stem:
            if mark == 'ㄹ':
                return stem + rest              # 살게, 살까, 살
            return with_final(drop_final(stem), mark) + rest   # 산, 사는데
        if needs_eu:
            return alt + eu_syllable(mark) + rest              # 먹을게, 들은, 나은
        return with_final(alt, mark) + rest                    # 갈게, 도운, 그런
    if base_kind == 'se':
        if l_stem:
            return drop_final(stem) + tail
        if needs_eu:
            return alt + '으' + tail
        return alt + tail
    if base_kind == 'eu':
        if l_stem:
            return (stem if tail in ('면', '려고') else drop_final(stem)) + tail
        if needs_eu:
            return alt + '으' + tail
        return alt + tail
    if base_kind == 'ndan':   # 동사 현재형 평서: 간다 / 먹는다 / 산다
        if l_stem:
            return with_final(drop_final(stem), 'ㄴ') + '다'
        if final_of(stem):
            return stem + '는다'
        return with_final(stem, 'ㄴ') + '다'
    if base_kind == 'plain':
        if l_stem and tail in L_DROP_PLAIN:
            return drop_final(stem) + tail
        return stem + tail
    raise ValueError(base_kind)


def parse_stem(token):
    if '/' in token:
        stem, flag = token.split('/', 1)
    else:
        stem, flag = token, None
    assert stem and all(is_hangul(c) for c in stem), token
    return stem, flag


def generate(path):
    """어간 파일을 읽어 {단어: 점수}와 종류별 통계를 돌려준다."""
    result = {}
    skipped = []
    with open(path, encoding='utf-8') as f:
        for raw in f:
            line = raw.strip()
            if not line or line.startswith('#'):
                continue
            kind, _, tokens = line.partition(':')
            kind = kind.strip()
            pos = 'v' if kind.startswith('v') else 'a'
            tier_full = kind.endswith('1')
            factor = 1.0 if tier_full else 0.4
            for token in tokens.split():
                stem, flag = parse_stem(token)
                for ek, tail, score, target, size in ENDINGS:
                    if pos not in target and target != 'va':
                        continue
                    if target == 'v' and pos != 'v':
                        continue
                    if target == 'a' and pos != 'a':
                        continue
                    if size == 'full' and not tier_full:
                        continue
                    try:
                        word = conjugate(stem, flag, ek, tail, pos)
                    except (AssertionError, KeyError, ValueError):
                        skipped.append(f'{token}+{ek}:{tail}')
                        continue
                    if word:
                        s = max(1, round(score * factor))
                        result[word] = max(result.get(word, 0), s)
    return result, skipped


# ---------------------------------------------------------------------------------------------
# 자체 검사: 알려진 정답과 대조한다.
# ---------------------------------------------------------------------------------------------
EXPECTED = {
    # (어간, 표시): {(종류, 어미): 정답}
    ('가', None): {('ao', ''): '가', ('ao', '요'): '가요', ('past', '어'): '갔어', ('jong', 'ㄹ게'): '갈게',
                  ('jong', 'ㅂ니다'): '갑니다', ('se', '세요'): '가세요', ('plain', '는'): '가는', ('jong', 'ㄴ'): '간',
                  ('jong', 'ㄹ'): '갈', ('ndan', ''): '간다', ('eu', '면'): '가면', ('help', '줘'): '가줘'},
    ('먹', None): {('ao', ''): '먹어', ('past', '어요'): '먹었어요', ('jong', 'ㄹ게'): '먹을게',
                  ('jong', 'ㅂ니다'): '먹습니다', ('se', '세요'): '먹으세요', ('plain', '는'): '먹는',
                  ('jong', 'ㄴ'): '먹은', ('ndan', ''): '먹는다', ('eu', '니까'): '먹으니까'},
    ('하', None): {('ao', ''): '해', ('past', '어'): '했어', ('jong', 'ㄹ게'): '할게', ('jong', 'ㅂ니다'): '합니다',
                  ('se', '세요'): '하세요', ('plain', '는'): '하는', ('help', '줘'): '해줘'},
    ('보', None): {('ao', ''): '봐', ('past', '어'): '봤어', ('jong', 'ㄹ게'): '볼게', ('jong', 'ㅂ니다'): '봅니다'},
    ('주', None): {('ao', ''): '줘', ('past', '어'): '줬어', ('jong', 'ㄹ게'): '줄게'},
    ('오', None): {('ao', ''): '와', ('past', '어'): '왔어', ('jong', 'ㄹ게'): '올게', ('jong', 'ㄴ'): '온'},
    ('마시', None): {('ao', ''): '마셔', ('past', '어'): '마셨어', ('jong', 'ㄹ게'): '마실게'},
    ('되', None): {('ao', ''): '돼', ('past', '어'): '됐어', ('jong', 'ㄹ게'): '될게', ('jong', 'ㅂ니다'): '됩니다'},
    ('기다리', None): {('ao', ''): '기다려', ('past', '어'): '기다렸어', ('jong', 'ㅂ니다'): '기다립니다'},
    ('쓰', None): {('ao', ''): '써', ('past', '어'): '썼어', ('jong', 'ㄹ게'): '쓸게', ('jong', 'ㅂ니다'): '씁니다',
                  ('se', '세요'): '쓰세요'},
    ('아프', None): {('ao', ''): '아파', ('past', '어'): '아팠어', ('jong', 'ㄹ까'): '아플까',
                    ('jong', 'ㅂ니다'): '아픕니다', ('jong', 'ㄴ'): '아픈'},
    ('예쁘', None): {('ao', ''): '예뻐', ('past', '어'): '예뻤어', ('jong', 'ㄴ'): '예쁜'},
    ('크', None): {('ao', ''): '커', ('past', '어'): '컸어', ('jong', 'ㄴ'): '큰', ('jong', 'ㅂ니다'): '큽니다'},
    ('바쁘', None): {('ao', ''): '바빠', ('past', '어'): '바빴어'},
    ('듣', 'd'): {('ao', ''): '들어', ('past', '어'): '들었어', ('jong', 'ㄹ게'): '들을게', ('jong', 'ㅂ니다'): '듣습니다',
                 ('se', '세요'): '들으세요', ('plain', '는'): '듣는', ('plain', '고'): '듣고', ('jong', 'ㄴ'): '들은'},
    ('돕', 'b'): {('ao', ''): '도와', ('past', '어'): '도왔어', ('jong', 'ㄹ게'): '도울게', ('jong', 'ㅂ니다'): '돕습니다',
                 ('se', '세요'): '도우세요', ('jong', 'ㄴ'): '도운', ('plain', '고'): '돕고'},
    ('춥', 'b'): {('ao', ''): '추워', ('past', '어'): '추웠어', ('jong', 'ㄹ까'): '추울까', ('jong', 'ㄴ'): '추운'},
    ('고맙', 'b'): {('ao', ''): '고마워', ('past', '어요'): '고마웠어요', ('jong', 'ㅂ니다'): '고맙습니다'},
    ('낫', 's'): {('ao', ''): '나아', ('past', '어'): '나았어', ('jong', 'ㄹ게'): '나을게', ('plain', '고'): '낫고',
                 ('se', '세요'): '나으세요'},
    ('모르', 'r'): {('ao', ''): '몰라', ('past', '어'): '몰랐어', ('jong', 'ㄹ게'): '모를게', ('jong', 'ㅂ니다'): '모릅니다',
                   ('plain', '는'): '모르는', ('se', '세요'): '모르세요'},
    ('부르', 'r'): {('ao', ''): '불러', ('past', '어'): '불렀어'},
    ('살', None): {('ao', ''): '살아', ('past', '어'): '살았어', ('jong', 'ㄹ게'): '살게', ('jong', 'ㅂ니다'): '삽니다',
                  ('se', '세요'): '사세요', ('plain', '는'): '사는', ('jong', 'ㄴ'): '산', ('eu', '면'): '살면',
                  ('eu', '니까'): '사니까', ('ndan', ''): '산다', ('plain', '네'): '사네'},
    ('만들', None): {('ao', ''): '만들어', ('jong', 'ㅂ니다'): '만듭니다', ('se', '세요'): '만드세요',
                    ('plain', '는'): '만드는', ('jong', 'ㄴ'): '만든', ('jong', 'ㄹ게'): '만들게'},
    ('그렇', 'h'): {('ao', ''): '그래', ('past', '어'): '그랬어', ('jong', 'ㄹ까'): '그럴까', ('eu', '면'): '그러면',
                   ('jong', 'ㅂ니다'): '그렇습니다', ('plain', '고'): '그렇고', ('jong', 'ㄴ'): '그런'},
    ('어떻', 'h'): {('ao', ''): '어때', ('past', '어'): '어땠어'},
    ('좋', None): {('ao', ''): '좋아', ('past', '어'): '좋았어', ('jong', 'ㄹ까'): '좋을까', ('jong', 'ㅂ니다'): '좋습니다',
                  ('jong', 'ㄴ'): '좋은', ('se', '세요'): '좋으세요'},
    ('괜찮', None): {('ao', ''): '괜찮아', ('past', '어'): '괜찮았어', ('jong', 'ㄴ'): '괜찮은'},
    ('사랑하', None): {('ao', '요'): '사랑해요', ('past', '어요'): '사랑했어요', ('jong', 'ㅂ니다'): '사랑합니다'},
    ('일어나', None): {('ao', ''): '일어나', ('past', '어'): '일어났어', ('jong', 'ㄹ게'): '일어날게'},
    ('배우', None): {('ao', ''): '배워', ('past', '어'): '배웠어'},
    ('기쁘', None): {('ao', ''): '기뻐', ('past', '어'): '기뻤어'},
    ('따르', None): {('ao', ''): '따라', ('past', '어'): '따랐어'},
    ('끄', None): {('ao', ''): '꺼', ('past', '어'): '껐어'},
    ('맵', 'b'): {('ao', ''): '매워', ('jong', 'ㄴ'): '매운'},
    ('걷', 'd'): {('ao', ''): '걸어', ('past', '어'): '걸었어'},
}


def selftest():
    wrong = 0
    total = 0
    for (stem, flag), cases in EXPECTED.items():
        for (kind, tail), expect in cases.items():
            total += 1
            got = conjugate(stem, flag, kind, tail, 'v')
            if got != expect:
                wrong += 1
                print(f'틀림: {stem}{"/" + flag if flag else ""} + {kind}:{tail} → {got} (정답 {expect})')
    print(f'자체 검사: {total - wrong}/{total} 통과')
    return wrong == 0


def main():
    if sys.argv[1:] == ['--selftest']:
        sys.exit(0 if selftest() else 1)
    src, dst = sys.argv[1], sys.argv[2]
    if not selftest():
        sys.exit('자체 검사에 실패해서 생성하지 않는다.')
    words, skipped = generate(src)
    if skipped:
        print(f'건너뜀 {len(skipped)}건: ' + ', '.join(skipped[:10]))
    by_score = {}
    for w, s in words.items():
        by_score.setdefault(s, []).append(w)
    with open(dst, 'w', encoding='utf-8') as f:
        f.write('# gen_ko_conjugations.py가 만든 활용형 목록 (직접 고치지 말고 ko_stems.txt를 고친 뒤 다시 만든다)\n')
        for s in sorted(by_score, reverse=True):
            f.write(f'{s}: ' + ' '.join(sorted(by_score[s])) + '\n')
    print(f'활용형 {len(words):,}개 → {dst}')


if __name__ == '__main__':
    main()
