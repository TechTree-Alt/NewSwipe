# NewSwipe 사전

단어 추천·자동 수정에 쓰는 사전을 만들고, 평가하고, 사전 팩으로 묶는 데 필요한 것을 모아 둔 폴더입니다.
앱 빌드에는 쓰이지 않습니다. 앱에 들어가는 결과물은 `app/src/main/assets/dict_*.bin`뿐입니다.

| 폴더 | 내용 |
|---|---|
| `build/` | 사전을 만드는 도구. `build_ko_model.py`(한국어 단어 사전 + 다음 단어 사전), `build_ko_dict.py`(걸러 내기·저장 함수, 차단 목록), `build_en_dict.py`(영어), `gen_ko_conjugations.py`(용언 활용형 생성), `gen_ko_pack_forms.py`(표준 사전 팩에 넣는 넓은 활용형: 높임·과거·보조 용언 꼴, Kiwi로 검사), `count_ko_stems.py`·`select_ko_stems.py`(형태소 분석기 Kiwi로 말뭉치의 용언 어간 세기·고르기), `make_dict_pack.py`(사전 팩 zip 만들기) |
| `lists/` | 직접 고른 목록. 구어체 단어(`ko_colloquial.txt`, `en_colloquial.txt`), 용언 어간(`ko_stems.txt`)과 그것으로 만든 활용형(`ko_conjugated.txt`), 말뭉치에서 센 어간(`ko_stems_kiwi.txt`, 만든 목록), 구어체 문장(`ko_phrases.txt`), 말뭉치를 섞을 때 뺄 주제어(`ko_mix_exclude_nsmc.txt`) |
| `eval/` | 평가 도구. 앱의 추천 코드로 한 키씩 쳐서 키 절약률·잘못 고침·오타 고침을 잰다 (`run_eval.sh`, `SuggestEval.java`), 두 설정을 같은 문장끼리 견준다 (`compare_ksr.py`), 공개 평가 말뭉치를 받아 dev·test로 나눈다 (`fetch_public_sets.sh`) |
| `packs/` | 설정에서 가져오는 사전 팩. `ko-standard.zip`(표준 사전: 단어 8만 개 + 단어 쌍 40만 개, 약 1.1MB)과 그 라이선스, 폴더별 라이선스 안내(`README.md`) |

## 만들기

말뭉치는 저장소에 넣지 않습니다. 받는 곳과 만드는 순서는 `build/build_ko_model.py` 맨 위의 설명과
[BUILDING.md](../BUILDING.md)의 '한국어 사전과 사전 팩'에 있습니다.

## 평가

```bash
dict/eval/run_eval.sh app/src/main/assets dict/eval/ko_chat_test.txt          # 앱에 든 기본 사전
dict/eval/run_eval.sh 만든_폴더 평가_문장.txt --per-sentence a.tsv            # 문장별 결과
python3 dict/eval/compare_ksr.py a.tsv b.tsv                                   # 두 설정의 차이와 95% 구간
```

`ko_chat_test.txt`는 직접 쓴 메신저 문장(약 600어절)이라 실제 사람이 쓴 글보다 표준적이고, 작아서 차이가 1%p 안쪽이면 가려내지
못합니다. 사람이 쓴 공개 말뭉치를 받아 dev(계수 맞추기)와 test(마지막 확인)로 나눠 함께 재 주세요. **계수는 dev로만 맞춥니다.**

```bash
dict/eval/fetch_public_sets.sh                                                  # dict/eval/data/에 받는다 (저장소에 넣지 않는다)
dict/eval/run_eval.sh app/src/main/assets dict/eval/data/chat_q_dev.txt         # Chatbot_data 질문 (대화체)
dict/eval/run_eval.sh app/src/main/assets dict/eval/data/nsmc_dev.txt           # NSMC 리뷰 (맞춤법을 틀린 글이 많다)
dict/eval/run_eval.sh app/src/main/assets dict/eval/data/chat_q_dev.txt --layout 7   # NewSwipe 단모음 자판의 오타
```

