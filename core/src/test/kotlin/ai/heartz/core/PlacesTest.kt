package ai.heartz.core

import ai.heartz.core.drive.RoadsideAnchor
import ai.heartz.core.drive.RoadsideRules
import ai.heartz.core.geo.Geo
import ai.heartz.core.model.GeoPoint
import ai.heartz.core.model.Place
import ai.heartz.core.schedule.Eligibility
import ai.heartz.core.schedule.LocationFix
import ai.heartz.core.seed.SeedData
import ai.heartz.core.seed.SeoulLandmarks
import ai.heartz.core.service.HeartzService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 장소 = 서울 각 구의 랜드마크, 사연 도착 = 랜드마크 반경 1 km 안 */
class PlacesTest {
    private val museum = Place(
        id = "p-test", shortName = "국립중앙박물관", displayName = "용산구 용산동6가 국립중앙박물관",
        publicLocation = GeoPoint(37.5239, 126.9803), district = "용산구", dong = "용산동6가",
    )

    /** 기준점에서 북쪽으로 [meters] 떨어진 위치 */
    private fun north(p: GeoPoint, meters: Double) = LocationFix(p.lat + meters / 111_320.0, p.lng, null, 0, "test")

    @Test
    fun storyArrivesWithinOneKilometerOfTheLandmark() {
        assertEquals(1_000.0, museum.radiusMeters, 0.0)
        assertTrue(Eligibility.inArea(museum, north(museum.publicLocation, 950.0)))
        assertFalse(Eligibility.inArea(museum, north(museum.publicLocation, 1_050.0)))
        // 방향(heading)과 상관없이 거리만 본다
        assertTrue(Eligibility.inArea(museum, north(museum.publicLocation, 10.0).copy(headingDeg = 180.0)))
    }

    @Test
    fun listShowsDistrictAndLandmarkWhileCardsShowOnlyTheLandmark() {
        assertEquals("용산구 국립중앙박물관", museum.listLabel)
        assertEquals("국립중앙박물관", museum.shortName)
    }

    @Test
    fun everySeoulDistrictHasLandmarksWithDistrictDongAndLandmark() {
        val districts = listOf(
            "종로구", "중구", "용산구", "성동구", "광진구", "동대문구", "중랑구", "성북구", "강북구", "도봉구", "노원구", "은평구", "서대문구",
            "마포구", "양천구", "강서구", "구로구", "금천구", "영등포구", "동작구", "관악구", "서초구", "강남구", "송파구", "강동구",
        )
        val all = SeoulLandmarks.places
        assertEquals(districts.toSet(), all.map { it.district }.toSet())
        for (d in districts) assertTrue(d, all.count { it.district == d } >= 3)
        assertEquals(all.size, all.map { it.id }.toSet().size)
        for (p in all) {
            assertTrue(p.id, p.dong.isNotBlank() && p.shortName.isNotBlank())
            assertEquals(p.id, "${p.district} ${p.dong} ${p.shortName}", p.displayName)
            assertTrue(p.id, p.publicLocation.lat in 37.42..37.71 && p.publicLocation.lng in 126.76..127.19)
            assertEquals(1_000.0, p.radiusMeters, 0.0)
        }
        // 같은 랜드마크가 두 번 들어가지 않는다
        assertEquals(all.size, all.map { it.district + it.shortName }.toSet().size)
    }

    @Test
    fun seedStoriesStayOnYongsanLandmarksAlongTheDemoRoute() {
        val db = SeedData.db(0)
        assertEquals(SeoulLandmarks.places.map { it.id }.toSet(), db.places.map { it.id }.toSet())
        val storyPlaces = db.stories.map { s -> db.places.single { it.id == s.placeId } }
        assertTrue(storyPlaces.all { it.district == "용산구" })
        // 시연 경로를 따라가면 사연이 있는 랜드마크마다 반경 1 km 안에 한 번은 들어간다
        for (p in storyPlaces.distinct()) {
            assertTrue(p.id, SeedData.demoRoute.any { Geo.distanceMeters(it, p.publicLocation) <= p.radiusMeters })
        }
    }

    @Test
    fun roadsideStoryIsBoundToTheNearestLandmarkWithinOneKilometer() {
        val far = Place("p-far", "먼 곳", "용산구 한남동 먼 곳", GeoPoint(37.5390, 127.0020), district = "용산구", dong = "한남동")
        val start = north(museum.publicLocation, 300.0)
        val anchor = RoadsideAnchor(GeoPoint(start.lat, start.lng), 0)
        assertEquals("p-test", RoadsideRules.bindPlace(anchor, listOf(far, museum))?.id)
        // 반경이 겹치면 녹음을 시작한 곳에서 더 가까운 랜드마크(먼저 나오지 않아도)
        val alsoNear = Place("p-near", "가까운 곳", "용산구 이촌동 가까운 곳", GeoPoint(museum.publicLocation.lat - 0.006, museum.publicLocation.lng), district = "용산구", dong = "이촌동")
        assertEquals("p-test", RoadsideRules.bindPlace(anchor, listOf(alsoNear, museum))?.id)
        val nowhere = RoadsideAnchor(GeoPoint(37.70, 127.18), 0)
        assertNull(RoadsideRules.bindPlace(nowhere, listOf(far, museum)))
    }

    @Test
    fun existingDataGetsTheNewLandmarkListWithoutLosingStories() {
        val store = MemoryStore()
        val blobs = MemoryBlobs()
        val old = SeedData.db(0)
        val oldIchon = old.places.single { it.id == "p-ichon" }.copy(shortName = "이촌", displayName = "이촌역 앞 이촌로", district = "", dong = "")
        store.db = old.copy(places = listOf(oldIchon))
        val service = HeartzService(store, blobs)
        service.refreshSeedPlaces(SeoulLandmarks.places)
        assertEquals(SeoulLandmarks.places.size, service.places().size)
        assertEquals("국립중앙박물관", service.places().single { it.id == "p-ichon" }.shortName)
        assertEquals(old.stories.size, store.db!!.stories.size)
        // 이미 같은 목록이면 다시 저장하지 않는다
        val saves = store.saves
        service.refreshSeedPlaces(SeoulLandmarks.places)
        assertEquals(saves, store.saves)
    }
}
