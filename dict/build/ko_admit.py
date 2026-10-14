"""사람이 쓴 글(NSMC 리뷰 등)을 섞을 때 기본 말뭉치에 없던 단어를 사전에 들일지 가린다 (build_ko_model.py --mix-admit).

리뷰에는 메신저와 같은 대화체 꼴(재밌음, 왜케, 못하겠네, 알겠는데)과 띄어 쓰지 않은 어절(가고싶다, 어릴때)이 많지만,
리뷰에만 쓰는 말(배우 이름, 영화 제목, 점준다)과 맞춤법이 틀린 말(봣는데, 재밋다, 쓰래기)도 많다. 틀린 말이 사전에 들어가면
자동 수정이 그 말을 고치지 못한다. 그래서 기본 말뭉치(Leipzig)가 아는 조각으로 풀리는 말만 들인다.

  - 기본 말뭉치의 단어 + 조사·의존 명사 (이런걸, 어릴때, 보는게)
  - 기본 말뭉치의 단어 + 기본 말뭉치의 흔한 단어 (띄어 쓰지 않은 두 어절: 가고싶다, 지금봐도)
  - 말뭉치로 확인한 용언 어간(gen_ko_forms.find_stems) + 어미 (재밌음, 못하겠네, 짜증난다, 안됨)
고유명사(송윤아 → 송 + 윤아?)와 틀린 말(봣 + 는데)은 앞 조각이 기본 말뭉치에 없어 걸러진다.
"""
import gen_ko_conjugations as g
import gen_ko_forms as gf

PARTICLES = set('''이 가 을 를 은 는 에 의 도 만 로 으로 에서 과 와 랑 이랑 한테 까지 부터 에게 처럼 보다 하고 에는 에도
    으로는 로는 에서는 이나 나 이라도 라도 밖에 뿐 이든 든 이야 야 이에요 예요 인데 이지 지 이고 고 이면 면 이라서 라서
    이니까 니까 이라고 라고 일까 인가 이잖아 잖아 이었어 였어 이었다 였다 이네 네 이죠 죠 입니다 이라는 라는 이란 란
    만큼 대로 마다 조차 마저 께 께서 한테서 에게서 처럼은 임 인듯 인거 인게'''.split())
BOUND = set('거 게 걸 건 거야 거지 거같아 듯 때 줄 수 지 데 만큼 적 척 뻔 뿐 때문 줄은 게임'.split()) - {'게임'}
ENDINGS = {t for _, t, _, _ in gf.ENDINGS if t} | set('''음 고 지 게 네 니 냐 다 대 래 자 죠 지만 기 던 더라 든 어 아
    어요 아요 어서 아서 어도 아도 어야 아야 면 으면 은 을 는 는데 은데 을까 을게 을래 잖아 거든 구나 군 겠다 겠어 겠지
    겠네 네요 지요 냐고 자고 다고 라고 더니 구 당 넹 음요'''.split())
# 흔한 맞춤법 실수 (사전에 들어가면 자동 수정이 고치지 못한다)
MISSPELLED = ('됬', '되요', '되서', '할께', '갈께', '줄께', '께요', '몇일', '어떻해', '웬지', '안되요')


class Admit:
    def __init__(self, base_freq, one_char_ok):
        self.f = base_freq
        self.one = one_char_ok
        self.stems = {s for s, _, _, _ in gf.find_stems(base_freq, 20000, 1.0)}

    def known(self, w, min_freq=1.0):
        if len(w) == 1 and w not in self.one:
            return False
        return self.f.get(w, 0.0) >= min_freq

    def __call__(self, w):
        if len(w) < 2 or any(m in w for m in MISSPELLED):
            return False
        for k in range(1, len(w)):
            a, b = w[:k], w[k:]
            if self.known(a) and (b in PARTICLES or b in BOUND or self.known(b, 20.0)):
                return True
            if a in self.stems and b in ENDINGS:
                return True
        # 어간 끝 글자에 받침으로 붙은 어미 (안됨 = 안되 + ㅁ, 간대 = 가 + ㄴ대, 할걸 = 하 + ㄹ걸)
        for k in range(1, len(w) + 1):
            c = w[k - 1]
            if not g.is_hangul(c):
                return False
            jong = g.final_of(w[:k])
            if jong in ('ㄴ', 'ㄹ', 'ㅁ', 'ㅂ'):
                stem = w[:k - 1] + g.drop_final(c)
                rest = w[k:]
                if stem in self.stems and (rest == '' or rest in ENDINGS or rest in BOUND):
                    return True
        return False
