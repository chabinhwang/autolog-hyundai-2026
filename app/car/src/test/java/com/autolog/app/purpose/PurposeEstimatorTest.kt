package com.autolog.app.purpose

import com.autolog.app.domain.BusinessCategory
import com.autolog.app.domain.Endpoint
import com.autolog.app.domain.EndpointType
import com.autolog.app.domain.Purpose
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class PurposeEstimatorTest {
    private val home = Endpoint(EndpointType.HOME, null, null)
    private val company = Endpoint(EndpointType.COMPANY, 1, "본사")
    private val client = Endpoint(EndpointType.CLIENT, 4, "한빛상사")
    private val unknown = Endpoint(EndpointType.UNREGISTERED, null, "강남역")

    // 2026-11-03은 화요일, 2026-11-07은 토요일 (KST)
    private fun kst(text: String) = Instant.parse(text.replace("+09:00", "") + "Z").minusSeconds(9 * 3600)

    @Test
    fun `평일 아침 집에서 회사면 출근`() {
        val e = PurposeEstimator.estimate(home, company, kst("2026-11-03T08:12:00+09:00"))
        assertEquals(Purpose.COMMUTE, e.purpose)
        assertEquals("출근으로 기록할까요?", e.question)
    }

    @Test
    fun `출근 시간대 경계는 06시 이상 11시 미만`() {
        assertEquals(Purpose.COMMUTE, PurposeEstimator.estimate(home, company, kst("2026-11-03T06:00:00+09:00")).purpose)
        assertEquals(Purpose.UNCONFIRMED, PurposeEstimator.estimate(home, company, kst("2026-11-03T11:00:00+09:00")).purpose)
        assertEquals(Purpose.UNCONFIRMED, PurposeEstimator.estimate(home, company, kst("2026-11-03T05:59:00+09:00")).purpose)
    }

    @Test
    fun `평일 저녁 회사에서 집이면 퇴근`() {
        val e = PurposeEstimator.estimate(company, home, kst("2026-11-03T18:30:00+09:00"))
        assertEquals(Purpose.COMMUTE, e.purpose)
        assertEquals("퇴근으로 기록할까요?", e.question)
    }

    @Test
    fun `주말 출근 경로는 추정하지 않는다`() {
        assertEquals(Purpose.UNCONFIRMED, PurposeEstimator.estimate(home, company, kst("2026-11-07T08:00:00+09:00")).purpose)
    }

    @Test
    fun `기기 시간대와 무관하게 서울 기준으로 판단한다`() {
        // UTC 23:30 = KST 다음날 08:30 (화요일)
        val e = PurposeEstimator.estimate(home, company, Instant.parse("2026-11-02T23:30:00Z"))
        assertEquals(Purpose.COMMUTE, e.purpose)
    }

    @Test
    fun `거래처 도착이면 방문 업무`() {
        val e = PurposeEstimator.estimate(company, client, kst("2026-11-07T14:00:00+09:00"))
        assertEquals(Purpose.BUSINESS, e.purpose)
        assertEquals(BusinessCategory.VISIT, e.businessCategory)
        assertEquals("한빛상사 방문, 업무로 기록할까요?", e.question)
    }

    @Test
    fun `그 외는 미확인으로 묻는다`() {
        val e = PurposeEstimator.estimate(company, unknown, kst("2026-11-03T13:00:00+09:00"))
        assertEquals(Purpose.UNCONFIRMED, e.purpose)
        assertEquals("업무였나요, 개인 용도였나요?", e.question)
    }

    @Test
    fun `도착 안내 문장`() {
        val e = PurposeEstimator.estimate(home, company, kst("2026-11-03T08:12:00+09:00"))
        assertEquals("본사 도착, 12.4킬로미터. 출근으로 기록할까요?", PurposeEstimator.arrivalPrompt(company, 12.4, e))
        assertEquals("집 도착, 12킬로미터. 출근으로 기록할까요?", PurposeEstimator.arrivalPrompt(home, 12.0, e))
    }
}
