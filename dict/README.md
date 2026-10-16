# NewSwipe 사전

단어 추천·자동 수정에 쓰는 사전을 만들고, 평가하고, 사전 팩으로 묶는 데 필요한 것을 모아 둔 폴더입니다.
앱 빌드에는 쓰이지 않습니다. 앱에 들어가는 결과물은 `app/src/main/assets/dict_*.bin`뿐입니다.

| 폴더 | 내용 |
|---|---|
| `build/` | 사전을 만드는 도구. `build_ko_model.py`(한국어 단어 사전 + 다음 단어 사전), `build_ko_dict.py`(걸러 내기·저장 함수, 차단 목록), `build_en_dict.py`(영어), `gen_ko_conjugations.py`(용언 활용형 생성), `make_dict_pack.py`(사전 팩 zip 만들기) |
| `lists/` | 직접 고른 목록. 구어체 단어(`ko_colloquial.txt`, `en_colloquial.txt`), 용언 어간(`ko_stems.txt`)과 그것으로 만든 활용형(`ko_conjugated.txt`), 구어체 문장(`ko_phrases.txt`), 말뭉치를 섞을 때 뺄 주제어(`ko_mix_exclude_nsmc.txt`) |
| `eval/` | 평가 도구. 앱의 추천 코드로 한 키씩 쳐서 키 절약률·잘못 고침·오타 고침을 잰다 (`run_eval.sh`, `SuggestEval.java`), 두 설정을 같은 문장끼리 견준다 (`compare_ksr.py`), 공개 평가 말뭉치를 받아 dev·test로 나눈다 (`fetch_public_sets.sh`) |
| `packs/` | 설정에서 가져오는 사전 팩. `ko-standard.zip`(표준 사전: 단어 8만 개 + 단어 쌍 40만 개, 약 1.1MB)과 그 라이선스 |

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
키 절약률, 다음 단어 예측이 보인 비율과 맞힌 비율, 바르게 친 말을 잘못 고친 비율, 추천 시간(평균·중앙값·99%·최대), 일부러 넣은 오타를
고친 비율(오타 종류별)과 고치지 못했어도 다 친 뒤 추천에 원래 단어가 보인 비율.

학습(`--learn`)의 효과는 `smile_<말투>.txt`로도 재 주세요. Chatbot_data·NSMC는 여러 사람이 쓴 글을 한 사람이 친 것처럼 이어 치므로
내 말버릇(자주 쓰는 어미 등)의 효과를 작게 잽니다: 내가 쓴 어미 배우기는 두 말뭉치에서 +0.1%p였지만, 말투가 한결같은 SmileStyle
12가지에서는 평균 +0.49%p(naruto +1.6%p, halbae +1.3%p)였습니다.

알아 둘 한계: 오타는 한 단어에 한 번만 넣고(실제로는 두 번 틀리기도 한다), 띄어쓰기를 빼먹은 글은 재지 않습니다. NSMC의 '잘못 고침'에는
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

릴리스를 만들 때 `packs/ko-standard.zip`을 GitHub 릴리스에 첨부하세요 (설정의 '사전 팩 받는 곳'이 릴리스 페이지를 엽니다).

## 라이선스

- 도구와 목록: Apache License 2.0 (저장소와 같음)
- 사전 데이터(앱의 기본 사전, 표준 사전 팩): CC BY 4.0 (Leipzig Corpora Collection)과 CC0 (NSMC) 자료로 만들었습니다.
  출처는 [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)와 `packs/ko-standard-LICENSE.txt`에 있습니다.
