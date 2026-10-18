# 사전 팩

설정의 '한국어 사전'에서 '사전 팩 가져오기'로 가져오는 한국어 사전입니다. 앱에는 들어가지 않으며, GitHub 릴리스(`dict-` 태그)에 버전을 붙인 이름(`ko-standard-2026.10.3.zip`)으로 배포합니다.

## 라이선스

이 폴더는 저장소의 다른 부분과 라이선스가 다릅니다.

| 파일 | 라이선스 |
|---|---|
| `ko-standard.zip` 안의 사전 데이터 (`dict_ko.bin`, `dict_ko_next.bin`) | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) (Leipzig Corpora Collection)과 CC0 1.0 (NSMC) 자료로 만들었습니다. 출처와 바꾼 내용은 `ko-standard-LICENSE.txt` (팩 안의 `LICENSE.txt`)에 있습니다. |
| 앱에 든 기본 사전 (`app/src/main/assets/dict_ko.bin`, `dict_ko_next.bin`) | 팩과 같은 자료로 만들어 같은 라이선스입니다 (출처는 [THIRD_PARTY_NOTICES.md](../../THIRD_PARTY_NOTICES.md)). |
| 사전을 만드는 도구 (`dict/build/`, `dict/eval/`)와 목록 (`dict/lists/`) | Apache License 2.0 (저장소의 `LICENSE`) |

사전 데이터를 다시 배포하거나 고쳐 쓸 때는 CC BY 4.0에 따라 출처(Leipzig Corpora Collection, NSMC, NewSwipe)를 밝히고 바꾼 내용을
적어 주세요. 데이터에는 문장 원문이 들어 있지 않고, 단어와 이어 나온 두 단어의 빈도만 들어 있습니다.

새 팩에 다른 라이선스의 자료(예: CC BY-SA, GPL, 이용 약정이 있는 말뭉치)를 넣으려면 이 표와 팩의 `LICENSE.txt`, `--license`·
`--attribution`을 함께 고쳐야 합니다. 동일조건변경허락(SA)이 붙은 자료로 만든 팩은 그 팩만 같은 라이선스로 배포하면 되며 앱 코드에는
영향이 없지만, 경계를 분명히 하려면 별도 저장소로 나누는 것을 검토하세요. 배포가 허락되지 않은 자료(국립국어원 말뭉치 등)로 만든
사전은 공개하지 않습니다.

## 릴리스

만드는 법과 릴리스 방법은 [BUILDING.md](../../BUILDING.md)의 '한국어 사전과 사전 팩'에 있습니다.
