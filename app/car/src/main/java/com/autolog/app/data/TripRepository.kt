package com.autolog.app.data

import com.autolog.app.data.local.PlaceDao
import com.autolog.app.data.local.PlaceEntity
import com.autolog.app.data.local.TripDao
import com.autolog.app.data.local.TripEditDao
import com.autolog.app.data.local.TripEditEntity
import com.autolog.app.data.local.TripEntity
import com.autolog.app.domain.BusinessCategory
import com.autolog.app.domain.DetectedTrip
import com.autolog.app.domain.Endpoint
import com.autolog.app.domain.EndpointType
import com.autolog.app.domain.GeoPoint
import com.autolog.app.domain.Place
import com.autolog.app.domain.Purpose
import com.autolog.app.domain.PurposeSource
import com.autolog.app.domain.SyncState
import com.autolog.app.purpose.PurposeDecision
import com.autolog.app.purpose.PurposeEstimate
import com.autolog.app.trip.ShortTripPolicy
import java.time.Clock
import java.util.UUID

/** 트립 저장·수정. 서버 업로드는 [com.autolog.app.sync.SyncEngine]이 한다. */
class TripRepository(
    private val tripDao: TripDao,
    private val editDao: TripEditDao,
    private val placeDao: PlaceDao,
    private val clock: Clock = Clock.systemUTC(),
) {
    sealed interface RecordResult {
        /** 새 트립 저장. 음성 확인을 기다리는 상태(answerPending). */
        data class Saved(val trip: TripEntity) : RecordResult
        data class Merged(val previousUuid: String) : RecordResult
        data object Dropped : RecordResult
    }

    suspend fun places(): List<Place> = placeDao.active().map { it.toDomain() }

    /** 가장 최근에 끝난 미확인 운행(질문 중이 아닌 것). 다음 시동 때 한 번 다시 묻는 데 쓴다. */
    suspend fun latestUnconfirmed(): TripEntity? = tripDao.latestUnconfirmed()

    /**
     * 확정된 트립을 저장한다. 질문 전에 미확인(AUTO)으로 먼저 저장해, 질문 중 앱이 꺼져도 기록이 남는다.
     */
    suspend fun record(
        detected: DetectedTrip,
        origin: Endpoint,
        dest: Endpoint,
        estimate: PurposeEstimate,
        vehicleId: Long,
        driverId: Long?,
    ): RecordResult {
        val previous = tripDao.latest()
        val mergeable = previous != null && previous.syncState == SyncState.PENDING && !previous.answerPending &&
            previous.vehicleId == vehicleId
        return when (val decision = ShortTripPolicy.decide(detected, previous?.clientUuid, mergeable)) {
            ShortTripPolicy.Decision.Drop -> RecordResult.Dropped
            is ShortTripPolicy.Decision.MergeIntoPrevious -> {
                val updated = tripDao.extendEnd(
                    decision.previousId, detected.end.at, detected.end.odometerKm, dest.type, dest.placeId, dest.label,
                )
                if (updated > 0) RecordResult.Merged(decision.previousId) else RecordResult.Dropped
            }
            ShortTripPolicy.Decision.Keep -> {
                val trip = TripEntity(
                    clientUuid = UUID.randomUUID().toString(),
                    vehicleId = vehicleId,
                    driverId = driverId,
                    startedAt = detected.start.at,
                    endedAt = detected.end.at,
                    odoStartKm = detected.start.odometerKm,
                    odoEndKm = detected.end.odometerKm,
                    originType = origin.type,
                    originPlaceId = origin.placeId,
                    originLabel = origin.label,
                    destType = dest.type,
                    destPlaceId = dest.placeId,
                    destLabel = dest.label,
                    autoPurpose = estimate.purpose,
                    purpose = Purpose.UNCONFIRMED,
                    purposeSource = PurposeSource.AUTO,
                    businessCategory = null,
                    note = null,
                    recordedAt = clock.instant(),
                    answerPending = true,
                    syncState = SyncState.PENDING,
                    syncErrorCode = null,
                    syncedAt = null,
                    localVersion = 0,
                )
                tripDao.insert(trip)
                RecordResult.Saved(trip)
            }
        }
    }

    /** 도착 확인(음성 대답 또는 팝업 버튼) 결과 반영. 무응답·'나중에'면 미확인(AUTO) 그대로 업로드 대상이 된다. */
    /**
     * 도착 확인 결과를 저장하되 아직 업로드하지 않는다('다시 선택' 대기). 이 사이 앱이 꺼지면
     * 다음 실행의 답변 대기 해제로 고른 용도 그대로 업로드된다.
     */
    suspend fun holdArrivalDecision(uuid: String, decision: PurposeDecision) {
        tripDao.holdPurpose(uuid, decision.purpose, decision.source, decision.businessCategory)
    }

    suspend fun applyArrivalDecision(uuid: String, decision: PurposeDecision) {
        tripDao.updatePurpose(uuid, decision.purpose, decision.source, decision.businessCategory)
    }

    /**
     * 차량 화면에서 용도를 고른다(미확인 정리). 서버에 이미 올라간 트립이면 수정 이력을 만든다.
     */
    suspend fun changePurpose(uuid: String, purpose: Purpose, category: BusinessCategory?, source: PurposeSource = PurposeSource.MANUAL) {
        require(purpose != Purpose.UNCONFIRMED) { "미확인으로는 바꿀 수 없습니다." }
        val trip = tripDao.find(uuid) ?: return
        // 세부를 고르지 않은 업무는 도착지가 거래처면 방문으로 둔다(음성 "응"과 같은 결과, car-app.md §3).
        val normalizedCategory = when {
            purpose != Purpose.BUSINESS -> null
            category != null -> category
            trip.destType == EndpointType.CLIENT -> BusinessCategory.VISIT
            else -> null
        }
        tripDao.updatePurpose(uuid, purpose, source, normalizedCategory)
        if (trip.syncState == SyncState.SYNCED) {
            editDao.insert(
                TripEditEntity(
                    clientEditUuid = UUID.randomUUID().toString(),
                    tripUuid = uuid,
                    vehicleId = trip.vehicleId,
                    editedAt = clock.instant(),
                    purposeSource = source,
                    purpose = purpose,
                    businessCategory = normalizedCategory,
                    syncState = SyncState.PENDING,
                    syncErrorCode = null,
                ),
            )
        }
    }

    private fun PlaceEntity.toDomain() = Place(serverId, name, type, GeoPoint(lat, lng), radiusM)
}
