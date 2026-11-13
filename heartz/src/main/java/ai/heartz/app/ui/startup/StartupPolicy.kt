package ai.heartz.app.ui.startup

enum class StartupDestination { SPLASH, MAIN }

/** 로고를 잠깐 보여 준 뒤 계정을 불러오면 바로 메인. 프로필은 Pleos Connect의 것을 쓰므로 설정 화면이 없다 */
fun startupDestination(splashFinished: Boolean, accountLoaded: Boolean): StartupDestination =
    if (!splashFinished || !accountLoaded) StartupDestination.SPLASH else StartupDestination.MAIN
