package ai.heartz.app.ui.drive

import ai.heartz.app.HeartzLog
import ai.heartz.app.data.BackendMode
import ai.heartz.app.data.PendingDraft
import ai.heartz.app.data.SaveOutcome
import ai.heartz.app.platform.record.RecordResult
import ai.heartz.app.platform.speech.RecognizeResult
import ai.heartz.app.platform.speech.SpeakResult
import ai.heartz.core.drive.Reply
import ai.heartz.core.drive.RoadsideAnchor
import ai.heartz.core.drive.RoadsideFinish
import ai.heartz.core.drive.RoadsideOutcome
import ai.heartz.core.drive.RoadsideRules
import ai.heartz.core.drive.SpeechEndDetector
import ai.heartz.core.drive.SpeechVerdict
import ai.heartz.core.drive.StoryChoice
import ai.heartz.core.drive.VoiceReplies
import ai.heartz.core.model.Mood
import ai.heartz.core.model.Place
import ai.heartz.core.model.StoryStatus
import ai.heartz.core.model.User
import ai.heartz.core.model.VoiceMode
import ai.heartz.core.schedule.LocationValidity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.math.roundToLong

interface ClipRecorder {
    val isRecording: Boolean
    fun start(target: File, maxDurationMs: Int, onMaxReached: () -> Unit): Result<Unit>
    /** 직전 호출 이후 최대 진폭(0..32767) */
    fun amplitude(): Int
    fun stop(): RecordResult
    fun cancel()
}

/** 녹음 흐름 동안 잡는 오디오 포커스. 통화·음성 비서 등으로 잃으면 [acquire]의 콜백으로 알린다. */
interface RecordingFocus {
    fun acquire(onLost: (String) -> Unit): Boolean
    fun release()
}

fun interface SpeakPort {
    suspend fun speak(text: String): SpeakResult
}

fun interface AnswerSource {
    suspend fun listenForAnswer(): RecognizeResult
}

interface RoadsideStore {
    /** 서버로 보내지 않고 기기에만 남긴다(주차 후 확인) */
    fun keepLocal(draft: PendingDraft)
    suspend fun send(draft: PendingDraft): SaveOutcome
    /** 방금 저장한 내 사연을 지운다 */
    suspend fun delete(storyId: String): Boolean
    /** 기기 초안을 지운다 */
    fun discardLocal(clientDraftId: String)
}

enum class InputSource { TOUCH, GLEO }

/** 결과 화면 종류(아이콘·문구). [RoadsideUi.Finished.message]는 안내 음성과 같은 문장이다 */
enum class FinishKind { SENT, SENT_MODULATED, DRAFT, DRAFT_NO_ANSWER, DRAFT_NO_PLACE, DISCARDED, NO_SPEECH, TOO_SHORT, NEED_MIC, CANNOT_START, FAILED, CANCELLED }

sealed interface StartResult {
    data object Started : StartResult
    data class Rejected(val reason: String) : StartResult
}

enum class DiscardResult { DISCARDED_IN_PROGRESS, DELETED_SAVED, NOTHING }

/** 녹음이 끝난 뒤 묻는 질문: 전달할지(다시 녹음·버리기 포함), 어떤 목소리로 전달할지 */
enum class AskKind { DELIVER, VOICE }

sealed interface RoadsideUi {
    data object Idle : RoadsideUi
    /** 안내 중. [placeName]: 녹음을 시작한 곳(장소를 못 정하면 null) */
    data class Preparing(val placeName: String?) : RoadsideUi
    data class Recording(val startedAtMillis: Long, val placeName: String?, val maxMs: Long) : RoadsideUi
    /** Gleo가 끼어들어 녹음을 멈추고 명령을 기다리는 중 */
    data object Paused : RoadsideUi
    data class Asking(val question: String, val kind: AskKind, val placeName: String? = null, val durationMs: Long = 0) : RoadsideUi
    data object Saving : RoadsideUi
    data class Finished(val message: String, val good: Boolean, val kind: FinishKind) : RoadsideUi

