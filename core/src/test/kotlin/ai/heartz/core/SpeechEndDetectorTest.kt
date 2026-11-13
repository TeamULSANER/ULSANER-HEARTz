package ai.heartz.core

import ai.heartz.core.drive.SpeechEndDetector
import ai.heartz.core.drive.SpeechVerdict
import org.junit.Assert.assertEquals
import org.junit.Test

/** 녹음 진폭(MediaRecorder.getMaxAmplitude, 0..32767)을 100 ms마다 넣어 말이 끝났는지 판단한다. */
class SpeechEndDetectorTest {
    private val d = SpeechEndDetector(silenceMs = 3_000, noSpeechMs = 8_000)

    /** [levels]를 100 ms 간격으로 넣고 처음으로 CONTINUE가 아닌 판정과 그 시각 */
    private fun run(levels: List<Int>): Pair<SpeechVerdict, Long> {
        levels.forEachIndexed { i, a ->
            val t = i * 100L
            val v = d.onLevel(t, a)
            if (v != SpeechVerdict.CONTINUE) return v to t
        }
        return SpeechVerdict.CONTINUE to -1
    }

    private fun rep(a: Int, ms: Int) = List(ms / 100) { a }

    @Test
    fun endsAfterSilenceFollowingSpeech() {
        val (v, t) = run(rep(300, 500) + rep(6_000, 2_000) + rep(400, 4_000))
        assertEquals(SpeechVerdict.SPEECH_ENDED, v)
        // 말이 2.5 s에 끝나고 3 s 조용하면 5.5 s 근처에 끝낸다
        assertEquals(5_500L, t)
    }

    @Test
    fun shortPausesInsideSpeechDoNotEnd() {
        val levels = rep(6_000, 1_000) + rep(300, 2_000) + rep(6_000, 1_000) + rep(300, 2_000) + rep(6_000, 1_000) + rep(300, 3_500)
        val (v, t) = run(levels)
        assertEquals(SpeechVerdict.SPEECH_ENDED, v)
        assertEquals(10_000L, t)
    }

    @Test
    fun noSpeechAtAllEndsAsNoSpeech() {
        val (v, t) = run(rep(300, 10_000))
        assertEquals(SpeechVerdict.NO_SPEECH, v)
        assertEquals(8_000L, t)
    }

    @Test
    fun singleClickIsNotSpeech() {
        // 100 ms짜리 툭 소리 한 번은 말로 치지 않는다
        val (v, _) = run(rep(300, 1_000) + listOf(20_000) + rep(300, 8_000))
        assertEquals(SpeechVerdict.NO_SPEECH, v)
    }

    @Test
    fun loudBackgroundRaisesThreshold() {
        // 주행 소음이 3,000 정도로 꾸준하면 그 소음은 말이 아니다
        val (v, _) = run(rep(3_000, 9_000))
        assertEquals(SpeechVerdict.NO_SPEECH, v)
    }
}
