package ai.heartz.app

import ai.heartz.app.data.BackendMode
import ai.heartz.app.data.PendingDraft
import ai.heartz.app.data.SaveOutcome
import ai.heartz.app.platform.record.RecordResult
import ai.heartz.app.platform.speech.RecognizeResult
import ai.heartz.app.platform.speech.SpeakResult
import ai.heartz.app.ui.drive.AnswerSource
import ai.heartz.app.ui.drive.ClipRecorder
import ai.heartz.app.ui.drive.DiscardResult
import ai.heartz.app.ui.drive.FinishKind
import ai.heartz.app.ui.drive.InputSource
import ai.heartz.app.ui.drive.RecordingFocus
import ai.heartz.app.ui.drive.RoadsideController
import ai.heartz.app.ui.drive.RoadsideDeps
import ai.heartz.app.ui.drive.RoadsideStore
import ai.heartz.app.ui.drive.RoadsideUi
import ai.heartz.app.ui.drive.SpeakPort
import ai.heartz.app.ui.drive.StartResult
import ai.heartz.core.drive.StoryChoice
import ai.heartz.core.model.VoiceMode
import ai.heartz.app.ui.drive.AskKind
import ai.heartz.core.model.StoryStatus
import ai.heartz.core.model.User
import ai.heartz.core.model.UserRole
import ai.heartz.core.schedule.LocationFix
import ai.heartz.core.schedule.LocationValidity
import ai.heartz.core.seed.SeedData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** fake 포트 기반 지나면서 남기기 흐름 테스트. 실제 마이크·SDK·Gleo·차량 입력의 증거가 아니다. */
@OptIn(ExperimentalCoroutinesApi::class)
class RoadsideControllerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val places = SeedData.db(0).places
    // 국립중앙박물관(p-ichon) 바로 옆, 잠수교(p-seobinggo) 북단 바로 옆
    private val ichon = LocationValidity.Valid(LocationFix(37.52450, 126.98030, null, 1_000, "test"))
    private val seobinggo = LocationValidity.Valid(LocationFix(37.51700, 126.99550, null, 2_000, "test"))
    private val user = User(SeedData.DRIVER_JANG, "장클로드", UserRole.LISTENER)

    private val events = mutableListOf<String>()
    private var now: () -> Long = { 0L }

    private inner class FakeRecorder : ClipRecorder {
        var durationMs = 12_000L
        var file: File? = null
        var startedAt = 0L
        /** 녹음 시작 후 경과 시간 → 진폭. 기본: 2초 말하고 조용함 */
        var level: (Long) -> Int = { t -> if (t < 2_000) 6_000 else 300 }
        override var isRecording = false
        override fun start(target: File, maxDurationMs: Int, onMaxReached: () -> Unit): Result<Unit> {
            events += "record-start"
            target.parentFile?.mkdirs()
            target.writeBytes(byteArrayOf(1, 2, 3))
            file = target
            isRecording = true
            startedAt = now()
            return Result.success(Unit)
        }
        override fun amplitude(): Int = level(now() - startedAt)
        override fun stop(): RecordResult {
            events += "record-stop"
            isRecording = false
            return RecordResult.Success(file!!, durationMs, 3)
        }
        override fun cancel() {
            events += "record-cancel"
            isRecording = false
            file?.delete()
        }
    }

    private inner class FakeFocus : RecordingFocus {
        var grant = true
        var onLost: ((String) -> Unit)? = null
        override fun acquire(onLost: (String) -> Unit): Boolean {
            events += "focus-acquire"
            this.onLost = onLost
            return grant
        }
        override fun release() { events += "focus-release" }
    }

    private inner class FakeSpeaker : SpeakPort {
        val said = mutableListOf<String>()
        override suspend fun speak(text: String): SpeakResult {
            // 실제 SDK처럼 취소된 코루틴에서는 말하지 못한다
            kotlinx.coroutines.yield()
            events += "speak"
            said += text
            return SpeakResult.Done
        }
    }

    /** 질문마다 큐에서 답을 하나씩 꺼낸다. 큐가 비면 [whenEmpty], null이면 계속 듣는 중(취소될 때까지) */
    private class FakeAnswers : AnswerSource {
        val queue = ArrayDeque<RecognizeResult>()
        var whenEmpty: RecognizeResult? = RecognizeResult.NoSpeech
        var asked = 0
        override suspend fun listenForAnswer(): RecognizeResult {
            asked++
            queue.removeFirstOrNull()?.let { return it }
            return whenEmpty ?: awaitCancellation()
        }
        fun say(vararg texts: String) = texts.forEach { queue += RecognizeResult.Text(it) }
    }

    private class FakeStore : RoadsideStore {
        val local = mutableListOf<PendingDraft>()
        val sent = mutableListOf<PendingDraft>()
        val deleted = mutableListOf<String>()
        val discardedLocal = mutableListOf<String>()
        var outcome: (PendingDraft) -> SaveOutcome = { SaveOutcome.Saved(StoryStatus.PENDING_REVIEW, "이촌", "s1") }
        override fun keepLocal(draft: PendingDraft) { local += draft }
        override suspend fun send(draft: PendingDraft): SaveOutcome { sent += draft; return outcome(draft) }
        override suspend fun delete(storyId: String): Boolean { deleted += storyId; return true }
        override fun discardLocal(clientDraftId: String) { discardedLocal += clientDraftId }
    }

    private val recorder = FakeRecorder()
    private val focus = FakeFocus()
    private val speaker = FakeSpeaker()
    private val answers = FakeAnswers()
    private val store = FakeStore()
    private var location: LocationValidity = ichon
    private var enabled = true
    private var mic = true
    private var signedIn: User? = user
    private var playbackStopped = 0
    private var inCall = false
    private var quietWaits = 0
    /** 잡음 정리를 붙잡아 두는 문(null이면 바로 끝남) */
    private var cleanGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    private fun TestScope.controller(): RoadsideController {
        now = { testScheduler.currentTime }
        return RoadsideController(
            scope = this,
            deps = RoadsideDeps(
                recorder = recorder, focus = focus, speaker = speaker, answers = answers, store = store,
                recordingsDir = tmp.newFolder(),
                places = { places }, location = { location }, user = { signedIn }, backendMode = { BackendMode.LOCAL_DEMO },
                enabled = { enabled }, micGranted = { mic }, stopPlayback = { playbackStopped++ },
                inCall = { inCall },
                awaitAssistantQuiet = { events += "assistant-quiet"; quietWaits++ },
                stopVoice = { events += "stop-voice" },
                cleanAudio = { f -> events += "clean:${f.name}"; cleanGate?.await(); events += "cleaned" },
            ),
            clock = { testScheduler.currentTime },
        )
    }

    private fun TestScope.recording(c: RoadsideController, source: InputSource = InputSource.TOUCH) {
        assertEquals(StartResult.Started, c.start(source))
        runCurrent()
        assertTrue(c.state.value.toString(), c.state.value is RoadsideUi.Recording)
    }

    @Test
    fun startIsRejectedWhenDisabledMicMissingOrSignedOut() = runTest {
        val c = controller()
        enabled = false
        assertTrue(c.start(InputSource.TOUCH) is StartResult.Rejected)
        enabled = true; mic = false
        assertTrue(c.start(InputSource.TOUCH) is StartResult.Rejected)
        assertEquals(FinishKind.NEED_MIC, (c.state.value as RoadsideUi.Finished).kind)
        mic = true; signedIn = null
        assertTrue(c.start(InputSource.TOUCH) is StartResult.Rejected)
        assertEquals(FinishKind.CANNOT_START, (c.state.value as RoadsideUi.Finished).kind)
        advanceUntilIdle()
        assertFalse("record-start" in events)
    }

    @Test
    fun touchStartPausesOtherSoundThenPromptsThenRecords() = runTest {
        val c = controller()
        c.start(InputSource.TOUCH)
        // 안내 중 화면에 녹음을 시작한 곳을 보여 준다
        assertEquals(RoadsideUi.Preparing("국립중앙박물관"), c.state.value)
        runCurrent()
        assertEquals(listOf("focus-acquire", "speak", "record-start"), events.take(3))
        assertEquals(1, playbackStopped)
        assertEquals(0, quietWaits)
    }

    @Test
    fun gleoStartWaitsUntilGleoStopsTalking() = runTest {
        val c = controller()
        recording(c, InputSource.GLEO)
        assertEquals("assistant-quiet", events.first())
    }

    @Test
    fun recordingStopsByItselfCleansNoiseAndAsksToDeliverWithRerecordHint() = runTest {
        val c = controller()
        recording(c)
        answers.whenEmpty = null
        advanceTimeBy(4_500)
        assertTrue(c.state.value is RoadsideUi.Recording)
        advanceTimeBy(1_000) // 2 s 말 + 3 s 조용 = 5 s 근처
        assertTrue("record-stop" in events)
        // 잡음 정리는 묻기 전에
        assertTrue(events.indexOfFirst { it.startsWith("clean:") } in 0 until events.lastIndexOf("speak"))
        val s = c.state.value
        assertTrue(s.toString(), s is RoadsideUi.Asking && s.kind == AskKind.DELIVER)
        // 질문 화면에 장소와 녹음 길이를 보여 준다
        s as RoadsideUi.Asking
        assertEquals("국립중앙박물관", s.placeName)
        assertEquals(12_000L, s.durationMs)
        assertTrue(speaker.said.last().contains("전달할까요") && speaker.said.last().contains("다시"))
        c.discard(viaGleo = false)
        advanceUntilIdle()
    }

    @Test
    fun recordingPromptIsShort() = runTest {
        val c = controller()
        recording(c)
        assertEquals("사연을 말해주세요.", speaker.said.first())
    }

    /** 녹음이 끝나면 잡음 정리를 기다리지 않고 바로 질문 화면으로 간다(정리는 보내기 전까지 끝낸다) */
    @Test
    fun screenLeavesRecordingRightAwayWhileNoiseCleanupRunsInTheBackground() = runTest {
        cleanGate = kotlinx.coroutines.CompletableDeferred()
        answers.say("응", "아니")
        val c = controller()
        recording(c)
        c.finish()
        runCurrent()
        // 정리가 끝나지 않았어도 녹음 화면에 머물지 않고 질문으로 넘어갔다
        assertTrue(c.state.value !is RoadsideUi.Recording)
        assertTrue(speaker.said.any { it.startsWith("이 사연을 전달할까요") })
        advanceUntilIdle()
        // 정리가 끝나기 전에는 보내지 않는다(보내는 중 화면에서 기다림)
        assertEquals(RoadsideUi.Saving, c.state.value)
        assertTrue(store.sent.isEmpty())
        assertFalse("cleaned" in events)
        cleanGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, store.sent.size)
        assertTrue(events.indexOf("cleaned") < events.lastIndexOf("speak"))
    }

    /** AVD 2026-11-13: 목소리 질문을 음성 인식이 다시 들어 변조 목소리로 보내졌다 */
    @Test
    fun ownQuestionHeardBackDoesNotChooseModulatedVoice() = runTest {
        answers.say("응", "원하시면 변조 목소리로 사연을 전달할까요")
        val c = controller()
        recording(c)
        c.finish()
        advanceUntilIdle()
        assertEquals(VoiceMode.ORIGINAL, store.sent.single().voiceMode)
    }

    @Test
    fun echoIsIgnoredAndTheRealAnswerRightAfterIsUsed() = runTest {
        answers.say("이 사연을 전달할까요 다시 녹음하려면", "응", "원하시면 변조 목소리로 사연을 전달할까", "변조 목소리로")
        val c = controller()
        recording(c)
        c.finish()
        advanceUntilIdle()
        assertEquals(VoiceMode.MODULATED, store.sent.single().voiceMode)
        // 메아리 때문에 질문을 다시 하지 않았다
        assertEquals(1, speaker.said.count { it.startsWith("이 사연을 전달할까요") })
    }

    @Test
    fun noSpeechAtAllStopsAndKeepsNothing() = runTest {
        recorder.level = { 200 }
        val c = controller()
        recording(c)
        advanceUntilIdle()
        assertTrue(store.sent.isEmpty() && store.local.isEmpty())
        assertFalse(recorder.file!!.exists())
        assertTrue(speaker.said.last().contains("말소리"))
        assertEquals(0, answers.asked)
        assertEquals(FinishKind.NO_SPEECH, (c.state.value as RoadsideUi.Finished).kind)
    }

    @Test
    fun deliverThenOwnVoiceSubmitsForReviewAtStartPlace() = runTest {
        answers.say("응 보내줘", "아니")
        val c = controller()
        recording(c)
        location = seobinggo
        c.finish()
        advanceUntilIdle()
        val d = store.sent.single()
        assertEquals("p-ichon", d.placeId)
        assertTrue(d.recordedWhileDriving)
        assertEquals(VoiceMode.ORIGINAL, d.voiceMode)
        assertEquals(37.5245, d.anchorLat!!, 0.0001)
        assertEquals(2, answers.asked)
        assertTrue(speaker.said.any { it.contains("변조 목소리") })
        assertTrue((c.state.value as RoadsideUi.Finished).message.contains("검수"))
        assertEquals(FinishKind.SENT, (c.state.value as RoadsideUi.Finished).kind)
        assertEquals("focus-release", events.last { it.startsWith("focus") })
    }

    @Test
    fun choosingModulatedVoiceIsSaved() = runTest {
        answers.say("응", "변조 목소리로 해줘")
        val c = controller()
        recording(c)
        c.finish()
        advanceUntilIdle()
        assertEquals(VoiceMode.MODULATED, store.sent.single().voiceMode)
        assertEquals(FinishKind.SENT_MODULATED, (c.state.value as RoadsideUi.Finished).kind)
    }

    @Test
    fun sayingVoiceChoiceRightAwaySkipsSecondQuestion() = runTest {
        answers.say("변조 목소리로 보내줘")
        val c = controller()
        recording(c)
        c.finish()
        advanceUntilIdle()
        assertEquals(VoiceMode.MODULATED, store.sent.single().voiceMode)
        assertEquals(1, answers.asked)
    }

    @Test
    fun unclearVoiceChoiceKeepsOwnVoice() = runTest {
        answers.say("응", "음", "글쎄")
        val c = controller()
        recording(c)
        c.finish()
        advanceUntilIdle()
        assertEquals(VoiceMode.ORIGINAL, store.sent.single().voiceMode)
        assertEquals(3, answers.asked)
    }

    @Test
    fun rerecordWhileDrivingStartsOverForTheSamePlace() = runTest {
        answers.say("다시 녹음할래")
        val c = controller()
        recording(c)
        val first = recorder.file!!
        location = seobinggo
        c.finish()
        runCurrent()
        // 첫 녹음은 버리고 같은 장소로 다시 녹음한다
        assertFalse(first.exists())
        assertEquals(2, events.count { it == "record-start" })
        assertTrue(c.state.value is RoadsideUi.Recording)
        assertTrue(speaker.said.any { it == "사연을 다시 말해주세요." })
        answers.say("응", "아니")
        c.finish()
        advanceUntilIdle()
        val d = store.sent.single()
        assertEquals("p-ichon", d.placeId)
        assertEquals(recorder.file!!.absolutePath, d.audioPath)
    }

    @Test
    fun touchRerecordFromQuestion() = runTest {
        answers.whenEmpty = null
        val c = controller()
        recording(c)
        c.finish()
        runCurrent()
        assertTrue(c.rerecord(viaGleo = false))
        runCurrent()
        assertEquals(2, events.count { it == "record-start" })
        assertTrue("stop-voice" in events)
        c.discard(viaGleo = false)
        advanceUntilIdle()
    }

    @Test
    fun sayingNoDiscardsWithAnnouncement() = runTest {
        answers.say("아니")
        val c = controller()
        recording(c)
        val f = recorder.file!!
        c.finish()
        advanceUntilIdle()
        assertTrue(store.sent.isEmpty() && store.local.isEmpty())
        assertFalse(f.exists())
        assertTrue(speaker.said.last().contains("버렸어요"))
        assertEquals(FinishKind.DISCARDED, (c.state.value as RoadsideUi.Finished).kind)
    }

    @Test
    fun unclearAnswersNeverPublishAndKeepDraftOnDevice() = runTest {
        answers.say("음", "글쎄")
        val c = controller()
        recording(c)
        c.finish()
        advanceUntilIdle()
        assertTrue(store.sent.isEmpty())
        assertEquals(1, store.local.size)
        assertEquals(2, answers.asked)
        assertEquals(FinishKind.DRAFT_NO_ANSWER, (c.state.value as RoadsideUi.Finished).kind)
    }

    @Test
    fun touchAnswersWhileListeningWin() = runTest {
        answers.whenEmpty = null
        val c = controller()
        recording(c)
        c.finish()
        runCurrent()
        assertTrue(c.state.value is RoadsideUi.Asking)
        assertTrue(c.answer(StoryChoice.SUBMIT, viaGleo = false))
        runCurrent()
        val s = c.state.value
        assertTrue(s.toString(), s is RoadsideUi.Asking && s.kind == AskKind.VOICE)
        assertTrue(c.chooseVoice(VoiceMode.MODULATED, viaGleo = false))
        advanceUntilIdle()
        assertEquals(VoiceMode.MODULATED, store.sent.single().voiceMode)
        assertTrue("stop-voice" in events)
    }

    @Test
    fun gleoDiscardDuringRecordingKeepsNothingAndLetsGleoSpeak() = runTest {
        val c = controller()
        recording(c)
        val f = recorder.file!!
        assertEquals(DiscardResult.DISCARDED_IN_PROGRESS, c.discard(viaGleo = true))
        advanceUntilIdle()
        assertTrue(store.sent.isEmpty() && store.local.isEmpty())
        assertFalse(f.exists())
        assertTrue(speaker.said.none { it.contains("버렸어요") })
        assertTrue(c.state.value is RoadsideUi.Finished)
    }

    @Test
    fun discardAfterSavingDeletesTheSavedStory() = runTest {
        answers.say("응", "아니")
        val c = controller()
        recording(c)
        c.finish()
        advanceUntilIdle()
        assertEquals(DiscardResult.DELETED_SAVED, c.discard(viaGleo = false))
        advanceUntilIdle()
        assertEquals(listOf("s1"), store.deleted)
        assertTrue(speaker.said.last().contains("버렸어요"))
        assertEquals(DiscardResult.NOTHING, c.discard(viaGleo = false))
    }

    @Test
    fun discardAfterLocalDraftRemovesTheDraft() = runTest {
        answers.say("음", "음")
        val c = controller()
        recording(c)
        c.finish()
        advanceUntilIdle()
        val id = store.local.single().clientDraftId
        assertEquals(DiscardResult.DELETED_SAVED, c.discard(viaGleo = true))
        assertEquals(listOf(id), store.discardedLocal)
    }

    @Test
    fun phoneCallInterruptsIntoSilentLocalDraft() = runTest {
        val c = controller()
        recording(c)
        val spoken = speaker.said.size
        inCall = true
        focus.onLost!!("통화")
        advanceUntilIdle()
        assertTrue(store.sent.isEmpty())
        assertTrue(store.local.single().interrupted)
        assertEquals(spoken, speaker.said.size)
        assertEquals(0, answers.asked)
    }

    @Test
    fun gleoInterruptionPausesRecordingThenAsks() = runTest {
        answers.say("응", "아니")
        val c = controller()
        recording(c)
        focus.onLost!!("음성 비서")
        advanceUntilIdle()
        assertTrue("record-stop" in events)
        assertEquals(1, quietWaits)
        assertEquals(VoiceMode.ORIGINAL, store.sent.single().voiceMode)
    }

    @Test
    fun gleoSubmitWithVoiceAfterInterruptionIsUsedWithoutAsking() = runTest {
        val c = controller()
        recording(c)
        focus.onLost!!("음성 비서")
        runCurrent()
        assertTrue(c.answer(StoryChoice.SUBMIT, viaGleo = true, voice = VoiceMode.MODULATED))
        advanceUntilIdle()
        assertEquals(VoiceMode.MODULATED, store.sent.single().voiceMode)
        assertEquals(0, answers.asked)
    }

    @Test
    fun gleoFinishStopsRecordingAndAsks() = runTest {
        answers.say("응", "아니")
        val c = controller()
        recording(c)
        assertTrue(c.finish())
        advanceUntilIdle()
        assertEquals(1, store.sent.size)
    }

    @Test
    fun missingLocationAtStartKeepsDraftWithoutPlaceAndDoesNotAsk() = runTest {
        location = LocationValidity.Stale(30_000)
        val c = controller()
        recording(c)
        location = ichon
        c.finish()
        advanceUntilIdle()
        assertTrue(store.sent.isEmpty())
        assertNull(store.local.single().placeId)
        assertNull(store.local.single().anchorLat)
        assertEquals(0, answers.asked)
        assertEquals(FinishKind.DRAFT_NO_PLACE, (c.state.value as RoadsideUi.Finished).kind)
    }

    @Test
    fun networkFailureLeavesDraftAndSaysSo() = runTest {
        answers.say("응", "아니")
        store.outcome = { SaveOutcome.Deferred("서버에 연결할 수 없어요") }
        val c = controller()
        recording(c)
        c.finish()
        advanceUntilIdle()
        val msg = (c.state.value as RoadsideUi.Finished).message
        assertTrue(msg, msg.contains("초안"))
        assertEquals(FinishKind.DRAFT, (c.state.value as RoadsideUi.Finished).kind)
    }

    @Test
    fun deniedFocusDoesNotRecord() = runTest {
        focus.grant = false
        val c = controller()
        c.start(InputSource.TOUCH)
        advanceUntilIdle()
        assertFalse("record-start" in events)
        assertTrue(c.state.value is RoadsideUi.Finished)
    }

    @Test
    fun tooShortRecordingIsNotKept() = runTest {
        recorder.durationMs = 800
        val c = controller()
        recording(c)
        c.finish()
        advanceUntilIdle()
        assertTrue(store.sent.isEmpty() && store.local.isEmpty())
        assertEquals(0, answers.asked)
    }
}
