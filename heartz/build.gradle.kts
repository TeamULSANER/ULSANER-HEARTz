import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 릴리스 서명: 저장소 밖의 로컬 파일(keystore.properties, .gitignore 대상)이 있을 때만 서명한다.
// 없으면 서명하지 않은 release APK가 만들어진다. 서명키·비밀번호는 저장소에 넣지 않는다.
val releaseSigning: Properties? = rootProject.file("keystore.properties").takeIf { it.isFile }?.let { f ->
    Properties().apply { f.inputStream().use { load(it) } }
}

android {
    namespace = "ai.heartz.app"
    compileSdk = 35
    // CarUxRestrictionsManager (AAOS). 기기에서는 com.android.car.framework APEX가 부트클래스패스로 제공한다.
    useLibrary("android.car")

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "ai.heartz.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // 에뮬레이터에서 호스트의 로컬 개발 서버(127.0.0.1:8787)로 접근하는 주소
            buildConfigField("String", "DEV_SERVER_URL", "\"http://10.0.2.2:8787\"")
        }
        release {
            // 릴리스는 기기 내 데모 백엔드만 사용한다(개발 서버 주소 없음)
            buildConfigField("String", "DEV_SERVER_URL", "\"\"")
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    sourceSets {
        // 시드 합성 음원을 assets/audio/ 로 포함한다 (서버와 같은 파일)
        getByName("main").assets.srcDir(rootProject.file("seed"))
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf("META-INF/{AL2.0,LGPL2.1}", "META-INF/versions/9/previous-compilation-data.bin")
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.navihelper.sdk)
    implementation(libs.fusedlocation.sdk)
    implementation(libs.tts.sdk)
    implementation(libs.stt.sdk)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
    debugImplementation(libs.androidx.ui.tooling)
}
