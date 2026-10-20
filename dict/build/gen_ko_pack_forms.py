#!/usr/bin/env python3
"""표준 사전 팩에 넣을 용언 활용형을 넓게 만든다 (build_ko_model.py --forms의 입력).

말뭉치(뉴스·웹·리뷰)에는 대화에서 흔한 꼴(좋으시네요, 그럽시다, 드리워진, 알아줄까, 실망스러워)이 드물거나 없다. 그런 말은
사전에 없으니 자동 수정이 맞게 친 말을 잘못 고치거나(실망스러워 → 사랑스러워), 틀리게 친 말을 바른 꼴로 고치지 못한다.
어간은 말뭉치에 흔하므로, 어간마다 어미를 두루 붙여 활용형을 만들고 말뭉치에서 잰 어미 몫으로 빈도를 매긴다.

  1) 어간: ko_stems.txt(손으로 고른 것)와 형태소 분석기 Kiwi로 센 어간(count_ko_stems.py의 출력 또는 ko_stems_kiwi.txt).
     분석 실수(성의하, 능력하)를 거르려고, 만든 꼴 중 서로 다른 MIN_SEEN개 이상이 말뭉치에 있는 어간만 쓴다.
  2) 꼴: 어간 그대로, 높임(-시-), 과거(-았/었-), 추측(-겠-), 높임 + 과거(-셨-), 과거 + 추측(-았겠-)과
     보조 용언(-아/어 주다·드리다·보다·버리다·지다·가다·오다·내다·놓다·두다) 뒤에 어미(종결·연결·전성)를 붙인다.
     동사와 형용사가 다른 어미는 따로 붙인다 (먹는다고 / 좋다고, 먹는 / 좋은, 먹는구나 / 좋구나).
     활용 규칙은 gen_ko_conjugations.py의 것(자체 검사가 있다)을 쓰고, 여기서도 자체 검사(EXPECTED)를 한다.
  3) 틀린 꼴을 막는 장치: 모음 어미가 붙지 않는 준말 어간(갖다 → 갖아 X, 가져 O)은 모음 어미를 붙이지 않는다. 띄어 써야
     하는 꼴(할 거야, 할 텐데, 할까 봐)은 만들지 않는다. 마지막으로 Kiwi로 분석해 어간이 같게 나뉘지 않거나, 조사·기호로
     풀리는 꼴(되니요 → 되 + 니 + 요)은 버린다.
  4) 빈도(100만 어절당) = 어간 무게 × 어미 몫 × SCALE. 어간 무게는 그 어간의 꼴들이 말뭉치에서 나온 빈도의 합, 어미 몫은
     (모든 어간에서 그 어미 꼴의 빈도 합) / (어간 무게의 합). 말뭉치 빈도가 더 크면 build_ko_model.py가 그것을 쓴다.

사용법:
  python3 dict/build/gen_ko_pack_forms.py OUT.txt --counts counts.pkl --mix nsmc.pkl:0.5 \
      --stems dict/lists/ko_stems.txt --kiwi-stems kiwi_stems.tsv [--min-stem-freq 0.3]
  python3 dict/build/gen_ko_pack_forms.py --selftest
counts.pkl·nsmc.pkl은 build_ko_model.py --cache가 만든 파일. 출력은 build_ko_dict.py의 --extra 형식('점수: 단어 ...').
Kiwi(kiwipiepy==0.24.0, Apache License 2.0)는 사전을 만들 때만 쓴다 (앱에는 들어가지 않는다).
"""
import argparse
import os
import pickle
import sys
from collections import defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_ko_conjugations as g  # noqa: E402

