package ai.heartz.core.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 녹음한 목소리를 또렷하게: (1) 저역 차단(80 Hz 2차 하이패스, 차량 웅웅거림·바람) (2) 스펙트럼 차감으로 꾸준한 잡음 줄이기
 * (3) 말소리 크기 맞추기(최대 진폭 제한). 16-bit mono PCM을 받아 같은 길이로 돌려준다. 순수 함수.
 * 잡음 모양은 녹음 안에서 가장 조용한 프레임들(하위 15%)의 주파수별 크기로 추정한다.
 */
object VoiceCleaner {
    private const val FRAME = 1024
    private const val HOP = FRAME / 2
    private const val OVER_SUBTRACT = 1.6
    private const val GAIN_FLOOR = 0.12
    private const val TARGET_RMS = 4_200.0
    private const val PEAK_LIMIT = 30_000.0
    private const val MAX_BOOST = 8.0

    fun clean(input: ShortArray, sampleRate: Int): ShortArray {
        if (input.isEmpty()) return input
        val x = DoubleArray(input.size) { input[it].toDouble() }
        if (x.all { it == 0.0 }) return ShortArray(input.size)
        highPass(x, sampleRate, 80.0)
        val y = spectralGate(x)
        normalize(y)
        return ShortArray(y.size) { y[it].coerceIn(-32768.0, 32767.0).toInt().toShort() }
    }

    /** 2차 버터워스 하이패스, 앞뒤로 두 번 걸어 위상 왜곡 없이(4차 효과) */
    private fun highPass(x: DoubleArray, rate: Int, cutoff: Double) {
        val w0 = 2 * PI * cutoff / rate
        val alpha = sin(w0) / (2 * sqrt(2.0) / 2 * 2)
        val cosw = cos(w0)
        val a0 = 1 + alpha
        val b0 = (1 + cosw) / 2 / a0
        val b1 = -(1 + cosw) / a0
        val b2 = (1 + cosw) / 2 / a0
        val a1 = -2 * cosw / a0
        val a2 = (1 - alpha) / a0
        fun pass(range: IntProgression) {
            var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
            for (i in range) {
                val v = x[i]
                val out = b0 * v + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
                x2 = x1; x1 = v; y2 = y1; y1 = out
                x[i] = out
            }
        }
        pass(x.indices)
        pass(x.indices.reversed())
    }

    private fun spectralGate(x: DoubleArray): DoubleArray {
        val n = x.size
        // 경계에서 프레임이 모자라지 않게 앞뒤로 덧댄다
        val padded = DoubleArray(n + 2 * FRAME).also { x.copyInto(it, FRAME) }
        val window = DoubleArray(FRAME) { sqrt(0.5 - 0.5 * cos(2 * PI * it / FRAME)) } // sqrt-Hann(분석·합성 50% 겹침 = 완전 복원)
        val frames = (padded.size - FRAME) / HOP + 1
        val re = Array(frames) { DoubleArray(FRAME) }
        val im = Array(frames) { DoubleArray(FRAME) }
        val mag = Array(frames) { DoubleArray(FRAME / 2 + 1) }
        val energy = DoubleArray(frames)
        for (f in 0 until frames) {
            val off = f * HOP
            for (i in 0 until FRAME) re[f][i] = padded[off + i] * window[i]
            fft(re[f], im[f])
            for (k in 0..FRAME / 2) {
                mag[f][k] = sqrt(re[f][k] * re[f][k] + im[f][k] * im[f][k])
                energy[f] += mag[f][k] * mag[f][k]
            }
        }
        // 잡음 모양: 가장 조용한 15% 프레임의 평균 크기
        val quiet = energy.indices.sortedBy { energy[it] }.take(max(1, frames * 15 / 100))
        val noise = DoubleArray(FRAME / 2 + 1) { k -> quiet.sumOf { mag[it][k] } / quiet.size }
        val prevGain = DoubleArray(FRAME / 2 + 1) { 1.0 }
        val out = DoubleArray(padded.size)
        for (f in 0 until frames) {
            for (k in 0..FRAME / 2) {
                val m = mag[f][k]
                val g = if (m <= 1e-9) GAIN_FLOOR else max(GAIN_FLOOR, 1 - OVER_SUBTRACT * noise[k] / m)
                // 음악 잡음(깜빡임)을 줄이려 프레임 사이 이득을 부드럽게
                val smooth = if (g > prevGain[k]) 0.7 * g + 0.3 * prevGain[k] else 0.5 * g + 0.5 * prevGain[k]
                prevGain[k] = smooth
                re[f][k] *= smooth; im[f][k] *= smooth
                if (k in 1 until FRAME / 2) {
                    re[f][FRAME - k] = re[f][k]; im[f][FRAME - k] = -im[f][k]
                }
            }
            ifft(re[f], im[f])
            val off = f * HOP
            for (i in 0 until FRAME) out[off + i] += re[f][i] * window[i]
        }
        return out.copyOfRange(FRAME, FRAME + n)
    }

    /** 말소리 구간(크기 상위 50% 프레임)의 RMS를 목표로 맞추되 최대 진폭은 넘지 않게 */
    private fun normalize(y: DoubleArray) {
        val block = 1024
        val rmsList = (0 until y.size step block).map { s ->
            val e = min(y.size, s + block)
            sqrt((s until e).sumOf { y[it] * y[it] } / (e - s))
        }.sortedDescending()
        val speech = rmsList.take(max(1, rmsList.size / 2)).average()
        if (speech < 1.0) return
        val peak = y.maxOf { abs(it) }
        val gain = min(min(TARGET_RMS / speech, MAX_BOOST), PEAK_LIMIT / max(peak, 1.0))
        for (i in y.indices) y[i] *= gain
    }

    private fun fft(re: DoubleArray, im: DoubleArray) = transform(re, im, inverse = false)

    private fun ifft(re: DoubleArray, im: DoubleArray) {
        transform(re, im, inverse = true)
        val n = re.size
        for (i in 0 until n) { re[i] /= n; im[i] /= n }
    }

    /** 반복형 radix-2 FFT(크기는 2의 거듭제곱) */
    private fun transform(re: DoubleArray, im: DoubleArray, inverse: Boolean) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = 2 * PI / len * if (inverse) 1 else -1
            val wr = cos(ang); val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0; var ci = 0.0
                for (k in 0 until len / 2) {
                    val ur = re[i + k]; val ui = im[i + k]
                    val vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ur + vr; im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi
                    val nr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr; cr = nr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
