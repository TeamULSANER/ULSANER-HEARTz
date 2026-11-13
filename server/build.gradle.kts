// 로컬 개발·시연용 Heartz API 서버. 외부 배포하지 않는다(127.0.0.1 바인딩).
// 실행: ./gradlew :server:run  (에뮬레이터에서는 http://10.0.2.2:8787)
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("ai.heartz.server.MainKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    args = listOf("--data", "build/heartz-server-data", "--seed-audio", "seed/audio")
}

dependencies {
    implementation(project(":core"))
    testImplementation(libs.junit)
}
