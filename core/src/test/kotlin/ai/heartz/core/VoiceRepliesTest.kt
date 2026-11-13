package ai.heartz.core

import ai.heartz.core.drive.Reply
import ai.heartz.core.drive.VoiceReplies
import ai.heartz.core.model.VoiceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/** 주행 중 짧은 음성 답(STT 결과 문자열) 해석. 분명하지 않으면 UNCLEAR로 둔다. */
class VoiceRepliesTest {
    private fun r(text: String) = VoiceReplies.parse(text)

    @Test
    fun yesAnswers() {
        listOf("응", "네", "예 읽어 주세요", "읽어줘", "들려줘", "열어 봐", "그래", "좋아", "보내 줘", "남겨줘", "전달해 줘").forEach {
            assertEquals(it, Reply.YES, r(it))
        }
    }

    @Test
    fun noAnswers() {
        listOf("아니", "아니요", "괜찮아", "됐어", "안 들을래", "안 읽어도 돼", "별로", "다음에", "넘어가", "싫어", "보내지 마", "남기지 마").forEach {
            assertEquals(it, Reply.NO, r(it))
        }
    }

    @Test
    fun visibilityAnswers() {
        assertEquals(Reply.PUBLIC, r("공개로 보내줘"))
        assertEquals(Reply.PUBLIC, r("모두에게 공개"))
        assertEquals(Reply.PRIVATE, r("나만 들을래"))
        assertEquals(Reply.PRIVATE, r("비공개로"))
        assertEquals(Reply.PRIVATE, r("공개하지 마"))
        assertEquals(Reply.PRIVATE, r("공개는 말고"))
    }

    @Test
    fun discardAnswers() {
        listOf("버려", "버려 줘", "지워줘", "삭제해", "없던 걸로 해").forEach { assertEquals(it, Reply.DISCARD, r(it)) }
    }

    @Test
    fun heartAnswer() {
        assertEquals(Reply.YES, r("하트 남겨 줘"))
        assertEquals(Reply.YES, r("공감할래"))
        assertEquals(Reply.NO, r("하트는 안 남길래"))
    }

    @Test
    fun rerecordAnswers() {
        listOf("다시", "다시 녹음할래", "재녹음", "다시 할게요", "처음부터 다시").forEach { assertEquals(it, Reply.RERECORD, r(it)) }
    }

    @Test
    fun voiceModeAnswers() {
        listOf("응", "변조 목소리로", "기계 목소리로 해 줘", "네 바꿔 주세요").forEach {
            assertEquals(it, VoiceMode.MODULATED, VoiceReplies.parseVoiceMode(it))
        }
        listOf("아니", "내 목소리 그대로", "원래 목소리로", "괜찮아요").forEach {
            assertEquals(it, VoiceMode.ORIGINAL, VoiceReplies.parseVoiceMode(it))
        }
        assertEquals(null, VoiceReplies.parseVoiceMode("음"))
    }

    @Test
    fun unclear() {
        listOf("", "  ", "음 글쎄", "오늘 날씨 어때").forEach { assertEquals(it, Reply.UNCLEAR, r(it)) }
    }

    /** AVD 2026-11-13 로그: 앱이 말한 질문을 음성 인식이 다시 들어 "변조"·"전달"을 답으로 오해했다 */
    @Test
    fun ownQuestionHeardBackIsAnEchoNotAnAnswer() {
        val voiceQ = "원하시면 변조 목소리로 사연을 전달할까요?"
        assertTrue(VoiceReplies.isEcho(voiceQ, "원하시면 변조 목소리로 사연을 전달할까"))
        assertTrue(VoiceReplies.isEcho(voiceQ, "팀은 변조 목소리로 사연을 전달할까요?"))
        assertTrue(VoiceReplies.isEcho(voiceQ, "원하시면 전조 목소리로 사연을 전달할까요?"))
        val deliverQ = "이 사연을 전달할까요? 다시 녹음하려면 '다시'라고 말해 주세요."
        assertTrue(VoiceReplies.isEcho(deliverQ, "이 사연을 전달할까요"))
        assertTrue(VoiceReplies.isEcho("이번 사연은 어떠셨어요? 공감 하트를 남길까요?", "공감 하트를 남길까요"))
    }

    @Test
    fun realShortAnswersAreNotEchoes() {
        val voiceQ = "원하시면 변조 목소리로 사연을 전달할까요?"
        for (a in listOf("응", "변조 목소리로", "응 변조로 해줘", "아니 그대로", "응 변조 목소리로 전달해줘", "버려")) {
            assertFalse(a, VoiceReplies.isEcho(voiceQ, a))
        }
        assertFalse(VoiceReplies.isEcho("이 사연을 전달할까요? 다시 녹음하려면 '다시'라고 말해 주세요.", "응 전달해 줘"))
    }
}
