package ai.heartz.core.moderation

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 검수 보조(기획서 4절): 음성 전사와 의심 구간 표시는 검수 **우선순위**를 돕는 데만 쓴다.
 * 플래그는 공개를 막거나 승인하지 않는다. 전사 실패는 자동 승인 사유가 아니며 "직접 들어 확인"으로 표시한다.
 */
@Serializable
enum class TranscriptStatus { DONE, NO_SPEECH, FAILED }

@Serializable
data class Transcript(
    val status: TranscriptStatus,
    val text: String? = null,
    /** 예: "Pleos SpeechToText 3.3.1 ON_DEVICE" */
    val engine: String,
    val reason: String? = null,
)

@Serializable
enum class ReviewFlagKind(val label: String) {
    PHONE_NUMBER("전화번호로 보이는 말"),
    ADDRESS_LIKE("집 주소로 보이는 말"),
    REPEATED_PHRASE("같은 말 반복"),
    SUDDEN_LOUD("갑작스러운 큰 소리"),
    NO_SPEECH("말소리를 찾지 못함"),
    TRANSCRIPT_UNAVAILABLE("받아쓰기 실패 · 직접 들어 확인"),
}

@Serializable
data class ReviewFlag(val kind: ReviewFlagKind, val detail: String? = null, val atMs: Long? = null)

@Serializable
data class ReviewAssist(
    val transcript: Transcript,
    val flags: List<ReviewFlag>,
    val createdAtMillis: Long,
)

object ReviewScreening {
    private val phone = Regex("""0\d{1,2}[-\s.]?\d{3,4}[-\s.]?\d{4}""")
    private val spokenDigits = Regex("""[공영일이삼사오육칠팔구][공영일이삼사오육칠팔구\s-]{6,}[공영일이삼사오육칠팔구]""")
    private val address = Regex("""\d+\s*동\s*\d+\s*호|\d+\s*번지""")

    fun flags(transcript: Transcript, loudMomentsMs: List<Long>): List<ReviewFlag> {
        val out = mutableListOf<ReviewFlag>()
        when (transcript.status) {
            TranscriptStatus.FAILED -> out += ReviewFlag(ReviewFlagKind.TRANSCRIPT_UNAVAILABLE, transcript.reason)
            TranscriptStatus.NO_SPEECH -> out += ReviewFlag(ReviewFlagKind.NO_SPEECH)
            TranscriptStatus.DONE -> {
                val text = transcript.text.orEmpty()
                if (phone.containsMatchIn(text) || spokenDigitRun(text)) out += ReviewFlag(ReviewFlagKind.PHONE_NUMBER)
                if (address.containsMatchIn(text)) out += ReviewFlag(ReviewFlagKind.ADDRESS_LIKE)
                repeatedToken(text)?.let { out += ReviewFlag(ReviewFlagKind.REPEATED_PHRASE, it) }
            }
        }
        loudMomentsMs.forEach { out += ReviewFlag(ReviewFlagKind.SUDDEN_LOUD, atMs = it) }
        return out
    }

    private fun spokenDigitRun(text: String): Boolean =
        spokenDigits.findAll(text).any { m -> m.value.count { !it.isWhitespace() && it != '-' } >= 8 }

    private fun repeatedToken(text: String): String? {
        val tokens = text.split(Regex("""[\s,.!?]+""")).filter { it.isNotBlank() }
        var run = 1
        for (i in 1 until tokens.size) {
            run = if (tokens[i] == tokens[i - 1]) run + 1 else 1
            if (run >= 5) return tokens[i]
        }
        return null
    }
}

/** 16-bit PCM 처리(순수 함수). STT 입력 변환과 큰 소리 구간 찾기에 쓴다. */
object Pcm16 {
    fun downmix(samples: ShortArray, channels: Int): ShortArray {
        if (channels <= 1) return samples
        val frames = samples.size / channels
        return ShortArray(frames) { f ->
            var sum = 0
            for (c in 0 until channels) sum += samples[f * channels + c]
            (sum / channels).toShort()
        }
    }

    /** 선형 보간 리샘플 */
    fun resample(samples: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        if (fromRate == toRate || samples.isEmpty()) return samples
        val outLen = (samples.size.toLong() * toRate / fromRate).toInt()
        val step = fromRate.toDouble() / toRate
        return ShortArray(outLen) { i ->
            val pos = i * step
            val i0 = pos.toInt().coerceAtMost(samples.size - 1)
            val i1 = (i0 + 1).coerceAtMost(samples.size - 1)
            val frac = pos - i0
            (samples[i0] * (1 - frac) + samples[i1] * frac).toInt().toShort()
        }
    }

    /** 최대 진폭이 [threshold] 미만이면 사실상 무음(입력 장치가 0을 주는 경우 포함) */
    fun isNearSilent(samples: ShortArray, threshold: Int = 300): Boolean = samples.all { abs(it.toInt()) < threshold }

    fun toLittleEndianBytes(samples: ShortArray): ByteArray {
        val out = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val v = samples[i].toInt()
            out[2 * i] = (v and 0xFF).toByte()
            out[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return out
    }

    /**
     * 주변보다 훨씬 큰 짧은 소리의 시작 시각(ms). 50 ms 창의 최댓값이 거의 최대 음량이고
     * RMS가 전체 중앙값의 6배 이상이면 "갑작스러운" 것으로 본다. 연속 창은 하나로 묶는다.
     */
    fun suddenLoudMoments(samples: ShortArray, rate: Int, windowMs: Int = 50): List<Long> {
        val win = rate * windowMs / 1000
        if (win <= 0 || samples.size < win) return emptyList()
        val n = samples.size / win
        val peaks = IntArray(n)
        val rms = DoubleArray(n)
        for (w in 0 until n) {
            var peak = 0
            var sq = 0.0
            for (i in w * win until (w + 1) * win) {
                val v = samples[i].toInt()
                peak = maxOf(peak, abs(v))
                sq += v.toDouble() * v
            }
            peaks[w] = peak
            rms[w] = sqrt(sq / win)
        }
        val median = rms.sorted()[n / 2].coerceAtLeast(300.0)
        val out = mutableListOf<Long>()
        var prevLoud = false
        for (w in 0 until n) {
            val loud = peaks[w] >= 28_000 && rms[w] >= median * 6
            if (loud && !prevLoud) out += w.toLong() * windowMs
            prevLoud = loud
        }
        return out
    }
}
