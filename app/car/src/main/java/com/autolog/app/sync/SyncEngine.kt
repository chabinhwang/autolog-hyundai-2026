package com.autolog.app.sync

import com.autolog.app.data.local.ConsentDao
import com.autolog.app.data.local.DriverDao
import com.autolog.app.data.local.DriverEntity
import com.autolog.app.data.local.PlaceDao
import com.autolog.app.data.local.PlaceEntity
import com.autolog.app.data.local.TripDao
import com.autolog.app.data.local.TripEditDao
import com.autolog.app.data.local.TripEditEntity
import com.autolog.app.data.remote.ApiResult
import com.autolog.app.data.remote.AutologApi
import com.autolog.app.data.remote.ConfigResponse
import com.autolog.app.data.remote.ConsentRequest
import com.autolog.app.data.remote.TripBatchRequest
import com.autolog.app.data.remote.apiCall
import com.autolog.app.data.settings.PairedVehicleInfo
import com.autolog.app.domain.EndpointType
import com.autolog.app.domain.Purpose
import com.autolog.app.domain.PurposeSource
import com.autolog.app.domain.SyncState
import java.time.Clock
import java.util.UUID

/** 동기화에 필요한 설정값. [com.autolog.app.data.settings.SettingsStore]가 구현한다. */
interface SyncSettings {
    suspend fun deviceToken(): String?
    suspend fun clearDeviceToken()
    suspend fun configSince(): String?
    suspend fun saveConfigSince(serverTime: String, consentVersion: String)
    suspend fun saveVehicle(info: PairedVehicleInfo)
}

/**
 * 동기화 한 번 (rules §6): 동의 기록 → 트립 업로드 → 수정 업로드 → 설정 내려받기.
 * 네트워크가 끊기면 [Outcome.Retry]로 끝내고 WorkManager가 다시 실행한다.
 */
