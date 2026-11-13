# 화면 글의 용어 (한국어 · 영어)

앱의 화면 글은 쓰는 자리에서 두 언어를 나란히 적습니다: `L.t("설정", "Settings")`
(`app/src/main/java/com/alternative_studios/newswipe/L.java`). 앱 언어가 한국어면 한국어, 그 밖의 언어면 영어로 보입니다.
Android 13 이상에서는 시스템 설정 › 앱 › NewSwipe › 언어에서 앱만 따로 바꿀 수 있습니다.

같은 것은 어디서나 같은 말로 옮깁니다. 새 글을 쓸 때 아래 표를 따르고, 표에 없는 말이 자주 쓰이면 표에 더해 주세요.

- 설정 이름은 문장 첫 글자만 대문자로 씁니다 (Sentence case): `Customize keyboard`, `Show number row`.
- 다른 설정을 가리킬 때는 화면에 보이는 이름을 그대로 작은따옴표로 감쌉니다: `'Swipe down for capitals'`.
- 한글 자모·자판 이름·따라 칠 한국어 예문은 영어 화면에서도 한국어로 둡니다 (`ㄱ → ㄲ`, `지금 가는 중`).
- 문자열을 `static final`에 담지 않습니다 (언어를 바꾸면 새로 그린 화면부터 새 언어가 보여야 하므로). 표는 메서드로 만듭니다.
- 영어 화면의 개인정보 처리방침·오픈소스 라이선스는 `app/src/main/assets/guide/en/`에 있습니다.

## 키보드와 자판

| 한국어 | English |
|---|---|
| 키보드 | keyboard |
| 자판 (글자 자판 · 기호 자판 · 숫자 패드) | keyboard (letter keyboard · symbol keyboard · number pad) |
| 화면 자판 | on-screen keyboard |
| 외장 키보드 | physical keyboard |
| 한글 자판 배열 · 배열 | Korean layout · layout |
| 두벌식 · 단모음 · NewSwipe 단모음 | Dubeolsik · Danmoeum · NewSwipe Danmoeum |
| 상세 배열 · 자·모음 균형 배열 | Advanced layout · balanced consonant/vowel layout |
| 분리 키보드 · 묶음 · 가운데 공간 | split keyboard · block · center gap |
| 글자 키 · 기능키 · 숫자 줄 | letter keys · function keys · number row |
| 기호 키 · 지구본 키(🌐︎) · 스페이스바 · 엔터 키 · 지우기 키(⌫) | symbol key · globe key · space bar · enter key · delete key |
| 도구 막대 · 도구 막대 버튼 | toolbar · toolbar button |
| 한 손 모드 | one-handed mode |
| 가로 모드 · 세로 모드 | landscape · portrait |
| 폴더블 내부 화면 | (foldable) inner screen |
| 영역 · 키보드 전체 | area · whole keyboard |
| 여백 | padding |
| 입력 언어 · 언어 전환 · 언어 고르기 | input languages · switch language · pick language |
| 한·영 키 | Han/Yeong key |

## 입력

| 한국어 | English |
|---|---|
| 누르기 · 길게 누르기 · 두 번 (연속으로) 누르기 | tap · long press · double tap |
| 밀기 · 밀어서 입력 | swipe · swipe to type |
| 쌍자음 · 된소리 | double consonants (ㄲ ㄸ ㅃ ㅆ ㅉ) |
| ㅣ계 이중모음 · 이중모음 입력 | Y-vowels (ㅑ ㅕ ㅛ ㅠ ㅒ ㅖ) · Y-vowel input |
| 겹받침 | final clusters |
| 거센소리 | aspirated consonants |
| 힌트 · 말풍선 | hint · popup |
| 커서 자유 이동 | free cursor |
| 실행 취소 · 다시 실행 | undo · redo |
| 기본값으로 되돌리기 | reset to default |

## 추천과 학습

| 한국어 | English |
|---|---|
| 단어 추천 · 추천 단어 | word suggestions · suggestion |
| 다음 단어 예측 | next-word prediction |
| 자동 수정 | autocorrect |
| 입력한 단어 학습 · 학습한 단어 · 학습하지 않는 단어 | learn typed words · learned words · blocked words |
| 단축어 · 문장 | text shortcut · phrase |
| 자동 완성 | autofill |
| 한국어 사전 · 사전 팩 · 기본 사전 | Korean dictionary · dictionary pack · built-in dictionary |

## 메뉴

| 한국어 | English |
|---|---|
| 입력 언어 | Input languages |
| 키보드 사용자화 · ○○ 사용자화 | Customize keyboard · Customize ○○ |
| 테마 · 화면 모드 · 강조 색 | Theme · Display mode · Accent color |
| 소리 및 진동 | Sound & vibration |
| 추천, 학습 및 완성 | Suggestions, learning & autofill |
| 클립보드 및 이모지 · 클립보드 기록 · 고정 | Clipboard & emoji · clipboard history · pin |
| 설정 가져오기 및 내보내기 | Import & export settings |
| 실험실 | Labs |
| 정보 · 개인정보 처리방침 · 오픈소스 라이선스 · 문의 | About · Privacy policy · Open source licenses · Contact |
