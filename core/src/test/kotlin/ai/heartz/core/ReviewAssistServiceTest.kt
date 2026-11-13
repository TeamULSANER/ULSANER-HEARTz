package ai.heartz.core

import ai.heartz.core.model.GeoPoint
import ai.heartz.core.model.Mood
import ai.heartz.core.model.Place
import ai.heartz.core.model.Segment
import ai.heartz.core.model.User
import ai.heartz.core.model.UserRole
import ai.heartz.core.moderation.ReviewAssist
import ai.heartz.core.moderation.ReviewFlag
import ai.heartz.core.moderation.ReviewFlagKind
import ai.heartz.core.moderation.Transcript
import ai.heartz.core.moderation.TranscriptStatus
import ai.heartz.core.service.ApiErrorCode
import ai.heartz.core.service.ApiException
import ai.heartz.core.service.CreateStoryRequest
import ai.heartz.core.service.HeartzDb
import ai.heartz.core.service.HeartzService
import ai.heartz.core.service.ModerationAction
import ai.heartz.core.service.ModerationRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** 받아쓰기·검수 보조는 작성자와 운영자에게만 보이고, 상태(승인 여부)를 바꾸지 않는다. */
class ReviewAssistServiceTest {
    private val writer = User("u-writer", "작성자 (테스트)", UserRole.LISTENER)
    private val listener = User("u-listener", "청취자 (테스트)", UserRole.LISTENER)
    private val moderator = User("u-mod", "운영자 (테스트)", UserRole.MODERATOR)
    private val place = Place(
        "p-ichon", "이촌", "이촌 한강공원 입구", GeoPoint(37.52, 126.97),
        Segment(listOf(GeoPoint(37.52, 126.96), GeoPoint(37.52, 126.98)), 40.0),
    )
    private lateinit var service: HeartzService
    private var nextId = 0
    private val assist = ReviewAssist(
        Transcript(TranscriptStatus.DONE, "제 번호는 010-1234-5678이에요", engine = "test"),
        listOf(ReviewFlag(ReviewFlagKind.PHONE_NUMBER)),
        createdAtMillis = 5,
    )

    @Before
    fun setUp() {
        service = HeartzService(MemoryStore(), MemoryBlobs(), clock = { 1_000L }, idGenerator = { "id-${nextId++}" })
        service.seedIfEmpty(HeartzDb(users = listOf(writer, listener, moderator), places = listOf(place)), emptyMap())
    }

    private fun submitted(): String {
        val s = service.createDraft(writer, CreateStoryRequest("c1", place.id, Mood.WARM, 10_000))
        service.uploadAudio(writer, s.id, byteArrayOf(1, 2), "audio/mp4")
        service.submit(writer, s.id)
        return s.id
    }

    private fun expect(code: ApiErrorCode, block: () -> Unit) {
        try { block(); fail("expected $code") } catch (e: ApiException) { assertEquals(code, e.code) }
    }

    @Test
    fun authorAttachesAssistAndModeratorSeesIt() {
        val id = submitted()
        service.attachAssist(writer, id, assist)
        assertEquals(assist, service.moderationOverview(moderator).pending.single().assist)
        assertEquals(assist, service.myStories(writer).single().assist)
    }

    @Test
    fun assistDoesNotChangeReviewStatus() {
        val id = submitted()
        val before = service.story(writer, id).status
        service.attachAssist(writer, id, assist)
        assertEquals(before, service.story(writer, id).status)
    }

    @Test
    fun othersCannotAttachAssist() {
        val id = submitted()
        expect(ApiErrorCode.NOT_FOUND) { service.attachAssist(listener, id, assist) }
    }

    @Test
    fun publicListenersNeverReceiveTranscript() {
        val id = submitted()
        service.attachAssist(writer, id, assist)
        service.moderate(moderator, id, ModerationRequest(ModerationAction.APPROVE))
        assertNull(service.publicFeed(listener).single().assist)
        assertNull(service.story(listener, id).assist)
        assertEquals(assist, service.story(moderator, id).assist)
    }

    @Test
    fun assistCannotBeAttachedAfterReview() {
        val id = submitted()
        service.moderate(moderator, id, ModerationRequest(ModerationAction.APPROVE))
        expect(ApiErrorCode.CONFLICT) { service.attachAssist(writer, id, assist) }
    }
}