class SyncEngine(
    private val api: AutologApi,
    private val settings: SyncSettings,
    private val tripDao: TripDao,
    private val editDao: TripEditDao,
    private val placeDao: PlaceDao,
    private val driverDao: DriverDao,
    private val consentDao: ConsentDao,
    private val appVersion: String,
    private val clock: Clock = Clock.systemUTC(),
) {
    enum class Outcome { SUCCESS, RETRY, UNPAIRED }

    private class StopSync(val outcome: Outcome) : Exception()

    suspend fun run(): Outcome {
        if (settings.deviceToken() == null) return Outcome.UNPAIRED
        return try {
            uploadConsents()
            uploadTrips()
            uploadEdits()
            downloadConfig()
            Outcome.SUCCESS
        } catch (stop: StopSync) {
            stop.outcome
        }
    }

    /** 공통 실패 처리: 401은 토큰 폐기(페어링 화면 안내), 네트워크·5xx·429는 재시도. */
    private suspend fun <T> ApiResult<T>.orStop(): ApiResult<T> {
        when (this) {
            is ApiResult.NetworkError -> throw StopSync(Outcome.RETRY)
            is ApiResult.Fail -> when {
                status == 401 -> {
                    settings.clearDeviceToken()
                    throw StopSync(Outcome.UNPAIRED)
                }
                status == 429 || status >= 500 -> throw StopSync(Outcome.RETRY)
            }
            is ApiResult.Ok -> Unit
        }
        return this
    }

    private suspend fun uploadConsents() {
        consentDao.pending().forEach { consent ->
            val result = apiCall {
                api.consent(ConsentRequest(consent.driverId, consent.consentVersion, TripMapper.formatTime(consent.consentedAt)))
            }.orStop()
            // 4xx(직원 없음·검증 실패)는 다시 보내도 같으므로 정리한다.
            if (result is ApiResult.Ok || result is ApiResult.Fail) consentDao.markSynced(consent.driverId, consent.consentVersion)
        }
    }

    private suspend fun uploadTrips() {
        while (true) {
            val batch = tripDao.pendingUploads(BATCH_SIZE)
            if (batch.isEmpty()) return
            val versions = batch.associate { it.clientUuid to it.localVersion }
            val result = apiCall {
                api.uploadTrips(TripBatchRequest(appVersion, batch.map(TripMapper::toUploadItem)))
            }.orStop()
            when (result) {
                is ApiResult.Ok -> {
                    result.data.results.forEach { item ->
                        when (item.result) {
                            "CREATED", "DUPLICATE" -> markUploaded(item.clientUuid, versions[item.clientUuid] ?: -1)
                            else -> tripDao.markFailed(item.clientUuid, item.errorCode ?: "REJECTED")
                        }
                    }
                    // 결과가 빠진 건은 반복 업로드를 막기 위해 실패로 둔다.
                    val answered = result.data.results.map { it.clientUuid }.toSet()
                    batch.filter { it.clientUuid !in answered }.forEach { tripDao.markFailed(it.clientUuid, "NO_RESULT") }
                }
                // 요청 전체 거절(400): 같은 요청을 반복하지 않도록 이번 묶음을 실패 처리한다.
                is ApiResult.Fail -> batch.forEach { tripDao.markFailed(it.clientUuid, result.code) }
                is ApiResult.NetworkError -> Unit
            }
        }
    }

    /**
     * 업로드 중 로컬에서 용도가 바뀌었으면 서버에는 이전 값이 저장됐으므로 수정 이력으로 보낸다.
     */
    private suspend fun markUploaded(uuid: String, version: Int) {
        val now = clock.instant()
        if (tripDao.markSyncedIfUnchanged(uuid, version, now) > 0) return
        val current = tripDao.find(uuid) ?: return
        tripDao.markSynced(uuid, now)
        if (current.purpose == Purpose.UNCONFIRMED) return
        editDao.insert(
            TripEditEntity(
                clientEditUuid = UUID.randomUUID().toString(),
                tripUuid = uuid,
                vehicleId = current.vehicleId,
                editedAt = now,
                purposeSource = if (current.purposeSource == PurposeSource.VOICE) PurposeSource.VOICE else PurposeSource.MANUAL,
                purpose = current.purpose,
                businessCategory = current.businessCategory,
                syncState = SyncState.PENDING,
                syncErrorCode = null,
            ),
        )
    }

    private suspend fun uploadEdits() {
        editDao.pending().forEach { edit ->
            when (val result = apiCall { api.editTrip(edit.tripUuid, TripMapper.toEditRequest(edit)) }.orStop()) {
                is ApiResult.Ok -> editDao.markSynced(edit.clientEditUuid)
                // 트립이 아직 서버에 없으면 다음 동기화 때 다시 보낸다(rules §4).
                is ApiResult.Fail -> if (result.code != "TRIP_NOT_FOUND") editDao.markFailed(edit.clientEditUuid, result.code)
                is ApiResult.NetworkError -> Unit
            }
        }
    }

    private suspend fun downloadConfig() {
        val result = apiCall { api.config(settings.configSince()) }.orStop()
        if (result is ApiResult.Ok) applyConfig(result.data)
    }

    private suspend fun applyConfig(config: ConfigResponse) {
        settings.saveVehicle(
            PairedVehicleInfo(config.vehicle.id, config.vehicle.regNo, config.vehicle.modelName, config.vehicle.defaultDriverId),
        )
        driverDao.upsertAll(config.drivers.map { DriverEntity(it.id, it.dept, it.name, it.active) })
        placeDao.upsertServerPlaces(
            config.places.mapNotNull { place ->
                val type = runCatching { EndpointType.valueOf(place.type) }.getOrNull() ?: return@mapNotNull null
                PlaceEntity(0, place.id, place.name, type, place.lat, place.lng, place.radiusM, place.deleted)
            },
        )
        settings.saveConfigSince(config.serverTime, config.consentVersion)
    }

    private companion object {
        const val BATCH_SIZE = 50
    }
}
