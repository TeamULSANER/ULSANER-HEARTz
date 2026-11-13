package ai.heartz.app

import ai.heartz.app.platform.speech.RecognizeResult
import ai.heartz.app.platform.speech.SpeakResult
import ai.heartz.app.ui.drive.CloseReason
import ai.heartz.app.ui.drive.LetterVoiceGuide
import ai.heartz.app.ui.drive.PromptFocus
import ai.heartz.app.ui.drive.VoiceStep
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** fake 음성·포커스 기반 주행 중 사연 흐름 테스트. 실제 TTS·STT·Gleo·오디오 포커스 증거가 아니다. */
@OptIn(ExperimentalCoroutinesApi::class)
class LetterVoiceGuideTest {
    private val events = mutableListOf<String>()
    private val said = mutableListOf<String>()
    private val replies = ArrayDeque<String>()
    /** 답 큐가 비었을 때 계속 듣는 중(취소될 때까지)이면 true */
    private var keepListening = false
    private val played = mutableListOf<String>()
    private val hearted = mutableListOf<String>()
    private val dismissed = mutableListOf<String>()
    private var heartOk = true
    private var quietWaits = 0
    private var queued = false
    private var clearGate: CompletableDeferred<Unit>? = null

    private val focus = object : PromptFocus {
        override fun duck(): Boolean { events += "duck"; return true }
        override fun release() { events += "release" }
        override fun holdPause() { events += "hold-pause" }
        override fun releasePause() { events += "release-pause" }
    }

    private fun TestScope.guide() = LetterVoiceGuide(
        scope = this,
        speaker = { text -> said += text; events += "speak"; SpeakResult.Done },
        answers = {
            events += "listen"
            replies.removeFirstOrNull()?.let { RecognizeResult.Text(it) }
                ?: if (keepListening) awaitCancellation() else RecognizeResult.NoSpeech
        },
        focus = focus,
        stopVoice = { events += "stop-voice" },
        awaitAssistantQuiet = { quietWaits++ },
        awaitOtherSpeechQuiet = { clearGate?.await(); events += "clear" },
        actions = object : LetterVoiceGuide.Actions {
            override fun play(storyId: String) { events += "play"; played += storyId }
            override suspend fun heart(storyId: String): Boolean { hearted += storyId; return heartOk }
            override fun dismiss(storyId: String) { events += "dismiss"; dismissed += storyId }
            override fun hasQueued(): Boolean = queued
        },
        closeDelayMs = 5_000,
        skipCloseMs = 1_500,
        resumeMusicAfterMs = 2_000,
        followUpWindowMs = 10_000,
        clock = { testScheduler.currentTime },
    )

    @Test
    fun arrivalIsAnnouncedAndReadRightAwayWithoutAsking() = runTest {
        val g = guide()
        g.arrive("s1", "이촌")
        advanceUntilIdle()
        assertEquals(listOf("이촌에서 사연이 도착했어요. 읽어드릴게요."), said)
        assertEquals(listOf("hold-pause", "clear", "speak", "play"), events)
        assertEquals(listOf("s1"), played)
        assertTrue("listen" !in events)
    }

    @Test
    fun announcementWaitsWhileNavigationIsSpeaking() = runTest {
        clearGate = CompletableDeferred()
        val g = guide()
        g.arrive("s1", "이촌")
        runCurrent()
        assertTrue(said.isEmpty())
        clearGate!!.complete(Unit)
        runCurrent()
        assertEquals(1, said.size)
    }

    @Test
    fun heartIsThankedThenMusicReturnsAndCardCloses() = runTest {
        replies += "응 남겨줘"
        val g = guide()
        g.letterPlaying("s1")
        g.letterEnded("s1")
        runCurrent()
        assertTrue(said.first().contains("공감 하트"))
        assertEquals(listOf("s1"), hearted)
        assertEquals("사연자님께 마음이 전달되었어요.", said.last())
        assertEquals(VoiceStep.Closing("s1", CloseReason.HEARTED), g.step.value)
        assertTrue("release-pause" !in events)
        advanceTimeBy(2_100)
        assertEquals(1, events.count { it == "release-pause" })
        assertTrue(dismissed.isEmpty())
        advanceTimeBy(3_000)
        assertEquals(listOf("s1"), dismissed)
    }

    /** 앱이 말한 공감 질문을 음성 인식이 다시 들어도 하트로 치지 않는다 */
    @Test
    fun ownHeartQuestionHeardBackIsNotAHeart() = runTest {
        replies += "이번 사연은 어떠셨어요 공감 하트를 남길까요"
        val g = guide()
        g.letterEnded("s1")
        advanceUntilIdle()
        assertTrue(hearted.isEmpty())
    }

    @Test
    fun realAnswerAfterTheEchoStillCounts() = runTest {
        replies += "공감 하트를 남길까요"
        replies += "응 남겨줘"
        val g = guide()
        g.letterEnded("s1")
        advanceUntilIdle()
        assertEquals(listOf("s1"), hearted)
    }

