package ai.heartz.app

import ai.heartz.app.ui.HeartzRoot
import ai.heartz.app.ui.drive.DriveViewModel
import ai.heartz.app.ui.library.LibraryViewModel
import ai.heartz.app.ui.studio.StudioViewModel
import ai.heartz.app.ui.theme.HeartzTheme
import ai.heartz.app.platform.gleo.GleoFunction
import ai.heartz.app.platform.gleo.GleoProtocol
import ai.heartz.app.platform.gleo.GleoStatus
import ai.heartz.app.platform.gleo.LetterChoice
import ai.heartz.app.platform.gleo.StoryDecision
import ai.heartz.app.ui.drive.DiscardResult
import ai.heartz.app.ui.drive.InputSource
import ai.heartz.app.ui.drive.StartResult
import ai.heartz.app.ui.drive.VoicePorts
import ai.heartz.core.drive.StoryChoice
import ai.heartz.core.model.VoiceMode
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/**
 * 단일 Activity. 화면이 보이는 동안(onStart~onStop)만 위치·내비·재생 컨트롤러를 연결한다.
 * 재생 자체는 HeartzPlaybackService에서 계속된다.
 */
class MainActivity : ComponentActivity() {
    private val graph by lazy { HeartzApp.graph(this) }

    private val drive: DriveViewModel by viewModels {
        viewModelFactory {
            initializer {
                DriveViewModel(
                    data = graph.repository,
                    player = graph.player,
                    ux = graph.ux.state,
                    location = graph.location.status,
                    onCardChanged = graph::setCurrentCard,
                    voicePorts = VoicePorts(
                        speaker = { text -> graph.tts.speak(text) },
                        answers = { graph.stt.listen(6_000) },
                        focus = graph.promptFocus,
                        stopVoice = graph::stopVoice,
                        awaitAssistantQuiet = { graph.awaitAssistantQuiet() },
                        awaitOtherSpeechQuiet = { graph.awaitOtherSpeechQuiet() },
                        enabled = { graph.voiceGuideEnabled.value },
                        busy = { graph.roadside.state.value.active },
                    ),
                )
            }
        }
    }
    private val library: LibraryViewModel by viewModels {
        viewModelFactory { initializer { LibraryViewModel(graph.repository) } }
    }
    private val studio: StudioViewModel by viewModels {
        viewModelFactory {
            initializer { StudioViewModel(applicationContext, graph.repository, graph.recorder, graph.ux.state, graph.location.status, graph.sender) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HeartzTheme {
                ai.heartz.app.ui.startup.HeartzLaunch(graph, drive, library, studio)
            }
        }
        if (savedInstanceState == null) handleGleo(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleGleo(intent)
    }

    /**
     * Gleo 앱 액션 → 화면 버튼과 같은 동작으로 처리하고 7초 안에 결과 콜백을 보낸다(가이드: 7초 뒤 취소).
     * 응답 message는 영어(가이드 요구)이며 Gleo가 이것으로 대답한다. 그래서 Gleo로 정한 결과는 앱이 다시 말하지 않는다.
     */
    private fun handleGleo(intent: Intent?) {
        val fn = GleoProtocol.functionFor(intent?.action) ?: return
        val extras = intent?.extras
        val plain = extras?.keySet()?.associateWith { k -> extras.get(k)?.toString().orEmpty() }.orEmpty()
        val params = GleoProtocol.params(extras?.getString(GleoProtocol.EXTRA_ACTION_PARAM), plain)
        HeartzLog.i("gleo", "action=${intent?.action} fn=$fn params=$params")
        lifecycleScope.launch {
            val (status, message) = withTimeoutOrNull(GleoProtocol.TIMEOUT_MS - 1_500) { decide(fn, params) }
                ?: (GleoStatus.FAIL to "HEARTz took too long to respond.")
            val response = GleoProtocol.response(fn, status, message)
            sendBroadcast(
                Intent(GleoProtocol.RESPONSE_ACTION)
                    .putExtra(GleoProtocol.EXTRA_PACKAGE, packageName)
                    .putExtra(GleoProtocol.EXTRA_RESPONSE, response),
            )
            HeartzLog.i("gleo", "response sent $response")
        }
    }

    private suspend fun decide(fn: GleoFunction, params: Map<String, String>): Pair<GleoStatus, String> = when (fn) {
        GleoFunction.START_STORY_RECORDING -> {
            drive.cancelVoice()
            when (val r = graph.roadside.start(InputSource.GLEO)) {
                StartResult.Started -> GleoStatus.SUCCESS to
                    "HEARTz will start recording the driver's story after you finish speaking. It stops automatically when the driver stops talking."
                is StartResult.Rejected -> GleoStatus.FAIL to "HEARTz cannot record now: ${r.reason}"
            }
        }
        GleoFunction.MANAGE_MY_STORY -> when (GleoProtocol.storyDecision(params)) {
            StoryDecision.FINISH ->
                if (graph.roadside.finish()) GleoStatus.SUCCESS to "Recording stopped. HEARTz will ask whether to deliver the story."
                else GleoStatus.FAIL to "No story is being recorded."
            StoryDecision.SUBMIT -> {
                val voice = GleoProtocol.voiceMode(params)
                if (graph.roadside.answer(StoryChoice.SUBMIT, viaGleo = true, voice = voice)) GleoStatus.SUCCESS to when (voice) {
                    VoiceMode.MODULATED -> "The story will be delivered in a modulated voice after review."
                    VoiceMode.ORIGINAL -> "The story will be delivered in the driver's own voice after review."
                    null -> "HEARTz will ask whether to deliver the story in a modulated voice."
                }
                else GleoStatus.FAIL to "There is no story waiting for a decision."
            }
            StoryDecision.RERECORD ->
                if (graph.roadside.rerecord(viaGleo = true)) GleoStatus.SUCCESS to "HEARTz will record the story again for the same place after you finish speaking."
                else GleoStatus.FAIL to "There is no story to record again."
            StoryDecision.DISCARD -> when (graph.roadside.discard(viaGleo = true)) {
                DiscardResult.DISCARDED_IN_PROGRESS -> GleoStatus.SUCCESS to "The story was discarded."
                DiscardResult.DELETED_SAVED -> GleoStatus.SUCCESS to "The story the driver just left was discarded."
                DiscardResult.NOTHING -> GleoStatus.FAIL to "There is no recent story to discard."
            }
            null -> GleoStatus.FAIL to "HEARTz did not understand what to do with the story."
        }
        GleoFunction.HANDLE_ARRIVED_LETTER -> when (GleoProtocol.letterChoice(params)) {
            LetterChoice.PLAY ->
                if (drive.openCard(viaGleo = true)) GleoStatus.SUCCESS to "Reading the story for this road."
                else GleoStatus.FAIL to "There is no story for this road right now."
            LetterChoice.SKIP ->
                if (drive.skipCard()) GleoStatus.SUCCESS to "Skipped the story."
                else GleoStatus.FAIL to "There is no story for this road right now."
            LetterChoice.SEND_HEART -> {
                val r = drive.heartCard(viaGleo = true)
                when {
                    r == null -> GleoStatus.FAIL to "There is no story to send a heart to."
                    r.await() -> GleoStatus.SUCCESS to "Your heart was delivered to the storyteller."
                    else -> GleoStatus.FAIL to "HEARTz could not send the heart right now."
                }
            }
            LetterChoice.NO_HEART ->
                if (drive.noHeart(viaGleo = true)) GleoStatus.SUCCESS to "Okay, no heart this time. Maybe leave a warm heart next time."
                else GleoStatus.SUCCESS to "Okay."
            null -> GleoStatus.FAIL to "HEARTz did not understand what to do with the story."
        }
    }

    override fun onStart() {
        super.onStart()
        HeartzLog.i("lifecycle", "activity onStart -> location/navi/player start")
        graph.player.connect()
        graph.location.start()
        graph.navi.start()
        drive.start()
    }

    override fun onStop() {
        HeartzLog.i("lifecycle", "activity onStop -> location/navi/player stop")
        // 화면 밖에서는 마이크를 쓰지 않는다(Android while-in-use 제약). 지나면서 녹음 중이면 초안으로 멈춘다.
        if (!isChangingConfigurations) graph.roadside.interruptExternally("HEARTz 화면이 닫힘")
        drive.stop()
        graph.location.stop()
        graph.navi.stop()
        graph.player.disconnect()
        super.onStop()
    }
}
