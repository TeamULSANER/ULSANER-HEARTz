package ai.heartz.core

import ai.heartz.core.audio.VoiceCleaner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** 녹음 잡음 정리(순수 함수). 합성 신호로 확인한다(실제 마이크 녹음 증거 아님). */
class VoiceCleanerTest {
    private val rate = 16_000

    private fun tone(n: Int, f: Double, amp: Double) = DoubleArray(n) { amp * sin(2 * PI * f * it / rate) }
    private fun noise(n: Int, amp: Double, seed: Int) = Random(seed).let { r -> DoubleArray(n) { amp * (r.nextDouble() * 2 - 1) } }
    private fun mix(vararg parts: DoubleArray) = DoubleArray(parts[0].size) { i -> parts.sumOf { it[i] } }
    private fun toShorts(d: DoubleArray) = ShortArray(d.size) { d[it].coerceIn(-32768.0, 32767.0).toInt().toShort() }
    private fun rms(s: ShortArray, from: Int, to: Int) = sqrt((from until to).sumOf { s[it].toDouble() * s[it] } / (to - from))

    /** Goertzel로 특정 주파수 세기 */
    private fun power(s: ShortArray, from: Int, to: Int, f: Double): Double {
        val k = 2 * cos(2 * PI * f / rate)
        var a = 0.0; var b = 0.0
        for (i in from until to) { val c = s[i] + k * a - b; b = a; a = c }
        return sqrt(maxOf(a * a + b * b - k * a * b, 0.0)) / (to - from)
    }

    @Test
    fun steadyNoiseIsReducedAroundSpeech() {
        val n = rate * 4
        val speech = DoubleArray(n).also { val t = tone(rate * 2, 300.0, 6_000.0); t.copyInto(it, rate) }
        val input = toShorts(mix(speech, noise(n, 1_500.0, 1)))
        val out = VoiceCleaner.clean(input, rate)
        // 말소리(1–3초) 대비 잡음만 있는 구간(0–0.8초)의 크기 비율이 2배(6 dB) 이상 좋아진다
        val before = power(input, rate + 1_000, 3 * rate - 1_000, 300.0) / rms(input, 0, rate * 8 / 10)
        val after = power(out, rate + 1_000, 3 * rate - 1_000, 300.0) / rms(out, 0, rate * 8 / 10)
        assertTrue("before=$before after=$after", after >= before * 2)
    }

    @Test
    fun lowRumbleIsCut() {
        val n = rate * 2
        val input = toShorts(mix(tone(n, 30.0, 8_000.0), tone(n, 500.0, 3_000.0)))
        val out = VoiceCleaner.clean(input, rate)
        val ratioBefore = power(input, 4_000, n - 4_000, 30.0) / power(input, 4_000, n - 4_000, 500.0)
        val ratioAfter = power(out, 4_000, n - 4_000, 30.0) / power(out, 4_000, n - 4_000, 500.0)
        assertTrue("before=$ratioBefore after=$ratioAfter", ratioAfter <= ratioBefore / 4) // 12 dB 이상
    }

    @Test
    fun lengthIsKeptAndOutputNeverClips() {
        val input = toShorts(mix(tone(rate * 2, 400.0, 40_000.0), noise(rate * 2, 500.0, 2)))
        val out = VoiceCleaner.clean(input, rate)
        assertEquals(input.size, out.size)
        assertTrue(out.maxOf { kotlin.math.abs(it.toInt()) } <= 31_200)
    }

    @Test
    fun quietVoiceIsBroughtToAClearLevel() {
        // 말소리에는 쉼이 있다: 0.6초 말 + 0.4초 쉼을 반복
        val speech = tone(rate * 3, 300.0, 600.0).also { d -> for (i in d.indices) if (i % rate >= rate * 6 / 10) d[i] = 0.0 }
        val input = toShorts(mix(speech, noise(rate * 3, 30.0, 3)))
        val out = VoiceCleaner.clean(input, rate)
        assertTrue(rms(out, 0, out.size) >= rms(input, 0, input.size) * 3)
    }

    @Test
    fun silenceStaysSilent() {
        val out = VoiceCleaner.clean(ShortArray(rate), rate)
        assertTrue(out.all { it.toInt() == 0 })
    }
}
