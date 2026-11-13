package ai.heartz.app

import ai.heartz.app.data.HeartzRepository
import ai.heartz.app.data.Settings
import ai.heartz.app.platform.audio.AudioFocusLeases
import ai.heartz.app.platform.audio.HeartzPlayer
import ai.heartz.app.platform.audio.PlaybackController
import ai.heartz.app.platform.audio.ReadFocus
import ai.heartz.app.platform.audio.TtsLetterReader
import ai.heartz.app.platform.record.VoiceCleanup
import ai.heartz.app.platform.location.FusedLocationSource
import ai.heartz.app.platform.navi.NaviProbe
import ai.heartz.app.platform.record.AudioRecorder
import ai.heartz.app.platform.restrictions.UxRestrictionsMonitor
import ai.heartz.app.platform.speech.PleosSpeechToText
import ai.heartz.app.platform.speech.PleosTextToSpeech
import ai.heartz.app.platform.speech.ReviewAssistRunner
import ai.heartz.app.data.DraftSender
import ai.heartz.core.model.Story
import ai.heartz.app.data.PendingDraft
import ai.heartz.app.platform.location.LocationStatus
import ai.heartz.app.platform.record.RecordingFocusManager
import ai.heartz.app.ui.drive.RoadsideController
import ai.heartz.app.ui.drive.RoadsideDeps
import ai.heartz.app.ui.drive.RoadsideStore
import ai.heartz.app.platform.gleo.AssistantQuiet
import ai.heartz.app.platform.gleo.AssistantSpeechMonitor
import ai.heartz.app.platform.gleo.QuietOutcome
import ai.heartz.app.platform.record.PromptFocusManager
import ai.heartz.app.platform.record.isInCall
import ai.heartz.core.schedule.LocationRules
import ai.heartz.core.schedule.PassedLetters
import ai.heartz.core.schedule.LocationValidity
import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HeartzApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.ux.start()
        // Pleos TTS/STT(ON_DEVICE)는 CAAS 서비스 바인딩이라 앱 프로세스 수명 동안 유지한다.
        graph.tts.start()
        graph.stt.start()
        graph.appScope.launch { graph.repository.bootstrap() }
    }

    companion object {
        fun graph(context: Context): AppGraph = (context.applicationContext as HeartzApp).graph
    }
}

/** 수동 의존성 그래프. 플랫폼 어댑터는 각자 수명주기(start/stop)를 가진다. */
class AppGraph(app: Application) {
    private val appContext: Context = app
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings = Settings(app)
    val repository = HeartzRepository(app, settings, appScope)
    val location = FusedLocationSource(app)
    val navi = NaviProbe(app)
    val ux = UxRestrictionsMonitor(app)
    /** 원본 목소리는 미디어 서비스, 변조 목소리는 TTS로 읽는다(포커스는 앱 하나로 묶음) */
    val player: HeartzPlayer by lazy {
        HeartzPlayer(
            media = PlaybackController(app),
            reader = TtsLetterReader(
                scope = appScope,
                speak = { text -> tts.speak(text, timeoutMs = 60_000) },
                stopSpeech = { tts.stop() },
                focus = object : ReadFocus {
                    private var lease: AudioFocusLeases.Lease? = null
                    override fun acquire(onChange: (Int) -> Unit): Boolean {
                        lease?.release()
                        lease = focusLeases.acquire("read", AudioFocusLeases.Kind.PAUSE, onChange)
                        return lease != null
                    }
                    override fun release() {
                        lease?.release()
                        lease = null
                    }
                },
            ),
            scope = appScope,
        )
    }
    val recorder = AudioRecorder(app)
    val tts = PleosTextToSpeech(app)
    val stt = PleosSpeechToText(app)
    val assist = ReviewAssistRunner(repository, stt, appScope, java.io.File(app.cacheDir, "assist"))
    val sender = DraftSender(repository, assist)

    private val _roadsideEnabled = MutableStateFlow(settings.roadsideEnabled)
    val roadsideEnabled: StateFlow<Boolean> = _roadsideEnabled.asStateFlow()
    fun setRoadsideEnabled(enabled: Boolean) {
        settings.roadsideEnabled = enabled
        _roadsideEnabled.value = enabled
        HeartzLog.i("roadside", "settings enabled=$enabled")
    }

    private val _voiceGuideEnabled = MutableStateFlow(settings.voiceGuideEnabled)
    val voiceGuideEnabled: StateFlow<Boolean> = _voiceGuideEnabled.asStateFlow()
    fun setVoiceGuideEnabled(enabled: Boolean) {
        settings.voiceGuideEnabled = enabled
        _voiceGuideEnabled.value = enabled
        HeartzLog.i("voice", "settings voice guide=$enabled")
    }

    /** 앱 전체 오디오 포커스(질문 덕킹·사연 재생·공감까지 음악 멈춤을 하나로) */
    val focusLeases = AudioFocusLeases(app)
    val promptFocus = PromptFocusManager(focusLeases)

