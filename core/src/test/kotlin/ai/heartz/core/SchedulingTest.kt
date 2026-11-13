package ai.heartz.core

import ai.heartz.core.geo.Geo
import ai.heartz.core.model.AudioRef
import ai.heartz.core.model.GeoPoint
import ai.heartz.core.model.ListenState
import ai.heartz.core.model.Mood
import ai.heartz.core.model.Place
import ai.heartz.core.model.Story
import ai.heartz.core.model.StoryStatus
import ai.heartz.core.schedule.Eligibility
import ai.heartz.core.schedule.Exclusion
import ai.heartz.core.schedule.LetterPicker
import ai.heartz.core.schedule.LocationFix
import ai.heartz.core.schedule.LocationRules
import ai.heartz.core.schedule.LocationValidity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GeoTest {
    @Test
    fun distanceOfOneThousandthDegreeLatitudeIsAbout111Meters() {
        val d = Geo.distanceMeters(GeoPoint(37.0, 127.0), GeoPoint(37.001, 127.0))
        assertEquals(111.195, d, 0.05)
    }

    @Test
    fun bearingNorthAndEast() {
        assertEquals(0.0, Geo.bearingDeg(GeoPoint(37.0, 127.0), GeoPoint(37.001, 127.0)), 0.01)
        assertEquals(90.0, Geo.bearingDeg(GeoPoint(37.0, 127.0), GeoPoint(37.0, 127.001)), 0.01)
        assertEquals(270.0, Geo.bearingDeg(GeoPoint(37.0, 127.001), GeoPoint(37.0, 127.0)), 0.01)
    }

    @Test
    fun headingDiffWrapsAround360() {
        assertEquals(20.0, Geo.headingDiffDeg(350.0, 10.0), 1e-9)
        assertEquals(180.0, Geo.headingDiffDeg(90.0, 270.0), 1e-9)
    }

    @Test
    fun nearestOnPolylineMeasuresPerpendicularDistance() {
        val line = listOf(GeoPoint(37.5, 127.0), GeoPoint(37.5, 127.01))
        val hit = Geo.nearestOnPolyline(GeoPoint(37.5003, 127.005), line)
        assertEquals(33.36, hit.distanceMeters, 0.3)
        assertEquals(90.0, hit.segmentBearingDeg, 0.5)
    }

    @Test
    fun nearestOnPolylineBeyondEndpointUsesEndpointDistance() {
        val line = listOf(GeoPoint(37.5, 127.0), GeoPoint(37.5, 127.01))
        // 시작점에서 정확히 남쪽 0.001° → 끝점 거리 111.2 m (선분 연장선 위가 아님)
        val hit = Geo.nearestOnPolyline(GeoPoint(37.499, 127.0), line)
        assertEquals(111.195, hit.distanceMeters, 0.3)
    }
}

class LocationRulesTest {
    private fun fix(lat: Double = 37.5, lng: Double = 127.0, t: Long = 10_000L) =
        LocationFix(lat, lng, headingDeg = 90.0, timestampMillis = t, source = "test")

    @Test
    fun nullIsMissingNotSuccess() {
        assertEquals(LocationValidity.Missing, LocationRules.validate(null, nowMillis = 10_000))
    }

    @Test
    fun oldFixIsStaleWithAge() {
        val r = LocationRules.validate(fix(t = 1_000), nowMillis = 21_000, maxAgeMillis = 15_000)
        assertEquals(LocationValidity.Stale(20_000), r)
    }

    @Test
    fun freshFixIsValid() {
        val f = fix(t = 10_000)
        assertEquals(LocationValidity.Valid(f), LocationRules.validate(f, nowMillis = 12_000))
    }

    @Test
    fun zeroZeroAndOutOfRangeAndNaNAreInvalid() {
        assertTrue(LocationRules.validate(fix(lat = 0.0, lng = 0.0), 10_000) is LocationValidity.Invalid)
        assertTrue(LocationRules.validate(fix(lat = 91.0), 10_000) is LocationValidity.Invalid)
        assertTrue(LocationRules.validate(fix(lng = Double.NaN), 10_000) is LocationValidity.Invalid)
    }

    @Test
    fun fixFarInTheFutureIsInvalid() {
        assertTrue(LocationRules.validate(fix(t = 200_000), nowMillis = 10_000) is LocationValidity.Invalid)
    }
}

class EligibilityTest {
    // 랜드마크 A, 그 북쪽 약 3 km에 랜드마크 B. 둘 다 반경 1 km
    private val roadA = Place(id = "a", shortName = "A", displayName = "A구 A동 A", publicLocation = GeoPoint(37.5, 127.005))
    private val roadB = Place(id = "b", shortName = "B", displayName = "B구 B동 B", publicLocation = GeoPoint(37.527, 127.005))
    private val places = listOf(roadA, roadB)

