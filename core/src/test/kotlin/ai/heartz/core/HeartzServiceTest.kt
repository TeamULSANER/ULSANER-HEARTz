package ai.heartz.core

import ai.heartz.core.model.GeoPoint
import ai.heartz.core.model.ListenKind
import ai.heartz.core.model.Mood
import ai.heartz.core.model.Place
import ai.heartz.core.model.Segment
import ai.heartz.core.model.StoryStatus
import ai.heartz.core.model.User
import ai.heartz.core.model.UserRole
import ai.heartz.core.model.VoiceMode
import ai.heartz.core.moderation.ReviewAssist
import ai.heartz.core.moderation.Transcript
import ai.heartz.core.moderation.TranscriptStatus
import ai.heartz.core.seed.SeedData
import ai.heartz.core.service.ApiErrorCode
import ai.heartz.core.service.ApiException
import ai.heartz.core.service.BlobStore
import ai.heartz.core.service.CreateStoryRequest
import ai.heartz.core.service.HeartzDb
import ai.heartz.core.service.HeartzService
import ai.heartz.core.service.HeartzStore
import ai.heartz.core.service.ModerationAction
import ai.heartz.core.service.ModerationRequest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class MemoryStore : HeartzStore {
    var db: HeartzDb? = null
    var saves = 0
    override fun load() = db
    override fun save(db: HeartzDb) { this.db = db; saves++ }
}

class MemoryBlobs : BlobStore {
    val map = HashMap<String, ByteArray>()
    override fun put(blobId: String, bytes: ByteArray) { map[blobId] = bytes }
    override fun get(blobId: String) = map[blobId]
    override fun delete(blobId: String) { map.remove(blobId) }
}

class HeartzServiceTest {
    private val writer = User("u-writer", "작성자 (테스트)", UserRole.LISTENER)
    private val listener = User("u-listener", "청취자 (테스트)", UserRole.LISTENER)
    private val moderator = User("u-mod", "운영자 (테스트)", UserRole.MODERATOR)
    private val place = Place(
        "p-ichon", "이촌", "이촌 한강공원 입구", GeoPoint(37.52, 126.97),
        Segment(listOf(GeoPoint(37.52, 126.96), GeoPoint(37.52, 126.98)), 40.0),
    )
    private val audioBytes = byteArrayOf(1, 2, 3, 4)

    private lateinit var store: MemoryStore
    private lateinit var blobs: MemoryBlobs
    private lateinit var service: HeartzService
    private var now = 1_000L
    private var nextId = 0

    @Before
    fun setUp() {
        store = MemoryStore()
        blobs = MemoryBlobs()
        service = HeartzService(store, blobs, clock = { now }, idGenerator = { "id-${nextId++}" })
        service.seedIfEmpty(HeartzDb(users = listOf(writer, listener, moderator), places = listOf(place)), emptyMap())
    }

    private fun expectError(code: ApiErrorCode, block: () -> Unit) {
        try {
            block()
            fail("expected $code")
        } catch (e: ApiException) {
            assertEquals(e.message, code, e.code)
        }
    }

    private fun draft(clientId: String = "c1", parent: String? = null, durationMs: Long = 30_000, allowReplies: Boolean = true) =
        service.createDraft(writer, CreateStoryRequest(clientId, place.id, Mood.WARM, durationMs, parent, allowReplies))

    private fun pendingStory(clientId: String = "c1"): String {
        val s = draft(clientId)
        service.uploadAudio(writer, s.id, audioBytes, "audio/mp4")
        service.submit(writer, s.id)
        return s.id
    }

    private fun approvedStory(clientId: String = "c1", allowReplies: Boolean = true): String {
        val s = draft(clientId, allowReplies = allowReplies)
        service.uploadAudio(writer, s.id, audioBytes, "audio/mp4")
        service.submit(writer, s.id)
        service.moderate(moderator, s.id, ModerationRequest(ModerationAction.APPROVE))
        return s.id
    }