    @Test
    fun noHeartIsGentlyEncouragedThenCloses() = runTest {
        replies += "별로"
        val g = guide()
        g.letterEnded("s1")
        advanceUntilIdle()
        assertTrue(hearted.isEmpty())
        assertEquals("다음에는 따뜻한 마음 하나 남겨보아요.", said.last())
        assertEquals(listOf("s1"), dismissed)
    }

    @Test
    fun noAnswerAlsoGetsTheGentleLine() = runTest {
        val g = guide()
        g.letterEnded("s1")
        advanceUntilIdle()
        assertEquals("다음에는 따뜻한 마음 하나 남겨보아요.", said.last())
    }

    @Test
    fun touchNoHeartWhileDrivingSaysItButGleoLetsGleoSpeak() = runTest {
        keepListening = true
        val g = guide()
        g.letterEnded("s1")
        runCurrent()
        g.noHeart("s1", viaGleo = false, announce = true)
        advanceUntilIdle()
        assertEquals("다음에는 따뜻한 마음 하나 남겨보아요.", said.last())
        g.letterEnded("s2")
        runCurrent()
        val before = said.size
        g.noHeart("s2", viaGleo = true, announce = false)
        advanceUntilIdle()
        assertEquals(before, said.size)
        assertEquals(listOf("s1", "s2"), dismissed)
    }

    @Test
    fun queuedStoryFollowsRightAfterTheDecisionWithThisTimePhrase() = runTest {
        replies += "남겨줘"
        queued = true
        val g = guide()
        g.letterPlaying("s1")
        g.letterEnded("s1")
        runCurrent()
        // 고마움 안내 뒤 기다리지 않고 바로 닫고(음악은 계속 멈춤) 다음 사연으로
        assertEquals(listOf("s1"), dismissed)
        assertTrue("release-pause" !in events)
        assertEquals(VoiceStep.Idle, g.step.value)
        queued = false
        g.arrive("s2", "서빙고")
        runCurrent()
        assertEquals("이번에는 서빙고에서 사연이 도착했어요. 읽어드릴게요.", said.last())
        advanceTimeBy(5_000)
        assertTrue("release-pause" !in events)
    }

    @Test
    fun arrivalWithinTenSecondsSaysThisTimeAfterwardsPlain() = runTest {
        val g = guide()
        g.letterEnded("s1")
        advanceUntilIdle() // 답 없음 → 5초 뒤 닫힘
        advanceTimeBy(9_000)
        g.arrive("s2", "서빙고")
        runCurrent()
        assertEquals("이번에는 서빙고에서 사연이 도착했어요. 읽어드릴게요.", said.last())
        g.letterEnded("s2")
        advanceUntilIdle()
        advanceTimeBy(11_000)
        g.arrive("s3", "이촌")
        runCurrent()
        assertEquals("이촌에서 사연이 도착했어요. 읽어드릴게요.", said.last())
    }

    @Test
    fun touchHeartWhileDrivingIsThankedButGleoHeartLetsGleoSpeak() = runTest {
        keepListening = true
        val g = guide()
        g.letterEnded("s1")
        runCurrent()
        g.heart("s1", viaGleo = true, announce = false)
        advanceUntilIdle()
        assertTrue(said.none { it.contains("마음") })
        g.letterEnded("s2")
        runCurrent()
        g.heart("s2", viaGleo = false, announce = true)
        advanceUntilIdle()
        assertEquals("사연자님께 마음이 전달되었어요.", said.last())
        assertEquals(listOf("s1", "s2"), hearted)
    }

    @Test
    fun heartFailureIsSaidAndNotClaimedAsDone() = runTest {
        replies += "남겨줘"
        heartOk = false
        val g = guide()
        g.letterEnded("s1")
        runCurrent()
        assertTrue(said.last(), said.last().contains("못했어요"))
        assertEquals(VoiceStep.Closing("s1", CloseReason.NO_HEART), g.step.value)
    }

    @Test
    fun gleoOpenPlaysAfterGleoSpeaksWithMusicHeld() = runTest {
        val g = guide()
        g.open("s1", viaGleo = true)
        advanceUntilIdle()
        assertEquals(1, quietWaits)
        assertEquals("hold-pause", events[events.indexOf("play") - 1])
    }

    @Test
    fun stoppingEarlyLetsMusicBackAndCancelAlwaysDoes() = runTest {
        val g = guide()
        g.letterPlaying("s1")
        g.letterStopped("s1")
        runCurrent()
        assertEquals(listOf("hold-pause", "release-pause"), events)
        g.letterPlaying("s2")
        g.cancel()
        runCurrent()
        assertEquals("release-pause", events.last())
    }

    @Test
    fun newArrivalIsIgnoredWhileBusy() = runTest {
        keepListening = true
        val g = guide()
        g.letterEnded("s1")
        runCurrent()
        g.arrive("s2", "서빙고")
        runCurrent()
        assertTrue(played.isEmpty())
        g.cancel()
    }
}
