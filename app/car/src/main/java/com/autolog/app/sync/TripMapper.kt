package com.autolog.app.sync

import com.autolog.app.data.local.TripEditEntity
import com.autolog.app.data.local.TripEntity
import com.autolog.app.data.remote.EndpointInput
import com.autolog.app.data.remote.TripEditRequest
import com.autolog.app.data.remote.TripUploadItem
import com.autolog.app.domain.EndpointType
import com.autolog.app.purpose.PurposeEstimator
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

/** Room 행 → 서버 요청 (docs/contracts/api/trips.md). */
object TripMapper {
    /** ISO-8601 + 서울 오프셋, 초 단위. 예: 2026-11-03T08:12:00+09:00 */
    fun formatTime(instant: Instant): String =
        OffsetDateTime.ofInstant(instant.truncatedTo(ChronoUnit.SECONDS), PurposeEstimator.SEOUL)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    /** 계기판 거리는 소수 첫째 자리까지(계약 공통 규칙). Float 변환 오차도 여기서 정리된다. */
    fun roundKm(km: Double): Double = (km * 10).roundToLong() / 10.0

    fun toUploadItem(trip: TripEntity): TripUploadItem = TripUploadItem(
        clientUuid = trip.clientUuid,
        vehicleId = trip.vehicleId,
        startedAt = formatTime(trip.startedAt),
        endedAt = formatTime(trip.endedAt),
        odoStartKm = roundKm(trip.odoStartKm),
        odoEndKm = roundKm(trip.odoEndKm),
        origin = endpoint(trip.originType, trip.originPlaceId, trip.originLabel),
        dest = endpoint(trip.destType, trip.destPlaceId, trip.destLabel),
        autoPurpose = trip.autoPurpose.name,
        purpose = trip.purpose.name,
        purposeSource = trip.purposeSource.name,
        businessCategory = trip.businessCategory?.name,
        note = trip.note,
        driverId = trip.driverId,
        recordedAt = formatTime(trip.recordedAt),
    )

    /**
     * 서버 규칙에 맞춘 출발·도착: COMPANY·CLIENT는 placeId만(라벨은 서버가 채움),
     * HOME은 둘 다 null, UNREGISTERED는 라벨만.
     */
    private fun endpoint(type: EndpointType, placeId: Long?, label: String?): EndpointInput = when (type) {
        EndpointType.COMPANY, EndpointType.CLIENT -> EndpointInput(type.name, placeId, null)
        EndpointType.HOME -> EndpointInput(type.name, null, null)
        EndpointType.UNREGISTERED -> EndpointInput(type.name, null, label)
    }

    fun toEditRequest(edit: TripEditEntity): TripEditRequest = TripEditRequest(
        clientEditUuid = edit.clientEditUuid,
        editedAt = formatTime(edit.editedAt),
        purposeSource = edit.purposeSource.name,
        changes = mapOf(
            "purpose" to JsonPrimitive(edit.purpose.name),
            "businessCategory" to (edit.businessCategory?.let { JsonPrimitive(it.name) } ?: JsonNull),
        ),
    )
}
