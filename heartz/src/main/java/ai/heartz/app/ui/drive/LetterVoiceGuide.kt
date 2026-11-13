package ai.heartz.app.ui.drive

import ai.heartz.app.HeartzLog
import ai.heartz.app.platform.speech.RecognizeResult
import ai.heartz.core.drive.Reply
import ai.heartz.core.drive.VoiceReplies
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 사연 흐름의 오디오 포커스.
 * - [duck]/[release]: 질문하는 동안 다른 소리(음악)를 줄여 달라는 요청. 실제로 줄일지는 차량 오디오 정책이 정한다.
 * - [holdPause]/[releasePause]: 사연을 알릴 때부터 공감을 정한 뒤 잠시까지 음악을 멈춰 둔다(재생 포커스와 같은 앱 포커스로 묶임).
 */
interface PromptFocus {
    fun duck(): Boolean
    fun release()
    fun holdPause()
    fun releasePause()
}

enum class CloseReason { HEARTED, NO_HEART, SKIPPED }

sealed interface VoiceStep {
    data object Idle : VoiceStep
    /** "OO에서 사연이 도착했어요. 읽어드릴게요." 말하는 중 */
    data class Announcing(val storyId: String) : VoiceStep
    /** "공감 하트를 남길까요?" 묻고 답을 듣는 중 */
    data class AskingHeart(val storyId: String) : VoiceStep
    /** 잠시 결과를 보여 준 뒤 대기 화면(또는 줄 선 다음 사연)으로 */
    data class Closing(val storyId: String, val reason: CloseReason) : VoiceStep
}

/**
 * 주행 중 사연 음성 흐름(앱이 먼저 말하는 부분). Gleo는 앱이 먼저 말을 걸게 하는 API가 없어서
 * 안내·질문은 Pleos TTS로 하고 답은 Pleos STT로 바로 듣는다. 같은 동작을 Gleo 명령·화면 버튼으로도 할 수 있다.
 * - 도착: 음악을 멈추고 "OO에서 사연이 도착했어요. 읽어드릴게요." → 묻지 않고 바로 재생.
 *   앞 사연을 닫은 지 [followUpWindowMs] 안이면(줄 선 사연 포함) "이번에는 OO에서 …".
 * - 다 들음: 음악은 멈춘 채 "공감 하트를 남길까요?" → 예: 하트 + "사연자님께 마음이 전달되었어요." / 아니오·답 없음: "다음에는 따뜻한 마음 하나 남겨보아요."
 * - 줄 선 사연이 있으면 바로 닫고 이어 간다. 없으면 [resumeMusicAfterMs] 뒤 음악을 돌려주고 [closeDelayMs] 뒤 대기 화면.
 * - 한 번에 하나만. Gleo로 정하면 Gleo가 대답하므로 앱은 결과를 말하지 않는다.
 */
