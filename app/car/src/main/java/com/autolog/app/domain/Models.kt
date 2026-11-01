package com.autolog.app.domain

import java.time.Instant

data class GeoPoint(val lat: Double, val lng: Double)

/** 장소. 회사·거래처는 서버에서 내려받고(serverId 있음), 집은 차량에만 저장한다(D2). */
data class Place(
    val serverId: Long?,
    val name: String,
    val type: EndpointType,
    val point: GeoPoint,
    val radiusM: Int,
)

/** 출발·도착 판정 결과. 서버로는 type·placeId·label만 보낸다. */
data class Endpoint(
    val type: EndpointType,
    val placeId: Long?,
    val label: String?,
) {
    /** 화면·음성에 쓰는 이름. */
    val displayName: String
        get() = when (type) {
            EndpointType.HOME -> "집"
            EndpointType.UNREGISTERED -> label ?: "미등록 장소"
            else -> label ?: "등록 장소"
        }

    companion object {
        val UNKNOWN = Endpoint(EndpointType.UNREGISTERED, null, null)
    }
}

/** 트립 감지 시점의 차량 상태. */
data class VehicleSnapshot(
    val at: Instant,
    val odometerKm: Double,
    val location: GeoPoint?,
)

/** 상태 머신이 확정한 트립 원본(용도 판정 전). */
data class DetectedTrip(
    val start: VehicleSnapshot,
    val end: VehicleSnapshot,
) {
    val distanceKm: Double get() = end.odometerKm - start.odometerKm
}
