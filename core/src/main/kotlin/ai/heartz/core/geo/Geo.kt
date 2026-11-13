package ai.heartz.core.geo

import ai.heartz.core.model.GeoPoint
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    const val EARTH_RADIUS_M = 6_371_008.8

    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(h)))
    }

    /** 진북 기준 0..360 방위각 */
    fun bearingDeg(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = Math.toRadians(from.lat)
        val lat2 = Math.toRadians(to.lat)
        val dLng = Math.toRadians(to.lng - from.lng)
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** 두 방위각의 차이 0..180 */
    fun headingDiffDeg(a: Double, b: Double): Double {
        val d = abs(((a - b) % 360.0 + 360.0) % 360.0)
        return if (d > 180.0) 360.0 - d else d
    }

    /**
     * 점과 폴리라인 사이 최소 거리와 가장 가까운 선분의 방위각.
     * 구간 길이(수 km)에서는 점 주변의 등거리 평면 근사로 충분하다.
     */
    fun nearestOnPolyline(p: GeoPoint, line: List<GeoPoint>): PolylineHit {
        require(line.size >= 2) { "polyline needs at least 2 points" }
        val cosLat = cos(Math.toRadians(p.lat))
        val mPerDegLat = Math.toRadians(1.0) * EARTH_RADIUS_M
        fun x(g: GeoPoint) = (g.lng - p.lng) * mPerDegLat * cosLat
        fun y(g: GeoPoint) = (g.lat - p.lat) * mPerDegLat

        var best = PolylineHit(Double.MAX_VALUE, 0.0)
        for (i in 0 until line.size - 1) {
            val a = line[i]
            val b = line[i + 1]
            val ax = x(a); val ay = y(a); val bx = x(b); val by = y(b)
            val dx = bx - ax; val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
            val cx = ax + t * dx; val cy = ay + t * dy
            val dist = sqrt(cx * cx + cy * cy)
            if (dist < best.distanceMeters) best = PolylineHit(dist, bearingDeg(a, b))
        }
        return best
    }
}

data class PolylineHit(val distanceMeters: Double, val segmentBearingDeg: Double)
