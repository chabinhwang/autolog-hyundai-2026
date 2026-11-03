package com.autolog.app.ui

import com.autolog.app.trip.TripRecorder
import com.autolog.app.trip.TripRecorder.Phase
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusHintTest {
    private val default = "기어를 D로 바꾸면 자동으로 기록해요."
    private val unconfirmed = TripRecorder.Status(Phase.IDLE, "미확인으로 남겨 둘게요. 정차 중에 화면에서 골라 주세요.", asksChoice = true)

    @Test
    fun `미확인 운행이 남아 있으면 골라 달라는 안내를 보여준다`() {
        assertEquals(unconfirmed.lastMessage, statusHint(unconfirmed, unconfirmedCount = 1, defaultHint = default))
    }

    @Test
    fun `미확인 운행을 다 고르면 골라 달라는 안내를 내린다`() {
        assertEquals(default, statusHint(unconfirmed, unconfirmedCount = 0, defaultHint = default))
    }

    @Test
    fun `확정 안내는 미확인 운행 수와 상관없이 남긴다`() {
        val confirmed = TripRecorder.Status(Phase.IDLE, "거래처 방문 업무로 기록했어요.")
        assertEquals(confirmed.lastMessage, statusHint(confirmed, unconfirmedCount = 0, defaultHint = default))
    }

    @Test
    fun `진행 중이면 지금 할 일을 보여준다`() {
        val driving = TripRecorder.Status(Phase.DRIVING, lastMessage = null)
        assertEquals("주행 중", statusHint(driving, unconfirmedCount = 1, defaultHint = "주행 중"))
    }
}
