package com.autolog.app.sync

import com.autolog.app.data.local.TripEditEntity
import com.autolog.app.domain.BusinessCategory
import com.autolog.app.domain.EndpointType
import com.autolog.app.domain.Purpose
import com.autolog.app.domain.PurposeSource
import com.autolog.app.domain.SyncState
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class TripMapperTest {
    @Test
    fun `시각은 서울 오프셋 ISO-8601 초 단위`() {
        assertEquals("2026-11-03T08:12:00+09:00", TripMapper.formatTime(Instant.parse("2026-11-02T23:12:00.789Z")))
    }

    @Test
    fun `계기판 거리는 소수 첫째 자리로 반올림`() {
        assertEquals(12.4, TripMapper.roundKm(12.399999618530273), 0.0)
        assertEquals(100.0, TripMapper.roundKm(99.96), 0.0)
    }

    @Test
    fun `출발·도착은 유형별 계약 규칙대로 보낸다`() {
        val trip = Fixtures.trip("a").copy(
            originType = EndpointType.HOME, originPlaceId = null, originLabel = "집",
            destType = EndpointType.CLIENT, destPlaceId = 4, destLabel = "한빛상사",
        )
        val item = TripMapper.toUploadItem(trip)
        assertEquals("HOME", item.origin.type)
        assertNull(item.origin.label)
        assertEquals(4L, item.dest.placeId)
        assertNull(item.dest.label)

        val unregistered = TripMapper.toUploadItem(trip.copy(destType = EndpointType.UNREGISTERED, destPlaceId = 9, destLabel = "강남역"))
        assertNull(unregistered.dest.placeId)
        assertEquals("강남역", unregistered.dest.label)
    }

    @Test
    fun `업로드 항목에 용도·출처·차량을 그대로 담는다`() {
        val item = TripMapper.toUploadItem(
            Fixtures.trip("a").copy(
                autoPurpose = Purpose.BUSINESS, purpose = Purpose.BUSINESS,
                purposeSource = PurposeSource.VOICE, businessCategory = BusinessCategory.VISIT,
            ),
        )
        assertEquals("a", item.clientUuid)
        assertEquals(3L, item.vehicleId)
        assertEquals("BUSINESS", item.autoPurpose)
        assertEquals("VOICE", item.purposeSource)
        assertEquals("VISIT", item.businessCategory)
    }

    @Test
    fun `수정 요청은 용도와 업무 세부를 함께 보낸다`() {
        val edit = TripEditEntity(
            "e1", "a", 3, Instant.parse("2026-11-03T09:05:00Z"), PurposeSource.MANUAL,
            Purpose.PERSONAL, null, SyncState.PENDING, null,
        )
        val request = TripMapper.toEditRequest(edit)
        assertEquals("MANUAL", request.purposeSource)
        assertEquals(JsonPrimitive("PERSONAL"), request.changes["purpose"])
        assertEquals(JsonNull, request.changes["businessCategory"])
        assertEquals("2026-11-03T18:05:00+09:00", request.editedAt)
    }
}