    private fun story(
        id: String,
        placeId: String = "a",
        status: StoryStatus = StoryStatus.APPROVED,
        author: String = "author-1",
        parent: String? = null,
        audio: Boolean = true,
    ) = Story(
        id = id, authorId = author, authorName = "작성자", placeId = placeId,
        audio = if (audio) AudioRef("blob-$id", "audio/mp4", 100) else null,
        durationMs = 30_000, mood = Mood.WARM, status = status, createdAtMillis = 0,
        parentStoryId = parent, clientDraftId = "draft-$id",
    )

    // 랜드마크 A 바로 옆, 동쪽(90°)으로 진행
    private val onA = LocationFix(37.5, 127.005, headingDeg = 90.0, timestampMillis = 0, source = "test")

    private fun exclusionsOf(s: Story, fix: LocationFix = onA, state: ListenState = ListenState()) =
        Eligibility.evaluate(listOf(s), places, fix, state, viewerId = "viewer").single().exclusions

    @Test
    fun approvedStoryInSegmentIsEligible() {
        assertEquals(emptySet<Exclusion>(), exclusionsOf(story("s1")))
    }

    @Test
    fun pendingRejectedHiddenAndPrivateAreNotApproved() {
        for (st in listOf(StoryStatus.PENDING_REVIEW, StoryStatus.REJECTED, StoryStatus.HIDDEN, StoryStatus.PRIVATE, StoryStatus.DRAFT)) {
            assertTrue(st.name, Exclusion.NOT_APPROVED in exclusionsOf(story("s", status = st)))
        }
    }

    @Test
    fun storyOfALandmarkMoreThanOneKilometerAwayIsNotSuggested() {
        assertEquals(setOf(Exclusion.OUTSIDE_AREA), exclusionsOf(story("s", placeId = "b")))
    }

    @Test
    fun anyDirectionOrMissingHeadingCountsInsideTheRadius() {
        assertEquals(emptySet<Exclusion>(), exclusionsOf(story("s"), fix = onA.copy(headingDeg = 270.0)))
        assertEquals(emptySet<Exclusion>(), exclusionsOf(story("s"), fix = onA.copy(headingDeg = null)))
    }

    @Test
    fun edgeOfTheRadiusDecidesArrival() {
        val near = LocationFix(37.5 + 950 / 111_320.0, 127.005, null, 0, "test")
        val far = LocationFix(37.5 - 1_050 / 111_320.0, 127.005, null, 0, "test")
        assertEquals(emptySet<Exclusion>(), exclusionsOf(story("s"), fix = near))
        assertEquals(setOf(Exclusion.OUTSIDE_AREA), exclusionsOf(story("s"), fix = far))
    }

    @Test
    fun listenStateAndViewerRulesExclude() {
        val state = ListenState(
            suggested = mapOf("sug" to 1L),
            played = mapOf("played" to 1L),
            hiddenAuthorIds = setOf("blocked"),
        )
        assertTrue(Exclusion.ALREADY_PLAYED in exclusionsOf(story("played"), state = state))
        assertTrue(Exclusion.ALREADY_SUGGESTED in exclusionsOf(story("sug"), state = state))
        assertTrue(Exclusion.HIDDEN_AUTHOR in exclusionsOf(story("x", author = "blocked"), state = state))
        assertTrue(Exclusion.OWN_STORY in exclusionsOf(story("mine", author = "viewer")))
        assertTrue(Exclusion.IS_REPLY in exclusionsOf(story("reply", parent = "s1")))
        assertTrue(Exclusion.NO_AUDIO in exclusionsOf(story("silent", audio = false)))
        assertTrue(Exclusion.PLACE_UNKNOWN in exclusionsOf(story("lost", placeId = "nowhere")))
    }
}

class LetterPickerTest {
    private fun eval(id: String, eligible: Boolean) = ai.heartz.core.schedule.Evaluation(
        Story(
            id = id, authorId = "a", authorName = "n", placeId = "p", durationMs = 1, mood = Mood.CALM,
            status = StoryStatus.APPROVED, createdAtMillis = 0, clientDraftId = id,
        ),
        if (eligible) emptySet() else setOf(Exclusion.ALREADY_PLAYED),
    )

    @Test
    fun noEligibleCandidateReturnsNull() {
        assertNull(LetterPicker(Random(1)).pick(listOf(eval("x", false))))
        assertNull(LetterPicker(Random(1)).pick(emptyList()))
    }

    @Test
    fun neverPicksIneligibleAndEveryEligibleCanBePicked() {
        val list = listOf(eval("x", false), eval("a", true), eval("y", false), eval("b", true), eval("c", true))
        val picked = (0 until 300).map { LetterPicker(Random(it)).pick(list)!!.id }.toSet()
        assertEquals(setOf("a", "b", "c"), picked)
    }

    @Test
    fun sameSeedReproducesSameChoice() {
        val list = listOf(eval("a", true), eval("b", true), eval("c", true), eval("d", true))
        val first = LetterPicker(Random(42)).pick(list)
        val second = LetterPicker(Random(42)).pick(list)
        assertEquals(first, second)
    }
}