    /** 내비 안내·다른 앱의 어시스턴트 음성이 말하는 중이면 끝날 때까지 기다린다(없으면 바로, 최대 10초) */
    suspend fun awaitOtherSpeechQuiet() {
        val monitor = AssistantSpeechMonitor(appContext, OTHER_SPEECH_USAGES)
        monitor.start()
        try {
            if (monitor.speaking.value) {
                HeartzLog.i("voice", "other speech playing -> wait before asking")
                val done = kotlinx.coroutines.withTimeoutOrNull(10_000) { monitor.speaking.first { !it }; kotlinx.coroutines.delay(600) }
                HeartzLog.i("voice", "other speech wait ${if (done == null) "timed out" else "done"}")
            }
        } finally {
            monitor.stop()
        }
    }

    /** 다른 앱(Gleo)의 어시스턴트 음성이 끝날 때까지 기다린다(말소리가 없으면 4초, 최대 15초) */
    suspend fun awaitAssistantQuiet(): QuietOutcome {
        val monitor = AssistantSpeechMonitor(appContext)
        monitor.start()
        return try {
            AssistantQuiet().await(monitor.speaking).also { HeartzLog.i("gleo", "assistant quiet wait=$it") }
        } finally {
            monitor.stop()
        }
    }

    fun stopVoice() {
        tts.stop()
        stt.stop()
    }

    val roadside = RoadsideController(
        appScope,
        RoadsideDeps(
            recorder = recorder,
            focus = RecordingFocusManager(app),
            speaker = { text -> tts.speak(text) },
            answers = { stt.listen(7_000) },
            store = object : RoadsideStore {
                override fun keepLocal(draft: PendingDraft) {
                    repository.drafts.upsert(draft)
                    repository.reloadPendingDrafts()
                }
                override suspend fun send(draft: PendingDraft) = sender.send(draft)
                override suspend fun delete(storyId: String): Boolean = runCatching {
                    repository.backend.delete(storyId)
                    repository.refresh()
                }.isSuccess
                override fun discardLocal(clientDraftId: String) {
                    repository.drafts.list(repository.currentUser?.id ?: return)
                        .firstOrNull { it.clientDraftId == clientDraftId }
                        ?.let(sender::discard)
                }
            },
            recordingsDir = repository.recordingsDir,
            places = { repository.places.value },
            location = {
                when (val l = location.status.value) {
                    is LocationStatus.Receiving -> LocationRules.validate(l.fix, System.currentTimeMillis())
                    else -> LocationValidity.Missing
                }
            },
            user = { repository.currentUser },
            backendMode = { repository.backend.mode },
            enabled = { roadsideEnabled.value },
            micGranted = {
                ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            },
            stopPlayback = { player.stop() },
            inCall = { isInCall(app) },
            awaitAssistantQuiet = { awaitAssistantQuiet() },
            stopVoice = ::stopVoice,
            cleanAudio = { file -> VoiceCleanup.cleanInPlace(file) },
        ),
    )

    /** 주행 화면이 현재 띄운 카드. 시스템 미디어 브라우저 트리에 쓴다. */
    private val _currentCardStoryId = MutableStateFlow<String?>(null)
    val currentCardStoryId: StateFlow<String?> = _currentCardStoryId.asStateFlow()
    fun setCurrentCard(storyId: String?) {
        _currentCardStoryId.value = storyId
    }

    /** (story, title, subtitle): 지금 카드 + 오늘 표시됐지만 재생하지 않은 사연. 변조 목소리 사연은 음원이 없어 빠진다. */
    fun browsableLetters(): List<Triple<Story, String, String>> {
        val feed = repository.feed.value
        val places = repository.places.value.associateBy { it.id }
        val listen = repository.listenState.value
        val today = startOfTodayMillis()
        val current = currentCardStoryId.value?.let { id -> feed.firstOrNull { it.id == id && it.authorId !in listen.hiddenAuthorIds } }
        val stories = listOfNotNull(current) + PassedLetters.unplayed(feed, listen, today).map { it.first }
        return stories.distinctBy { it.id }.filter { it.voiceMode == ai.heartz.core.model.VoiceMode.ORIGINAL }.map { s ->
            val place = places[s.placeId]
            Triple(s, "${place?.shortName ?: "어딘가"}에서 온 사연", s.authorName)
        }
    }
}

private val OTHER_SPEECH_USAGES = setOf(
    android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
    android.media.AudioAttributes.USAGE_ASSISTANT,
)

fun startOfTodayMillis(now: Long = System.currentTimeMillis()): Long {
    val c = java.util.Calendar.getInstance()
    c.timeInMillis = now
    c.set(java.util.Calendar.HOUR_OF_DAY, 0)
    c.set(java.util.Calendar.MINUTE, 0)
    c.set(java.util.Calendar.SECOND, 0)
    c.set(java.util.Calendar.MILLISECOND, 0)
    return c.timeInMillis
}
