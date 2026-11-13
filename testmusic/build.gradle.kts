// 시험 전용: 오디오 포커스에 표준 방식으로 반응하는 가짜 음악 앱(HEARTz 덕킹·일시정지 확인용). 제출물에 포함하지 않는다.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "ai.heartz.testmusic"
    compileSdk = 35
    defaultConfig {
        applicationId = "ai.heartz.testmusic"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "test"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
