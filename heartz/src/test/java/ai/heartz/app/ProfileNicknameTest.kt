package ai.heartz.app

import ai.heartz.app.ui.components.shortName
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileNicknameTest {
    @Test fun customNicknameKeepsParentheses() {
        assertEquals("구름 (호랑이)", "구름 (호랑이)".shortName())
    }
    @Test fun legacySeedAndTestAccountSuffixesStayOutOfCards() {
        assertEquals("하늘", "하늘 (테스트 청취자)".shortName())
        assertEquals("은행나무", "은행나무 (시드 작성자)".shortName())
        assertEquals("운영자", "운영자 (테스트)".shortName())
    }
}
