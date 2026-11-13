package ai.heartz.app.ui.studio

import ai.heartz.app.HeartzLog
import ai.heartz.app.data.HeartzRepository
import ai.heartz.app.data.DraftSender
import ai.heartz.app.data.PendingDraft
import ai.heartz.app.data.SaveOutcome
import ai.heartz.app.data.userMessage
import ai.heartz.app.platform.location.LocationStatus
import ai.heartz.app.platform.record.AudioRecorder
import ai.heartz.app.platform.record.RecordResult
import ai.heartz.core.drive.DrivePolicy
import ai.heartz.core.drive.UxState
import ai.heartz.core.geo.Geo
import ai.heartz.core.model.GeoPoint
import ai.heartz.core.model.Mood
import ai.heartz.core.model.Place
import ai.heartz.core.model.VoiceMode
import ai.heartz.app.platform.record.VoiceCleanup
import ai.heartz.core.model.Story
import ai.heartz.core.model.StoryStatus
import ai.heartz.core.schedule.LocationRules
import ai.heartz.core.schedule.LocationValidity
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class StudioStep { PICK_PLACE, RECORD, REVIEW, DONE }

data class PlaceOption(val place: Place, val distanceMeters: Double?)

data class StudioState(
    val step: StudioStep = StudioStep.PICK_PLACE,
    val places: List<PlaceOption> = emptyList(),
    val placeId: String? = null,
    val recording: Boolean = false,
    val elapsedMs: Long = 0,
    val level: Float = 0f,
    val clip: File? = null,
    val clipDurationMs: Long = 0,
    /** 받는 사람이 들을 목소리. 변조 목소리면 받아쓴 글을 TTS로 읽어 준다 */
    val voiceMode: VoiceMode = VoiceMode.ORIGINAL,
    /** 녹음 잡음을 줄이는 중 */
    val cleaning: Boolean = false,
    val previewPlaying: Boolean = false,
    val saving: Boolean = false,
    val result: SaveOutcome? = null,
    val error: String? = null,
) {
    val maxDurationMs: Long get() = MAX_STORY_MS

    companion object {
        const val MAX_STORY_MS = 60_000L
        const val MIN_MS = 2_000L
    }
}

/**
 * 주차 후 녹음 → 잡음 줄이기 → 미리 듣기(마음에 안 들면 다시 녹음) → 목소리 선택 → 검수에 보내기.
 * 녹음 시작은 [DrivePolicy.allowParkedFeatures]일 때만. 주행 상태로 바뀌면 즉시 멈추고 초안으로 보관한다.
 * 주행 중 녹음·전송(기획서 6절)은 이 ViewModel에 경로가 없다.
 */