    @Test
    fun nicknameFromPleosProfileIsSavedAndShownOnExistingStories() {
        val id = approvedStory()
        val user = service.setNickname(writer, "  운전자  ")
        assertEquals("운전자", user.displayName)
        assertEquals("운전자", service.publicFeed(listener).single { it.id == id }.authorName)
        assertEquals("운전자", service.story(listener, id).authorName)
        assertEquals("운전자", service.myStories(writer).single().authorName)
        val restored = HeartzService(store, blobs)
        assertEquals(user, restored.user(writer.id))
        assertEquals(listener, restored.user(listener.id))
    }

    @Test
    fun invalidNicknameIsNotSaved() {
        for (nickname in listOf("   ", "가".repeat(13), "두\n줄")) {
            expectError(ApiErrorCode.INVALID) { service.setNickname(writer, nickname) }
        }
        assertEquals(writer, service.user(writer.id))
    }

    @Test
    fun unknownAccountCannotSaveNickname() {
        expectError(ApiErrorCode.UNAUTHORIZED) { service.setNickname(writer.copy(id = "unknown"), "운전자") }
    }

    @Test
    fun seedIsAppliedOnlyWhenStoreIsEmpty() {
        val other = HeartzService(store, blobs)
        other.seedIfEmpty(HeartzDb(users = listOf(listener)), emptyMap())
        assertEquals(3, other.users().size)
    }

    @Test
    fun draftCreationIsIdempotentPerClientDraftId() {
        val a = draft("same")
        val b = draft("same")
        assertEquals(a.id, b.id)
        assertEquals(1, service.myStories(writer).size)
        assertEquals(StoryStatus.DRAFT, a.status)
    }

    @Test
    fun draftWithUnknownPlaceOrBadDurationIsInvalid() {
        expectError(ApiErrorCode.INVALID) {
            service.createDraft(writer, CreateStoryRequest("x", "nowhere", Mood.CALM, 30_000))
        }
        expectError(ApiErrorCode.INVALID) { draft("short", durationMs = 500) }
        expectError(ApiErrorCode.INVALID) { draft("long", durationMs = 120_000) }
    }

    @Test
    fun uploadSubmitApproveReturnDistinctResults() {
        val s = draft()
        val upload = service.uploadAudio(writer, s.id, audioBytes, "audio/mp4")
        assertEquals(StoryStatus.DRAFT, upload.status)
        assertEquals(4L, upload.sizeBytes)

        val submit = service.submit(writer, s.id)
        assertEquals(StoryStatus.PENDING_REVIEW, submit.status)
        assertFalse(submit.alreadySubmitted)

        val approve = service.moderate(moderator, s.id, ModerationRequest(ModerationAction.APPROVE))
        assertEquals(StoryStatus.APPROVED, approve.status)
        assertTrue(approve.publiclyPlayable)
    }

    @Test
    fun submitWithoutAudioIsInvalid() {
        val s = draft()
        expectError(ApiErrorCode.INVALID) { service.submit(writer, s.id) }
    }

    @Test
    fun resubmitDoesNotDuplicateAndUploadAfterSubmitConflicts() {
        val id = pendingStory()
        val again = service.submit(writer, id)
        assertTrue(again.alreadySubmitted)
        assertEquals(StoryStatus.PENDING_REVIEW, again.status)
        assertEquals(1, service.moderationOverview(moderator).pending.size)
        expectError(ApiErrorCode.CONFLICT) { service.uploadAudio(writer, id, audioBytes, "audio/mp4") }
    }

    @Test
    fun onlyAuthorCanUploadOrSubmit() {
        val s = draft()
        expectError(ApiErrorCode.NOT_FOUND) { service.uploadAudio(listener, s.id, audioBytes, "audio/mp4") }
        service.uploadAudio(writer, s.id, audioBytes, "audio/mp4")
        expectError(ApiErrorCode.NOT_FOUND) { service.submit(listener, s.id) }
    }

