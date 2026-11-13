package ai.heartz.core.drive

import kotlin.math.max

/** 주행 중 짧은 질문에 대한 운전자의 답 */
enum class Reply { YES, NO, PUBLIC, PRIVATE, DISCARD, RERECORD, UNCLEAR }

/**
 * STT 결과 문자열 → [Reply]. 공백을 지우고 낱말 조각으로 판단한다.
 * 우선순위: 다시 녹음 > 버리기 > 나만 > 공개 > 아니오 > 예. 분명하지 않으면 UNCLEAR(호출자가 공개·삭제로 바꾸지 않는다).
 */
object VoiceReplies {
    private val rerecord = listOf("다시", "재녹음")
    private val discard = listOf("버려", "버리", "지워", "지우", "삭제", "없던걸로", "없던거로")
    private val privateWords = listOf("나만", "비공개", "혼자")
    private val publicWords = listOf("공개", "모두", "다같이", "모든사람")
    private val negation = listOf("하지마", "하지말", "말고", "말아", "안해", "싫")
    private val no = listOf(
        "아니", "괜찮", "됐어", "됐습", "안들", "안읽", "안열", "안남", "안보내", "안할", "안해", "별로", "다음에",
        "넘어가", "넘겨", "패스", "싫", "그만", "하지마", "하지말", "지마", "지말", "말아", "말고", "필요없",
    )
    private val yes = listOf(
        "응", "네", "예", "그래", "좋아", "좋습", "읽어", "들려", "열어", "틀어", "보내", "전달", "남겨", "해줘", "해주세요",
        "할래", "공감", "하트", "오케이", "ok", "어어", "당연", "부탁",
    )

    fun parse(text: String): Reply {
        val t = text.replace(Regex("""\s+"""), "").lowercase()
        if (t.isEmpty()) return Reply.UNCLEAR
        fun has(words: List<String>) = words.any { it in t }
        return when {
            has(rerecord) -> Reply.RERECORD
            has(discard) -> Reply.DISCARD
            has(privateWords) || ("공개" in t && has(negation)) -> Reply.PRIVATE
            has(publicWords) -> Reply.PUBLIC
            has(no) -> Reply.NO
            has(yes) -> Reply.YES
            else -> Reply.UNCLEAR
        }
    }

    /**
     * 음성 인식이 앱이 방금 말한 질문을 다시 들은 것인지(스피커 소리가 마이크로 들어감).
     * 들은 글이 8자 이상이고 두 글자 묶음의 85% 이상이 질문에 있으면 메아리로 본다. 짧은 실제 답("응", "변조 목소리로")은 메아리가 아니다.
     */
    fun isEcho(prompt: String, heard: String): Boolean {
        fun norm(x: String) = x.filter { it.isLetterOrDigit() }.lowercase()
        val h = norm(heard)
        if (h.length < ECHO_MIN_CHARS) return false
        val promptPairs = norm(prompt).windowed(2).toSet()
        val heardPairs = h.windowed(2)
        val shared = heardPairs.count { it in promptPairs }
        return shared.toDouble() / heardPairs.size >= ECHO_OVERLAP
    }

    private const val ECHO_MIN_CHARS = 8
    private const val ECHO_OVERLAP = 0.85

    private val originalVoice = listOf("그대로", "원래", "원본", "내목소리", "제목소리", "진짜목소리")
    private val modulatedVoice = listOf("변조", "기계", "바꿔", "바꾸", "티티에스", "tts", "다른목소리")

    /** "변조 목소리"·"내 목소리 그대로"처럼 목소리를 직접 말했을 때만. 예/아니오는 보지 않는다 */
    fun explicitVoiceMode(text: String): ai.heartz.core.model.VoiceMode? {
        val t = text.replace(Regex("""\s+"""), "").lowercase()
        return when {
            originalVoice.any { it in t } -> ai.heartz.core.model.VoiceMode.ORIGINAL
            modulatedVoice.any { it in t } -> ai.heartz.core.model.VoiceMode.MODULATED
            else -> null
        }
    }

    /** "변조 목소리로 전달할까요?"에 대한 답. 분명하지 않으면 null */
    fun parseVoiceMode(text: String): ai.heartz.core.model.VoiceMode? {
        val t = text.replace(Regex("""\s+"""), "").lowercase()
        if (t.isEmpty()) return null
        return when {
            explicitVoiceMode(text) != null -> explicitVoiceMode(text)
            else -> when (parse(text)) {
                Reply.YES, Reply.PUBLIC -> ai.heartz.core.model.VoiceMode.MODULATED
                Reply.NO, Reply.PRIVATE -> ai.heartz.core.model.VoiceMode.ORIGINAL
                else -> null
            }
        }
    }
}

enum class SpeechVerdict { CONTINUE, SPEECH_ENDED, NO_SPEECH }

/**
 * 녹음 중 진폭(0..32767)으로 "말이 끝났는지" 판단한다.
 * - 배경 소음 수준을 따라가며, 소음의 [ratio]배 이상(최소 [minLevel])이면 소리가 큰 것으로 본다.
 * - 큰 소리가 [minSpeechMs] 이상 이어져야 말로 친다(툭 소리 제외).
 * - 말한 뒤 [silenceMs] 동안 조용하면 SPEECH_ENDED, 처음부터 [noSpeechMs] 동안 말이 없으면 NO_SPEECH.
 */
class SpeechEndDetector(
    private val silenceMs: Long = 3_000,
    private val noSpeechMs: Long = 8_000,
    private val minSpeechMs: Long = 200,
    private val minLevel: Int = 1_200,
    private val ratio: Double = 2.5,
) {
    private var startAt = -1L
    private var floor = -1.0
    private var loudRunStart = -1L
    private var spoke = false
    private var quietSince = -1L

    fun onLevel(atMs: Long, amplitude: Int): SpeechVerdict {
        if (startAt < 0) startAt = atMs
        if (floor < 0) floor = minOf(amplitude, minLevel).toDouble()
        val threshold = max(minLevel.toDouble(), floor * ratio)
        val loud = amplitude > threshold
        if (loud) {
            if (loudRunStart < 0) loudRunStart = atMs
            if (atMs - loudRunStart >= minSpeechMs) spoke = true
            if (spoke) quietSince = -1
        } else {
            loudRunStart = -1
            floor = floor * 0.9 + amplitude * 0.1
            if (spoke && quietSince < 0) quietSince = atMs
        }
        return when {
            spoke && quietSince >= 0 && atMs - quietSince >= silenceMs -> SpeechVerdict.SPEECH_ENDED
            !spoke && atMs - startAt >= noSpeechMs -> SpeechVerdict.NO_SPEECH
            else -> SpeechVerdict.CONTINUE
        }
    }
}