SCALE = 1.0
MIN_SEEN = 3   # 어간의 꼴 중 말뭉치에 있는 것이 이보다 적으면 그 어간은 쓰지 않는다 (분석 실수·드문 어간)
# 본용언 + 보조 용언은 말이 되는 짝만 쓰려고, 그 짝의 꼴이 말뭉치에 이만큼 있어야 만든다 (알아주다 O, 가내다 X)
MIN_AUX_SEEN = 2
# 모음으로 시작하는 어미가 붙지 않는 준말 어간 (갖다 → 가져, 머물다 → 머물러, 서툴다 → 서툴러, 내딛다 → 내디뎌, 뵙다 → 봬)
DEFECTIVE = {'갖', '머물', '서툴', '내딛', '딛', '건들', '뵙'}
# 용언처럼 활용하지 않는 어간 (형태소 분석기가 용언으로 센 서술격 조사 '이'와 '아니다': 아니에요 O, 아녀요 X)
NOT_STEMS = {'이', '아니'}

# 보조 용언과 거기에 붙일 어미 (본용언의 아/어 꼴 뒤에 붙여 쓴다: 알아줄까, 드리워진, 늙어가는). 지다는 형용사에도 붙인다.
AUX = {
    '주': ('v', ['ao:', 'ao:요', 'ao:서', 'ao:도', 'ao:야', 'P:ao:', 'P:ao:요', 'P:다', 'P:는데', 'P:eu:면', 'se', 'S:eu:면',
                 'S:고', 'S:ao:서', 'S:ㄴ', 'S:ㄹ', 'SP:ao:요', '고', '는', 'ㄴ', 'ㄹ', 'ㄹ게', 'ㄹ게요', 'ㄹ까', 'ㄹ까요', 'ㄹ래',
                 'ㄹ래요', 'eu:면', 'eu:니까', '네', '지', '죠', 'ndan', 'ㅂ니다']),
    '드리': ('v', ['ao:', 'ao:요', 'ao:서', 'P:ao:요', 'P:ㅂ니다', '고', '는', 'ㄴ', 'ㄹ', 'ㄹ게요', 'ㄹ까요', 'eu:면', 'ㅂ니다',
                   'F:ㅂ니다']),
    '보': ('v', ['ao:', 'ao:요', 'ao:서', 'ao:도', 'ao:야', 'P:ao:', 'P:ao:요', 'P:다', 'P:는데', 'P:eu:면', 'se', '고', '는',
                 'ㄴ', 'ㄹ', 'ㄹ게', 'ㄹ게요', 'ㄹ까', 'ㄹ까요', 'ㄹ래', 'eu:면', 'eu:니까', '자', '네', '지', 'ndan', 'ㅂ니다']),
    '버리': ('v', ['ao:', 'ao:요', 'P:ao:', 'P:ao:요', 'P:다', 'P:네', 'P:는데', '고', '는', 'ㄴ', 'ㄹ', 'ㄹ까', 'eu:면']),
    '지': ('va', ['ao:', 'ao:요', 'ao:서', 'ao:도', 'ao:야', 'P:ao:', 'P:ao:요', 'P:다', 'P:네', 'P:네요', 'P:는데', 'P:ㅂ니다',
                  'P:eu:면', '고', '는', '는데', 'ㄴ', 'ㄹ', 'ㄹ까', 'eu:면', 'eu:니까', '네', '지', '죠', 'ndan', 'ㅂ니다']),
    '가': ('v', ['ao:', 'ao:요', 'ao:서', 'P:ao:', 'P:ao:요', 'P:다', '고', '는', 'ㄴ', 'ㄹ', 'ㄹ게', 'eu:면', '네', 'ndan']),
    '오': ('v', ['ao:', 'ao:요', 'ao:서', 'P:ao:', 'P:ao:요', 'P:다', 'P:는데', '고', '는', 'ㄴ', 'ㄹ', 'ㄹ게', 'eu:면']),
    '내': ('v', ['ao:', 'P:ao:', 'P:ao:요', 'P:다', '고', '는', 'ㄴ', 'ㄹ', 'eu:면']),
    '놓': ('v', ['ao:', 'P:ao:', 'P:다', '고', '는', 'ㄴ', 'ㄹ', 'ㄹ게', 'eu:면']),
    '두': ('v', ['ao:', 'P:ao:', '고', 'ㄴ', 'ㄹ게', 'eu:면']),
}

