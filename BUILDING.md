# 빌드하기

NewSwipe를 직접 빌드하고 서명하는 방법입니다. 사용법은 [README](README.md)를 보세요.

## 준비물

| 항목 | 값 |
|---|---|
| JDK | 17 (CI도 Temurin 17을 씁니다) |
| Android SDK | 플랫폼 36 (`compileSdk = 36`). `ANDROID_HOME`을 지정하거나 저장소 루트에 `local.properties`를 만들어 `sdk.dir=/경로/Android/sdk`를 적습니다 |
| Gradle | 래퍼가 받습니다 (`gradle/wrapper/gradle-wrapper.properties`, 9.6.1) |
| Android Gradle 플러그인 | 9.4.0 (`gradle/libs.versions.toml`) |

앱은 `minSdk 28`, `targetSdk 35`입니다. `targetSdk`를 36 이상으로 올리면 키보드 창의 하단 바 처리가 달라지므로, 올리기 전에 `app/build.gradle.kts`의 설명을 먼저 읽으세요.

앱은 AndroidX 등 외부 라이브러리를 쓰지 않습니다. 의존성은 단위 테스트용 JUnit뿐입니다.

## 빌드 명령

저장소 루트에서 실행합니다 (Windows는 `gradlew.bat`).

```bash
./gradlew testDebugUnitTest     # 단위 테스트
./gradlew assembleDebug         # 디버그 APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease       # 릴리스 APK → app/build/outputs/apk/release/app-release.apk
```

- 릴리스 빌드는 R8 코드 축소와 리소스 축소를 켠 채 만듭니다.
- 서명 정보를 주지 않으면 릴리스 APK도 **디버그 키로 서명**됩니다. 그 APK는 내 키로 서명한 APK 위에 덮어 설치할 수 없습니다 (서명이 달라 업데이트가 거절됩니다).
- 처음 빌드할 때 Android SDK 라이선스에 동의하지 않았다면 `sdkmanager --licenses`로 동의해야 합니다.

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

`.github/workflows/build.yml`이 `main`에 푸시할 때, 풀 리퀘스트를 열 때, `v*` 태그를 푸시할 때, 수동으로 실행할 때 돕니다. 테스트(`testDebugUnitTest`)와 릴리스 빌드(`assembleRelease`)를 하고 APK를 `NewSwipe-apk` 아티팩트로 올립니다.

릴리스 서명을 쓰려면 저장소 **Settings › Secrets and variables › Actions**에 아래 네 개를 등록합니다.

| 시크릿 | 내용 |
|---|---|
| `KEYSTORE_BASE64` | 키스토어 파일을 base64로 인코딩한 한 줄 (`base64 -w0 release.jks`, macOS는 `base64 -i release.jks`) |
| `KEYSTORE_PASSWORD` | 키스토어 비밀번호 |
| `KEY_ALIAS` | 키 별칭 |
| `KEY_PASSWORD` | 키 비밀번호 |

`KEYSTORE_BASE64`가 없으면 워크플로는 서명 없이 (디버그 키로) 빌드합니다. 풀 리퀘스트는 저장소 밖(포크)에서 열면 시크릿을 받지 못하니 이 경우가 정상입니다.

## 새 버전 내보내기

1. `app/build.gradle.kts`의 `versionCode`를 올리고 `versionName`을 바꿉니다.
2. 변경을 `main`에 합칩니다.
3. 태그를 푸시합니다.

   ```bash
   git tag v1.0.1
   git push origin v1.0.1
   ```

4. 워크플로가 APK를 `NewSwipe-v1.0.1.apk`로 이름을 붙여 GitHub 릴리스에 올리고, 릴리스 노트를 자동으로 만듭니다.
