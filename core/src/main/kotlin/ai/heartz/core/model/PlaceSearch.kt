package ai.heartz.core.model

/** 남기기 장소 검색. 띄어 쓴 낱말이 모두 "구 동 랜드마크" 어딘가에 있으면 찾는다. 랜드마크 이름에서 다 찾은 곳을 먼저 둔다. */
object PlaceSearch {
    private fun norm(s: String) = s.filterNot { it.isWhitespace() }.lowercase()

    fun filter(places: List<Place>, query: String): List<Place> {
        val tokens = query.split(Regex("""\s+""")).map(::norm).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return places
        val hits = places.filter { p ->
            val all = norm(p.district + p.dong + p.shortName + p.displayName)
            tokens.all { it in all }
        }
        val (byName, others) = hits.partition { p -> tokens.all { it in norm(p.shortName) } }
        return byName + others
    }
}
