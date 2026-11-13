package ai.heartz.app

import ai.heartz.app.platform.audio.PlayRequest
import ai.heartz.app.platform.audio.ReadFocus
import ai.heartz.app.platform.audio.TtsLetterReader
import ai.heartz.app.platform.speech.SpeakResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** fake TTS·포커스 기반 변조 목소리 읽기 테스트. 실제 Pleos TTS·오디오 포커스 증거가 아니다. */
@OptIn(ExperimentalCoroutinesApi::class)
class TtsLetterReaderTest {
    private val said = mutableListOf<String>()
    private val events = mutableListOf<String>()
    /** 문장마다 끝을 직접 알려 준다 */
    private var gates = ArrayDeque<CompletableDeferred<SpeakResult>>()
    private var focusOk = true
    private var onFocusLoss: ((Int) -> Unit)? = null

    private val focus = object : ReadFocus {
        override fun acquire(onChange: (Int) -> Unit): Boolean {
            events += "focus"
            onFocusLoss = onChange
            return focusOk
        }
        override fun release() { events += "unfocus" }
    }

    private fun TestScope.reader() = TtsLetterReader(
        scope = this,
        speak = { text ->
            said += text
            val g = CompletableDeferred<SpeakResult>()
            gates += g
            g.await()
        },
        stopSpeech = { events += "stop"; gates.lastOrNull()?.complete(SpeakResult.Stopped) },
        focus = focus,
    )

    private fun req(text: String?) = PlayRequest("s1", file = null, title = "이촌에서 온 사연", subtitle = "봄날", spokenText = text, durationMs = 9_000)

    private fun finishSentence() = gates.removeFirst().complete(SpeakResult.Done)

    @Test
    fun readsSentencesInOrderAndEndsWithFocusReleased() = runTest {
        val r = reader()
        r.play(req("오늘 퇴근길이 길었어요. 그래도 노을이 예뻤어요!"))
        runCurrent()
        assertEquals(listOf("오늘 퇴근길이 길었어요."), said)
        assertTrue(r.state.value.isPlaying)
        assertEquals("s1", r.state.value.storyId)
        finishSentence()
        runCurrent()
        assertEquals("그래도 노을이 예뻤어요!", said.last())
        finishSentence()
        runCurrent()
        val s = r.state.value
        assertTrue(s.ended)
        assertFalse(s.isPlaying)
        assertEquals("s1", s.storyId)
        assertEquals(listOf("focus", "unfocus"), events)
    }

    @Test
    fun pauseStopsSpeechAndResumeContinuesFromTheSameSentence() = runTest {
        val r = reader()
        r.play(req("첫 문장. 둘째 문장. 셋째 문장."))
        runCurrent()
        finishSentence()
        runCurrent()
        r.pause()
        runCurrent()
        assertFalse(r.state.value.isPlaying)
        assertFalse(r.state.value.idle)
        assertFalse(r.state.value.ended)
        r.resume()
        runCurrent()
        assertEquals(listOf("첫 문장.", "둘째 문장.", "둘째 문장."), said)
        assertTrue(r.state.value.isPlaying)
        r.stop()
    }

    @Test
    fun stopClearsWithoutClaimingTheEnd() = runTest {
        val r = reader()
        r.play(req("하나. 둘."))
        runCurrent()
        r.stop()
        runCurrent()
        val s = r.state.value
        assertFalse(s.ended)
        assertTrue(s.idle)
        assertNull(s.storyId)
        assertEquals("unfocus", events.last())
    }

    @Test
    fun ttsErrorIsReportedNotTreatedAsFinished() = runTest {
        val r = reader()
        r.play(req("하나. 둘."))
        runCurrent()
        gates.removeFirst().complete(SpeakResult.Error("engine Failed"))
        runCurrent()
        val s = r.state.value
        assertFalse(s.ended)
        assertFalse(s.isPlaying)
        assertTrue(s.error!!.contains("engine"))
    }

    @Test
    fun missingTextOrFocusRefusalDoesNotPlay() = runTest {
        val r = reader()
        r.play(req("  "))
        runCurrent()
        assertTrue(said.isEmpty())
        assertTrue(r.state.value.error != null)
        focusOk = false
        r.play(req("하나."))
        runCurrent()
        assertTrue(said.isEmpty())
        assertFalse(r.state.value.isPlaying)
    }

    @Test
    fun transientFocusLossPausesAndGainResumes() = runTest {
        val r = reader()
        r.play(req("하나. 둘."))
        runCurrent()
        onFocusLoss!!(android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        runCurrent()
        assertFalse(r.state.value.isPlaying)
        assertTrue(r.state.value.suppressedByFocus)
        onFocusLoss!!(android.media.AudioManager.AUDIOFOCUS_GAIN)
        runCurrent()
        assertTrue(r.state.value.isPlaying)
        assertEquals(listOf("하나.", "하나."), said)
        r.stop()
    }

    @Test
    fun longSentenceIsSplitIntoSpeakableChunks() {
        val text = "가".repeat(130) + " " + "나".repeat(130) + "."
        val parts = TtsLetterReader.sentences(text)
        assertTrue(parts.size >= 2)
        assertTrue(parts.all { it.length <= TtsLetterReader.MAX_CHUNK })
        assertEquals(text.replace(" ", ""), parts.joinToString("").replace(" ", ""))
    }
}