    @Test
    fun oversizedAudioIsRejected() {
        val s = draft()
        expectError(ApiErrorCode.TOO_LARGE) {
            service.uploadAudio(writer, s.id, ByteArray((HeartzService.MAX_AUDIO_BYTES + 1).toInt()), "audio/mp4")
        }
    }

    @Test
    fun privatePendingAndRejectedAreInvisibleToOtherAccounts() {
        // 보내지 않은 초안(나만 보기 범위는 없앴다)
        val privateStory = draft("private").also { service.uploadAudio(writer, it.id, audioBytes, "audio/mp4") }
        val pending = pendingStory("pending")
        val rejected = pendingStory("rejected").also {
            service.moderate(moderator, it, ModerationRequest(ModerationAction.REJECT, "음질"))
        }

        assertTrue(service.publicFeed(listener).isEmpty())
        for (id in listOf(privateStory.id, pending, rejected)) {
            expectError(ApiErrorCode.NOT_FOUND) { service.audio(listener, id) }
            expectError(ApiErrorCode.NOT_FOUND) { service.story(listener, id) }
        }
        // 작성자 본인은 자신의 사연과 음원을 확인할 수 있다
        assertEquals(3, service.myStories(writer).size)
        assertArrayEquals(audioBytes, service.audio(writer, pending).second)
        assertEquals("음질", service.story(writer, rejected).reviewNote)
    }

    @Test
    fun approvedStoryReachesSecondAccountWithAudio() {
        val id = approvedStory()
        assertEquals(listOf(id), service.publicFeed(listener).map { it.id })
        assertArrayEquals(audioBytes, service.audio(listener, id).second)
    }

    @Test
    fun hiddenStoryLeavesPublicFeedAndAudio() {
        val id = approvedStory()
        service.moderate(moderator, id, ModerationRequest(ModerationAction.HIDE, "신고 검토"))
        assertTrue(service.publicFeed(listener).isEmpty())
        expectError(ApiErrorCode.NOT_FOUND) { service.audio(listener, id) }
    }

    @Test
    fun listenerCannotModerateOrReadQueue() {
        val id = pendingStory()
        expectError(ApiErrorCode.FORBIDDEN) { service.moderate(writer, id, ModerationRequest(ModerationAction.APPROVE)) }
        expectError(ApiErrorCode.FORBIDDEN) { service.moderationOverview(listener) }
        assertEquals(StoryStatus.PENDING_REVIEW, service.story(writer, id).status)
    }

    @Test
    fun moderationTransitionsAreValidated() {
        val id = approvedStory()
        expectError(ApiErrorCode.CONFLICT) { service.moderate(moderator, id, ModerationRequest(ModerationAction.APPROVE)) }
        val pending = pendingStory("p2")
        expectError(ApiErrorCode.CONFLICT) { service.moderate(moderator, pending, ModerationRequest(ModerationAction.HIDE)) }
    }

    @Test
    fun voiceRepliesToOthersAreRejected() {
        // 남의 사연에 대한 음성 답장은 제공하지 않는다(공감 하트만)
        val open = approvedStory("open")
        expectError(ApiErrorCode.INVALID) { draft("r1", parent = open, durationMs = 15_000) }
        assertEquals(listOf(open), service.publicFeed(listener).map { it.id })
    }

    @Test
    fun listenerCanLeaveOneHeartOnApprovedStoryAndAuthorSeesCount() {
        val id = approvedStory()
        val st = service.heart(listener, id, on = true)
        assertTrue(id in st.hearted)
        service.heart(listener, id, on = true) // 같은 사람이 여러 번 눌러도 한 번
        assertEquals(1, service.myStories(writer).single { it.id == id }.heartCount)
        assertEquals(1, service.story(listener, id).heartCount)
        service.heart(listener, id, on = false)
        assertEquals(0, service.myStories(writer).single { it.id == id }.heartCount)
        assertFalse(id in service.listenState(listener).hearted)
    }

