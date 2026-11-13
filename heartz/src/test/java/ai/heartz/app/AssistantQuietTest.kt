package ai.heartz.app

import ai.heartz.app.platform.gleo.AssistantQuiet
import ai.heartz.app.platform.gleo.QuietOutcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Gleo가 앱 액션 응답을 받은 뒤 말하는 동안 편지 재생을 미루는 규칙.
 * 입력은 "다른 앱의 USAGE_ASSISTANT 재생이 있는가"를 흉내 낸 fake 흐름이다(실제 Gleo 증거 아님).
 * 시간 값은 2026-11-10 AVD 관찰(응답 후 약 1.1 s 뒤 말 시작, 대답·효과음 약 2.2 s)을 본떴다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AssistantQuietTest {
    private val quiet = AssistantQuiet(startWaitMs = 4_000, quietMs = 600, maxWaitMs = 15_000)

    @Test
    fun waitsUntilAssistantFinishesSpeaking() = runTest {
        val speaking = MutableStateFlow(false)
        launch {
            delay(1_100); speaking.value = true // Gleo 대답 시작
            delay(1_400); speaking.value = false // 대답 끝
            delay(100); speaking.value = true // 끝맺음 효과음
            delay(800); speaking.value = false
        }
        val r = async { quiet.await(speaking) }
        assertEquals(QuietOutcome.AFTER_SPEECH, r.await())
        // 마지막 소리가 멈춘 3.4 s + 조용한 0.6 s 뒤에 재생한다
        assertEquals(4_000L, currentTime)
    }

    @Test
    fun shortGapInsideSpeechDoesNotStartPlayback() = runTest {
        val speaking = MutableStateFlow(true)
        launch {
            delay(1_000); speaking.value = false
            delay(300); speaking.value = true // 문장 사이 짧은 쉼
            delay(1_000); speaking.value = false
        }
        assertEquals(QuietOutcome.AFTER_SPEECH, quiet.await(speaking))
        assertEquals(2_900L, currentTime)
    }

    @Test
    fun playsAfterStartWaitWhenAssistantNeverSpeaks() = runTest {
        val speaking = MutableStateFlow(false)
        assertEquals(QuietOutcome.NO_SPEECH, quiet.await(speaking))
        assertEquals(4_000L, currentTime)
    }

    @Test
    fun givesUpAtMaxWaitWhenAssistantKeepsTalking() = runTest {
        val speaking = MutableStateFlow(true)
        assertEquals(QuietOutcome.TIMED_OUT, quiet.await(speaking))
        assertEquals(15_000L, currentTime)
    }
}
