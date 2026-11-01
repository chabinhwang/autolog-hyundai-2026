package com.autolog.app.purpose

import com.autolog.app.domain.Endpoint
import com.autolog.app.domain.EndpointType
import com.autolog.app.domain.GeoPoint
import com.autolog.app.domain.Place
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 좌표 → 출발·도착 장소 판정. 등록 장소 반경 안이면 가장 가까운 장소, 아니면 미등록.
 * 좌표는 차 안에서만 쓰고 서버에는 유형·placeId·라벨만 보낸다.
 */
object PlaceMatcher {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun match(point: GeoPoint?, places: List<Place>, unregisteredLabel: String? = null): Endpoint {
        val unregistered = Endpoint(EndpointType.UNREGISTERED, null, unregisteredLabel?.take(MAX_LABEL))
        if (point == null) return unregistered
        val nearest = places
            .map { it to distanceM(point, it.point) }
            .filter { (place, distance) -> distance <= place.radiusM }
            .minByOrNull { it.second }
            ?.first
            ?: return unregistered
        return when (nearest.type) {
            EndpointType.HOME -> Endpoint(EndpointType.HOME, null, null)
            else -> Endpoint(nearest.type, nearest.serverId, nearest.name)
        }
    }

    fun distanceM(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }

    /** 서버 계약: UNREGISTERED 라벨 최대 100자. */
    const val MAX_LABEL = 100
}
