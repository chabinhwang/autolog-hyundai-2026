package com.autolog.app.ui

import com.autolog.app.purpose.PurposeEstimator
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter

/** 운행 목록의 시각 범위·걸린 시간 표시 (Asia/Seoul 기준). */
object TripTime {
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")

    /** "22:06–22:41". 날짜를 넘기면 끝 시각 앞에 "다음날"을 붙인다. */
    fun range(startedAt: Instant, endedAt: Instant): String {
        val start = startedAt.atZone(PurposeEstimator.SEOUL)
        val end = endedAt.atZone(PurposeEstimator.SEOUL)
        val nextDay = if (end.toLocalDate().isAfter(start.toLocalDate())) "다음날 " else ""
        return "${TIME.format(start)}–$nextDay${TIME.format(end)}"
    }

    /** "35분", "1시간 5분", "2시간". 1분이 안 되면 "1분 미만". */
    fun duration(startedAt: Instant, endedAt: Instant): String {
        val minutes = Duration.between(startedAt, endedAt).toMinutes().coerceAtLeast(0)
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            minutes < 1 -> "1분 미만"
            hours == 0L -> "${rest}분"
            rest == 0L -> "${hours}시간"
            else -> "${hours}시간 ${rest}분"
        }
    }
}