# 어간에 붙이는 어미. 'ao:X' = 아/어 꼴 + X, 'ㄴ…'·'ㄹ…'·'ㅂ…'·'ㅁ' = 받침으로 붙는 어미, 'eu:X' = 으 계열(면, 니까 ...),
# 'se' = 세요, 'ndan' = 동사의 -ㄴ다/는다, 'ndan:X' = -ㄴ다/는다의 '다' 자리에 X (간다고, 먹는대), 그 밖은 그대로 붙인다.
# 보조 용언의 어미 앞의 'P:'·'S:'·'SP:'·'F:'는 과거·높임·높임 과거·추측 어간에 붙인다는 뜻 (derive).
# 대상: 'va' 동사·형용사, 'v' 동사만, 'a' 형용사만.
BASE_ENDINGS = [
    ('va', ['ao:', 'ao:요', 'ao:서', 'ao:서요', 'ao:도', 'ao:도요', 'ao:야', 'ao:야지', 'ao:야죠', 'ao:야겠다',
            'ao:야겠어', 'ao:야겠어요', 'ao:야겠네', 'ao:야겠네요',
            '고', '고요', '고서', '지', '지요', '죠', '지만', '지도', '네', '네요', '나', '나요', '니', '냐', '냐고',
            '던', '던데', '던데요', '더라', '더라고', '더라고요', '더니', '게', '기', '기가', '기도', '기는', '기를',
            '기에', '기로', '기만', '길래', '잖아', '잖아요', '거든', '거든요', '든지', '든가',
            'eu:면', 'eu:면서', 'eu:며', 'eu:니까', 'eu:니까요', 'eu:니',
            'ㄴ', 'ㄹ', 'ㄹ까', 'ㄹ까요', 'ㄹ걸', 'ㄹ걸요', 'ㄹ지', 'ㄹ지도', 'ㄹ수록', 'ㅂ니다', 'ㅂ니까', 'ㅁ', 'se']),
    ('v', ['ao:라', '는', '는데', '는데요', '는지', '는가', '는구나', '는군요', '자', '자고', '도록', '다가',
           'eu:려고', 'eu:려고요', 'eu:려면', 'eu:러', 'ㄹ게', 'ㄹ게요', 'ㄹ래', 'ㄹ래요', 'ㅂ시다',
           'ndan', 'ndan:다고', 'ndan:다며', 'ndan:다면', 'ndan:다니', 'ndan:대', 'ndan:대요', 'ndan:다더라']),
    ('a', ['구나', '군요', 'ㄴ데', 'ㄴ데요', 'ㄴ지', 'ㄴ가', 'ㄴ가요', '다', '다고', '다며', '다면', '다니', '대', '대요']),
]
# 높임(-시-) 어간(가시, 먹으시, 사시)에 붙이는 어미
SI_ENDINGS = [
    ('va', ['ao:', 'ao:서', 'ao:도', 'ao:야', 'ao:요', '고', '고요', '지', '지요', '죠', '지만', '네', '네요', '나요', '니',
            '던', '더라고요', '잖아요', '거든요', '기', '게', 'eu:면', 'eu:면서', 'eu:며', 'eu:니까',
            'ㄴ', 'ㄹ', 'ㄹ까요', 'ㄹ지', 'ㅂ니다', 'ㅂ니까']),
    ('v', ['는', '는데', '는데요', '는군요', 'eu:려고', 'ㄹ래요', 'ㅂ시다', 'ndan', 'ndan:다고']),
    ('a', ['군요', 'ㄴ데', 'ㄴ데요', 'ㄴ가요', '다', '다고']),
]
# 과거(갔, 먹었, 셨)·추측(가겠) 어간에 붙이는 어미 (어간 끝이 ㅆ이라 늘 '어'가 붙고, 으가 들어간다)
PAST_ENDINGS = ['ao:', 'ao:요', 'ao:도', 'ao:야', 'ao:야지', '고', '고요', '지', '지요', '죠', '지만', '네', '네요', '나',
                '나요', '니', '냐', '던', '던데', '더라', '더라고', '더라고요', '더니', '다', '다고', '다며', '다면', '다니', '대',
                '대요', '잖아', '잖아요', '거든', '거든요', '구나', '군요', '는데', '는데요', '는지', '는가', '기', '기에',
                '길래', 'eu:면', 'eu:면서', 'eu:니까', 'eu:니', 'eu:며', 'ㄹ까', 'ㄹ까요', 'ㄹ지', 'ㄹ지도', 'ㄹ걸', 'ㄹ걸요',
                'ㅁ', 'ㅂ니다', 'ㅂ니까']
