package com.autolog.app.purpose

import com.autolog.app.domain.Endpoint
import com.autolog.app.domain.EndpointType
import com.autolog.app.domain.GeoPoint
import com.autolog.app.domain.Place
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceMatcherTest {
    private val company = Place(1, "본사", EndpointType.COMPANY, GeoPoint(37.497942, 127.027621), 200)
    private val client = Place(4, "한빛상사", EndpointType.CLIENT, GeoPoint(37.566535, 126.977969), 150)
    private val home = Place(null, "집", EndpointType.HOME, GeoPoint(37.400000, 127.100000), 200)
    private val places = listOf(company, client, home)

    @Test
    fun `반경 안이면 등록 장소로 판정한다`() {
        // 본사에서 북쪽 약 100m
        val near = GeoPoint(37.498842, 127.027621)
        assertEquals(Endpoint(EndpointType.COMPANY, 1, "본사"), PlaceMatcher.match(near, places))
    }

    @Test
    fun `반경 밖이면 미등록이고 내비 목적지명을 라벨로 쓴다`() {
        // 본사에서 북쪽 약 300m
        val far = GeoPoint(37.500642, 127.027621)
        assertEquals(
            Endpoint(EndpointType.UNREGISTERED, null, "강남역"),
            PlaceMatcher.match(far, places, "강남역"),
        )
    }

    @Test
    fun `집은 placeId와 라벨 없이 HOME으로 보낸다`() {
        assertEquals(Endpoint(EndpointType.HOME, null, null), PlaceMatcher.match(GeoPoint(37.4, 127.1), places))
    }

    @Test
    fun `반경이 겹치면 가장 가까운 장소를 고른다`() {
        val annex = Place(2, "별관", EndpointType.COMPANY, GeoPoint(37.498942, 127.027621), 300)
        val nearAnnex = GeoPoint(37.498800, 127.027621)
        assertEquals(2L, PlaceMatcher.match(nearAnnex, listOf(company, annex)).placeId)
    }

    @Test
    fun `위치를 모르면 미등록`() {
        assertEquals(EndpointType.UNREGISTERED, PlaceMatcher.match(null, places).type)
    }

    @Test
    fun `미등록 라벨은 100자로 자른다`() {
        val label = "가".repeat(150)
        assertEquals(100, PlaceMatcher.match(null, places, label).label!!.length)
    }

    @Test
    fun `거리 계산은 위도 0_001도에 약 111m`() {
        val d = PlaceMatcher.distanceM(GeoPoint(37.0, 127.0), GeoPoint(37.001, 127.0))
        assertEquals(111.2, d, 0.5)
    }
}
