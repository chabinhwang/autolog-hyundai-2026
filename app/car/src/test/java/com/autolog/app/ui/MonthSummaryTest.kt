package com.autolog.app.ui

import com.autolog.app.domain.Purpose
import com.autolog.app.sync.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonthSummaryTest {
    private fun trip(id: String, purpose: Purpose, km: Double, answerPending: Boolean = false) =
        Fixtures.trip(id).copy(purpose = purpose, odoStartKm = 1000.0, odoEndKm = 1000.0 + km, answerPending = answerPending)

    @Test
    fun `업무사용비율은 출퇴근과 업무를 합쳐 총거리로 나누고 미확인은 분모에만 넣는다`() {
        val summary = MonthSummary.of(
            listOf(
                trip("a", Purpose.COMMUTE, 30.0),
                trip("b", Purpose.BUSINESS, 50.0),
                trip("c", Purpose.PERSONAL, 10.0),
                trip("d", Purpose.UNCONFIRMED, 10.0),
            ),
        )
        assertEquals(4, summary.tripCount)
        assertEquals(100.0, summary.totalKm, 0.001)
        assertEquals(80, summary.businessRatio)
        assertEquals(1, summary.unconfirmedCount)
    }

    @Test
    fun `음성 확인을 기다리는 트립은 아직 세지 않는다`() {
        val summary = MonthSummary.of(listOf(trip("a", Purpose.UNCONFIRMED, 12.0, answerPending = true)))
        assertEquals(0, summary.tripCount)
        assertNull(summary.businessRatio)
    }
}