    @Test
    fun clearingListenHistoryKeepsHeartsAndHiddenAuthors() {
        val id = approvedStory()
        service.recordListen(listener, id, ListenKind.SUGGESTED)
        service.recordListen(listener, id, ListenKind.PLAYED)
        service.heart(listener, id, on = true)
        service.hideAuthor(listener, "someone")
        val st = service.clearListenHistory(listener)
        assertTrue(st.suggested.isEmpty() && st.played.isEmpty())
        assertTrue(id in st.hearted)
        assertEquals(setOf("someone"), st.hiddenAuthorIds)
    }

    @Test
    fun seedRefreshMovesAnExistingSampleStoryToItsNewTeamRecording() {
        val seeded = HeartzService(MemoryStore(), MemoryBlobs(), clock = { now })
        val db = ai.heartz.core.seed.SeedData.db(0)
        // 예전 기기 데이터: 시드 사연이 합성 음원(old-synth)을 가리킨다
        val old = db.copy(stories = db.stories.map {
            if (it.id == "s-seed-seobinggo") it.copy(audio = it.audio!!.copy(blobId = "old-synth"), durationMs = 7_000, sampleLabel = SeedData.SAMPLE_LABEL, createdAtMillis = 123) else it
        })
        seeded.seedIfEmpty(old, mapOf("old-synth" to byteArrayOf(9)))
        val listener = db.users.first()
        val mine = seeded.createDraft(listener, CreateStoryRequest("own", "p-ichon", Mood.WARM, 30_000))
        // 새 시드 정의: 같은 사연이 팀원 녹음(new-team)을 가리킨다
        val newSeed = db.stories.map {
            if (it.id == "s-seed-seobinggo") it.copy(audio = it.audio!!.copy(blobId = "new-team"), durationMs = 15_000, sampleLabel = SeedData.TEAM_LABEL, voiceMode = VoiceMode.MODULATED, spokenText = "읽어 줄 글") else it
        }
        val newBytes = byteArrayOf(1, 2, 3, 4, 5)
        seeded.refreshSeedStories(newSeed, mapOf("new-team" to newBytes))
        val s = seeded.story(listener, "s-seed-seobinggo")
        assertEquals("new-team", s.audio!!.blobId)
        assertEquals(5L, s.audio!!.sizeBytes)
        assertEquals(15_000L, s.durationMs)
        assertEquals(SeedData.TEAM_LABEL, s.sampleLabel)
        assertEquals(VoiceMode.MODULATED, s.voiceMode)
        assertEquals("읽어 줄 글", s.spokenText)
        assertEquals(123L, s.createdAtMillis) // 기존 데이터(작성 시각·상태)는 그대로
        assertEquals(StoryStatus.APPROVED, s.status)
        // 변조 목소리 사연의 원본 녹음은 운영자만 받는다(듣는 사람에게는 글을 TTS로 읽어 준다)
        val moderator = db.users.single { it.role == UserRole.MODERATOR }
        assertArrayEquals(newBytes, seeded.audio(moderator, "s-seed-seobinggo").second)
        try {
            seeded.audio(listener, "s-seed-seobinggo")
            fail("listener must not get the original recording of a modulated story")
        } catch (e: ApiException) {
            assertEquals(ApiErrorCode.NOT_FOUND, e.code)
        }
        // 음원이 없는 시드 사연은 건드리지 않는다
        assertEquals(db.stories.single { it.id == "s-seed-dongbu" }.audio, seeded.story(listener, "s-seed-dongbu").audio)
        // 사람이 남긴 사연은 그대로
        assertEquals(30_000L, seeded.myStories(listener).single { it.id == mine.id }.durationMs)
    }

