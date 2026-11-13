pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Pleos 공식 Maven (Vehicle SDK project-setup 가이드)
        maven {
            url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/")
            content { includeGroupByRegex("ai\\.pleos\\..*") }
        }
    }
}

rootProject.name = "Heartz"
// :app = 세팅 단계의 Vehicle SDK 검증 앱(PleosSetupProbe, 유지)
// :heartz = 제품 앱, :core = 앱·서버 공용 도메인, :server = 로컬 개발용 API 서버
include(":app", ":core", ":heartz", ":server")
// :testmusic = 시험 전용 가짜 음악 앱(오디오 포커스 반응 확인용, 제출물 아님)
include(":testmusic")