    val active: Boolean
        get() = this is Preparing || this is Recording || this is Paused || this is Asking || this is Saving
}

class RoadsideDeps(
    val recorder: ClipRecorder,
    val focus: RecordingFocus,
    val speaker: SpeakPort,
    val answers: AnswerSource,
    val store: RoadsideStore,
    val recordingsDir: File,
    val places: () -> List<Place>,
    val location: () -> LocationValidity,
    val user: () -> User?,
    val backendMode: () -> BackendMode,
    val enabled: () -> Boolean,
    val micGranted: () -> Boolean,
    val stopPlayback: () -> Unit,
    /** 전화 수신·통화 중인지(AudioManager mode) */
    val inCall: () -> Boolean,
    /** Gleo(다른 앱의 어시스턴트 음성)가 말을 마칠 때까지 기다린다 */
    val awaitAssistantQuiet: suspend () -> Unit,
    /** 진행 중인 안내 음성·음성 인식을 멈춘다 */
    val stopVoice: () -> Unit,
    /** 녹음 파일의 잡음을 정리해 같은 자리에 다시 쓴다. 실패하면 원본을 그대로 둔다 */
    val cleanAudio: suspend (File) -> Unit = {},
)

/**
 * 지나면서 남기기.
 * - 시작은 명시적 입력(주행 화면 큰 버튼, Gleo 앱 액션)으로만. 시작하는 순간의 유효 위치로 장소를 정한다.
 * - 다른 소리를 잠시 멈추고(포커스) 짧은 안내 뒤 녹음한다. Gleo로 시작하면 Gleo가 말을 마친 뒤 시작한다.
 * - 말이 멈추면 스스로 끝내고 잡음을 정리한 뒤 묻는다:
 *   ① "이 사연을 전달할까요? 다시 녹음하려면 '다시'라고 말해 주세요." → 보내기 / 다시 녹음(같은 장소로) / 버리기
 *   ② "원하시면 변조 목소리로 사연을 전달할까요?" → 예: 변조(TTS) / 아니오: 내 목소리
 *   답(말·버튼·Gleo)대로만 보낸다. ①을 못 들으면 공개하지 않고 기기 초안, ②를 못 들으면 내 목소리.
 * - 보내면 항상 검수에 제출한다(나만 보기 범위 없음).
 * - 언제든 "버려"로 버린다. 방금 저장한 사연도 [KEEP_WINDOW_MS] 안이면 지운다.
 * - 통화로 끊기면 안내 없이 기기 초안으로, Gleo가 끼어들면 녹음을 멈추고 명령을 기다린 뒤 묻는다.
 */