찍는 것: 키 절약률(세 칸을 다 보는 사용자, 첫 칸만 보는 사용자), 단어 종류(사전 단어 / 사전 단어 + 조사 / 사전에 없는 말)별 비율과
키 절약률, 다음 단어 예측이 보인 비율과 맞힌 비율, 예측을 늘 보였다면 맞힌 비율과 점수 상위 n%만 보였을 때의 적중률(예측 문턱을
맞출 때 본다), 마지막 글자만 남기고 쳤을 때 맞는 조사·어미가 보인 비율(명사 + 조사 / 용언 활용 / 그 밖), 바르게 친 말을 잘못 고친 비율, 추천 시간(평균·중앙값·99%·최대), 일부러 넣은 오타를
고친 비율(오타 종류별)과 고치지 못했어도 다 친 뒤 추천에 원래 단어가 보인 비율.

두 사전의 자동 수정을 견줄 때는 `--typo-ref 기준_사전_폴더`를 붙여 같은 단어에 같은 오타를 넣습니다 (사전이 크면 오타가
사전 단어가 되어 고칠 수 없게 되는 것도 그대로 센다).

### 자동 수정의 판단 (KoreanCorrect.SHAPE_RULE)

사전에 없는 말을 고칠지는 고칠 단어의 빈도, 오타 비용(같은 키의 다른 동작이 가장 싸다), 그리고 친 말과 고칠 말이 한국어 낱말처럼
생겼는지(사전 단어로 배운 음절 바이그램 모형, `SyllableModel`)를 함께 보고 정합니다. 오타는 낯선 음절 이음을 만들기 쉽고(자댕입니다,
사옹하세요), 사전에 없지만 맞는 말은 흔한 이음입니다(혼수, 통학, 미역). 예전에는 고칠 단어의 최소 빈도 하나(200)로 정했습니다.
계수는 오타 사례와 사전에 없는 말을 문턱 없이 모두 뽑아 둔 뒤 dev(Chatbot_data·SmileStyle formal·NSMC)에서 골랐습니다.

| (test: Chatbot_data / NSMC) | 오타 고침 | 잘못 고침 (전체 단어의) |
|---|---|---|
| 기본 사전, 최소 빈도 200 | 24.1% / 25.7% | 0.20% / 0.57% |
| 기본 사전, SHAPE_RULE | 32.4% / 34.1% | 0.17% / 0.53% |
| 표준 사전 팩, 최소 빈도 100 | 37.0% / 35.9% | 0.40% / 0.78% |
| 표준 사전 팩, SHAPE_RULE | 35.7% / 38.7% | 0.16% / 0.58% |

**맞는 말 목록 (`dict_ko_known.bin`, `build_known_words.py`)**: 사전에 없는 맞는 말을 잘못 고치는 일(공황 → 공항, 배탈이 →
배달이, 명언 → 명은)은 대부분 말뭉치에는 있지만 빈도 상위에 들지 못한 단어였습니다. Leipzig 말뭉치에 100만 어절당 0.1번 이상 나온
말을 블룸 필터(단어 하나에 12비트, 0.3%를 잘못 알아본다)로 담아 그런 말은 고치지 않고, 그만큼 기준을 3000에서 1200으로 낮춰 흔한
말의 오타(자덩입니다 → 자동입니다)를 더 고칩니다. 리뷰(NSMC)는 맞춤법이 틀린 말이 많아 넣지 않습니다. 기본 사전용 640KB, 팩용 530KB.
1%일 때는 오타(이야기하보자)가 우연히 목록에 걸려 고치지 않는 일이 있었습니다. 말뭉치에 나오지만 대화에서는 거의 쓰지 않아 흔한 말의
오타로 보는 편이 나은 말(배일 → 매일)은 `lists/ko_known_exclude.txt`에 적어 목록에서 뺍니다.