FUT_ENDINGS = ['ao:', 'ao:요', '다', '다고', '네', '네요', '지', '지요', '죠', '지만', '고', '는데', '는데요', '구나', '군요',
               '냐', '니', '나', '나요', '더라', '거든', 'ㅁ', 'ㅂ니다', 'ㅂ니까']
# 띄어 써야 하는 말이 붙은 꼴은 만들지 않는다: 할 거야, 할 텐데, 할 수 있어, 할까 봐, 하고 싶어 (위 목록에 없다)


# 아/어 꼴이 '애'로 줄어드는 어간 (그러 + 어 → 그래, 어쩌 + 어 → 어째)
AE_STEMS = {'그러': '그래', '이러': '이래', '저러': '저래', '어쩌': '어째'}


def ao_base(stem, flag):
    if flag == 'X':   # 과거·추측 어간: 갔+어, 먹었+어, 가겠+어
        return stem + '어'
    if stem in AE_STEMS:
        return AE_STEMS[stem]
    return g.ao_base(stem, flag)


def classify(stem, flag):
    if flag == 'X':
        return stem, True, False
    return g.classify(stem, flag)


def conj(stem, flag, pos, ending):
    """어간 + 어미 하나. 만들 수 없거나 만들면 안 되는 꼴이면 None. flag 'X' = 과거·추측 어간 (갔, 먹었, 가겠)."""
    vowel_ending = ending.startswith(('ao:', 'eu:', 'se')) or ending == 'ㅂ시다' or (
        ending[0] in 'ㄴㄹㅁ' and g.final_of(stem) not in ('', 'ㄹ'))
    if stem in DEFECTIVE and flag != 'X' and vowel_ending:
        return None
    if ending.startswith('ao:'):
        return ao_base(stem, flag) + ending[3:]
    alt, needs_eu, l_stem = classify(stem, flag)
    if ending == 'se':
        return None if flag == 'X' else g.conjugate(stem, flag, 'se', '세요', pos)
    if ending.startswith('eu:'):
        tail = ending[3:]
        if l_stem:   # ㄹ은 ㄴ 앞에서 떨어진다: 사니까, 살면, 살려고, 살러, 살며
            return (drop_l(stem) if tail[0] == '니' else stem) + tail
        return alt + ('으' if needs_eu else '') + tail
    if ending == 'ndan' or ending.startswith('ndan:'):
        if pos != 'v' or flag == 'X':
            return None
        base = g.conjugate(stem, flag, 'ndan', '', pos)   # 간다, 먹는다, 산다
        return base[:-1] + ending[5:] if ending != 'ndan' else base
    if ending == 'ㅁ':
        if flag == 'h':   # 그렇 + ㅁ은 '그럼'(다른 말)과 같아져 만들지 않는다
            return None
        if l_stem:   # 살 → 삶, 만들 → 만듦
            l, v, _ = g.dec(stem[-1])
            return stem[:-1] + g.com(l, v, g.JONG.index('ㄻ'))
        return (alt + '음') if needs_eu else g.with_final(alt, 'ㅁ')
    if ending == 'ㅂ시다':
        if l_stem:
            return g.with_final(drop_l(stem), 'ㅂ') + '시다'
        return (alt + '읍시다') if needs_eu else g.with_final(alt, 'ㅂ') + '시다'
    if ending[0] in 'ㄴㄹㅂ':
        if flag == 'X':   # 갔을까, 갔습니다 (갔은 X)
            if ending[0] == 'ㄴ':
                return None
            return stem + ('습' if ending[0] == 'ㅂ' else '을') + ending[1:]
        return g.conjugate(stem, flag, 'jong', ending, pos)
    # 그대로 붙이는 어미: ㄹ 어간은 ㄴ으로 시작하는 어미 앞에서 ㄹ이 떨어진다 (사네, 사는, 사니, 사냐, 사나요)
    if l_stem and g.dec(ending[0])[0] == g.CHO.index('ㄴ'):
        return drop_l(stem) + ending
    return stem + ending