class RoadsideController(
    private val scope: CoroutineScope,
    private val deps: RoadsideDeps,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow<RoadsideUi>(RoadsideUi.Idle)
    val state: StateFlow<RoadsideUi> = _state.asStateFlow()

    private enum class Answer { SUBMIT, DISCARD, RERECORD, ORIGINAL, MODULATED }

    private class Take(
        val id: String,
        val file: File,
        val anchor: RoadsideAnchor?,
        val place: Place?,
        val user: User,
        val backendMode: BackendMode,
        val startedAt: Long,
    ) {
        var rec: RecordResult? = null
        /** 뒤에서 도는 잡음 정리. 보내기·보관 전에 기다리고, 버릴 때는 끝난 뒤 파일을 지운다 */
        var cleaning: Deferred<Unit>? = null
        var pending: CompletableDeferred<Answer?>? = null
        var pendingKind: AskKind? = null
        /** Gleo로 정했으면 결과를 Gleo가 말하므로 앱은 말하지 않는다 */
        var quiet = false
        var presetVoice: VoiceMode? = null
    }

    private sealed interface Kept {
        val at: Long
        data class Saved(val storyId: String, override val at: Long) : Kept
        data class Local(val clientDraftId: String, override val at: Long) : Kept
    }

    private var take: Take? = null
    private var job: Job? = null
    private var monitor: Job? = null
    private var voiceAsk: Job? = null
    private var focusHeld = false
    private var lastKept: Kept? = null

    fun start(source: InputSource): StartResult {
        if (_state.value.active) return StartResult.Rejected("이미 녹음 중이에요")
        val reject = when {
            !deps.enabled() -> "지나면서 남기기가 꺼져 있어요."
            !deps.micGranted() -> "마이크 권한이 필요해요."
            deps.user() == null -> "로그인이 필요해요."
            else -> null
        }
        if (reject != null) {
            HeartzLog.i("roadside", "start rejected source=$source reason=$reject")
            _state.value = RoadsideUi.Finished(reject, good = false, kind = if (!deps.micGranted()) FinishKind.NEED_MIC else FinishKind.CANNOT_START)
            return StartResult.Rejected(reject)
        }
        val validity = deps.location()
        val anchor = RoadsideRules.anchorAt(validity)
        val place = RoadsideRules.bindPlace(anchor, deps.places())
        val t = newTake(anchor, place, deps.user()!!, deps.backendMode(), clock())
        take = t
        HeartzLog.i("roadside", "start source=$source anchor=${if (anchor != null) "valid" else validity::class.simpleName} place=${place?.id}")
        deps.stopPlayback()
        _state.value = RoadsideUi.Preparing(place?.shortName)
        job = scope.launch {
            if (source == InputSource.GLEO) deps.awaitAssistantQuiet()
            if (take !== t) return@launch
            if (!deps.focus.acquire { reason -> scope.launch { take?.let { onFocusLost(it, reason) } } }) {
                HeartzLog.i("roadside", "focus denied -> no recording")
                finishWith(t, "지금은 녹음할 수 없어요", good = false, deleteFile = true, announce = false, kind = FinishKind.CANNOT_START)
                return@launch
            }
            focusHeld = true
            beginRecording(t, "사연을 말해주세요.")
        }
        return StartResult.Started
    }

    private fun newTake(anchor: RoadsideAnchor?, place: Place?, user: User, mode: BackendMode, startedAt: Long): Take {
        val id = UUID.randomUUID().toString()
        return Take(id, File(deps.recordingsDir, "roadside-$id.m4a"), anchor, place, user, mode, startedAt)
    }

    private suspend fun beginRecording(t: Take, prompt: String) {
        deps.speaker.speak(prompt)
        if (take !== t) return
        val r = deps.recorder.start(t.file, RoadsideRules.MAX_MS.toInt()) { scope.launch { finish() } }
        if (r.isFailure) {
            HeartzLog.i("roadside", "recorder start failed ${r.exceptionOrNull()?.message}")
            finishWith(t, "녹음을 시작하지 못했어요", good = false, deleteFile = true, announce = true, kind = FinishKind.FAILED)
            return
        }
        _state.value = RoadsideUi.Recording(clock(), t.place?.shortName, RoadsideRules.MAX_MS)
        monitor = scope.launch { watchSpeech(t) }
    }

    /** 말이 끝났는지(또는 아예 없는지) 진폭으로 본다 */
    private suspend fun watchSpeech(t: Take) {
        val detector = SpeechEndDetector()
        val t0 = clock()
        while (scope.isActive && take === t && deps.recorder.isRecording && _state.value is RoadsideUi.Recording) {
            delay(LEVEL_INTERVAL_MS)
            when (detector.onLevel(clock() - t0, deps.recorder.amplitude())) {
                SpeechVerdict.SPEECH_ENDED -> {
                    HeartzLog.i("roadside", "silence after speech -> auto finish")
                    finish()
                    return
                }
                SpeechVerdict.NO_SPEECH -> {
                    HeartzLog.i("roadside", "no speech -> discard")
                    // 이 코루틴이 monitor 자신이므로 finishWith가 취소하지 않게 비운다(취소되면 안내가 나오지 않음)
                    monitor = null
                    deps.recorder.cancel()
                    finishWith(t, "말소리가 들리지 않아 녹음을 멈췄어요.", good = false, deleteFile = true, announce = true, kind = FinishKind.NO_SPEECH)
                    return
                }
                SpeechVerdict.CONTINUE -> Unit
            }
        }
    }

    /** 끝내기(버튼·Gleo·말 멈춤·최대 길이). 녹음 중일 때만 의미가 있다. */
    fun finish(): Boolean {
        val t = take ?: return false
        if (_state.value !is RoadsideUi.Recording || !deps.recorder.isRecording) return false
        stopRecording(t)
        job = scope.launch { conclude(t, preset = null) }
        return true
    }

    /** 보내기(버튼·Gleo). 녹음 중이면 끝내고 보낸다. [voice]가 있으면 목소리 질문을 건너뛴다. */
    fun answer(choice: StoryChoice, viaGleo: Boolean, voice: VoiceMode? = null): Boolean {
        if (choice == StoryChoice.DISCARD) return discard(viaGleo) != DiscardResult.NOTHING
        val t = take ?: return false
        t.quiet = t.quiet || viaGleo
        if (voice != null) t.presetVoice = voice
        HeartzLog.i("roadside", "answer=$choice voice=$voice gleo=$viaGleo in ${_state.value::class.simpleName}")
        val a = when (voice) { VoiceMode.ORIGINAL -> Answer.ORIGINAL; VoiceMode.MODULATED -> Answer.MODULATED; null -> Answer.SUBMIT }
        return feed(t, a)
    }

    /** 목소리 고르기(버튼·Gleo) */
    fun chooseVoice(mode: VoiceMode, viaGleo: Boolean): Boolean {
        val t = take ?: return false
        t.quiet = t.quiet || viaGleo
        t.presetVoice = mode
        return feed(t, if (mode == VoiceMode.MODULATED) Answer.MODULATED else Answer.ORIGINAL)
    }

    /** 다시 녹음(버튼·말·Gleo). 같은 장소로 처음부터 다시 녹음한다 */
    fun rerecord(viaGleo: Boolean): Boolean {
        val t = take ?: return false
        t.quiet = t.quiet || viaGleo
        HeartzLog.i("roadside", "rerecord requested gleo=$viaGleo in ${_state.value::class.simpleName}")
        return feed(t, Answer.RERECORD)
    }

    /** 지금 단계에 답을 넣는다(녹음 중이면 멈추고 그 답으로 이어 간다) */
    private fun feed(t: Take, a: Answer): Boolean = when (_state.value) {
        is RoadsideUi.Recording -> {
            stopRecording(t)
            job = scope.launch { conclude(t, preset = a) }
            true
        }
        RoadsideUi.Paused -> {
            job?.cancel()
            job = scope.launch { conclude(t, preset = a) }
            true
        }
        is RoadsideUi.Asking -> {
            deps.stopVoice()
            t.pending?.complete(a)
            true
        }
        else -> false
    }

    /** 버리기(버튼·말·Gleo). 진행 중인 것이 없으면 방금 저장한 사연을 지운다. */
    fun discard(viaGleo: Boolean): DiscardResult {
        val t = take
        val s = _state.value
        if (t != null && s.active && s !is RoadsideUi.Saving) {
            job?.cancel()
            monitor?.cancel()
            voiceAsk?.cancel()
            deps.stopVoice()
            if (deps.recorder.isRecording) deps.recorder.cancel()
            HeartzLog.i("roadside", "discarded in progress (gleo=$viaGleo)")
            scope.launch { finishWith(t, "사연을 버렸어요.", good = true, deleteFile = true, announce = !viaGleo, kind = FinishKind.DISCARDED) }
            return DiscardResult.DISCARDED_IN_PROGRESS
        }
        if (s is RoadsideUi.Saving) {
            val j = job
            scope.launch {
                j?.join()
                discardKept(viaGleo)
            }
            return DiscardResult.DELETED_SAVED
        }
        val k = lastKept ?: return DiscardResult.NOTHING
        if (clock() - k.at > KEEP_WINDOW_MS) return DiscardResult.NOTHING
        discardKept(viaGleo)
        return DiscardResult.DELETED_SAVED
    }

    private fun discardKept(viaGleo: Boolean) {
        val k = lastKept ?: return
        lastKept = null
        when (k) {
            is Kept.Local -> {
                deps.store.discardLocal(k.clientDraftId)
                HeartzLog.i("roadside", "discarded kept local draft")
                _state.value = RoadsideUi.Finished("방금 남긴 사연을 버렸어요.", good = true, kind = FinishKind.DISCARDED)
                if (!viaGleo) scope.launch { deps.speaker.speak("방금 남긴 사연을 버렸어요.") }
            }
            is Kept.Saved -> scope.launch {
                val ok = runCatching { deps.store.delete(k.storyId) }.getOrDefault(false)
                HeartzLog.i("roadside", "deleted saved story=${k.storyId} ok=$ok")
                val msg = if (ok) "방금 남긴 사연을 버렸어요." else "사연을 버리지 못했어요. 주차 후 내 사연함에서 지워 주세요."
                _state.value = RoadsideUi.Finished(msg, good = ok, kind = if (ok) FinishKind.DISCARDED else FinishKind.FAILED)
                if (!viaGleo) deps.speaker.speak(msg)
            }
        }
    }

    /** 화면의 버리기 버튼 */
    fun cancel() {
        discard(viaGleo = false)
    }

    /** 외부 사유(앱이 화면에서 사라짐 등)로 멈춘다. 말하지 않고 기기 초안으로만 남긴다. */
    fun interruptExternally(reason: String) {
        val t = take ?: return
        when (_state.value) {
            is RoadsideUi.Preparing -> {
                job?.cancel()
                scope.launch { finishWith(t, "취소했어요.", good = true, deleteFile = true, announce = false, kind = FinishKind.CANCELLED) }
            }
            is RoadsideUi.Recording, RoadsideUi.Paused -> scope.launch { keepAsInterrupted(t, reason) }
            is RoadsideUi.Asking -> {
                t.quiet = true
                deps.stopVoice()
                t.pending?.complete(null)
            }
            else -> Unit
        }
    }

    fun dismiss() {
        if (!_state.value.active) _state.value = RoadsideUi.Idle
    }

    private fun stopRecording(t: Take) {
        monitor?.cancel()
        if (deps.recorder.isRecording) t.rec = deps.recorder.stop()
    }

    private suspend fun onFocusLost(t: Take, reason: String) {
        if (take !== t) return
        val call = deps.inCall()
        HeartzLog.i("roadside", "focus lost reason=$reason inCall=$call state=${_state.value::class.simpleName}")
        when (_state.value) {
            is RoadsideUi.Recording -> if (call) keepAsInterrupted(t, "통화") else pauseForAssistant(t)
            is RoadsideUi.Asking -> if (call) {
                t.quiet = true
                deps.stopVoice()
                t.pending?.complete(null)
            } else {
                // Gleo로 답할 수 있게 우리 질문을 멈추고, Gleo가 말을 마치면 다시 묻는다
                voiceAsk?.cancel()
                deps.stopVoice()
                val d = t.pending
                val kind = t.pendingKind
                if (d != null && kind != null) {
                    voiceAsk = scope.launch {
                        deps.awaitAssistantQuiet()
                        delay(RESUME_AFTER_ASSISTANT_MS)
                        if (!d.isCompleted) d.complete(askByVoice(t, kind))
                    }
                }
            }
            else -> Unit
        }
    }

    private fun pauseForAssistant(t: Take) {
        stopRecording(t)
        _state.value = RoadsideUi.Paused
        job = scope.launch {
            deps.awaitAssistantQuiet()
            delay(RESUME_AFTER_ASSISTANT_MS)
            if (take === t && _state.value == RoadsideUi.Paused) conclude(t, preset = null)
        }
    }

    private suspend fun keepAsInterrupted(t: Take, reason: String) {
        if (take !== t) return
        stopRecording(t)
        val rec = t.rec
        val duration = (rec as? RecordResult.Success)?.durationMs ?: 0
        if (rec !is RecordResult.Success || duration < RoadsideRules.MIN_MS) {
            finishWith(t, "녹음이 짧아 남기지 않았어요.", good = false, deleteFile = true, announce = false, kind = FinishKind.TOO_SHORT)
            return
        }
        HeartzLog.i("roadside", "interrupted reason=$reason -> local draft")
        deps.store.keepLocal(draft(t, duration, interrupted = true, note = "$reason 때문에 녹음을 멈췄어요.", voice = VoiceMode.ORIGINAL))
        lastKept = Kept.Local(t.id, clock())
        finishWith(t, "초안으로 보관했어요. 주차 후 내 사연함에서 확인해 주세요.", good = true, deleteFile = false, announce = false, kind = FinishKind.DRAFT)
    }

    private suspend fun conclude(t: Take, preset: Answer?) {
        if (preset == Answer.RERECORD) {
            rerecordFrom(t)
            return
        }
        val rec = t.rec
        val duration = (rec as? RecordResult.Success)?.durationMs ?: 0
        if (rec !is RecordResult.Success) {
            finishWith(t, "녹음 파일을 만들지 못했어요.", good = false, deleteFile = true, announce = !t.quiet, kind = FinishKind.FAILED)
            return
        }
        if (duration < RoadsideRules.MIN_MS) {
            finishWith(t, "너무 짧아서 남기지 않았어요.", good = false, deleteFile = true, announce = !t.quiet, kind = FinishKind.TOO_SHORT)
            return
        }
        // 잡음 정리는 뒤에서 돌리고 화면은 바로 다음 단계로 넘긴다(녹음이 끝났는데 녹음 화면이 몇 초 남지 않게)
        if (t.cleaning == null) {
            t.cleaning = scope.async {
                runCatching { deps.cleanAudio(t.file) }.onFailure { HeartzLog.i("roadside", "noise cleanup failed ${it.message} -> keep original") }
                Unit
            }
        }
        if (t.place == null) {
            t.cleaning?.await()
            deps.store.keepLocal(draft(t, duration, interrupted = false, note = "녹음을 시작한 곳이 등록된 장소 밖이었어요. 남길 장소를 골라 주세요.", voice = VoiceMode.ORIGINAL))
            lastKept = Kept.Local(t.id, clock())
            finishWith(t, "장소를 찾지 못해 초안으로 보관했어요. 주차 후 장소를 골라 주세요.", good = true, deleteFile = false, announce = !t.quiet, kind = FinishKind.DRAFT_NO_PLACE)
            return
        }
        val deliver = preset ?: ask(t, AskKind.DELIVER)
        HeartzLog.i("roadside", "deliver=$deliver")
        if (deliver == Answer.RERECORD) {
            rerecordFrom(t)
            return
        }
        val choice = when (deliver) {
            Answer.DISCARD -> StoryChoice.DISCARD
            null -> null
            else -> StoryChoice.SUBMIT
        }
        when (val outcome = RoadsideRules.decide(RoadsideFinish(false, true, duration, null, t.place, choice))) {
            is RoadsideOutcome.Discard -> finishWith(t, "사연을 버렸어요.", good = true, deleteFile = true, announce = !t.quiet, kind = FinishKind.DISCARDED)
            is RoadsideOutcome.LocalDraft -> {
                t.cleaning?.await()
                deps.store.keepLocal(draft(t, duration, interrupted = false, note = outcome.reason, voice = VoiceMode.ORIGINAL))
                lastKept = Kept.Local(t.id, clock())
                finishWith(t, "답을 듣지 못해 초안으로 보관했어요. 주차 후 내 사연함에서 확인해 주세요.", good = true, deleteFile = false, announce = !t.quiet, kind = FinishKind.DRAFT_NO_ANSWER)
            }
            RoadsideOutcome.Submit -> {
                val voice = when (deliver) {
                    Answer.ORIGINAL -> VoiceMode.ORIGINAL
                    Answer.MODULATED -> VoiceMode.MODULATED
                    else -> t.presetVoice ?: when (ask(t, AskKind.VOICE)) {
                        Answer.RERECORD -> { rerecordFrom(t); return }
                        Answer.DISCARD -> { finishWith(t, "사연을 버렸어요.", good = true, deleteFile = true, announce = !t.quiet, kind = FinishKind.DISCARDED); return }
                        Answer.MODULATED -> VoiceMode.MODULATED
                        else -> VoiceMode.ORIGINAL // 못 들으면 사연자 목소리 그대로
                    }
                }
                HeartzLog.i("roadside", "submit voice=$voice")
                _state.value = RoadsideUi.Saving
                t.cleaning?.await()
                val (msg, kind) = when (val s = deps.store.send(draft(t, duration, interrupted = false, note = null, voice = voice))) {
                    is SaveOutcome.Saved -> {
                        lastKept = Kept.Saved(s.storyId, clock())
                        if (voice == VoiceMode.MODULATED) "변조 목소리로 보냈어요. 검수 후 공개돼요." to FinishKind.SENT_MODULATED
                        else "사연을 보냈어요. 검수 후 공개돼요." to FinishKind.SENT
                    }
                    is SaveOutcome.Deferred -> {
                        lastKept = Kept.Local(t.id, clock())
                        "지금은 보내지 못해 초안으로 남겼어요. 주차 후 다시 보내 주세요." to FinishKind.DRAFT
                    }
                }
                finishWith(t, msg, good = true, deleteFile = false, announce = !t.quiet, kind = kind)
            }
        }
    }

    /** 같은 장소·같은 시작 위치로 다시 녹음한다. 앞선 녹음은 지운다. 포커스는 그대로 쥐고 있다. */
    private suspend fun rerecordFrom(t: Take) {
        if (take !== t) return
        deleteWhenCleaned(t)
        val next = newTake(t.anchor, t.place, t.user, t.backendMode, t.startedAt)
        take = next
        _state.value = RoadsideUi.Preparing(t.place?.shortName)
        if (t.quiet) deps.awaitAssistantQuiet()
        HeartzLog.i("roadside", "rerecord -> new take place=${t.place?.id}")
        beginRecording(next, "사연을 다시 말해주세요.")
    }

    private suspend fun ask(t: Take, kind: AskKind): Answer? {
        val d = CompletableDeferred<Answer?>()
        t.pending = d
        t.pendingKind = kind
        voiceAsk = scope.launch { if (!d.isCompleted) d.complete(askByVoice(t, kind)) }
        val r = d.await()
        voiceAsk?.cancel()
        t.pending = null
        t.pendingKind = null
        return r
    }

    /** 말로 묻고 답을 듣는다. 분명하지 않으면 한 번 더 묻고, 그래도 모르면 null */
    private suspend fun askByVoice(t: Take, kind: AskKind): Answer? {
        var misses = 0
        while (take === t) {
            val q = when (kind) {
                AskKind.DELIVER -> if (misses == 0) "이 사연을 전달할까요? 다시 녹음하려면 '다시'라고 말해 주세요." else "잘 못 들었어요. 이 사연을 전달할까요?"
                AskKind.VOICE -> if (misses == 0) "원하시면 변조 목소리로 사연을 전달할까요?" else "잘 못 들었어요. 변조 목소리로 전달할까요?"
            }
            _state.value = RoadsideUi.Asking(q, kind, t.place?.shortName, (t.rec as? RecordResult.Success)?.durationMs ?: 0)
            deps.speaker.speak(q)
            var heard = (deps.answers.listenForAnswer() as? RecognizeResult.Text)?.text
            if (heard != null && VoiceReplies.isEcho(q, heard)) {
                // 스피커로 나간 질문을 마이크가 다시 들었다. 답으로 쓰지 않고 한 번 더 듣는다
                HeartzLog.i("roadside", "echo of own question ignored ($kind)")
                heard = (deps.answers.listenForAnswer() as? RecognizeResult.Text)?.text?.takeUnless { VoiceReplies.isEcho(q, it) }
            }
            val answer = heard?.let { interpret(it, kind) }
            HeartzLog.i("roadside", "voice answer=$answer ($kind)")
            if (answer != null) return answer
            misses++
            if (misses >= 2) return null
        }
        return null
    }

    private fun interpret(text: String, kind: AskKind): Answer? {
        val reply = VoiceReplies.parse(text)
        if (reply == Reply.RERECORD) return Answer.RERECORD
        if (reply == Reply.DISCARD) return Answer.DISCARD
        return when (kind) {
            AskKind.DELIVER -> when (VoiceReplies.explicitVoiceMode(text)) {
                VoiceMode.MODULATED -> Answer.MODULATED
                VoiceMode.ORIGINAL -> Answer.ORIGINAL
                null -> when (reply) {
                    Reply.YES, Reply.PUBLIC -> Answer.SUBMIT
                    Reply.NO -> Answer.DISCARD
                    else -> null
                }
            }
            AskKind.VOICE -> when (VoiceReplies.parseVoiceMode(text)) {
                VoiceMode.MODULATED -> Answer.MODULATED
                VoiceMode.ORIGINAL -> Answer.ORIGINAL
                null -> null
            }
        }
    }

    private fun draft(t: Take, duration: Long, interrupted: Boolean, note: String?, voice: VoiceMode) = PendingDraft(
        clientDraftId = t.id,
        userId = t.user.id,
        backendMode = t.backendMode,
        audioPath = t.file.absolutePath,
        placeId = t.place?.id,
        mood = Mood.WARM,
        durationMs = duration,
        createdAtMillis = t.startedAt,
        lastError = note,
        interrupted = interrupted,
        recordedWhileDriving = true,
        anchorLat = t.anchor?.point?.lat?.round4(),
        anchorLng = t.anchor?.point?.lng?.round4(),
        voiceMode = voice,
    )

    /** 버린 녹음 지우기. 잡음 정리가 도는 중이면 끝난 뒤 지운다(정리가 파일을 다시 쓰지 않게). 화면은 기다리지 않는다 */
    private fun deleteWhenCleaned(t: Take) {
        val c = t.cleaning
        if (c == null || c.isCompleted) t.file.delete() else scope.launch { runCatching { c.await() }; t.file.delete() }
    }

    private suspend fun finishWith(t: Take, message: String, good: Boolean, deleteFile: Boolean, announce: Boolean, kind: FinishKind) {
        monitor?.cancel()
        if (deleteFile) deleteWhenCleaned(t)
        if (take === t) take = null
        _state.value = RoadsideUi.Finished(message, good, kind)
        if (announce) deps.speaker.speak(message)
        if (focusHeld) {
            focusHeld = false
            deps.focus.release()
        }
    }

    private fun Double.round4(): Double = (this * 10_000).roundToLong() / 10_000.0

    companion object {
        const val LEVEL_INTERVAL_MS = 100L
        const val RESUME_AFTER_ASSISTANT_MS = 1_500L
        /** 저장 뒤 "버려"로 지울 수 있는 시간 */
        const val KEEP_WINDOW_MS = 30 * 60_000L
    }
}
