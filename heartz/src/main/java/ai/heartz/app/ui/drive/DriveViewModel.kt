package ai.heartz.app.ui.drive

import ai.heartz.app.HeartzLog
import ai.heartz.app.data.LetterData
import ai.heartz.app.data.SessionState
import ai.heartz.app.data.userMessage
import ai.heartz.app.ui.components.shortName
import ai.heartz.app.platform.audio.PlayRequest
import ai.heartz.app.platform.audio.LetterPlayer
import ai.heartz.app.platform.audio.PlaybackUi
import ai.heartz.app.platform.location.LocationStatus
import ai.heartz.core.drive.DrivePolicy
import ai.heartz.core.drive.UxState
import ai.heartz.core.model.ListenKind
import ai.heartz.core.model.StoryTone
import ai.heartz.core.model.VoiceMode
import ai.heartz.core.model.Story
import ai.heartz.core.schedule.LetterCard
import ai.heartz.core.schedule.LetterPicker
import ai.heartz.core.schedule.LetterSession
import ai.heartz.core.schedule.LocationRules
import ai.heartz.core.schedule.LocationValidity
import ai.heartz.core.schedule.SessionEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlin.random.Random

data class CardUi(
    val storyId: String,
    val title: String,
    /** 카드의 큰 장소 이름 */
    val placeShort: String,
    val placeName: String,
    val durationLabel: String,
    /** 화면 색(감정 분석 전까지 사연마다 임의로 고정) */
    val tone: StoryTone,
    val authorName: String,
    val sampleLabel: String?,
    val playing: Boolean,
    val playedToEnd: Boolean,
    val preparing: Boolean,
    val hearted: Boolean = false,
    /** 변조 목소리(받아쓴 글을 TTS로 읽음) */
    val modulated: Boolean = false,
)

data class DriveUiState(
    val ux: UxState = UxState.Unknown("init"),
    val drivingView: Boolean = true,
    val location: LocationStatus = LocationStatus.NotStarted,
    val validity: LocationValidity = LocationValidity.Missing,
    val card: CardUi? = null,
    val playback: PlaybackUi = PlaybackUi(),
    val preparingStoryId: String? = null,
    val message: String? = null,
    val voice: VoiceStep = VoiceStep.Idle,
)

/** 주행 중 음성 흐름에 필요한 포트. 없으면(테스트·미지원) 음성 없이 화면만 동작한다. */
class VoicePorts(
    val speaker: SpeakPort,
    val answers: AnswerSource,
    val focus: PromptFocus,
    val stopVoice: () -> Unit,
    val awaitAssistantQuiet: suspend () -> Unit,
    /** 내비 안내 등 다른 음성이 끝날 때까지(없으면 바로) */
    val awaitOtherSpeechQuiet: suspend () -> Unit = {},
    /** 설정의 "사연이 오면 바로 읽어 주기" */
    val enabled: () -> Boolean,
    /** 지나면서 남기기 등 다른 음성 흐름이 진행 중이면 true */
    val busy: () -> Boolean,
)

/**
 * 위치·주행 제한·편성 데이터·재생 상태를 합쳐 주행 화면 상태를 만든다.
 * 주차 중에는 사용자 동작(버튼·Gleo)으로만 재생한다. 주행 중 음성 안내를 켜 두면 [LetterVoiceGuide]가
 * 사연 도착을 알리고 바로 읽은 뒤 공감 하트를 묻는다. 읽는 동안 다른 곳에 들어오면 그곳 사연은 줄 서서 이어진다.
 */
