plugins {
    alias(libs.plugins.android.application)
}

// 릴리스 서명 정보는 환경 변수로 받는다 (GitHub Actions Secrets 또는 로컬 셸).
// 없으면 디버그 키로 서명한다. 저장소의 BUILDING.md '릴리스 서명' 참고.
val releaseKeystore: String? = System.getenv("NEWSWIPE_KEYSTORE")

// Google Play는 새 앱과 업데이트가 targetSdk 36 이상이어야 받아 준다. 기본은 36이다.
// 36에서는 키보드 창의 edge-to-edge 제외(windowOptOutEdgeToEdgeEnforcement)가 무시되어 하단 바 색이 투명으로 고정된다.
// 일부 기기(비보 등)의 하단 바 아이콘이 밝게 나오는지 비교해 볼 때만 -Pnewswipe.targetSdk=35 로 낮춰 빌드한다.
// 그렇게 만든 APK/AAB는 Play에 올리지 않는다.
val targetSdkVersion: Int = (findProperty("newswipe.targetSdk") as? String)?.toIntOrNull() ?: 36

android {
    namespace = "com.alternative_studios.newswipe"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.alternative_studios.newswipe"
        minSdk = 28
        targetSdk = targetSdkVersion
        versionCode = 8
        versionName = "1.0.7"
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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    testImplementation(libs.junit)
}
