package ai.heartz.app.platform.speech

/**
 * STT 결과 조각 모으기. Pleos STT는 말이 멈출 때마다(끝점 검출) 조각을 닫고 completed=true를 보낸 뒤 다음 조각을 다시 인식한다.
 * - [keepAll]=false(질문의 답): 첫 확정 결과로 끝낸다.
 * - [keepAll]=true(녹음 받아쓰기): 모든 확정 조각을 모아 줄바꿈으로 잇는다. 확정되지 않은 중간 결과는 쓰지 않는다.
 */
class TranscriptSegments(private val keepAll: Boolean) {
    private val finals = mutableListOf<String>()
    private var partial: String = ""

    /** 확정되지 않은 중간 결과가 남아 있는지(마지막 조각이 아직 닫히지 않음) */
    val pending: Boolean get() = partial.isNotBlank()

    /** 답 모드에서 확정 결과가 오면 결과를 돌려준다. 받아쓰기 모드는 항상 null(끝은 [finish]) */
    fun onUpdated(text: String, completed: Boolean): RecognizeResult? {
        if (!completed) {
            partial = text
            return null
        }
        partial = ""
        val t = text.trim()
        if (!keepAll) return if (t.isEmpty()) RecognizeResult.NoSpeech else RecognizeResult.Text(t)
        if (t.isNotEmpty()) finals += t
        return null
    }

    fun onEnded() = Unit

    fun finish(): RecognizeResult =
        if (finals.isEmpty()) RecognizeResult.NoSpeech else RecognizeResult.Text(finals.joinToString("\n"))
}