    @Test
    fun modulatedStoryIsReadFromTranscriptAndOriginalVoiceStaysWithAuthor() {
        val s = service.createDraft(writer, CreateStoryRequest("m1", place.id, Mood.WARM, 30_000, voiceMode = VoiceMode.MODULATED))
        service.uploadAudio(writer, s.id, audioBytes, "audio/mp4")
        service.submit(writer, s.id)
        // 읽어 줄 글이 없으면 공개할 수 없다(원래 목소리를 대신 내보내지 않는다)
        expectError(ApiErrorCode.CONFLICT) { service.moderate(moderator, s.id, ModerationRequest(ModerationAction.APPROVE)) }
        service.attachAssist(writer, s.id, ReviewAssist(Transcript(TranscriptStatus.DONE, "오늘 이 길에서 노을을 봤어요", "test"), emptyList(), now))
        service.moderate(moderator, s.id, ModerationRequest(ModerationAction.APPROVE))
        val seen = service.story(listener, s.id)
        assertEquals(VoiceMode.MODULATED, seen.voiceMode)
        assertEquals("오늘 이 길에서 노을을 봤어요", seen.spokenText)
        assertEquals(null, seen.assist)
        expectError(ApiErrorCode.NOT_FOUND) { service.audio(listener, s.id) }
        assertArrayEquals(audioBytes, service.audio(writer, s.id).second)
        assertArrayEquals(audioBytes, service.audio(moderator, s.id).second)
    }

    @Test
    fun originalVoiceStoryHasNoSpokenText() {
        val s = draft("o1")
        service.uploadAudio(writer, s.id, audioBytes, "audio/mp4")
        service.attachAssist(writer, s.id, ReviewAssist(Transcript(TranscriptStatus.DONE, "안녕하세요", "test"), emptyList(), now))
        assertEquals(VoiceMode.ORIGINAL, service.myStories(writer).single { it.id == s.id }.voiceMode)
        assertEquals(null, service.myStories(writer).single { it.id == s.id }.spokenText)
    }

    @Test
    fun heartIsOnlyForOthersPublicStories() {
        val id = approvedStory()
        expectError(ApiErrorCode.INVALID) { service.heart(writer, id, on = true) }
        val pending = pendingStory("p")
        expectError(ApiErrorCode.NOT_FOUND) { service.heart(listener, pending, on = true) }
    }

    @Test
    fun reportIsVisibleToModeratorOnlyForVisibleStories() {
        val id = approvedStory()
        service.report(listener, id, "개인정보 노출")
        assertEquals(listOf(id), service.moderationOverview(moderator).reports.map { it.storyId })
        val pending = pendingStory("p")
        expectError(ApiErrorCode.NOT_FOUND) { service.report(listener, pending, "x") }
    }

    @Test
    fun listenStateAndHiddenAuthorsArePerUser() {
        val id = approvedStory()
        service.recordListen(listener, id, ListenKind.SUGGESTED)
        val state = service.recordListen(listener, id, ListenKind.PLAYED)
        assertEquals(setOf(id), state.played.keys)
        assertEquals(setOf(id), state.suggested.keys)
        assertTrue(service.listenState(writer).played.isEmpty())

        assertEquals(setOf(writer.id), service.hideAuthor(listener, writer.id).hiddenAuthorIds)
        assertTrue(service.unhideAuthor(listener, writer.id).hiddenAuthorIds.isEmpty())
        expectError(ApiErrorCode.INVALID) { service.hideAuthor(listener, listener.id) }
    }

    @Test
    fun stateSurvivesReloadFromStore() {
        val id = approvedStory()
        val reloaded = HeartzService(store, blobs)
        assertEquals(listOf(id), reloaded.publicFeed(listener).map { it.id })
    }

    @Test
    fun deletingOwnStoryRemovesItAndItsAudio() {
        val id = approvedStory()
        service.deleteOwn(writer, id)
        assertTrue(service.publicFeed(listener).isEmpty())
        assertTrue(blobs.map.isEmpty())
        val other = approvedStory("other")
        expectError(ApiErrorCode.NOT_FOUND) { service.deleteOwn(listener, other) }
    }
}
