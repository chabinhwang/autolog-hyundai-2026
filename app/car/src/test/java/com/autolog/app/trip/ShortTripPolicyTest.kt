package com.autolog.app.trip

import com.autolog.app.domain.DetectedTrip
import com.autolog.app.domain.VehicleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ShortTripPolicyTest {
    private fun trip(km: Double) = DetectedTrip(
        VehicleSnapshot(Instant.EPOCH, 100.0, null),
        VehicleSnapshot(Instant.EPOCH.plusSeconds(60), 100.0 + km, null),
    )

    @Test
    fun `0_5km 이상이면 새 트립으로 저장한다`() {
        assertEquals(ShortTripPolicy.Decision.Keep, ShortTripPolicy.decide(trip(0.5), "prev", true))
    }

    @Test
    fun `짧은 트립은 업로드 전 직전 트립에 병합한다`() {
        assertEquals(
            ShortTripPolicy.Decision.MergeIntoPrevious("prev"),
            ShortTripPolicy.decide(trip(0.4), "prev", true),
        )
    }

    @Test
    fun `직전 트립이 이미 올라갔거나 없으면 버린다`() {
        assertEquals(ShortTripPolicy.Decision.Drop, ShortTripPolicy.decide(trip(0.0), "prev", false))
        assertEquals(ShortTripPolicy.Decision.Drop, ShortTripPolicy.decide(trip(0.2), null, true))
    }
}
