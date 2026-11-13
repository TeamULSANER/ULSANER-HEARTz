package ai.heartz.app

import ai.heartz.app.platform.profile.PleosProfile
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pleos Connect 프로필 모양의 첫 글자 */
class PleosProfileTest {
    @Test fun profileCircleShowsTheFirstLetter() {
        assertEquals("운", PleosProfile.initial("운전자"))
        assertEquals("H", PleosProfile.initial("hana"))
        assertEquals("하", PleosProfile.initial("하늘 (테스트 청취자)"))
        assertEquals("?", PleosProfile.initial("  "))
    }
}