class DriveViewModel(
    private val data: LetterData,
    private val player: LetterPlayer,
    private val ux: StateFlow<UxState>,
    private val location: StateFlow<LocationStatus>,
    private val clock: () -> Long = System::currentTimeMillis,
    random: Random = Random.Default,
    private val tickMillis: Long = 3_000,
    private val onCardChanged: (String?) -> Unit = {},
    private val voicePorts: VoicePorts? = null,
    private val closeDelayMs: Long = 5_000,
) : ViewModel() {
    private val _state = MutableStateFlow(DriveUiState())
    val state: StateFlow<DriveUiState> = _state.asStateFlow()

    private val session = LetterSession(LetterPicker(random))
    private var jobs: List<Job> = emptyList()
    private var viewerId: String? = null
    private val playedRecorded = mutableSetOf<String>()
    private var lastPlayback = PlaybackUi()
    /** 마지막으로 끝까지 들은 사연(카드가 닫힌 뒤 Gleo로 하트를 남길 때) */
    private var lastFinished: Pair<String, Long>? = null

    private val guide: LetterVoiceGuide? = voicePorts?.let { v ->
        LetterVoiceGuide(
            scope = viewModelScope,
            speaker = v.speaker,
            answers = v.answers,
            focus = v.focus,
            stopVoice = v.stopVoice,
            awaitAssistantQuiet = v.awaitAssistantQuiet,
            awaitOtherSpeechQuiet = v.awaitOtherSpeechQuiet,
            actions = object : LetterVoiceGuide.Actions {
                override fun play(storyId: String) {
                    data.feed.value.firstOrNull { it.id == storyId }?.let(::play)
                }
                override suspend fun heart(storyId: String): Boolean = runCatching { data.heart(storyId, true) }
                    .onFailure { HeartzLog.i("drive", "heart failed ${it.userMessage()}") }.isSuccess
                override fun dismiss(storyId: String) = dismissCard(storyId)
                override fun hasQueued(): Boolean = session.queuedCount > 0
            },
            closeDelayMs = closeDelayMs,
        )
    }

    /** 화면이 보일 때 시작(위치 구독과 같은 수명) */
    fun start() {
        if (jobs.isNotEmpty()) return
        jobs = listOf(
            viewModelScope.launch {
                combine(ux, location, data.feed, data.places, data.listenState) { _, _, _, _, _ -> Unit }
                    .collect { evaluate() }
            },
            viewModelScope.launch {
                data.session.collect { s ->
                    val id = (s as? SessionState.SignedIn)?.user?.id
                    if (id != viewerId) {
                        // 계정이 바뀌면 이전 계정의 재생을 멈춘다(재생 상태가 계정 간에 이어지지 않게)
                        if (viewerId != null) player.stop()
                        viewerId = id
                        session.reset()
                        playedRecorded.clear()
                        evaluate()
                    }
                }
            },
            viewModelScope.launch { player.state.collect(::onPlayback) },
            viewModelScope.launch { guide?.step?.collect { v -> _state.update { it.copy(voice = v) } } },
            viewModelScope.launch {
                while (isActive) {
                    delay(tickMillis)
                    evaluate()
                }
            },
        )
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
    }

    private fun validity(): LocationValidity = when (val l = location.value) {
        is LocationStatus.Receiving -> LocationRules.validate(l.fix, clock())
        else -> LocationValidity.Missing
    }

    private fun evaluate() {
        val uxState = ux.value
        val validity = validity()
        val viewer = viewerId
        if (viewer != null) {
            session.busy = guide?.busy == true || lastPlayback.isPlaying || _state.value.preparingStoryId != null
            val events = session.onLocation(validity, clock(), data.feed.value, data.places.value, data.listenState.value, viewer)
            for (e in events) {
                when (e) {
                    is SessionEvent.Shown -> {
                        HeartzLog.i("drive", "card shown story=${e.card.story.id} place=${e.card.place.id}")
                        viewModelScope.launch { runCatching { data.recordListen(e.card.story.id, ListenKind.SUGGESTED) } }
                        maybeArrive(e.card)
                    }
                    is SessionEvent.Queued -> HeartzLog.i("drive", "story queued story=${e.card.story.id} place=${e.card.place.id}")
                    is SessionEvent.Cleared -> HeartzLog.i("drive", "card cleared story=${e.storyId}")
                }
            }
        }
        val card = session.card
        val before = _state.value.card?.storyId
        _state.update {
            it.copy(
                ux = uxState,
                drivingView = DrivePolicy.showDrivingView(uxState),
                location = location.value,
                validity = validity,
                card = card?.let { c -> toUi(c, it.preparingStoryId) },
                playback = lastPlayback,
            )
        }
        if (before != card?.story?.id) onCardChanged(card?.story?.id)
    }

    /** 주행 중이고 다른 소리·음성 흐름이 없을 때만 알리고 읽는다 */
    private fun maybeArrive(c: LetterCard) {
        val v = voicePorts ?: return
        val g = guide ?: return
        val playing = lastPlayback.isPlaying || _state.value.preparingStoryId != null
        if (!DrivePolicy.showDrivingView(ux.value) || !v.enabled() || v.busy() || playing || g.busy) return
        g.arrive(c.story.id, c.place.shortName)
    }

    private fun toUi(c: LetterCard, preparing: String?) = CardUi(
        storyId = c.story.id,
        title = "${c.place.shortName}에서 온 사연",
        placeShort = c.place.shortName,
        placeName = c.place.displayName,
        durationLabel = durationLabel(c.story.durationMs),
        tone = StoryTone.of(c.story.id),
        authorName = c.story.authorName.shortName(),
        sampleLabel = c.story.sampleLabel,
        playing = c.playing,
        playedToEnd = c.playedToEnd,
        preparing = preparing == c.story.id,
        hearted = c.story.id in data.listenState.value.hearted,
        modulated = c.story.voiceMode == VoiceMode.MODULATED,
    )

    private fun onPlayback(p: PlaybackUi) {
        val prev = lastPlayback
        lastPlayback = p
        val id = p.storyId
        if (id != null && p.isPlaying) {
            session.onPlaybackStarted(id)
            if (!prev.isPlaying || prev.storyId != id) onLetterPlaying(id)
            if (playedRecorded.add(id)) {
                HeartzLog.i("drive", "playback started story=$id -> PLAYED")
                viewModelScope.launch { runCatching { data.recordListen(id, ListenKind.PLAYED) } }
            }
        }
        val prevId = prev.storyId
        if (prevId != null && prev.isPlaying && (!p.isPlaying || id != prevId)) {
            val completed = p.ended && id == prevId
            session.onPlaybackStopped(prevId, completed = completed, nowMillis = clock())
            if (completed) onLetterFinished(prevId)
            else if (!p.isPlaying && p.idle) guide?.letterStopped(prevId)
        }
        _state.update { it.copy(playback = p) }
        evaluate()
    }

    /** [original]: 운영자 검수처럼 변조 목소리 사연의 원본 녹음을 들을 때 */
    fun play(story: Story, original: Boolean = false) {
        if (story.voiceMode == VoiceMode.MODULATED && !original && story.spokenText.isNullOrBlank()) {
            // 받아쓰기가 아직 없으면 원본으로 대신 틀지 않는다(작성자가 고른 목소리가 아님)
            HeartzLog.i("drive", "modulated story=${story.id} has no transcript yet -> not played")
            _state.update { it.copy(message = "변조 목소리를 아직 준비하는 중이에요. 잠시 뒤 다시 들어 주세요") }
            return
        }
        _state.update { it.copy(preparingStoryId = story.id, message = null) }
        evaluate()
        viewModelScope.launch {
            try {
                val place = data.places.value.firstOrNull { it.id == story.placeId }
                val title = "${place?.shortName ?: "어딘가"}에서 온 사연"
                val text = story.spokenText?.takeIf { it.isNotBlank() }
                val req = if (story.voiceMode == VoiceMode.MODULATED && !original && text != null) {
                    // 변조 목소리: 받아쓴 글을 TTS로 읽는다. 원본 녹음은 작성자·운영자만 받을 수 있다.
                    PlayRequest(story.id, file = null, title = title, subtitle = story.authorName, spokenText = text, durationMs = story.durationMs)
                } else {
                    PlayRequest(story.id, file = data.audioFile(story), title = title, subtitle = story.authorName, durationMs = story.durationMs)
                }
                player.play(req)
            } catch (e: Exception) {
                HeartzLog.i("drive", "audio unavailable story=${story.id} ${e.userMessage()}")
                _state.update { it.copy(message = "음원을 불러오지 못했어요 (${e.userMessage()})") }
            } finally {
                _state.update { it.copy(preparingStoryId = null) }
                evaluate()
            }
        }
    }

    /** 주행 중 카드 사연이 재생되기 시작하면 공감을 정할 때까지 음악을 멈춰 둔다 */
    private fun onLetterPlaying(storyId: String) {
        val v = voicePorts ?: return
        if (session.card?.story?.id != storyId) return
        if (DrivePolicy.showDrivingView(ux.value) && v.enabled()) guide?.letterPlaying(storyId)
    }

    private fun onLetterFinished(storyId: String) {
        lastFinished = storyId to clock()
        val v = voicePorts ?: return
        if (session.card?.story?.id != storyId) return
        if (DrivePolicy.showDrivingView(ux.value) && v.enabled() && !v.busy()) guide?.letterEnded(storyId)
    }

    /** 카드를 닫고 대기 화면으로 */
    fun dismissCard(storyId: String) {
        if (session.dismiss(storyId)) {
            HeartzLog.i("drive", "card dismissed story=$storyId")
            evaluate()
        }
    }

    private fun currentStory(): Story? = session.card?.let { c -> data.feed.value.firstOrNull { it.id == c.story.id } }

    /** 열기(화면 버튼·Gleo). 카드가 없으면 false */
    fun openCard(viaGleo: Boolean): Boolean {
        val s = currentStory() ?: return false
        val g = guide
        if (g != null) g.open(s.id, viaGleo) else play(s)
        return true
    }

    fun skipCard(): Boolean {
        val id = session.card?.story?.id ?: return false
        val g = guide
        if (g != null) g.skip(id) else dismissCard(id)
        return true
    }

    /** 하트를 남길 사연: 지금 카드, 없으면 10분 안에 끝까지 들은 사연 */
    private fun heartTarget(): String? = session.card?.story?.id
        ?: lastFinished?.takeIf { clock() - it.second < 10 * 60_000L }?.first

    /** 공감 하트(화면 버튼·Gleo). 결과 성공 여부. 대상이 없으면 null */
    fun heartCard(viaGleo: Boolean): kotlinx.coroutines.Deferred<Boolean>? {
        val id = heartTarget() ?: return null
        guide?.let { return it.heart(id, viaGleo, announce = !viaGleo && voiceOn()) }
        return viewModelScope.async { runCatching { data.heart(id, true) }.isSuccess }
    }

    /** 하트 없이 닫기 */
    fun noHeart(viaGleo: Boolean): Boolean {
        val id = session.card?.story?.id ?: return false
        val g = guide
        if (g != null) g.noHeart(id, viaGleo, announce = !viaGleo && voiceOn()) else dismissCard(id)
        return true
    }

    /** 주행 중이고 음성 안내를 켰는지 */
    private fun voiceOn(): Boolean = DrivePolicy.showDrivingView(ux.value) && voicePorts?.enabled?.invoke() == true

    /** 주차 화면·상세에서 하트를 켜고 끈다 */
    fun toggleHeart(storyId: String, on: Boolean) {
        viewModelScope.launch {
            runCatching { data.heart(storyId, on) }
                .onFailure { e -> _state.update { it.copy(message = "하트를 남기지 못했어요 (${e.userMessage()})") } }
            evaluate()
        }
    }

    /** 시험용: 이번 주행에서 띄운 사연 기록을 비워 같은 사연을 다시 받을 수 있게 한다 */
    fun resetSessionForTesting() {
        guide?.cancel()
        session.reset()
        playedRecorded.clear()
        evaluate()
    }

    /** 다른 음성 흐름(지나면서 남기기)이 시작되면 질문을 멈춘다 */
    fun cancelVoice() = guide?.cancel()

    fun pause() = player.pause()
    fun resume() = player.resume()
    fun stopPlayback() = player.stop()
    fun dismissMessage() = _state.update { it.copy(message = null) }

    override fun onCleared() = stop()

    companion object {
        fun durationLabel(ms: Long): String {
            val total = (ms + 500) / 1000
            val m = total / 60
            val sec = total % 60
            return if (m == 0L) "${sec}초" else if (sec == 0L) "${m}분" else "${m}분 ${sec}초"
        }
    }
}
