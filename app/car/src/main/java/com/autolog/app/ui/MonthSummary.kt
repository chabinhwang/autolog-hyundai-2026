package com.autolog.app.ui

import com.autolog.app.data.local.TripEntity
import com.autolog.app.domain.Purpose
import kotlin.math.roundToInt

/**
 * 홈 화면의 이번 달 요약. 업무사용비율은 서버 집계와 같이 (출퇴근 + 업무) ÷ 총 주행거리이고,
 * 미확인은 분모에만 들어간다(rules: 업무사용비율). 거리 0이면 비율 없음.
 */
data class MonthSummary(
    val tripCount: Int,
    val totalKm: Double,
    val businessKm: Double,
    val unconfirmedCount: Int,
) {
    val businessRatio: Int? get() = if (totalKm <= 0.0) null else (businessKm / totalKm * 100).roundToInt()

    companion object {
        val Empty = MonthSummary(0, 0.0, 0.0, 0)

        fun of(trips: List<TripEntity>): MonthSummary {
            val done = trips.filterNot { it.answerPending && it.purpose == Purpose.UNCONFIRMED }
            return MonthSummary(
                tripCount = done.size,
                totalKm = done.sumOf { it.distanceKm },
                businessKm = done.filter { it.purpose == Purpose.COMMUTE || it.purpose == Purpose.BUSINESS }.sumOf { it.distanceKm },
                unconfirmedCount = done.count { it.purpose == Purpose.UNCONFIRMED },
            )
        }
    }
}
