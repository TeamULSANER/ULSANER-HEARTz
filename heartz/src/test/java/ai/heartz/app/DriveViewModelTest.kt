package ai.heartz.app

import ai.heartz.app.data.LetterData
import ai.heartz.app.data.SessionState
import ai.heartz.app.platform.audio.LetterPlayer
import ai.heartz.app.platform.audio.PlayRequest
import ai.heartz.app.platform.audio.PlaybackUi
import ai.heartz.app.platform.location.LocationStatus
import ai.heartz.app.platform.speech.RecognizeResult
import ai.heartz.app.platform.speech.SpeakResult
import ai.heartz.app.ui.drive.CloseReason
import ai.heartz.app.ui.drive.DriveViewModel
import ai.heartz.app.ui.drive.PromptFocus
import ai.heartz.app.ui.drive.VoicePorts
import ai.heartz.app.ui.drive.VoiceStep
import ai.heartz.core.drive.UxState
import ai.heartz.core.model.ListenKind
import ai.heartz.core.model.ListenState
import ai.heartz.core.model.Place
import ai.heartz.core.model.Story
import ai.heartz.core.model.User
import ai.heartz.core.model.UserRole
import ai.heartz.core.model.VoiceMode
import ai.heartz.core.schedule.LocationFix
import ai.heartz.core.seed.SeedData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** fake 데이터·플레이어 기반 상태 테스트. 실제 SDK·재생 연결 증거가 아니다. */
@OptIn(ExperimentalCoroutinesApi::class)
class DriveViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val seed = SeedData.db(nowMillis = 0)
    private val viewer = User(SeedData.DRIVER_JANG, "장클로드", UserRole.LISTENER)

    private class FakeData(seedPlaces: List<Place>, seedFeed: List<Story>, user: User) : LetterData {
        override val session = MutableStateFlow<SessionState>(SessionState.SignedIn(user))
        override val places = MutableStateFlow(seedPlaces)
        override val feed = MutableStateFlow(seedFeed)
        override val listenState = MutableStateFlow(ListenState())
        val recorded = mutableListOf<Pair<String, ListenKind>>()
        var failAudio = false
        override suspend fun recordListen(storyId: String, kind: ListenKind) {
            recorded += storyId to kind
        }
        override suspend fun audioFile(story: Story): File {
            if (failAudio) throw IllegalStateException("download failed")
            return File("/tmp/${story.id}.m4a")
        }
        val hearts = mutableListOf<String>()
        override suspend fun heart(storyId: String, on: Boolean) {
            if (on) hearts += storyId
            listenState.value = listenState.value.copy(hearted = listenState.value.hearted + (storyId to 1L))
        }
    }

    private class FakePlayer : LetterPlayer {
        override val state = MutableStateFlow(PlaybackUi(connected = true))
        val played = mutableListOf<PlayRequest>()
        var stops = 0
        override fun play(req: PlayRequest) { played += req }
        override fun pause() {}
        override fun resume() {}
        override fun stop() { stops++ }
    }

    private lateinit var data: FakeData
    private lateinit var player: FakePlayer
    private val ux = MutableStateFlow<UxState>(UxState.Known(true, 0x50))
    private val location = MutableStateFlow<LocationStatus>(LocationStatus.NotStarted)
    private var now = 1_000_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        data = FakeData(seed.places, seed.stories.filter { it.status.name == "APPROVED" }, viewer)
        player = FakePlayer()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.vm(): DriveViewModel =
        DriveViewModel(data, player, ux as StateFlow<UxState>, location, clock = { now }, random = Random(3), tickMillis = 1_000)

    /** 음성 포트를 붙인 VM. 답은 [replies]에서 하나씩 꺼낸다(비면 못 들음). fake이며 실제 TTS·STT 증거가 아니다. */
    private val said = mutableListOf<String>()
    private val replies = ArrayDeque<String>()
    private var voiceBusy = false
    private val pauseEvents = mutableListOf<String>()

    private fun TestScope.voiceVm(): DriveViewModel = DriveViewModel(
        data, player, ux as StateFlow<UxState>, location, clock = { now }, random = Random(3), tickMillis = 1_000,
        voicePorts = VoicePorts(
            speaker = { said += it; SpeakResult.Done },
            answers = { replies.removeFirstOrNull()?.let { RecognizeResult.Text(it) } ?: RecognizeResult.NoSpeech },
            focus = object : PromptFocus {
                override fun duck() = true
                override fun release() {}
                override fun holdPause() { pauseEvents += "hold" }
                override fun releasePause() { pauseEvents += "release" }
            },
            stopVoice = {},
            awaitAssistantQuiet = {},
            enabled = { true },
            busy = { voiceBusy },
        ),
        closeDelayMs = 5_000,
    )

    private fun receiving(lat: Double, lng: Double, ts: Long = now) = LocationStatus.Receiving(
        LocationFix(lat, lng, 100.0, ts, "test"), receivedAtMillis = now, callbacks = 1, nullCallbacks = 0, confidence = 1f,
    )

    // 이촌한강공원(p-riverside) 반경 안이고 다른 랜드마크 반경 밖인 곳. 사연은 하나뿐이다
    private val ichon = 37.51600 to 126.96400
    // 잠수교(p-seobinggo) 반경 안이고 다른 사연 랜드마크 반경 밖인 곳
    private val seobinggo = 37.51500 to 127.00000

    @Test
    fun cardIsShownAndSuggestedButNothingPlays() = runTest(dispatcher) {
        val vm = vm()
        vm.start()
        try {
        location.value = receiving(ichon.first, ichon.second)
        runCurrent()
        val card = vm.state.value.card
        assertNotNull(card)
        assertTrue(card!!.title, card.title == "이촌한강공원에서 온 사연")
        assertEquals(listOf(card.storyId to ListenKind.SUGGESTED), data.recorded)
        assertTrue(player.played.isEmpty())
        } finally {
            vm.stop()
        }
    }

    @Test
    fun playStartsOnlyOnExplicitCallAndPlayedIsRecordedWhenPlayerPlays() = runTest(dispatcher) {
        val vm = vm()
        vm.start()
        try {
        location.value = receiving(ichon.first, ichon.second)
        runCurrent()
        val id = vm.state.value.card!!.storyId
        val story = data.feed.value.first { it.id == id }

        vm.play(story)
        runCurrent()
        assertEquals(listOf(id), player.played.map { it.storyId })
        assertFalse(data.recorded.any { it.second == ListenKind.PLAYED })

        player.state.value = PlaybackUi(connected = true, storyId = id, isPlaying = true, idle = false)
        runCurrent()
        assertEquals(1, data.recorded.count { it == (id to ListenKind.PLAYED) })
        assertTrue(vm.state.value.card!!.playing)
        } finally {
            vm.stop()
        }
    }

    @Test
    fun drivingViewFollowsPlatformRestrictions() = runTest(dispatcher) {
        val vm = vm()
        vm.start()
        try {
        ux.value = UxState.Known(false, 0)
        runCurrent()
        assertFalse(vm.state.value.drivingView)
        ux.value = UxState.Known(true, 0x50)
        runCurrent()
        assertTrue(vm.state.value.drivingView)
        ux.value = UxState.Unknown("disconnected")
        runCurrent()
        assertTrue(vm.state.value.drivingView)
        } finally {
            vm.stop()
        }
    }

    @Test
    fun noCardWithoutFreshLocation() = runTest(dispatcher) {
        val vm = vm()
        vm.start()
        try {
        location.value = LocationStatus.Waiting(now)
        runCurrent()
        assertNull(vm.state.value.card)
        location.value = receiving(ichon.first, ichon.second, ts = now - 60_000)
        runCurrent()
        assertNull(vm.state.value.card)
        assertTrue(data.recorded.isEmpty())
        } finally {
            vm.stop()
        }
    }

    @Test
    fun audioFailureShowsMessageAndDoesNotPlay() = runTest(dispatcher) {
        val vm = vm()
        vm.start()
        try {
        location.value = receiving(ichon.first, ichon.second)
        runCurrent()
        data.failAudio = true
        val story = data.feed.value.first { it.id == vm.state.value.card!!.storyId }
        vm.play(story)
        runCurrent()
        assertTrue(player.played.isEmpty())
        assertNotNull(vm.state.value.message)
        } finally {
            vm.stop()
        }
    }

    /** 서비스는 끝까지 재생하면 ENDED 다음 stop()(IDLE, 포커스 반납)으로 간다. 카드는 '끝까지 들음'으로 남아야 한다. */
    @Test
    fun endedThenIdleMarksCardPlayedToEnd() = runTest(dispatcher) {
        val vm = vm()
        vm.start()
        try {
        location.value = receiving(ichon.first, ichon.second)
        runCurrent()
        val id = vm.state.value.card!!.storyId
        player.state.value = PlaybackUi(connected = true, storyId = id, isPlaying = true, idle = false)
        runCurrent()
        // 컨트롤러가 ENDED를 건너뛰고 IDLE(위치=끝)만 받은 경우
        player.state.value = PlaybackUi(connected = true, storyId = id, isPlaying = false, ended = true, idle = true)
        runCurrent()
        val card = vm.state.value.card!!
        assertTrue(card.playedToEnd)
        assertFalse(card.playing)
        } finally {
            vm.stop()
        }
    }

    /** 다른 테스트 계정으로 바꾸면 이전 계정의 재생을 멈춘다(재생 상태가 계정 간에 이어지지 않게). */
    @Test
    fun switchingAccountStopsPlaybackAndCardIsNotPlayed() = runTest(dispatcher) {
        val vm = vm()
        vm.start()
        try {
        location.value = receiving(ichon.first, ichon.second)
        runCurrent()
        val id = vm.state.value.card!!.storyId
        player.state.value = PlaybackUi(connected = true, storyId = id, isPlaying = false, ended = true, idle = true)
        runCurrent()
        assertEquals(0, player.stops)

        data.session.value = SessionState.SignedIn(User(SeedData.DRIVER_MESSI, "상도동메시", UserRole.LISTENER))
        runCurrent()
        assertEquals(1, player.stops)
        assertFalse(vm.state.value.card!!.playedToEnd)
        } finally {
            vm.stop()
        }
    }

    @Test
    fun drivingArrivalIsAnnouncedAndReadWithoutAsking() = runTest(dispatcher) {
        val vm = voiceVm()
        vm.start()
        try {
            location.value = receiving(ichon.first, ichon.second)
            runCurrent()
            val id = vm.state.value.card!!.storyId
            assertEquals(listOf("이촌한강공원에서 사연이 도착했어요. 읽어드릴게요."), said)
            assertEquals(listOf(id), player.played.map { it.storyId })
        } finally {
            vm.stop()
        }
    }

    @Test
    fun noArrivalQuestionWhileAnotherVoiceFlowIsBusyOrParked() = runTest(dispatcher) {
        voiceBusy = true
        val vm = voiceVm()
        vm.start()
        try {
            location.value = receiving(ichon.first, ichon.second)
            runCurrent()
            assertTrue(said.isEmpty())
            assertTrue(player.played.isEmpty())
        } finally {
            vm.stop()
        }
    }

    @Test
    fun afterLetterEndsHeartIsAskedAndCardReturnsToWaitingAfterFiveSeconds() = runTest(dispatcher) {
        replies += "남겨줘"
        val vm = voiceVm()
        vm.start()
        try {
            location.value = receiving(ichon.first, ichon.second)
            runCurrent()
            val id = vm.state.value.card!!.storyId
            player.state.value = PlaybackUi(connected = true, storyId = id, isPlaying = true, idle = false)
            runCurrent()
            player.state.value = PlaybackUi(connected = true, storyId = id, isPlaying = false, ended = true, idle = true)
            runCurrent()
            assertEquals("사연자님께 마음이 전달되었어요.", said.last())
            assertEquals(listOf(id), data.hearts)
            assertEquals(VoiceStep.Closing(id, CloseReason.HEARTED), vm.state.value.voice)
            assertTrue(vm.state.value.card!!.hearted)
            // 재생 시작부터 공감을 정할 때까지 음악을 멈춰 두고, 정한 뒤 놓는다
            assertEquals(listOf("hold"), pauseEvents)
            dispatcher.scheduler.advanceTimeBy(5_100)
            runCurrent()
            assertNull(vm.state.value.card)
            assertEquals(listOf("hold", "release"), pauseEvents)
            // 같은 곳에 있는 동안 같은 사연이 다시 오지 않는다
            now += 1_000
            location.value = receiving(ichon.first, ichon.second)
            runCurrent()
            assertNull(vm.state.value.card)
        } finally {
            vm.stop()
        }
    }

    @Test
    fun gleoHeartWorksShortlyAfterTheCardClosed() = runTest(dispatcher) {
        replies += "별로"
        val vm = voiceVm()
        vm.start()
        try {
            location.value = receiving(ichon.first, ichon.second)
            runCurrent()
            val id = vm.state.value.card!!.storyId
            player.state.value = PlaybackUi(connected = true, storyId = id, isPlaying = true, idle = false)
            runCurrent()
            player.state.value = PlaybackUi(connected = true, storyId = id, isPlaying = false, ended = true, idle = true)
            runCurrent()
            dispatcher.scheduler.advanceTimeBy(5_100)
            runCurrent()
            assertNull(vm.state.value.card)
            assertTrue(data.hearts.isEmpty())
            val r = vm.heartCard(viaGleo = true)!!
            runCurrent()
            assertTrue(r.await())
            assertEquals(listOf(id), data.hearts)
        } finally {
            vm.stop()
        }
    }

    @Test
    fun storyArrivingWhileReadingWaitsThenFollowsWithThisTimePhrase() = runTest(dispatcher) {
        replies += "남겨줘"
        val vm = voiceVm()
        vm.start()
        try {
            location.value = receiving(ichon.first, ichon.second)
            runCurrent()
            val first = vm.state.value.card!!.storyId
            player.state.value = PlaybackUi(connected = true, storyId = first, isPlaying = true, idle = false)
            runCurrent()
            // 읽는 중 잠수교 근처에 들어옴 → 카드는 그대로, 서빙고 사연은 줄 선다
            now += 1_000
            location.value = receiving(seobinggo.first, seobinggo.second)
            runCurrent()
            assertEquals(first, vm.state.value.card!!.storyId)
            assertEquals(1, said.size)
            player.state.value = PlaybackUi(connected = true, storyId = first, isPlaying = false, ended = true, idle = true)
            runCurrent()
            // 공감 → 고마움 안내 → 바로 다음 사연
            assertEquals(listOf(first), data.hearts)
            assertEquals("이번에는 잠수교에서 사연이 도착했어요. 읽어드릴게요.", said.last())
            val next = vm.state.value.card!!.storyId
            assertTrue(next != first)
            assertEquals(next, player.played.last().storyId)
            assertFalse("release" in pauseEvents)
        } finally {
            vm.stop()
        }
    }

    @Test
    fun storyArrivingDuringHeartQuestionWaitsForTheCardToClose() = runTest(dispatcher) {
        val vm = voiceVm()
        vm.start()
        try {
            location.value = receiving(ichon.first, ichon.second)
            runCurrent()
            val first = vm.state.value.card!!.storyId
            player.state.value = PlaybackUi(connected = true, storyId = first, isPlaying = true, idle = false)
            runCurrent()
            player.state.value = PlaybackUi(connected = true, storyId = first, isPlaying = false, ended = true, idle = true)
            runCurrent()
            // 공감 질문을 마치고 닫히는 중(재생은 끝남)에 잠수교 근처에 들어옴 → 카드를 바꾸지 않고 줄 세운다
            now += 1_000
            location.value = receiving(seobinggo.first, seobinggo.second)
            runCurrent()
            assertEquals(first, vm.state.value.card!!.storyId)
            dispatcher.scheduler.advanceTimeBy(5_100)
            runCurrent()
            val next = vm.state.value.card!!.storyId
            assertTrue(next != first)
            assertEquals("이번에는 잠수교에서 사연이 도착했어요. 읽어드릴게요.", said.last())
        } finally {
            vm.stop()
        }
    }

    @Test
    fun modulatedStoryIsReadFromTranscriptAndNeverFallsBackToTheOriginal() = runTest(dispatcher) {
        val vm = vm()
        vm.start()
        try {
            val base = data.feed.value.first()
            val waiting = base.copy(id = "mod-1", voiceMode = VoiceMode.MODULATED, spokenText = null)
            vm.play(waiting)
            runCurrent()
            assertTrue(player.played.isEmpty())
            assertTrue(vm.state.value.message!!.contains("변조 목소리"))
            val ready = waiting.copy(spokenText = "제가 한강을 걸으면서\n야경 보는게 취미입니다")
            vm.play(ready)
            runCurrent()
            val req = player.played.single()
            assertNull(req.file)
            assertEquals(ready.spokenText, req.spokenText)
            // 운영자 검수는 원본을 듣는다
            vm.play(ready, original = true)
            runCurrent()
            assertNotNull(player.played.last().file)
            assertNull(player.played.last().spokenText)
        } finally {
            vm.stop()
        }
    }

    @Test
    fun durationLabelIsInSeconds() {
        assertEquals("30초", DriveViewModel.durationLabel(30_120))
        assertEquals("1분 5초", DriveViewModel.durationLabel(65_000))
    }
}
