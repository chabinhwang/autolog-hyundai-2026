package com.autolog.app.purpose

import com.autolog.app.domain.BusinessCategory
import com.autolog.app.domain.Endpoint
import com.autolog.app.domain.EndpointType
import com.autolog.app.domain.Purpose
import com.autolog.app.domain.PurposeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArrivalChoiceTest {
    private val company = Endpoint(EndpointType.COMPANY, 1, "본사")
    private val client = Endpoint(EndpointType.CLIENT, 2, "한빛상사")
    private val commute = PurposeEstimate(Purpose.COMMUTE, null, "출근으로 기록할까요?")
    private val visit = PurposeEstimate(Purpose.BUSINESS, BusinessCategory.VISIT, "업무로 기록할까요?")
    private val unknown = PurposeEstimate(Purpose.UNCONFIRMED, null, "어떤 운행이었나요?")

    @Test
    fun `버튼 선택은 화면 선택(MANUAL)으로 기록한다`() {
        val decision = ArrivalChoice.COMMUTE.decide(commute, company)
        assertEquals(Purpose.COMMUTE, decision.purpose)
        assertEquals(PurposeSource.MANUAL, decision.source)
        assertNull(decision.businessCategory)
        assertEquals(PurposeSource.MANUAL, ArrivalChoice.PERSONAL.decide(commute, company).source)
    }

    @Test
    fun `업무는 추정 세부를 따르고 없으면 거래처 도착일 때 방문으로 둔다`() {
        assertEquals(BusinessCategory.VISIT, ArrivalChoice.BUSINESS.decide(visit, client).businessCategory)
        assertEquals(BusinessCategory.VISIT, ArrivalChoice.BUSINESS.decide(unknown, client).businessCategory)
        assertNull(ArrivalChoice.BUSINESS.decide(commute, company).businessCategory)
    }

    @Test
    fun `나중에는 미확인으로 남긴다`() {
        val decision = ArrivalChoice.LATER.decide(commute, company)
        assertEquals(Purpose.UNCONFIRMED, decision.purpose)
        assertEquals(PurposeSource.AUTO, decision.source)
    }
}
