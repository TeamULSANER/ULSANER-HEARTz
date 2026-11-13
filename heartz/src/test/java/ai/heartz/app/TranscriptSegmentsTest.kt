package ai.heartz.app

import ai.heartz.app.platform.speech.RecognizeResult
import ai.heartz.app.platform.speech.TranscriptSegments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** STT 콜백 순서(AVD 2026-11-11 로그 재현)로 받아쓰기 조각을 모으는 규칙 테스트. 실제 STT 증거가 아니다. */
class TranscriptSegmentsTest {
    @Test
    fun answerModeEndsAtTheFirstFinalResult() {
        val t = TranscriptSegments(keepAll = false)
        t.onUpdated("응", completed = false)
        assertEquals(RecognizeResult.Text("응 읽어줘"), t.onUpdated("응 읽어줘", completed = true))
    }

    @Test
    fun storyModeKeepsEverySegmentAcrossPauses() {
        val t = TranscriptSegments(keepAll = true)
        t.onUpdated("은행나무길", completed = false)
        assertNull(t.onUpdated("은행나무길 하면은 또 제가", completed = true))
        t.onEnded()
        t.onUpdated("은행 밟고", completed = false)
        assertNull(t.onUpdated("은행 밟고 냄새 났던 적이 있죠", completed = true))
        t.onEnded()
        assertEquals(RecognizeResult.Text("은행나무길 하면은 또 제가\n은행 밟고 냄새 났던 적이 있죠"), t.finish())
    }

    @Test
    fun storyModeWithNothingFinalIsNoSpeech() {
        val t = TranscriptSegments(keepAll = true)
        t.onUpdated("음", completed = false)
        assertEquals(RecognizeResult.NoSpeech, t.finish())
        assertEquals(RecognizeResult.NoSpeech, TranscriptSegments(keepAll = true).finish())
    }

    @Test
    fun blankFinalsAreSkipped() {
        val t = TranscriptSegments(keepAll = true)
        t.onUpdated("  ", completed = true)
        t.onUpdated("야경 보는게 취미입니다", completed = true)
        assertEquals(RecognizeResult.Text("야경 보는게 취미입니다"), t.finish())
    }

    @Test
    fun pendingPartialIsReported() {
        val t = TranscriptSegments(keepAll = true)
        t.onUpdated("제가 한강을", completed = false)
        assertEquals(true, t.pending)
        t.onUpdated("제가 한강을 걸으면서", completed = true)
        assertEquals(false, t.pending)
    }
}
