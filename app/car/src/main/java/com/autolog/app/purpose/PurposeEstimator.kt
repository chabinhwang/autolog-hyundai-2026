package com.autolog.app.purpose

import com.autolog.app.domain.BusinessCategory
import com.autolog.app.domain.Endpoint
import com.autolog.app.domain.EndpointType
import com.autolog.app.domain.Purpose
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/** 용도 추정값과 도착 시 확인 질문. */
data class PurposeEstimate(
    val purpose: Purpose,
    val businessCategory: BusinessCategory?,
    val question: String,
)

/**
 * 규칙 기반 용도 추정 (car-app-rules.md 용도 추정 규칙). 시간대는 기기 설정과 무관하게 Asia/Seoul.
 */
object PurposeEstimator {
    val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

    private val COMMUTE_IN_HOURS = 6 until 11
    private val COMMUTE_OUT_HOURS = 16 until 23

    fun estimate(origin: Endpoint, dest: Endpoint, startedAt: Instant): PurposeEstimate {
        val local = startedAt.atZone(SEOUL)
        val weekday = local.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        val hour = local.hour
        return when {
            weekday && origin.type == EndpointType.HOME && dest.type == EndpointType.COMPANY && hour in COMMUTE_IN_HOURS ->
                PurposeEstimate(Purpose.COMMUTE, null, "출근으로 기록할까요?")
            weekday && origin.type == EndpointType.COMPANY && dest.type == EndpointType.HOME && hour in COMMUTE_OUT_HOURS ->
                PurposeEstimate(Purpose.COMMUTE, null, "퇴근으로 기록할까요?")
            dest.type == EndpointType.CLIENT ->
                PurposeEstimate(Purpose.BUSINESS, BusinessCategory.VISIT, "${dest.displayName} 방문, 업무로 기록할까요?")
            else ->
                PurposeEstimate(Purpose.UNCONFIRMED, null, "업무였나요, 개인 용도였나요?")
        }
    }

    /** 도착 안내 문장. 예: "본사 도착, 12.4킬로미터. 출근으로 기록할까요?" */
    fun arrivalPrompt(dest: Endpoint, distanceKm: Double, estimate: PurposeEstimate): String {
        val km = String.format(java.util.Locale.ROOT, "%.1f", distanceKm).removeSuffix(".0")
        return "${dest.displayName} 도착, ${km}킬로미터. ${estimate.question}"
    }
}
