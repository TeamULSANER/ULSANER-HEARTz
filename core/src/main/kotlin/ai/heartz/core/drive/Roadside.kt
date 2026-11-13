package ai.heartz.core.drive

import ai.heartz.core.geo.Geo
import ai.heartz.core.model.GeoPoint
import ai.heartz.core.model.Place
import ai.heartz.core.schedule.Eligibility
import ai.heartz.core.schedule.LocationFix
import ai.heartz.core.schedule.LocationValidity

/**
 * 지나면서 녹음(기획서 3·6절, M3)의 판단 규칙.
 * - 녹음 시작 시점의 최신 유효 위치만 임시 위치(anchor)로 쓴다. 종료 위치로 바꾸지 않는다.
 * - anchor는 기기 안에서만 쓰고 서버에는 공개 장소 ID만 보낸다.
 * - 녹음이 끝나면 운전자에게 말(또는 큰 버튼)로 묻고, 그 답([StoryChoice])대로만 보낸다.
 *   답을 듣지 못하면 공개하지 않고 기기 초안으로만 남긴다.
 */
enum class StoryChoice { SUBMIT, DISCARD }

data class RoadsideAnchor(val point: GeoPoint, val fixTimeMillis: Long)

data class RoadsideFinish(
    val cancelled: Boolean,
    val recordedOk: Boolean,
    val durationMs: Long,
    /** 통화·음성 비서 등 포커스 상실, 권한 회수 등으로 중단된 사유 */
    val interruption: String?,
    val place: Place?,
    /** 운전자의 답. null이면 답을 듣지 못함 */
    val choice: StoryChoice?,
)

sealed interface RoadsideOutcome {
    /** 아무것도 남기지 않는다 */
    data class Discard(val reason: String) : RoadsideOutcome
    /** 기기에 초안으로만 남기고 주차 후 사용자가 확인한다(서버 전송 없음) */
    data class LocalDraft(val reason: String) : RoadsideOutcome
    /** 검수에 보낸다(나만 보기 범위는 없다). 실패하면 앱이 LocalDraft로 바꾼다 */
    data object Submit : RoadsideOutcome
}

object RoadsideRules {
    const val MAX_MS = 60_000L
    const val MIN_MS = 2_000L

    fun anchorAt(validity: LocationValidity): RoadsideAnchor? = when (validity) {
        is LocationValidity.Valid -> RoadsideAnchor(GeoPoint(validity.fix.lat, validity.fix.lng), validity.fix.timestampMillis)
        else -> null
    }

    /** 녹음을 시작한 곳(anchor)에서 반경 안에 있는 랜드마크 중 가장 가까운 곳. 어느 반경에도 없으면 장소를 정하지 않는다. */
    fun bindPlace(anchor: RoadsideAnchor?, places: List<Place>): Place? {
        anchor ?: return null
        val fix = LocationFix(anchor.point.lat, anchor.point.lng, null, anchor.fixTimeMillis, "roadside-anchor")
        return places.filter { p -> Eligibility.inArea(p, fix) }
            .minByOrNull { Geo.distanceMeters(anchor.point, it.publicLocation) }
    }

    fun decide(f: RoadsideFinish): RoadsideOutcome {
        if (f.cancelled) return RoadsideOutcome.Discard("취소했어요")
        if (!f.recordedOk) return RoadsideOutcome.Discard("녹음 파일을 만들지 못했어요")
        if (f.durationMs < MIN_MS) return RoadsideOutcome.Discard("너무 짧아서 남기지 않았어요")
        if (f.interruption != null) return RoadsideOutcome.LocalDraft(f.interruption)
        if (f.place == null) return RoadsideOutcome.LocalDraft("녹음을 시작한 곳을 공개 장소에 연결하지 못했어요")
        return when (f.choice) {
            StoryChoice.DISCARD -> RoadsideOutcome.Discard("사연을 버렸어요")
            StoryChoice.SUBMIT -> RoadsideOutcome.Submit
            null -> RoadsideOutcome.LocalDraft("답을 듣지 못해 초안으로 보관했어요")
        }
    }
}
