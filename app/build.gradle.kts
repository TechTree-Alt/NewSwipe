plugins {
    alias(libs.plugins.android.application)
}

// 릴리스 서명 정보는 환경 변수로 받는다 (GitHub Actions Secrets 또는 로컬 셸).
// 없으면 디버그 키로 서명한다. 저장소의 BUILDING.md '릴리스 서명' 참고.
val releaseKeystore: String? = System.getenv("NEWSWIPE_KEYSTORE")

// Google Play는 새 앱과 업데이트가 targetSdk 36 이상이어야 받아 준다.
// 키보드 창은 모든 Android 버전에서 edge-to-edge로 그리고 하단 바 자리는 직접 비운다 (NewSwipeIME.applySystemInsets).

android {
    namespace = "com.alternative_studios.newswipe"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.alternative_studios.newswipe"
        minSdk = 28
        targetSdk = 36
        versionCode = 27
        versionName = "2.5.0"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("NEWSWIPE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("NEWSWIPE_KEY_ALIAS")
                keyPassword = System.getenv("NEWSWIPE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // 안드로이드 기본 규칙(proguard-android-optimize.txt)은 XML 레이아웃·onClick 속성을 위해 View의 set*/get*과
            // 액티비티의 (View) 메서드를 모두 남긴다. 이 앱은 XML 레이아웃도 리플렉션도 쓰지 않으므로 필요한 규칙만 proguard-rules.pro에 둔다.
            proguardFiles("proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // 화면 글은 기본 로케일로 한국어·영어를 고른다(L). 시험은 한국어 글을 보므로 한국어 로케일로 돌린다.
        unitTests.all { it.jvmArgs("-Duser.language=ko", "-Duser.country=KR") }
    }

    // assets의 .bin은 이미 압축했거나(dict/build/pack_assets.py, zlib) 압축이 되지 않는 것(맞는 말 필터)이라 APK에서 다시 압축하지 않는다.
    androidResources {
        noCompress += "bin"
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    testImplementation(libs.junit)
}

// 단위 테스트가 실패하면 CI 로그에 실패한 확인 문의 메시지와 위치를 모두 보인다.
tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
