plugins {
    alias(libs.plugins.android.application)
}

// 릴리스 서명 정보는 환경 변수로 받는다 (GitHub Actions Secrets 또는 로컬 셸).
// 없으면 디버그 키로 서명한다. 저장소의 BUILDING.md '릴리스 서명' 참고.
val releaseKeystore: String? = System.getenv("NEWSWIPE_KEYSTORE")

android {
    namespace = "com.alternative_studios.newswipe"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.alternative_studios.newswipe"
        minSdk = 28
        // 35로 둔다. 36을 대상으로 하면 Android 16이 키보드 창의 edge-to-edge 제외 설정을 무시해
        // 하단 바 색이 투명으로 고정되고, 일부 기기(비보 등)에서 하단 바 아이콘이 밝게 나온다.
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
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
