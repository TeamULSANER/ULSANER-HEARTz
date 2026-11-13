package ai.heartz.core

import ai.heartz.core.drive.RoadsideFinish
import ai.heartz.core.drive.RoadsideOutcome
import ai.heartz.core.drive.RoadsideRules
import ai.heartz.core.drive.StoryChoice
import ai.heartz.core.model.GeoPoint
import ai.heartz.core.schedule.LocationFix
import ai.heartz.core.schedule.LocationValidity
import ai.heartz.core.seed.SeedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadsideRulesTest {
    private val places = SeedData.db(0).places
    // 국립중앙박물관(p-ichon) 북쪽 바로 옆, 잠수교(p-seobinggo) 북단 바로 옆
    private val ichonOnRoad = GeoPoint(37.52450, 126.98030)
    private val seobinggo = GeoPoint(37.51700, 126.99550)

    private fun valid(p: GeoPoint, t: Long = 1_000) = LocationValidity.Valid(LocationFix(p.lat, p.lng, null, t, "test"))

    @Test
    fun anchorOnlyFromValidLocation() {
        assertEquals(ichonOnRoad, RoadsideRules.anchorAt(valid(ichonOnRoad))?.point)
        assertNull(RoadsideRules.anchorAt(LocationValidity.Missing))
        assertNull(RoadsideRules.anchorAt(LocationValidity.Stale(60_000)))
    }

    @Test
    fun placeIsBoundFromStartAnchorNotFromWhereRecordingEnds() {
        val anchor = RoadsideRules.anchorAt(valid(ichonOnRoad))
        // 녹음이 끝날 때 차는 서빙고에 있어도, 장소는 시작 시점 기준이다
        val bound = RoadsideRules.bindPlace(anchor, places)
        assertEquals("p-ichon", bound?.id)
        assertEquals("p-seobinggo", RoadsideRules.bindPlace(RoadsideRules.anchorAt(valid(seobinggo)), places)?.id)
    }

    @Test
    fun noPlaceWhenAnchorOutsideEveryLandmarkOrMissing() {
        assertNull(RoadsideRules.bindPlace(RoadsideRules.anchorAt(valid(GeoPoint(37.30, 127.30))), places))
        assertNull(RoadsideRules.bindPlace(null, places))
    }

    private fun finish(
        cancelled: Boolean = false,
        recordedOk: Boolean = true,
        durationMs: Long = 10_000,
        interruption: String? = null,
        placeFound: Boolean = true,
        choice: StoryChoice? = StoryChoice.SUBMIT,
    ) = RoadsideFinish(cancelled, recordedOk, durationMs, interruption, if (placeFound) places.first() else null, choice)

    @Test
    fun cancelDiscardsAndNeverSaves() {
        val o = RoadsideRules.decide(finish(cancelled = true, choice = StoryChoice.SUBMIT))
        assertTrue(o is RoadsideOutcome.Discard)
    }

    @Test
    fun failedOrTooShortRecordingIsDiscarded() {
        assertTrue(RoadsideRules.decide(finish(recordedOk = false)) is RoadsideOutcome.Discard)
        assertTrue(RoadsideRules.decide(finish(durationMs = 1_000)) is RoadsideOutcome.Discard)
    }

    @Test
    fun interruptionKeepsLocalDraftEvenWhenUserSaidPublic() {
        val o = RoadsideRules.decide(finish(interruption = "통화", choice = StoryChoice.SUBMIT))
        assertTrue(o is RoadsideOutcome.LocalDraft)
    }

    @Test
    fun missingPlaceKeepsLocalDraft() {
        val o = RoadsideRules.decide(finish(placeFound = false, choice = StoryChoice.SUBMIT))
        assertTrue(o is RoadsideOutcome.LocalDraft)
    }

    @Test
    fun answerDecidesWhatHappens() {
        // 나만 보기는 없다: 보내면 항상 검수 대기
        assertEquals(RoadsideOutcome.Submit, RoadsideRules.decide(finish(choice = StoryChoice.SUBMIT)))
        assertTrue(RoadsideRules.decide(finish(choice = StoryChoice.DISCARD)) is RoadsideOutcome.Discard)
    }

    @Test
    fun noClearAnswerNeverPublishesAndKeepsDraftOnDevice() {
        // 답을 못 들으면 공개하지 않고 기기 초안으로만 남긴다(주차 후 확인)
        assertTrue(RoadsideRules.decide(finish(choice = null)) is RoadsideOutcome.LocalDraft)
    }
}
