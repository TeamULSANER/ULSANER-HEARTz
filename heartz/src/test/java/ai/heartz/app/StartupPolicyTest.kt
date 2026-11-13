package ai.heartz.app

import ai.heartz.app.ui.startup.StartupDestination
import ai.heartz.app.ui.startup.startupDestination
import org.junit.Assert.assertEquals
import org.junit.Test

class StartupPolicyTest {
    @Test fun logoStaysUntilDelayAndAccountLoadFinish() {
        assertEquals(StartupDestination.SPLASH, startupDestination(splashFinished = false, accountLoaded = true))
        assertEquals(StartupDestination.SPLASH, startupDestination(splashFinished = true, accountLoaded = false))
    }

    /** 프로필은 Pleos Connect의 것을 쓰므로 첫 방문에도 프로필 설정 화면 없이 바로 메인으로 간다 */
    @Test fun firstVisitGoesStraightToMainWithoutProfileSetup() {
        assertEquals(StartupDestination.MAIN, startupDestination(splashFinished = true, accountLoaded = true))
    }
}
