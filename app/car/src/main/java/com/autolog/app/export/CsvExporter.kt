package com.autolog.app.export

import com.autolog.app.data.local.DriverEntity
import com.autolog.app.data.local.TripEntity
import com.autolog.app.domain.BusinessCategory
import com.autolog.app.domain.EndpointType
import com.autolog.app.domain.Purpose
import com.autolog.app.purpose.PurposeEstimator
import com.autolog.app.sync.TripMapper
import java.time.format.DateTimeFormatter

/**
 * 차량 앱 CSV 내보내기. 국세청 업무용승용차 운행기록부 별지 서식의 운행 내역 열 순서를 따른다:
 * 사용일자 · 부서 · 성명 · 주행 전/후 계기판 거리 · 주행거리 · 출퇴근용 · 일반 업무용 · 비고.
 * 서식 그대로의 기록부(XLSX)는 서버가 만든다.
 */
object CsvExporter {
    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")

    val HEADER = listOf(
        "사용일자", "부서", "성명", "주행 전 계기판 거리(km)", "주행 후 계기판 거리(km)", "주행거리(km)",
        "출퇴근용(km)", "일반 업무용(km)", "용도", "출발", "도착", "시작", "종료", "비고",
    )

    fun export(trips: List<TripEntity>, drivers: Map<Long, DriverEntity>): String {
        val rows = trips.sortedBy { it.startedAt }.map { trip ->
            val start = trip.startedAt.atZone(PurposeEstimator.SEOUL)
            val end = trip.endedAt.atZone(PurposeEstimator.SEOUL)
            val distance = TripMapper.roundKm(trip.odoEndKm) - TripMapper.roundKm(trip.odoStartKm)
            val driver = trip.driverId?.let(drivers::get)
            listOf(
                DATE.format(start),
                driver?.dept.orEmpty(),
                driver?.name.orEmpty(),
                km(trip.odoStartKm),
                km(trip.odoEndKm),
                km(distance),
                if (trip.purpose == Purpose.COMMUTE) km(distance) else "",
                if (trip.purpose == Purpose.BUSINESS) km(distance) else "",
                purposeLabel(trip.purpose, trip.businessCategory),
                endpointLabel(trip.originType, trip.originLabel),
                endpointLabel(trip.destType, trip.destLabel),
                TIME.format(start),
                TIME.format(end),
                trip.note.orEmpty(),
            )
        }
        return (listOf(HEADER) + rows).joinToString("\r\n") { row -> row.joinToString(",", transform = ::escape) } + "\r\n"
    }

    fun purposeLabel(purpose: Purpose, category: BusinessCategory?): String = when (purpose) {
        Purpose.COMMUTE -> "출퇴근"
        Purpose.BUSINESS -> when (category) {
            BusinessCategory.VISIT -> "업무(거래처 방문)"
            BusinessCategory.MEETING -> "업무(회의)"
            BusinessCategory.PROMOTION -> "업무(판촉)"
            BusinessCategory.TRAINING -> "업무(교육)"
            BusinessCategory.OTHER, null -> "업무"
        }
        Purpose.PERSONAL -> "개인"
        Purpose.UNCONFIRMED -> "미확인"
    }

    fun endpointLabel(type: EndpointType, label: String?): String = when (type) {
        EndpointType.HOME -> "집"
        EndpointType.UNREGISTERED -> label ?: "미등록 장소"
        else -> label ?: ""
    }

    private fun km(value: Double) = String.format(java.util.Locale.ROOT, "%.1f", TripMapper.roundKm(value))

    /** RFC 4180: 쉼표·따옴표·줄바꿈이 있으면 따옴표로 감싼다. 엑셀 수식 주입을 막으려고 =,+,-,@로 시작하는 문자열 앞에 '를 붙인다. */
    private fun escape(value: String): String {
        val safe = if (value.isNotEmpty() && value[0] in "=+-@" && value.toDoubleOrNull() == null) "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${safe.replace("\"", "\"\"")}\"" else safe
    }
}
