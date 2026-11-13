package ai.heartz.app

import ai.heartz.app.ui.components.withObject
import org.junit.Assert.assertEquals
import org.junit.Test

class KoreanParticleTest {
    @Test
    fun objectParticleFollowsFinalConsonant() {
        assertEquals("동부이촌동 은행나무길을", "동부이촌동 은행나무길".withObject())
        assertEquals("서빙고역 사거리를", "서빙고역 사거리".withObject())
        assertEquals("한강 산책로 입구를", "한강 산책로 입구".withObject())
    }
}