**후보가 사실상 하나일 때 (`SHAPE_MIN_UNIQUE`)**: 키 하나 오타(대각선 제외)로 고치는 단어가 대각선 키 없이 설명되는 다른 모든
후보보다 뚜렷이(`UNIQUE_MARGIN`) 앞서면 기준(500)보다 드문 단어로도 고칩니다. 기준을 낮출수록 오타를 많이 고치지만, 뉴스 말뭉치에
드문 구어(남자애 → 남자와, 선톡 → 선택, 예민함 → 예민한)를 망가뜨리는 일이 더 빨리 늘어 300에 둡니다 (Chatbot_data·NSMC dev, 기본 사전):

| 기준 | 바로 옆·위아래 키 오타 고침 | 잘못 고침 chat / smile / nsmc |
|---|---|---|
| 쓰지 않음 | 55.9% | 52 / 30 / 154 |
| 300 | 62.0% | 59 / 34 / 165 |
| 200 | 66.1% | 74 / 37 / 184 |
| 20 | 80.2% | 120 / 59 / 256 |
| 1 | 84.7% | 129 / 66 / 291 |

그보다 드문 말(굴자 → 글자, 학습니 → 학습이)은 추천란 첫 칸에 보이므로, 같은 오타에서 같은 단어를 세 번 고르면 그다음부터 그 단어로
고칩니다 (`CorrectionMemory`, 기기 안의 `picked_corrections.txt`). 자동 수정을 지우기 키로 되돌리면 잊습니다.

**자판 거리**: 실제로 쓸 때 대각선 키를 잘못 누르는 일은 거의 없으므로, 대각선 키 없이(바로 옆·위·아래 키나 다른 편집으로)
설명되는 고칠 단어가 하나라도 있으면 그중에서만 고르고, 대각선 키를 잘못 눌렀다고 봐야 하는 단어는 그런 단어가 없을 때만
고릅니다(`KoreanCorrect.DIAGONAL_LAST`, `TypoModel.distanceDirect`: 많나 → 많아, 예전에는 대각선 키로 만나). 된소리(ㄲ·ㄸ·ㅃ·ㅆ·ㅉ)는
밀거나 Shift를 눌러 일부러 치므로, 입력한 된소리를 다른 글자로 바꾸는 비용은 보통 바꾸기와 같게 비쌉니다(`TypoModel.TENSE_TYPED`:
다끔 → 가끔, 다음이 아니라). 예사소리를 쳤는데 된소리가 맞는 것(가금 → 가끔)은 그대로 쌉니다. 키 하나를 잘못 누른 오타(오타 비용
0.6 이하)는 빠뜨리기·더 치기보다 분명해 기준을 500으로 둡니다(`SHAPE_MIN_ONE_KEY`).

자판의 이웃 키 오타 하나를 빠짐없이 넣어 재는 도구로 쟀습니다(Chatbot_data 문장의 사전 단어, 8방향, test): 바로 옆·위아래 키
오타를 고친 비율이 기본 사전 39.8% → 51.8%, 표준 사전 팩 38.6% → 50.1%가 되었고, 맞게 친 말을 잘못 고친 것은 Chatbot_data
test에서 31 → 43개, 40 → 48개(팩)로 늘었습니다 (늘어난 것의 절반쯤은 힙듭니다 → 힘듭니다처럼 바른 고침). 남는 것은 대개 다른 흔한 말과 똑같이 그럴듯하거나(감시합니다: 감시하다), 말뭉치에 나와
맞는 말 목록에 든 말입니다(배일: 排日).

명사 + 조사에서 명사만 틀린 말(자댕입니다 → 자동입니다)도 같은 판단으로 명사를 고칩니다. 예전에 이렇게 고치면 사전에 없는 드문
명사(미역하고 → 지역하고)를 망가뜨렸는데, 그런 명사는 낱말답게 생겨 이제는 고치지 않습니다.

