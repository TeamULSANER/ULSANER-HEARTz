package ai.heartz.core

import ai.heartz.core.geo.Geo
import ai.heartz.core.model.GeoPoint
import ai.heartz.core.model.StoryStatus
import ai.heartz.core.model.UserRole
import ai.heartz.core.model.VoiceMode
import ai.heartz.core.schedule.Eligibility
import ai.heartz.core.schedule.LocationFix
import ai.heartz.core.seed.SeedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedDataTest {
    private val db = SeedData.db(nowMillis = 1_000)

    /** 경로를 약 10 m 간격으로 보간한 위치 (heading = 진행 방향) */
    private fun densify(route: List<GeoPoint>): List<LocationFix> = route.zipWithNext().flatMap { (a, b) ->
        val n = (Geo.distanceMeters(a, b) / 10).toInt().coerceAtLeast(1)
        val heading = Geo.bearingDeg(a, b)
        (0 until n).map { i ->
            val t = i.toDouble() / n
            LocationFix(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t, heading, 0, "test")
        }
    }

    @Test
    fun demoRouteReachesEveryLandmarkThatHasSeedStories() {
        val fixes = densify(SeedData.demoRoute)
        val storyPlaces = db.stories.map { it.placeId }.toSet()
        val visited = db.places.filter { place -> fixes.any { Eligibility.inArea(place, it) } }.map { it.id }.toSet()
        assertTrue("visited=$visited", visited.containsAll(storyPlaces))
    }

    @Test
    fun everyStoryHasKnownPlaceAuthorAndAudioFile() {
        val placeIds = db.places.map { it.id }.toSet()
        val userIds = db.users.map { it.id }.toSet()
        for (s in db.stories) {
            assertTrue(s.id, s.placeId in placeIds)
            assertTrue(s.id, s.authorId in userIds)
            assertTrue(s.id, s.audio!!.blobId in SeedData.audioFiles)
            assertTrue(s.id, s.sampleLabel in setOf(SeedData.SAMPLE_LABEL, SeedData.TEAM_LABEL))
        }
    }

    @Test
    fun demoAccountsAreThreeDriversAndOneModerator() {
        val signIn = db.users.filter { it.canSignIn }
        assertEquals(listOf("장클로드", "김피티", "상도동메시", "운영자 (테스트)"), signIn.map { it.displayName })
        assertEquals(listOf(SeedData.DRIVER_JANG, SeedData.DRIVER_KIM, SeedData.DRIVER_MESSI), signIn.filter { it.role == UserRole.LISTENER }.map { it.id })
        assertEquals(1, signIn.count { it.role == UserRole.MODERATOR })
        assertTrue(signIn.all { it.isTestAccount })
        // 예전 계정 id를 그대로 쓴다(바다→장클로드, 봄날→김피티, 은행나무→상도동메시), 하늘은 없앴다
        assertEquals(listOf("u-listener-b", "u-seed-spring", "u-seed-ginkgo"), listOf(SeedData.DRIVER_JANG, SeedData.DRIVER_KIM, SeedData.DRIVER_MESSI))
        assertEquals(signIn, db.users)
    }

    @Test
    fun onlyAFewDemoStoriesRemainAndKimsRiversideStoryIsTheTeamRecording() {
        assertTrue(db.stories.size <= 4)
        assertEquals(1, db.stories.count { it.status == StoryStatus.PENDING_REVIEW })
        assertTrue(db.stories.all { it.authorId in setOf(SeedData.DRIVER_KIM, SeedData.DRIVER_MESSI) })
        val riverside = db.stories.single { it.placeId == "p-riverside" }
        assertEquals(SeedData.DRIVER_KIM, riverside.authorId)
        assertEquals("김피티", riverside.authorName)
        assertEquals("seed-kim", riverside.audio!!.blobId)
        assertEquals("seed-kim.m4a", SeedData.audioFiles["seed-kim"])
        assertEquals(SeedData.TEAM_LABEL, riverside.sampleLabel)
        assertEquals(13_000, riverside.durationMs)
        assertEquals(StoryStatus.APPROVED, riverside.status)
    }

    @Test
    fun messisHangulMuseumStoryIsAModulatedVoiceStoryFromTheTeamRecording() {
        val dongbu = db.stories.single { it.id == "s-seed-dongbu" }
        assertEquals("p-dongbu", dongbu.placeId)
        assertEquals(SeedData.DRIVER_MESSI, dongbu.authorId)
        assertEquals("상도동메시", dongbu.authorName)
        assertEquals("seed-jeon", dongbu.audio!!.blobId)
        assertEquals("seed-jeon.m4a", SeedData.audioFiles["seed-jeon"])
        assertEquals(SeedData.TEAM_LABEL, dongbu.sampleLabel)
        assertEquals(13_000, dongbu.durationMs)
        assertEquals(StoryStatus.APPROVED, dongbu.status)
        // 듣는 사람에게는 변조 목소리(TTS)로 읽어 준다. 원본 녹음은 운영자만 듣는다
        assertEquals(VoiceMode.MODULATED, dongbu.voiceMode)
        assertEquals("한글박물관에서 아이가 처음으로 자기 이름을 써 봤어요. 삐뚤빼뚤한 글씨를 보고 둘이 한참 웃었는데, 그 종이가 아직도 냉장고에 붙어 있어요.", dongbu.spokenText)
        // 나머지 시드 사연은 원래 목소리
        assertTrue(db.stories.filter { it.id != dongbu.id }.all { it.voiceMode == VoiceMode.ORIGINAL && it.spokenText == null })
        // 쓰지 않는 합성 음원은 목록에 남기지 않는다
        assertEquals(db.stories.map { it.audio!!.blobId }.toSet(), SeedData.audioFiles.keys)
    }
}