def derive(stem, flag, pos, spec):
    """spec 앞의 'P:'·'SP:'·'S:'·'F:'에 따라 과거·높임 과거·높임·추측 어간에 어미를 붙인다."""
    if spec.startswith('P:'):
        return conj(past_stem(stem, flag), 'X', pos, spec[2:])
    if spec.startswith('SP:'):
        return conj(si_stem(stem, flag)[:-1] + '셨', 'X', pos, spec[3:])
    if spec.startswith('S:'):
        return conj(si_stem(stem, flag), None, pos, spec[2:])
    if spec.startswith('F:'):
        return conj(stem + '겠', 'X', pos, spec[2:])
    return conj(stem, flag, pos, spec)


def drop_l(stem):
    return g.drop_final(stem)


def si_stem(stem, flag):
    """높임 어간: 가 → 가시, 먹 → 먹으시, 살 → 사시, 돕 → 도우시, 듣 → 들으시, 그렇 → 그러시."""
    alt, needs_eu, l_stem = classify(stem, flag)
    if l_stem:
        return drop_l(stem) + '시'
    return alt + ('으시' if needs_eu else '시')


def past_stem(stem, flag):
    return g.with_final(ao_base(stem, flag), 'ㅆ')


# 있다·없다(재미있다, 맛없다)는 형용사지만 관형형·연결형이 동사처럼 '는'이다 (있는 O, 있은 X, 재밌는데 O, 재밌은데 X)
EXIST_SWAP = {'ㄴ': '는', 'ㄴ데': '는데', 'ㄴ데요': '는데요', 'ㄴ지': '는지', 'ㄴ가': '는가', 'ㄴ가요': '나요'}


def is_exist(stem):
    return stem.endswith(('있', '없', '밌'))


def endings_for(table, pos, stem=''):
    out = []
    for target, es in table:
        if target == 'va' or target == pos:
            out += es
    if pos == 'a' and is_exist(stem):
        out = [EXIST_SWAP.get(e, e) for e in out] + ['는구나', '는군요']
    return out


def forms_of(stem, flag, pos):
    """{꼴: 어미 이름} (어미 이름은 어미 몫을 잴 때 쓴다)."""
    out = {}

    def put(word, key):
        if word and all(g.is_hangul(c) for c in word) and 2 <= len(word) <= 12:   # 한 글자 말은 사전에 넣지 않는다
            out.setdefault(word, key)

    def run(s, f, p, endings, prefix):
        for e in endings:
            try:
                put(conj(s, f, p, e), prefix + e)
            except (AssertionError, KeyError, ValueError, IndexError):
                pass

    run(stem, flag, pos, endings_for(BASE_ENDINGS, pos, stem), '')
    if stem in DEFECTIVE:
        return out   # 준말 어간은 높임·과거도 모음 어미 꼴(갖으시, 갖았)이 되어 만들지 않는다
    try:
        si = si_stem(stem, flag)
        past = past_stem(stem, flag)
    except (AssertionError, KeyError, ValueError):
        return out
    run(si, None, pos, endings_for(SI_ENDINGS, pos), 'si+')   # 있으시 + ㄴ = 있으신 (높임 어간은 규칙 활용)
    run(past, 'X', pos, PAST_ENDINGS, 'past+')
    run(si[:-1] + '셨', 'X', pos, PAST_ENDINGS, 'sipast+')
    run(si[:-1] + '셨겠', 'X', pos, FUT_ENDINGS, 'sipastfut+')
    run(stem + '겠', 'X', pos, FUT_ENDINGS, 'fut+')
    run(past + '겠', 'X', pos, FUT_ENDINGS, 'pastfut+')
    ao = ao_base(stem, flag)
    for aux, (target, es) in AUX.items():
        if target == 'v' and pos != 'v' or aux in '가오' and stem[-1] in '가오':   # 가가, 가와 (가다 + 가다)
            continue
        for e in es:
            try:
                w = derive(aux, None, 'v', e)
            except (AssertionError, KeyError, ValueError, IndexError):
                continue
            put(ao + w if w else None, 'aux' + aux + '+' + e)
    return out


