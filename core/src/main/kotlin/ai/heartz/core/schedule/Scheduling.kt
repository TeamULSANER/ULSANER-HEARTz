package ai.heartz.core.schedule

import ai.heartz.core.geo.Geo
import ai.heartz.core.model.ListenState
import ai.heartz.core.model.Place
import ai.heartz.core.model.Story
import ai.heartz.core.model.StoryStatus
import kotlin.random.Random

/**
 * 앱이 받은 위치 한 건. 출처(실제 SDK 콜백인지 테스트 입력인지)는 [source]로 남긴다.
 * headingDeg가 없으면 방향 조건이 있는 구간은 판정하지 않는다(성공으로 바꾸지 않음).
 */
data class LocationFix(
    val lat: Double,
    val lng: Double,
    val headingDeg: Double?,
    val timestampMillis: Long,
    val source: String,
)

sealed interface LocationValidity {
    data class Valid(val fix: LocationFix) : LocationValidity
    data object Missing : LocationValidity
    data class Stale(val ageMillis: Long) : LocationValidity
    data class Invalid(val reason: String) : LocationValidity
}

object LocationRules {
    const val MAX_AGE_MILLIS = 15_000L

    /** 미래 시각 허용 오차(기기·서비스 시계 차이) */
    const val MAX_FUTURE_SKEW_MILLIS = 60_000L

    fun validate(fix: LocationFix?, nowMillis: Long, maxAgeMillis: Long = MAX_AGE_MILLIS): LocationValidity {
        if (fix == null) return LocationValidity.Missing
        if (!fix.lat.isFinite() || !fix.lng.isFinite()) return LocationValidity.Invalid("non-finite coordinate")
        if (fix.lat !in -90.0..90.0 || fix.lng !in -180.0..180.0) return LocationValidity.Invalid("coordinate out of range")
        if (fix.lat == 0.0 && fix.lng == 0.0) return LocationValidity.Invalid("0,0 is not treated as a real fix")
        val age = nowMillis - fix.timestampMillis
        if (age < -MAX_FUTURE_SKEW_MILLIS) return LocationValidity.Invalid("timestamp is ${-age} ms in the future")
        if (age > maxAgeMillis) return LocationValidity.Stale(age)
        return LocationValidity.Valid(fix)
    }
}

enum class Exclusion {
    NOT_APPROVED,
    NO_AUDIO,
    IS_REPLY,
    OWN_STORY,
    ALREADY_PLAYED,
    ALREADY_SUGGESTED,
    HIDDEN_AUTHOR,
    PLACE_UNKNOWN,
    OUTSIDE_AREA,
}

data class Evaluation(val story: Story, val exclusions: Set<Exclusion>) {
    val eligible: Boolean get() = exclusions.isEmpty()
}

/** 후보 적격성 판정. 무작위 선택([LetterPicker])과 분리해 테스트에서 재현할 수 있게 한다. */
object Eligibility {
    fun evaluate(
        stories: List<Story>,
        places: List<Place>,
        fix: LocationFix,
        listenState: ListenState,
        viewerId: String,
    ): List<Evaluation> {
        val placeById = places.associateBy { it.id }
        val areaCache = HashMap<String, Boolean>()
        return stories.map { story ->
            val ex = mutableSetOf<Exclusion>()
            if (story.status != StoryStatus.APPROVED) ex += Exclusion.NOT_APPROVED
            if (story.audio == null) ex += Exclusion.NO_AUDIO
            if (story.parentStoryId != null) ex += Exclusion.IS_REPLY
            if (story.authorId == viewerId) ex += Exclusion.OWN_STORY
            if (story.id in listenState.played) ex += Exclusion.ALREADY_PLAYED
            if (story.id in listenState.suggested) ex += Exclusion.ALREADY_SUGGESTED
            if (story.authorId in listenState.hiddenAuthorIds) ex += Exclusion.HIDDEN_AUTHOR
            val place = placeById[story.placeId]
            if (place == null) {
                ex += Exclusion.PLACE_UNKNOWN
            } else if (!areaCache.getOrPut(place.id) { inArea(place, fix) }) {
                ex += Exclusion.OUTSIDE_AREA
            }
            Evaluation(story, ex)
        }
    }

    /** 위치가 랜드마크 반경([Place.radiusMeters], 기본 1 km) 안인지. 진행 방향은 보지 않는다. */
    fun inArea(place: Place, fix: LocationFix): Boolean =
        Geo.distanceMeters(ai.heartz.core.model.GeoPoint(fix.lat, fix.lng), place.publicLocation) <= place.radiusMeters
}

/** 초기 편성: 조건을 통과한 후보 사이에서 균등 무작위. 품질·인기 점수는 쓰지 않는다. */
class LetterPicker(private val random: Random) {
    fun pick(evaluations: List<Evaluation>): Story? {
        val eligible = evaluations.filter { it.eligible }
        if (eligible.isEmpty()) return null
        return eligible[random.nextInt(eligible.size)].story
    }
}
