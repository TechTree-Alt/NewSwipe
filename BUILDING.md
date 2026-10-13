# 빌드하기

NewSwipe를 직접 빌드하고 서명하는 방법입니다. 사용법은 [README](README.md)를 보세요.

## 준비물

| 항목 | 값 |
|---|---|
| JDK | 17 (CI도 Temurin 17을 씁니다) |
| Android SDK | 플랫폼 36 (`compileSdk = 36`). `ANDROID_HOME`을 지정하거나 저장소 루트에 `local.properties`를 만들어 `sdk.dir=/경로/Android/sdk`를 적습니다 |
| Gradle | 래퍼가 받습니다 (`gradle/wrapper/gradle-wrapper.properties`, 9.6.1) |
| Android Gradle 플러그인 | 9.4.0 (`gradle/libs.versions.toml`) |

앱은 `minSdk 28`, `targetSdk 36`입니다. Google Play가 새 앱과 업데이트에 `targetSdk 36` 이상을 요구하기 때문입니다 ([요건](https://developer.android.com/google/play/requirements/target-sdk)).

36부터는 키보드 창이 하단 바 뒤까지 그려지는 것을 끌 수 없어서, 일부 기기(비보 등)에서 하단 바 아이콘 색이 맞는지 직접 확인해야 합니다. 비교해 볼 때만 `./gradlew assembleRelease -Pnewswipe.targetSdk=35`처럼 낮춰 빌드할 수 있습니다. 그렇게 만든 파일은 Play에 올리지 마세요. 자세한 이유는 `app/build.gradle.kts`의 주석을 보세요.

앱은 AndroidX 등 외부 라이브러리를 쓰지 않습니다. 의존성은 단위 테스트용 JUnit뿐입니다.

## 빌드 명령

저장소 루트에서 실행합니다 (Windows는 `gradlew.bat`).

```bash
./gradlew testDebugUnitTest     # 단위 테스트
./gradlew assembleDebug         # 디버그 APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease       # 릴리스 APK → app/build/outputs/apk/release/app-release.apk
./gradlew bundleRelease         # 릴리스 AAB(Play용) → app/build/outputs/bundle/release/app-release.aab
```

- 릴리스 빌드는 R8 코드 축소와 리소스 축소를 켠 채 만듭니다. APK와 AAB는 같은 서명 설정을 씁니다.
- 서명 정보를 주지 않으면 릴리스 APK·AAB도 **디버그 키로 서명**됩니다. 디버그 키로 서명한 AAB는 Play에 올릴 수 없습니다. 그 APK는 내 키로 서명한 APK 위에 덮어 설치할 수 없습니다 (서명이 달라 업데이트가 거절됩니다).
- 처음 빌드할 때 Android SDK 라이선스에 동의하지 않았다면 `sdkmanager --licenses`로 동의해야 합니다.

## 한국어 사전과 사전 팩

앱에 든 한국어 기본 사전(`app/src/main/assets/dict_ko.bin`, 5만 단어)과 설정에서 가져오는 사전 팩(`dict/packs/ko-standard.zip`,
8만 단어 + 단어 쌍 40만 개)은 `dict/build/build_ko_model.py`로 만듭니다. 말뭉치는 저장소에 넣지 않으며, 만드는 명령과 받는 곳은
`dict/build/build_ko_model.py` 맨 위에 적혀 있습니다. NSMC(`ratings_train.txt`, CC0)는 한 번 센 뒤 `--mix 센결과.pkl:0.5
--mix-exclude dict/lists/ko_mix_exclude_nsmc.txt`로 섞습니다.

```bash
# 기본 사전 (단어 쌍 없이 빈도 기준만 담은 dict_ko_next.bin도 함께 나온다)
python3 dict/build/build_ko_model.py out_base --cache counts.pkl --mix nsmc.pkl:0.5 --mix-exclude dict/lists/ko_mix_exclude_nsmc.txt \
    --top 50000 --pairs 0 --extra dict/lists/ko_colloquial.txt --extra dict/lists/ko_conjugated.txt --phrases dict/lists/ko_phrases.txt 말뭉치들...
cp out_base/dict_ko.bin out_base/dict_ko_next.bin app/src/main/assets/

# 표준 사전 팩
python3 dict/build/build_ko_model.py out_pack ... --top 80000 --pairs 400000 ...
python3 dict/build/make_dict_pack.py out_pack dict/packs/ko-standard.zip --name "표준 사전" --version 2026.10 \
    --license "CC BY 4.0" --attribution "Leipzig Corpora Collection (CC BY 4.0), NSMC (CC0), NewSwipe" \
    --license-file dict/packs/ko-standard-LICENSE.txt

# 성능 확인 (실제 조합기로 한 키씩 쳐서 키 절약률을 잰다)
dict/eval/run_eval.sh out_pack dict/eval/ko_chat_test.txt
```

사전 팩은 앱에 들어가지 않습니다. 릴리스를 만들 때 `dict/packs/ko-standard.zip`을 GitHub 릴리스에 첨부하면, 설정의 '사전 팩 받는 곳'이
그 페이지를 엽니다. 팩 형식(zip 안의 `manifest.properties`, `dict_ko.bin`, `dict_ko_next.bin`, `LICENSE.txt`)은
`app/src/main/java/com/alternative_studios/newswipe/suggest/DictionaryPack.java`에 설명되어 있습니다. 직접 만든 팩도 같은 방법으로
가져올 수 있으며, 넣은 데이터의 라이선스와 출처는 `--license`, `--attribution`에 정확히 적어 주세요.

## 릴리스 서명

릴리스 키 정보는 환경 변수로 받습니다. `NEWSWIPE_KEYSTORE`가 있을 때만 릴리스 서명을 쓰고, 없으면 디버그 키를 씁니다 (`app/build.gradle.kts`).

| 환경 변수 | 내용 |
|---|---|
| `NEWSWIPE_KEYSTORE` | 키스토어 파일 경로 |
| `NEWSWIPE_KEYSTORE_PASSWORD` | 키스토어 비밀번호 |
| `NEWSWIPE_KEY_ALIAS` | 키 별칭 |
| `NEWSWIPE_KEY_PASSWORD` | 키 비밀번호 |

```bash
# 키스토어 만들기 (한 번만)
keytool -genkeypair -v -keystore release.jks -alias newswipe \
        -keyalg RSA -keysize 4096 -validity 10000

# 서명해서 빌드하기
export NEWSWIPE_KEYSTORE=$PWD/release.jks
export NEWSWIPE_KEYSTORE_PASSWORD=...
export NEWSWIPE_KEY_ALIAS=newswipe
export NEWSWIPE_KEY_PASSWORD=...
./gradlew assembleRelease
```

> **키스토어는 잃어버리면 안 됩니다.** 같은 키로 서명한 APK만 기존 설치본을 업데이트할 수 있습니다. 안전한 곳에 따로 백업해 두세요.
> `.gitignore`가 `*.jks`, `*.keystore`, `release-keystore*`를 제외하지만, 키스토어와 비밀번호를 저장소에 올리지 않도록 늘 확인하세요.

## GitHub Actions

`.github/workflows/build.yml`이 `main`에 푸시할 때, 풀 리퀘스트를 열 때, `v*` 태그를 푸시할 때, 수동으로 실행할 때 돕니다. 테스트(`testDebugUnitTest`)를 돌리고 릴리스 APK(`assembleRelease`)와 AAB(`bundleRelease`)를 만들어 각각 `NewSwipe-apk`, `NewSwipe-aab` 아티팩트로 올립니다.

릴리스 서명을 쓰려면 저장소 **Settings › Secrets and variables › Actions**에 아래 네 개를 등록합니다.

| 시크릿 | 내용 |
|---|---|
| `KEYSTORE_BASE64` | 키스토어 파일을 base64로 인코딩한 한 줄 (`base64 -w0 release.jks`, macOS는 `base64 -i release.jks`) |
| `KEYSTORE_PASSWORD` | 키스토어 비밀번호 |
| `KEY_ALIAS` | 키 별칭 |
| `KEY_PASSWORD` | 키 비밀번호 |

`KEYSTORE_BASE64`가 없으면 워크플로는 디버그 키로 빌드하고 실행 요약에 경고를 띄웁니다. 이때는 태그를 푸시해도 **GitHub 릴리스를 게시하지 않습니다** (디버그 키로 서명된 APK가 릴리스로 나가지 않게). 포크에서 열린 풀 리퀘스트는 시크릿을 받지 못하니 이 경우가 정상입니다.

## 새 버전 내보내기

1. `app/build.gradle.kts`의 `versionCode`를 올리고 `versionName`을 바꿉니다.
2. 변경을 `main`에 합칩니다.
3. 태그를 푸시합니다.

   ```bash
   git tag v1.0.1
   git push origin v1.0.1
   ```

4. 워크플로가 APK를 `NewSwipe-v1.0.1.apk`로 이름을 붙여 GitHub 릴리스에 올리고, 릴리스 노트를 자동으로 만듭니다. AAB는 릴리스에 붙지 않고 Actions의 `NewSwipe-aab` 아티팩트로 받습니다.

## Google Play에 올리기

1. Actions 실행 결과의 `NewSwipe-aab` 아티팩트에서 `.aab`를 받습니다. 릴리스 키로 서명된 것이어야 합니다 (위 '릴리스 서명' 참고).
2. Play Console의 새 릴리스에 올립니다. 올릴 때마다 `versionCode`가 이전보다 커야 합니다.
3. **서명:** Play 앱 서명을 쓰면 Play에서 설치한 앱의 최종 서명은 Play가 관리하는 키로 이루어져, GitHub APK와 서명이 달라 서로 덮어 설치할 수 없습니다. 같은 서명을 쓰려면 Play Console에서 앱 서명 키를 등록할 때 이 키스토어를 가져오는 방법을 쓰는데, 첫 릴리스를 만들 때만 고를 수 있는 것으로 알고 있습니다. 자세한 내용은 Play Console 도움말을 확인하세요.
4. 2023-11-13 이후 만든 개인 개발자 계정은 정식 출시 전에 12명 이상이 14일 연속 참여하는 비공개 테스트를 마쳐야 합니다 ([안내](https://support.google.com/googleplay/android-developer/answer/14151465)).

## 배포 전 기기에서 확인할 것

`targetSdk 36`에서는 아래가 달라지므로 실제 기기로 확인하세요.

- **하단 바 (비보 등):** 라이트·다크 모드와 세로·가로에서 키보드 아래의 하단 바 아이콘이 키보드 배경과 구분되는지, 키가 제스처 바와 겹치지 않는지.
- **큰 화면 (태블릿, 폴더블 내부 화면):** 설정 화면 등 9개 화면이 `screenOrientation="portrait"`로 선언되어 있는데, 36에서는 큰 화면에서 이 값이 무시됩니다. 가로나 분할 창에서도 열려도 레이아웃이 깨지지 않는지.
- **뒤로 가기:** 첫 시작 가이드와 설정 화면에서 뒤로 가기 동작 (예측형 뒤로 가기가 켜져 있습니다).