`--typo-typing`을 붙이면 오타를 섞어 치는 동안의 추천만 잽니다: 사전 단어의 셋 중 하나에 오타를 하나(여섯 키 이상인 말은 셋 중
하나에 하나 더) 넣고 한 키씩 치면서, 오타 키를 친 뒤 원래 단어가 추천 칸에 보인 비율(치는 중 / 첫 칸 / 다 친 뒤 보이거나 스페이스로
고침), 그때 아낀 키의 비율, 그 추천 시간을 찍습니다.

### 순위 재정렬 다시 학습하기

추천 순위의 마지막 단계는 학습한 재정렬(`KoreanSuggest.RERANK`, 입력 상태마다 특징 가중치)입니다. 특징(`KoreanSuggest.features`)이나
순위 점수를 크게 바꾸면 다시 학습해야 합니다 (그대로 두면 오히려 나빠질 수 있으니 `--no-rerank`와 견주어 보세요).

```bash
mkdir -p /tmp/dev /tmp/test
DUMP=/tmp/dev dict/eval/run_eval.sh app/src/main/assets dict/eval/data/chat_q_dev.txt --learn --no-typo --no-rerank   # dev 말뭉치마다
DUMP=/tmp/test dict/eval/run_eval.sh app/src/main/assets dict/eval/data/chat_q_test.txt --learn --no-typo --no-rerank # 확인용
python3 dict/eval/train_rerank.py --cache /tmp/dev.npz /tmp/dev/*.dump
python3 dict/eval/train_rerank.py --cache /tmp/test.npz /tmp/test/*.dump
python3 dict/eval/train_rerank.py w.txt --dev /tmp/dev.npz --test /tmp/test.npz   # w.txt를 KoreanSuggest.RERANK에 옮겨 적는다
dict/eval/run_eval.sh app/src/main/assets dict/eval/data/chat_q_test.txt --learn --rerank w.txt                      # 실제로 잰다
```