# ---------------------------------------------------------------------------------------------
# 자체 검사
# ---------------------------------------------------------------------------------------------
EXPECTED = {
    ('좋', None, 'a'): ['좋으시네요', '좋으세요', '좋았어요', '좋다고', '좋구나', '좋은데요', '좋은가요', '좋겠네요', '좋아졌어',
                        '좋음', '좋습니다', '좋았겠다', '좋으셨어요', '좋을까요', '좋아지는'],
    ('그러', None, 'v'): ['그럽시다', '그러세요', '그러시죠', '그랬어요', '그러자', '그런다고'],
    ('드리우', None, 'v'): ['드리워진', '드리워져', '드리웠다'],
    ('반갑', 'b', 'a'): ['반가워요', '반가웠어요', '반갑습니다', '반가우셨어요', '반가운'],
    ('들어오', None, 'v'): ['들어오세요', '들어왔어요', '들어올게요', '들어오는', '들어온다고'],
    ('사용하', None, 'v'): ['사용하세요', '사용해주세요', '사용하셨어요', '사용합니다', '사용해봤는데', '사용했대'],
    ('되', None, 'v'): ['되네요', '되나요', '돼요', '됐어요', '된대요', '될까요', '돼버렸어'],
    ('가', None, 'v'): ['갑니다', '갑시다', '가십시다', '가셨겠네', '가겠습니다', '갔을까', '간다고', '가는구나', '가려고요'],
    ('먹', None, 'v'): ['먹읍시다', '먹으셨어요', '먹었겠다', '먹는다고', '먹는대', '먹음', '먹어봐야', '먹어버렸어', '먹으러'],
    ('살', None, 'v'): ['삽니다', '삽시다', '사세요', '사시는', '사니까', '살면서', '사네요', '사나요', '산대', '살려고'],
    ('알', None, 'v'): ['알아줄까', '알아주세요', '알아봤어요', '압니다', '아세요'],
    ('늙', None, 'v'): ['늙어가는', '늙었네'],
    ('실망스럽', 'b', 'a'): ['실망스러워', '실망스러웠어요', '실망스럽네요'],
    ('듣', 'd', 'v'): ['들으셨어요', '들어봐', '들을게요', '듣는다고', '들읍시다', '들음'],
    ('모르', 'r', 'v'): ['몰랐어요', '모르시나요', '모릅니다', '몰라요'],
    ('낫', 's', 'v'): ['나았어요', '나으세요', '나을까요', '낫네요'],
    ('있', None, 'a'): ['있는', '있는데', '있는데요', '있는지', '있나요', '있었어요', '있습니다', '있으면', '있으세요',
                        '있으셨어요', '있으시면', '있으신데요', '있으신가요'],
    ('재밌', None, 'a'): ['재밌는', '재밌는데', '재밌었어요', '재밌네요'],
    ('그렇', 'h', 'a'): ['그래요', '그랬구나', '그럴까요', '그러시군요', '그렇네요', '그렇습니다', '그런가요'],
    ('예쁘', None, 'a'): ['예뻐졌어', '예쁘시네요', '예뻤어요', '예쁜데요'],
    ('하', None, 'v'): ['해드릴게요', '해주셔서', '하셨어요', '했겠네', '한다고', '합시다'],
    ('만들', None, 'v'): ['만듦', '만드세요', '만들어줄게', '만듭니다'],
}
NOT_EXPECTED = {
    ('갖', None, 'v'): ['갖아', '갖아요', '갖으세요', '갖았어', '갖으면'],
    ('좋', None, 'a'): ['좋는', '좋는다', '좋자', '좋으러', '좋을게', '좋습시다', '좋으시세요'],
    ('먹', None, 'v'): ['먹다고', '먹대', '먹구나', '먹습시다', '먹었은', '먹었는다', '먹을거야', '먹을까봐'],
    ('살', None, 'v'): ['살는', '살으니까', '살네', '살ㅂ니다'],
    ('되', None, 'v'): ['되니요', '되요', '되서'],
    ('있', None, 'a'): ['있은', '있은데', '있으시는데요'],
    ('없', None, 'a'): ['없은', '없은지'],
}


