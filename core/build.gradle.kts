// 앱(:heartz)과 로컬 서버(:server)가 함께 쓰는 순수 Kotlin/JVM 도메인.
// Android·Pleos SDK 타입을 두지 않아 JVM 단위 테스트로 검증한다.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