class LetterVoiceGuide(
    private val scope: CoroutineScope,
    private val speaker: SpeakPort,
    private val answers: AnswerSource,
    private val focus: PromptFocus,
    private val stopVoice: () -> Unit,
    private val awaitAssistantQuiet: suspend () -> Unit,
    /** 질문 전에 내비 안내·다른 음성이 말하고 있으면 끝날 때까지 기다린다 */
    private val awaitOtherSpeechQuiet: suspend () -> Unit = {},
    private val actions: Actions,
    private val closeDelayMs: Long = 5_000,
    private val skipCloseMs: Long = 1_500,
    /** 공감 여부를 정한 뒤 음악을 다시 틀기까지 */
    private val resumeMusicAfterMs: Long = 2_000,
    /** 앞 사연을 닫고 이 시간 안에 온 사연은 "이번에는 …"으로 알린다 */
    private val followUpWindowMs: Long = 10_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    interface Actions {
        fun play(storyId: String)
        /** 공감 하트를 서버에 남긴다. 실패하면 false */
        suspend fun heart(storyId: String): Boolean
        /** 카드를 닫는다(줄 선 다음 사연이 있으면 이어서 도착한다) */
        fun dismiss(storyId: String)
        /** 다음 차례로 줄 선 사연이 있는지 */
        fun hasQueued(): Boolean = false
    }

    private val _step = MutableStateFlow<VoiceStep>(VoiceStep.Idle)
    val step: StateFlow<VoiceStep> = _step.asStateFlow()

    private var job: Job? = null
    private var ducked = false
    private var paused = false
    private var lastClosedAt: Long? = null
    /** 닫은 뒤 다음 사연이 이어졌는지(이어지지 않으면 음악을 돌려준다) */
    private var continuedAfterClose = false

    val busy: Boolean get() = _step.value != VoiceStep.Idle

    /** 사연 도착: 알리고 묻지 않고 바로 읽는다 */
    fun arrive(storyId: String, placeShortName: String) {
        if (busy) return
        val followUp = lastClosedAt?.let { clock() - it <= followUpWindowMs } == true
        HeartzLog.i("voice", "arrive story=$storyId followUp=$followUp")
        continuedAfterClose = true
        _step.value = VoiceStep.Announcing(storyId)
        holdPause()
        job = scope.launch {
            awaitOtherSpeechQuiet()
            speaker.speak((if (followUp) "이번에는 " else "") + "${placeShortName}에서 사연이 도착했어요. 읽어드릴게요.")
            _step.value = VoiceStep.Idle
            actions.play(storyId)
        }
    }

    fun letterEnded(storyId: String) {
        stopCurrent()
        HeartzLog.i("voice", "story ended story=$storyId -> ask heart")
        _step.value = VoiceStep.AskingHeart(storyId)
        job = scope.launch {
            val reply = ask("이번 사연은 어떠셨어요? 공감 하트를 남길까요?")
            HeartzLog.i("voice", "heart reply=$reply")
            if (reply == Reply.YES || reply == Reply.PUBLIC) sendHeart(storyId, announce = true)
            else declineHeart(storyId, announce = true)
        }
    }

    /** 열기(화면 버튼·Gleo). Gleo면 Gleo가 대답을 마친 뒤 재생한다. 음악은 공감을 정할 때까지 멈춰 둔다. */
    fun open(storyId: String, viaGleo: Boolean) {
        stopCurrent()
        continuedAfterClose = true
        _step.value = VoiceStep.Idle
        holdPause()
        job = scope.launch {
            if (viaGleo) awaitAssistantQuiet()
            actions.play(storyId)
        }
    }

    /** 주행 중 사연 재생이 시작됨(도착 안내·버튼·Gleo 어느 경로든). 음악을 멈춘 상태로 묶는다. */
    fun letterPlaying(storyId: String) {
        continuedAfterClose = true
        HeartzLog.i("voice", "story playing story=$storyId -> hold music paused")
        holdPause()
    }

    /** 끝까지 듣기 전에 멈춤(그만). 질문하지 않으므로 음악을 바로 돌려준다. */
    fun letterStopped(storyId: String) {
        if (_step.value is VoiceStep.AskingHeart || _step.value is VoiceStep.Closing) return
        HeartzLog.i("voice", "story stopped early story=$storyId -> music back")
        releasePause()
    }

    fun skip(storyId: String) {
        stopCurrent()
        job = scope.launch { close(storyId, CloseReason.SKIPPED) }
    }

    /**
     * 하트(화면 버튼·Gleo). [announce]면 결과를 말한다(주행 중 화면 버튼). Gleo로 남기면 Gleo가 대답하므로 말하지 않는다.
     * 성공 여부를 돌려준다.
     */
    fun heart(storyId: String, viaGleo: Boolean, announce: Boolean): Deferred<Boolean> {
        stopCurrent()
        HeartzLog.i("voice", "heart by ${if (viaGleo) "gleo" else "touch"} story=$storyId")
        val result = CompletableDeferred<Boolean>()
        job = scope.launch { sendHeart(storyId, announce = announce && !viaGleo, result) }
        return result
    }

    /** 하트 없이 닫기(화면 버튼·Gleo). [announce]면 다음을 권하는 한마디를 한다(주행 중 버튼). Gleo면 Gleo가 대답한다. */
    fun noHeart(storyId: String, viaGleo: Boolean, announce: Boolean) {
        stopCurrent()
        job = scope.launch { declineHeart(storyId, announce = announce && !viaGleo) }
    }

    private suspend fun declineHeart(storyId: String, announce: Boolean) {
        _step.value = VoiceStep.Closing(storyId, CloseReason.NO_HEART)
        if (announce) say(NO_HEART_LINE)
        close(storyId, CloseReason.NO_HEART)
    }

    private suspend fun say(text: String) {
        ducked = focus.duck()
        try {
            speaker.speak(text)
        } finally {
            releaseDuck()
        }
    }

    fun cancel() {
        stopCurrent()
        releasePause()
        _step.value = VoiceStep.Idle
    }

    private fun holdPause() {
        if (paused) return
        paused = true
        focus.holdPause()
    }

    private fun releasePause() {
        if (!paused) return
        paused = false
        focus.releasePause()
    }

    private fun stopCurrent() {
        val j = job ?: return
        job = null
        if (j.isActive) {
            j.cancel()
            stopVoice()
        }
        releaseDuck()
    }

    private suspend fun ask(question: String): Reply {
        awaitOtherSpeechQuiet()
        ducked = focus.duck()
        try {
            speaker.speak(question)
            var heard = (answers.listenForAnswer() as? RecognizeResult.Text)?.text
            if (heard != null && VoiceReplies.isEcho(question, heard)) {
                // 스피커로 나간 질문을 마이크가 다시 들었다. 답으로 쓰지 않고 한 번 더 듣는다
                HeartzLog.i("voice", "echo of own question ignored")
                heard = (answers.listenForAnswer() as? RecognizeResult.Text)?.text?.takeUnless { VoiceReplies.isEcho(question, it) }
            }
            return heard?.let { VoiceReplies.parse(it) } ?: Reply.UNCLEAR
        } finally {
            releaseDuck()
        }
    }

    private fun releaseDuck() {
        if (ducked) {
            ducked = false
            focus.release()
        }
    }

    private suspend fun sendHeart(storyId: String, announce: Boolean, result: CompletableDeferred<Boolean>? = null) {
        val ok = runCatching { actions.heart(storyId) }.getOrDefault(false)
        result?.complete(ok)
        HeartzLog.i("voice", "heart story=$storyId ok=$ok")
        _step.value = VoiceStep.Closing(storyId, if (ok) CloseReason.HEARTED else CloseReason.NO_HEART)
        if (announce) say(if (ok) HEARTED_LINE else "하트를 남기지 못했어요.")
        close(storyId, if (ok) CloseReason.HEARTED else CloseReason.NO_HEART)
    }

    private suspend fun close(storyId: String, reason: CloseReason) {
        _step.value = VoiceStep.Closing(storyId, reason)
        if (actions.hasQueued()) {
            // 줄 선 사연이 있으면 음악을 멈춘 채 바로 이어 간다
            HeartzLog.i("voice", "next story queued -> continue")
            continuedAfterClose = false
            finishClose(storyId)
            delay(resumeMusicAfterMs)
            if (!continuedAfterClose) releasePause() // 이어질 사연이 오지 않았으면 음악을 돌려준다
            return
        }
        val total = if (reason == CloseReason.SKIPPED) skipCloseMs else closeDelayMs
        val resumeAt = minOf(resumeMusicAfterMs, total)
        delay(resumeAt)
        releasePause()
        delay(total - resumeAt)
        finishClose(storyId)
    }

    private fun finishClose(storyId: String) {
        lastClosedAt = clock()
        _step.value = VoiceStep.Idle
        actions.dismiss(storyId)
    }

    companion object {
        const val HEARTED_LINE = "사연자님께 마음이 전달되었어요."
        const val NO_HEART_LINE = "다음에는 따뜻한 마음 하나 남겨보아요."
    }
}
