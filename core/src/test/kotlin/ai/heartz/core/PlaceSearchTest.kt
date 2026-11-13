package ai.heartz.core

import ai.heartz.core.model.GeoPoint
import ai.heartz.core.model.Place
import ai.heartz.core.model.PlaceSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceSearchTest {
    private fun p(id: String, district: String, dong: String, name: String) =
        Place(id, name, "$district $dong $name", GeoPoint(37.5, 127.0), district = district, dong = dong)

    private val all = listOf(
        p("a", "용산구", "서빙고동", "잠수교"),
        p("b", "용산구", "용산동6가", "국립중앙박물관"),
        p("c", "종로구", "세종로", "경복궁"),
        p("d", "송파구", "신천동", "롯데월드타워"),
    )

    private fun ids(q: String) = PlaceSearch.filter(all, q).map { it.id }

    @Test fun emptyQueryKeepsEverythingInOrder() = assertEquals(listOf("a", "b", "c", "d"), ids("  "))

    @Test fun matchesLandmarkDistrictOrDong() {
        assertEquals(listOf("c"), ids("경복"))
        assertEquals(listOf("a", "b"), ids("용산구"))
        assertEquals(listOf("a"), ids("서빙고"))
        assertEquals(listOf("d"), ids("신천"))
    }

    @Test fun spacesAndCaseDoNotMatter() {
        assertEquals(listOf("b"), ids("국립 중앙"))
        assertEquals(listOf("d"), ids("송파구 롯데"))
        // 낱말이 모두 맞아야 한다(용산구의 다른 곳은 빠진다)
        assertEquals(listOf("a"), ids("용산구 잠수"))
    }

    @Test fun landmarkNameMatchesComeFirst() {
        val more = all + p("e", "서초구", "반포동", "용산 쪽 한강뷰")
        val r = PlaceSearch.filter(more, "용산").map { it.id }
        assertEquals("e", r.first())
        assertTrue(r.containsAll(listOf("a", "b")))
    }

    @Test fun noMatchGivesEmptyList() = assertTrue(ids("없는장소").isEmpty())
}