class StudioViewModel(
    context: Context,
    private val repo: HeartzRepository,
    private val recorder: AudioRecorder,
    private val ux: StateFlow<UxState>,
    private val location: StateFlow<LocationStatus>,
    private val sender: DraftSender,
    /** 녹음 잡음 줄이기. 실패하면 원본을 그대로 쓴다 */
    private val cleanAudio: suspend (File) -> Boolean = VoiceCleanup::cleanInPlace,
) : ViewModel() {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(StudioState())
    val state: StateFlow<StudioState> = _state.asStateFlow()

    private var clientDraftId: String = UUID.randomUUID().toString()
    private var meterJob: Job? = null
    private var preview: ExoPlayer? = null

    init {
        viewModelScope.launch {
            ux.collect { u ->
                if (recorder.isRecording && !DrivePolicy.allowParkedFeatures(u)) interruptForDriving()
            }
        }
    }

    fun begin() {
        stopPreview()
        if (recorder.isRecording) recorder.cancel()
        clientDraftId = UUID.randomUUID().toString()
        _state.value = StudioState(places = placeOptions())
    }

    private fun placeOptions(): List<PlaceOption> {
        val fix = (location.value as? LocationStatus.Receiving)?.fix
        val valid = fix != null && LocationRules.validate(fix, System.currentTimeMillis()) is LocationValidity.Valid
        return repo.places.value.map { p ->
            PlaceOption(p, if (valid) Geo.distanceMeters(GeoPoint(fix!!.lat, fix.lng), p.publicLocation) else null)
        }.sortedWith(compareBy(nullsLast()) { it.distanceMeters })
    }

    fun pickPlace(id: String) = _state.update { it.copy(placeId = id, step = StudioStep.RECORD, error = null) }

    fun backToPlaces() {
        if (recorder.isRecording) recorder.cancel()
        _state.update { it.copy(step = StudioStep.PICK_PLACE, recording = false, clip = null) }
    }

    fun startRecording() {
        if (!DrivePolicy.allowParkedFeatures(ux.value)) {
            _state.update { it.copy(error = "주차한 뒤에 녹음할 수 있어요.") }
            return
        }
        stopPreview()
        val target = File(repo.recordingsDir, "$clientDraftId.m4a")
        val max = _state.value.maxDurationMs
        val r = recorder.start(target, max.toInt()) { viewModelScope.launch { stopRecording() } }
        r.onFailure { e ->
            HeartzLog.i("record", "start failed ${e::class.java.simpleName} ${e.message}")
            _state.update { it.copy(error = "녹음을 시작하지 못했어요 (${e.message ?: e::class.java.simpleName})") }
            return
        }
        _state.update { it.copy(recording = true, elapsedMs = 0, level = 0f, clip = null, error = null) }
        meterJob = viewModelScope.launch {
            while (isActive && recorder.isRecording) {
                val amp = recorder.amplitude() / 32767f
                _state.update { it.copy(elapsedMs = recorder.elapsedMs(), level = (it.level * 0.6f + amp * 0.4f)) }
                delay(100)
            }
        }
    }

    fun stopRecording() {
        if (!recorder.isRecording) return
        meterJob?.cancel()
        when (val r = recorder.stop()) {
            is RecordResult.Success -> {
                if (r.durationMs < StudioState.MIN_MS) {
                    r.file.delete()
                    _state.update { it.copy(recording = false, level = 0f, error = "조금 더 길게 들려주세요.") }
                } else {
                    _state.update { it.copy(recording = false, level = 0f, cleaning = true) }
                    viewModelScope.launch {
                        val cleaned = cleanAudio(r.file)
                        HeartzLog.i("record", "noise cleanup applied=$cleaned")
                        _state.update {
                            it.copy(cleaning = false, clip = r.file, clipDurationMs = r.durationMs, step = StudioStep.REVIEW)
                        }
                    }
                }
            }
            is RecordResult.Failure -> _state.update {
                it.copy(recording = false, level = 0f, error = "녹음 파일을 만들지 못했어요 (${r.reason})")
            }
        }
    }

    private fun interruptForDriving() {
        meterJob?.cancel()
        val r = recorder.stop()
        HeartzLog.i("record", "interrupted by driving restriction")
        val s = _state.value
        val user = repo.currentUser
        if (r is RecordResult.Success && r.durationMs >= StudioState.MIN_MS && user != null && s.placeId != null) {
            repo.drafts.upsert(
                PendingDraft(
                    clientDraftId = clientDraftId,
                    userId = user.id,
                    backendMode = repo.backend.mode,
                    audioPath = r.file.absolutePath,
                    placeId = s.placeId,
                    mood = Mood.WARM,
                    durationMs = r.durationMs,
                    voiceMode = s.voiceMode,
                    createdAtMillis = System.currentTimeMillis(),
                    lastError = "주행이 시작되어 녹음을 멈췄어요. 확인 후 보내 주세요.",
                    interrupted = true,
                ),
            )
            repo.reloadPendingDrafts()
        } else if (r is RecordResult.Success) {
            r.file.delete()
        }
        _state.update {
            it.copy(recording = false, level = 0f, step = StudioStep.PICK_PLACE, clip = null,
                error = "주행이 시작되어 녹음을 멈췄어요. 내 사연함에 보관했어요.")
        }
        clientDraftId = UUID.randomUUID().toString()
    }

    /** 마음에 들지 않으면 지우고 같은 장소에서 다시 녹음한다 */
    fun discardClip() {
        stopPreview()
        _state.value.clip?.delete()
        clientDraftId = UUID.randomUUID().toString()
        _state.update { it.copy(clip = null, clipDurationMs = 0, step = StudioStep.RECORD) }
    }

    fun togglePreview() {
        val clip = _state.value.clip ?: return
        val p = preview ?: ExoPlayer.Builder(appContext)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                true,
            )
            .build().also { pl ->
                pl.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) = _state.update { it.copy(previewPlaying = isPlaying) }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) pl.seekTo(0)
                    }
                })
                preview = pl
            }
        if (p.isPlaying) {
            p.pause()
        } else {
            if (p.currentMediaItem == null) {
                p.setMediaItem(MediaItem.fromUri(Uri.fromFile(clip)))
                p.prepare()
            }
            p.play()
        }
    }

    private fun stopPreview() {
        preview?.release()
        preview = null
        _state.update { it.copy(previewPlaying = false) }
    }

    fun selectVoice(m: VoiceMode) = _state.update { it.copy(voiceMode = m) }
    fun clearError() = _state.update { it.copy(error = null) }

    /** 검수에 보낸다(검수 후 공개) */
    fun save() {
        val s = _state.value
        val clip = s.clip ?: return
        val placeId = s.placeId ?: return
        val user = repo.currentUser ?: run {
            _state.update { it.copy(error = "로그인이 필요해요.") }
            return
        }
        stopPreview()
        val draft = PendingDraft(
            clientDraftId = clientDraftId,
            userId = user.id,
            backendMode = repo.backend.mode,
            audioPath = clip.absolutePath,
            placeId = placeId,
            mood = Mood.WARM,
            durationMs = s.clipDurationMs,
            voiceMode = s.voiceMode,
            createdAtMillis = System.currentTimeMillis(),
        )
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val outcome = send(draft)
            _state.update { it.copy(saving = false, result = outcome, step = StudioStep.DONE) }
            clientDraftId = UUID.randomUUID().toString()
        }
    }

    private suspend fun send(draft: PendingDraft): SaveOutcome = sender.send(draft)

    /** 내 사연함의 보내지 못한 초안을 같은 clientDraftId로 다시 보낸다. */
    fun retry(draft: PendingDraft, onDone: (SaveOutcome) -> Unit) {
        viewModelScope.launch {
            onDone(send(draft.copy(lastError = null, interrupted = false)))
        }
    }

    fun discardPending(draft: PendingDraft) = sender.discard(draft)

    override fun onCleared() {
        if (recorder.isRecording) recorder.cancel()
        preview?.release()
        preview = null
    }
}