def selftest():
    if not g.selftest():
        return False
    wrong = 0
    for (stem, flag, pos), words in EXPECTED.items():
        got = forms_of(stem, flag, pos)
        for w in words:
            if w not in got:
                wrong += 1
                print(f'빠짐: {stem} → {w}')
    for (stem, flag, pos), words in NOT_EXPECTED.items():
        got = forms_of(stem, flag, pos)
        for w in words:
            if w in got:
                wrong += 1
                print(f'틀린 꼴: {stem} → {w} ({got[w]})')
    print(f'활용형 자체 검사: 틀림 {wrong}')
    return wrong == 0


# ---------------------------------------------------------------------------------------------

def read_stems(paths, kiwi_tsv, min_freq):
    """[(어간, 표시, 품사)]. 같은 어간은 먼저 읽은 것(손으로 고른 목록)을 쓴다."""
    seen, out = set(), []
    for p in paths:
        with open(p, encoding='utf-8') as f:
            for raw in f:
                line = raw.strip()
                if not line or line.startswith('#'):
                    continue
                kind, _, tokens = line.partition(':')
                pos = 'v' if kind.strip().startswith('v') else 'a'
                for t in tokens.split():
                    stem, flag = g.parse_stem(t)
                    if stem not in seen and stem not in NOT_STEMS:
                        seen.add(stem)
                        out.append((stem, flag, pos))
    if kiwi_tsv:
        with open(kiwi_tsv, encoding='utf-8') as f:
            for line in f:
                p = line.rstrip('\n').split('\t')
                if len(p) < 4:
                    continue
                stem, pos, flag, freq = p[0], p[1], p[2] or None, float(p[3])
                if freq < min_freq or stem in seen or stem in NOT_STEMS or not all(g.is_hangul(c) for c in stem):
                    continue
                seen.add(stem)
                out.append((stem, flag, pos))
    return out


def load_freq(counts, mixes):
    with open(counts, 'rb') as f:
        pm = dict(pickle.load(f)[0])
    for spec in mixes:
        path, _, w = spec.rpartition(':')
        w = float(w)
        with open(path, 'rb') as f:
            other = pickle.load(f)[0]
        for k in pm:
            pm[k] *= 1 - w
        for k, v in other.items():
            pm[k] = pm.get(k, 0.0) + w * v
    return pm


