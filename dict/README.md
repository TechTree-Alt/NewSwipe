# NewSwipe 사전

단어 추천·자동 수정에 쓰는 사전을 만들고, 평가하고, 사전 팩으로 묶는 데 필요한 것을 모아 둔 폴더입니다.
앱 빌드에는 쓰이지 않습니다. 앱에 들어가는 결과물은 `app/src/main/assets/dict_*.bin`뿐입니다.

| 폴더 | 내용 |
|---|---|
| `build/` | 사전을 만드는 도구. `build_ko_model.py`(한국어 단어 사전 + 다음 단어 사전), `build_ko_dict.py`(걸러 내기·저장 함수, 차단 목록), `build_en_dict.py`(영어), `gen_ko_conjugations.py`(용언 활용형 생성), `make_dict_pack.py`(사전 팩 zip 만들기) |
| `lists/` | 직접 고른 목록. 구어체 단어(`ko_colloquial.txt`, `en_colloquial.txt`), 용언 어간(`ko_stems.txt`)과 그것으로 만든 활용형(`ko_conjugated.txt`), 구어체 문장(`ko_phrases.txt`), 말뭉치를 섞을 때 뺄 주제어(`ko_mix_exclude_nsmc.txt`) |
| `eval/` | 평가 도구. 앱의 추천 코드로 한 키씩 쳐서 키 절약률·잘못 고침·오타 고침을 잰다 (`run_eval.sh`, `SuggestEval.java`), 두 설정을 같은 문장끼리 견준다 (`compare_ksr.py`) |
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

`ko_chat_test.txt`는 직접 쓴 메신저 문장이라 실제 사람이 쓴 글보다 표준적입니다. 사람이 쓴 글(예: NSMC, Chatbot_data의 질문)로도
함께 재 보세요.

## 사전 팩 형식

zip 안에 `manifest.properties`(형식 번호, 언어, 이름, 출처, 라이선스, 해시), `dict_ko.bin`(필수), `dict_ko_next.bin`(선택),
`LICENSE.txt`(선택)를 담습니다. 자세한 설명은 `app/src/main/java/com/alternative_studios/newswipe/suggest/DictionaryPack.java`에
있습니다. 앱은 자기가 아는 것보다 높은 형식 번호의 팩을 거부하므로, 형식을 바꿀 때는 번호를 올리고 예전 형식도 계속 읽게 합니다.

릴리스를 만들 때 `packs/ko-standard.zip`을 GitHub 릴리스에 첨부하세요 (설정의 '사전 팩 받는 곳'이 릴리스 페이지를 엽니다).

## 라이선스

- 도구와 목록: Apache License 2.0 (저장소와 같음)
- 사전 데이터(앱의 기본 사전, 표준 사전 팩): CC BY 4.0 (Leipzig Corpora Collection)과 CC0 (NSMC) 자료로 만들었습니다.
  출처는 [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)와 `packs/ko-standard-LICENSE.txt`에 있습니다.