학습 자료는 고르기와 상관없이 단어의 모든 접두에서 쓴다 (고른 뒤의 키를 빼면 '앞에서 못 골랐다'는 조건이 붙어, 켜면 맞지 않는다).
목표는 키마다 확률 q(1, 0.5, 0.25)로 추천란을 보는 사용자들의 키 절약률 평균이다 (늘 보는 사용자만 맞추면 '앞에서 보였을 말을
뒤에서 내리게' 되어, '엄마'까지 친 뒤 '엄마가'가 첫 칸에서 빠지는 식으로 늦게 보는 사용자가 손해를 본다).

학습(`--learn`)의 효과는 `smile_<말투>.txt`로도 재 주세요. Chatbot_data·NSMC는 여러 사람이 쓴 글을 한 사람이 친 것처럼 이어 치므로
내 말버릇(자주 쓰는 어미 등)의 효과를 작게 잽니다: 내가 쓴 어미 배우기는 두 말뭉치에서 +0.1%p였지만, 말투가 한결같은 SmileStyle
12가지에서는 평균 +0.49%p(naruto +1.6%p, halbae +1.3%p)였습니다.

알아 둘 한계: 자동 수정 평가에서 오타는 한 단어에 한 번만 넣고(`--typo-typing`은 가끔 두 번), 띄어쓰기를 빼먹은 글은 재지 않습니다. NSMC의 '잘못 고침'에는
맞춤법을 바로잡은 것(봣는데 → 봤는데)도 섞여 있습니다. 실제로 친 글과 고친 글이 함께 있는 말뭉치(국립국어원 메신저 말뭉치의
original_form·form)가 자동 수정을 재기에 가장 알맞습니다.

### 공개할 수 없는 말뭉치로 평가하기 (국립국어원 메신저 말뭉치 등)

이용 약정상 원문을 복제·배포할 수 없는 말뭉치는 **내 컴퓨터에서만** 평가합니다. 말뭉치는 저장소 밖에 두고, 아래 스크립트가 임시 폴더에
문장을 뽑아 평가한 뒤 지웁니다. 화면에는 집계(키 절약률 등)만 나오므로 그 결과는 옮겨 적어도 됩니다.

```bash
dict/eval/run_private_eval.sh ~/corpora/messenger            # 기본 사전
dict/eval/run_private_eval.sh ~/corpora/messenger dict/packs/ko-standard.zip   # 사전 팩도 함께
```

- 필요한 것: JDK 17 이상(`javac`), Python 3, `unzip`. Windows에서는 WSL이나 Git Bash에서 돌립니다.
- 말뭉치 폴더 안의 `*.json`과 `*.zip` 속 `*.json`을 모두 읽고, 대화(`utterance` 목록)마다 발화의 `form`을 씁니다
  (`FIELD=original_form`으로 바꿀 수 있습니다). 가명 처리 표시(`&name&` 등)는 단어 사이를 끊는 표시로 바꿉니다.
- 대화 단위로 dev(조정에 쓸 절반)와 test(조정에 쓰지 않을 절반)로 나눕니다. 나눈 쪽마다 최대 `MAX`(기본 20,000)문장.
- 국립국어원 메신저 말뭉치는 화자마다 기기·자판이 적혀 있어 `DEVICE=스마트폰 KEYBOARD=2벌식`처럼 거를 수 있습니다
  (휴대폰 2벌식 사용자가 NewSwipe와 가장 비슷합니다). 기기·자판별 발화 수와 form·original_form이 다른 비율도 집계로 찍습니다.
- 말뭉치의 JSON 구조는 `python3 dict/eval/json_schema.py 말뭉치`로 원문 없이 키 이름·자료형만 볼 수 있습니다.
- 예시 문장을 찍는 평가 옵션(`SHOW_MADE`, `SHOW_WRONG`, `WHY`)은 이 스크립트에서 꺼집니다. 이런 말뭉치로는 직접 켜지 마세요.
- 이 말뭉치로 만든 사전이나 잰 숫자를 공개하려면 약정에 따라 먼저 국립국어원의 승인을 받고 출처를 밝혀야 합니다.

## 사전 팩 형식

zip 안에 `manifest.properties`(형식 번호, 언어, 이름, 출처, 라이선스, 해시), `dict_ko.bin`(필수), `dict_ko_next.bin`(선택),
`LICENSE.txt`(선택)를 담습니다. 자세한 설명은 `app/src/main/java/com/alternative_studios/newswipe/suggest/DictionaryPack.java`에
있습니다. 앱은 자기가 아는 것보다 높은 형식 번호의 팩을 거부하므로, 형식을 바꿀 때는 번호를 올리고 예전 형식도 계속 읽게 합니다.

사전 팩에는 데이터(단어·빈도·단어 쌍)만 담습니다. 추천 방식(조사·어미 규칙, 순위 매기기)은 앱에 두어, 앱이 좋아지면 기본 사전과
사전 팩 사용자 모두 좋아지게 합니다. 키보드는 사용자가 치는 글을 모두 보므로 팩에 실행 코드를 넣지 않으며, Google Play도 앱 밖에서
받은 코드를 실행하지 못하게 합니다. 사전을 크게 바꾸면 엔진의 가중치가 그 데이터에 맞춰져 있으므로 `eval/`로 다시 재 보세요.

사전 팩은 `dict-<버전>` 태그로 앱과 따로 릴리스합니다 (`.github/workflows/dict-pack.yml`, 자세한 것은 [BUILDING.md](../BUILDING.md)). 설정의
'사전 팩 받는 곳'은 `dict-` 릴리스만 모아 보여 주는 페이지를 엽니다. 폴더별 라이선스는 [packs/README.md](packs/README.md)에 있습니다.

## 라이선스

- 도구와 목록: Apache License 2.0 (저장소와 같음)
- 사전 데이터(앱의 기본 사전, 표준 사전 팩): CC BY 4.0 (Leipzig Corpora Collection)과 CC0 (NSMC) 자료로 만들었습니다.
  출처는 [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)와 `packs/ko-standard-LICENSE.txt`에 있습니다.