def kiwi_filter(words, stems_of):
    """Kiwi로 분석해 어간이 같게 나뉘는 꼴만 남긴다. 기호·외국어·관형사 등으로 풀리는 꼴과, 연결 어미 '니' 뒤에 '요'가 붙은
    꼴(되니요)은 버린다. Kiwi는 틀린 꼴도 너그럽게 분석하므로(갖아 → 갖 + 어) 활용이 맞는지는 생성 규칙이 책임지고,
    여기서는 어간을 잘못 나눈 꼴만 거른다."""
    from kiwipiepy import Kiwi
    kiwi = Kiwi(num_workers=os.cpu_count() or 1)
    lexical = {'VV', 'VA', 'XSV', 'XSA', 'NNG', 'NNP', 'XR', 'MAG', 'XPN'}
    keep = set()
    words = sorted(words)
    for w, res in zip(words, kiwi.analyze(words, top_n=1)):
        toks = res[0][0]
        tags = [t.tag.split('-')[0] for t in toks]
        first_e = next((i for i, t in enumerate(tags) if t.startswith('E')), len(tags))
        ok = all(t in lexical or t == 'VX' for t in tags[:first_e])
        for i in range(first_e, len(tags)):
            t, prev = tags[i], tags[i - 1]
            if t.startswith('E') or t == 'VX' or t in lexical:
                continue
            if t == 'NNB' and prev == 'ETM' or t.startswith('J') and (prev.startswith('E') or prev == 'NNB'):
                if toks[i].form == '요' and toks[i - 1].form == '니':
                    ok = False
                continue
            ok = False
        if not ok:
            continue
        lex = ''.join(t.form for t in toks[:first_e] if t.tag.split('-')[0] in lexical)
        main = ''.join(t.form for t in toks[:first_e])
        if stems_of[w] & {lex, main} or any(main.startswith(s) or lex.startswith(s) for s in stems_of[w]):
            keep.add(w)
    return keep


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('out', nargs='?')
    ap.add_argument('--selftest', action='store_true')
    ap.add_argument('--counts')
    ap.add_argument('--mix', action='append', default=[])
    ap.add_argument('--stems', action='append', default=[])
    ap.add_argument('--kiwi-stems')
    ap.add_argument('--min-stem-freq', type=float, default=0.3)
    ap.add_argument('--no-kiwi', action='store_true')
    a = ap.parse_args()
    if not selftest():
        sys.exit('자체 검사에 실패해서 만들지 않는다.')
    if a.selftest:
        return
    pm = load_freq(a.counts, a.mix)
    stems = read_stems(a.stems, a.kiwi_stems, a.min_stem_freq)
    print(f'어간 {len(stems):,}개')
    per_stem = []
    share_num, mass_total = defaultdict(float), 0.0
    dropped = 0
    for stem, flag, pos in stems:
        fs = forms_of(stem, flag, pos)
        seen = {w: pm[w] for w in fs if pm.get(w, 0) > 0}
        if len(seen) < MIN_SEEN:
            dropped += 1
            continue
        aux_seen = defaultdict(int)
        for w in seen:
            if fs[w].startswith('aux'):
                aux_seen[fs[w].split('+')[0]] += 1
        fs = {w: k for w, k in fs.items() if not k.startswith('aux') or aux_seen[k.split('+')[0]] >= MIN_AUX_SEEN}
        mass = sum(f for w, f in seen.items() if w in fs)
        mass_total += mass
        for w, f in seen.items():
            if w in fs:
                share_num[fs[w]] += f
        per_stem.append((stem, fs, mass))
    print(f'말뭉치에 꼴이 {MIN_SEEN}개 미만이라 뺀 어간 {dropped:,}개, 쓴 어간 {len(per_stem):,}개')
    share = {k: v / mass_total for k, v in share_num.items()}
    est, stems_of = {}, defaultdict(set)
    for stem, fs, mass in per_stem:
        for w, key in fs.items():
            # 말뭉치에서 한 번도 안 본 어미는 가장 드문 어미의 몫으로 본다 (0이면 사전에 들 수 없다)
            s = mass * share.get(key, 1e-7) * SCALE
            if s > est.get(w, 0):
                est[w] = s
            stems_of[w].add(stem)
    print(f'만든 꼴 {len(est):,}개')
    if not a.no_kiwi:
        keep = kiwi_filter(list(est), stems_of)
        print(f'Kiwi로 거른 꼴 {len(est) - len(keep):,}개')
        est = {w: s for w, s in est.items() if w in keep}
    by = defaultdict(list)
    for w, s in est.items():
        by[round(s, 3)].append(w)
    with open(a.out, 'w', encoding='utf-8') as f:
        f.write('# gen_ko_pack_forms.py가 만든 활용형 (점수 = 어림한 100만 어절당 빈도)\n')
        for s in sorted(by, reverse=True):
            f.write(f'{s}: ' + ' '.join(sorted(by[s])) + '\n')
    print(f'활용형 {len(est):,}개 → {a.out}')


if __name__ == '__main__':
    main()
