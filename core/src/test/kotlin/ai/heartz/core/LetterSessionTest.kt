package ai.heartz.core

import ai.heartz.core.model.AudioRef
import ai.heartz.core.model.GeoPoint
import ai.heartz.core.model.ListenState
import ai.heartz.core.model.Mood
import ai.heartz.core.model.Place
import ai.heartz.core.model.Story
import ai.heartz.core.model.StoryStatus
import ai.heartz.core.schedule.LetterPicker
import ai.heartz.core.schedule.LetterSession
import ai.heartz.core.schedule.LocationFix
import ai.heartz.core.schedule.LocationValidity
import ai.heartz.core.schedule.SessionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LetterSessionTest {
    // 서·동 두 랜드마크, 서로 약 2.6 km 떨어짐(반경 1 km)
    private val west = Place("w", "서쪽", "서구 서동 서쪽", GeoPoint(37.5, 127.0))
    private val east = Place("e", "동쪽", "동구 동동 동쪽", GeoPoint(37.5, 127.03))
    private val places = listOf(west, east)

    private fun story(id: String, place: String) = Story(
        id = id, authorId = "author", authorName = "작성자", placeId = place,
        audio = AudioRef("b-$id", "audio/mp4", 1), durationMs = 30_000, mood = Mood.WARM,
        status = StoryStatus.APPROVED, createdAtMillis = 0, clientDraftId = id,
    )

    private val stories = listOf(story("w1", "w"), story("e1", "e"))
    private fun at(lng: Double, t: Long) = LocationValidity.Valid(LocationFix(37.5, lng, 90.0, t, "test"))
    private val inWest = 127.0
    private val between = 127.015
    private val inEast = 127.03

    private val session = LetterSession(LetterPicker(Random(0)), lingerMillis = 90_000)

    private fun step(v: LocationValidity, now: Long, state: ListenState = ListenState()) =
        session.onLocation(v, now, stories, places, state, viewerId = "viewer")

    @Test
    fun validFixInsideSegmentShowsOneCard() {
        val events = step(at(inWest, 0), 0)
        val shown = events.single() as SessionEvent.Shown
        assertEquals("w1", shown.card.story.id)
        assertEquals("w", session.card!!.place.id)
    }

    @Test
    fun missingStaleOrInvalidLocationNeverShowsCard() {
        assertTrue(step(LocationValidity.Missing, 0).isEmpty())
        assertTrue(step(LocationValidity.Stale(30_000), 0).isEmpty())
        assertTrue(step(LocationValidity.Invalid("x"), 0).isEmpty())
        assertNull(session.card)
    }

    @Test
    fun cardStaysWhileInSegmentWithoutDuplicateShown() {
        step(at(inWest, 0), 0)
        for (t in 1..10) assertTrue(step(at(inWest, t * 1000L), t * 1000L).isEmpty())
        assertEquals("w1", session.card!!.story.id)
    }

    @Test
    fun cardExpiresOnlyAfterLingerOutsideSegment() {
        step(at(inWest, 0), 0)
        assertTrue(step(at(between, 1_000), 1_000).isEmpty())
        assertTrue(step(at(between, 90_000), 90_000).isEmpty())
        assertNotNull(session.card)
        val events = step(at(between, 91_001), 91_001)
        assertEquals(listOf<SessionEvent>(SessionEvent.Cleared("w1")), events)
        assertNull(session.card)
    }

    @Test
    fun staleLocationAlsoLetsCardExpireAfterLinger() {
        step(at(inWest, 0), 0)
        assertTrue(step(LocationValidity.Stale(20_000), 60_000).isEmpty())
        assertEquals(SessionEvent.Cleared("w1"), step(LocationValidity.Missing, 90_001).single())
    }

    @Test
    fun playingCardNeverExpiresAndExpiresAfterPlaybackEnds() {
        step(at(inWest, 0), 0)
        session.onPlaybackStarted("w1")
        assertTrue(step(at(between, 500_000), 500_000).isEmpty())
        assertTrue(session.card!!.playing)
        session.onPlaybackStopped("w1", completed = true, nowMillis = 600_000)
        assertTrue(session.card!!.playedToEnd)
        assertTrue(step(at(between, 650_000), 650_000).isEmpty())
        assertEquals(SessionEvent.Cleared("w1"), step(at(between, 690_001), 690_001).single())
    }

    @Test
    fun enteringAnotherSegmentReplacesCardThatIsNotPlaying() {
        step(at(inWest, 0), 0)
        step(at(between, 5_000), 5_000)
        val events = step(at(inEast, 10_000), 10_000)
        assertEquals(SessionEvent.Cleared("w1"), events[0])
        assertEquals("e1", (events[1] as SessionEvent.Shown).card.story.id)
    }

    @Test
    fun enteringAnotherSegmentDoesNotInterruptPlayingCardButQueuesTheNewStory() {
        step(at(inWest, 0), 0)
        session.onPlaybackStarted("w1")
        val events = step(at(inEast, 10_000), 10_000)
        assertEquals("e1", (events.single() as SessionEvent.Queued).card.story.id)
        assertEquals("w1", session.card!!.story.id)
    }

    @Test
    fun queuedStoryFollowsRightAfterTheCurrentOneCloses() {
        step(at(inWest, 0), 0)
        session.busy = true // 읽는 중·공감 묻는 중
        assertTrue(step(at(inEast, 10_000), 10_000).single() is SessionEvent.Queued)
        // 같은 곳에 머물러도 다시 줄 세우지 않는다
        assertTrue(step(at(inEast, 11_000), 11_000).isEmpty())
        // 바쁜 동안에는 구간을 벗어나 오래 지나도 지금 카드를 내리지 않는다
        assertTrue(step(at(between, 200_000), 200_000).isEmpty())
        assertEquals("w1", session.card!!.story.id)
        session.busy = false
        assertTrue(session.dismiss("w1"))
        // 이미 그곳을 지났어도 줄 선 사연이 바로 이어진다
        assertEquals("e1", (step(at(between, 201_000), 201_000).single() as SessionEvent.Shown).card.story.id)
    }

    @Test
    fun storyShownOnceIsNotShownAgainInSameSession() {
        step(at(inWest, 0), 0)
        step(at(between, 100_000), 100_000) // 만료
        assertNull(session.card)
        assertTrue(step(at(inWest, 110_000), 110_000).isEmpty())
    }

    @Test
    fun dismissedCardClearsAndNoNewLetterUntilLeavingThatPlace() {
        val two = stories + story("w2", "w")
        fun stepTwo(v: LocationValidity, now: Long) = session.onLocation(v, now, two, places, ListenState(), viewerId = "viewer")
        val first = (stepTwo(at(inWest, 0), 0).single() as SessionEvent.Shown).card.story.id
        assertTrue(session.dismiss(first))
        assertNull(session.card)
        // 같은 곳에 남아 있는 동안에는 다음 편지를 바로 띄우지 않는다(대기 화면으로 돌아감)
        assertTrue(stepTwo(at(inWest, 1_000), 1_000).isEmpty())
        assertNull(session.card)
        // 다른 곳에 들어가면 다시 편지가 온다
        assertEquals("e1", (stepTwo(at(inEast, 2_000), 2_000).single() as SessionEvent.Shown).card.story.id)
    }

    @Test
    fun dismissOfOtherStoryDoesNothing() {
        step(at(inWest, 0), 0)
        assertTrue(!session.dismiss("e1"))
        assertEquals("w1", session.card!!.story.id)
    }

    @Test
    fun resetForgetsCardAndShownHistory() {
        step(at(inWest, 0), 0)
        session.reset()
        assertNull(session.card)
        assertEquals("w1", (step(at(inWest, 1_000), 1_000).single() as SessionEvent.Shown).card.story.id)
    }

    @Test
    fun playbackCallbacksForOtherStoryAreIgnored() {
        step(at(inWest, 0), 0)
        session.onPlaybackStarted("other")
        assertTrue(!session.card!!.playing)
    }

    @Test
    fun hidingTheAuthorClearsTheCurrentCardImmediately() {
        step(at(inWest, 0), 0)
        assertEquals("w1", session.card?.story?.id)
        val events = step(at(inWest, 1_000), 1_000, ListenState(hiddenAuthorIds = setOf("author")))
        assertNull(session.card)
        assertEquals(SessionEvent.Cleared("w1"), events.single())
    }

    @Test
    fun storyWithdrawnFromFeedClearsTheCurrentCard() {
        step(at(inWest, 0), 0)
        val events = session.onLocation(at(inWest, 1_000), 1_000, stories.filter { it.id != "w1" }, places, ListenState(), "viewer")
        assertNull(session.card)
        assertEquals(SessionEvent.Cleared("w1"), events.single())
    }
}
