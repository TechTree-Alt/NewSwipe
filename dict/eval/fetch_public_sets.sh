#!/bin/bash
# 평가용 공개 말뭉치를 받아 dev(계수 맞추기)·test(마지막 확인)로 나눈다. 받은 원문은 저장소에 넣지 않는다 (.gitignore).
#   dict/eval/fetch_public_sets.sh [OUT_DIR]     (기본 dict/eval/data)
# 만드는 파일 (한 줄에 한 문장):
#   chat_q_dev.txt / chat_q_test.txt  Chatbot_data(MIT, github.com/songys/Chatbot_data)의 질문. 사람이 쓴 짧은 대화체
#                                     (연애 상담이 많아 주제가 치우쳐 있다). 같은 질문은 하나만, 번갈아 dev/test로 나눈다.
#   nsmc_dev.txt / nsmc_test.txt     NSMC(CC0, github.com/e9t/nsmc)의 ratings_test.txt에서 세 어절 이상인 리뷰 3,000개씩.
#                                     사전을 만들 때 섞는 것은 ratings_train.txt라 겹치지 않는다. 맞춤법을 틀린 글이 많아
#                                     '잘못 고침'에 맞게 고친 것(봣는데 → 봤는데)도 섞여 있다.
#   smile_<말투>.txt                  SmileStyle(Smilegate AI, CC BY-NC 4.0, github.com/smilegate-ai/korean_smile_style_dataset):
#                                     같은 대화를 말투 17가지(formal, informal, chat, enfp, naruto ...)로 다시 쓴 것. 말투마다 한
#                                     사람처럼 어미가 한결같아, 학습(--learn)이 개인화에 얼마나 도움이 되는지 재기에 알맞다
#                                     (위 두 말뭉치는 여러 사람의 글이라 내 말버릇의 효과를 작게 잰다). 비상업 라이선스라 평가에만 쓴다.
# 평가: dict/eval/run_eval.sh app/src/main/assets dict/eval/data/chat_q_dev.txt
#       dict/eval/run_eval.sh app/src/main/assets dict/eval/data/smile_naruto.txt --learn --no-typo
set -e
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
OUT=${1:-$ROOT/dict/eval/data}
mkdir -p "$OUT/raw"
curl -sSfL -o "$OUT/raw/ChatbotData.csv" https://raw.githubusercontent.com/songys/Chatbot_data/master/ChatbotData.csv
curl -sSfL -o "$OUT/raw/ratings_test.txt" https://raw.githubusercontent.com/e9t/nsmc/master/ratings_test.txt
curl -sSfL -o "$OUT/raw/smilestyle_dataset.tsv" \
    https://raw.githubusercontent.com/smilegate-ai/korean_smile_style_dataset/main/smilestyle_dataset.tsv
python3 -I - "$OUT" <<'PY'
import csv, random, sys
out = sys.argv[1]
qs, seen = [], set()
with open(f'{out}/raw/ChatbotData.csv', encoding='utf-8') as f:
    for r in csv.DictReader(f):
        q = r['Q'].strip()
        if q and q not in seen:
            seen.add(q)
            qs.append(q)
reviews = []
with open(f'{out}/raw/ratings_test.txt', encoding='utf-8') as f:
    next(f)
    for line in f:
        p = line.rstrip('\n').split('\t')
        if len(p) >= 2 and len(p[1].split()) >= 3:
            reviews.append(p[1].strip())
random.Random(1).shuffle(reviews)
def write(name, rows):
    with open(f'{out}/{name}.txt', 'w', encoding='utf-8') as f:
        f.write('\n'.join(rows) + '\n')
write('chat_q_dev', qs[0::2])
write('chat_q_test', qs[1::2])
write('nsmc_dev', reviews[:3000])
write('nsmc_test', reviews[3000:6000])
with open(f'{out}/raw/smilestyle_dataset.tsv', encoding='utf-8') as f:
    rows = list(csv.reader(f, delimiter='\t'))
for c, name in enumerate(rows[0]):
    write('smile_' + name, [r[c].strip() for r in rows[1:] if c < len(r) and r[c].strip()])
print(f'질문 {len(qs)}개 (dev {len(qs[0::2])}, test {len(qs[1::2])}), 리뷰 dev 3000 / test 3000, 말투 {len(rows[0])}가지 → {out}')
PY
