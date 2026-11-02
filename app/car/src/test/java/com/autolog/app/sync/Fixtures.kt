package com.autolog.app.sync

import com.autolog.app.data.local.ConsentDao
import com.autolog.app.data.local.ConsentEntity
import com.autolog.app.data.local.DriverDao
import com.autolog.app.data.local.DriverEntity
import com.autolog.app.data.local.PlaceDao
import com.autolog.app.data.local.PlaceEntity
import com.autolog.app.data.local.TripDao
import com.autolog.app.data.local.TripEditDao
import com.autolog.app.data.local.TripEditEntity
import com.autolog.app.data.local.TripEntity
import com.autolog.app.data.settings.PairedVehicleInfo
import com.autolog.app.domain.BusinessCategory
import com.autolog.app.domain.EndpointType
import com.autolog.app.domain.Purpose
import com.autolog.app.domain.PurposeSource
import com.autolog.app.domain.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

object Fixtures {
    fun trip(uuid: String, startedAt: Instant = Instant.parse("2026-11-02T23:12:00Z")) = TripEntity(
        clientUuid = uuid,
        vehicleId = 3,
        driverId = 11,
        startedAt = startedAt,
        endedAt = startedAt.plusSeconds(35 * 60),
        odoStartKm = 12034.5,
        odoEndKm = 12051.2,
        originType = EndpointType.HOME,
        originPlaceId = null,
        originLabel = null,
        destType = EndpointType.COMPANY,
        destPlaceId = 1,
        destLabel = "본사",
        autoPurpose = Purpose.COMMUTE,
        purpose = Purpose.COMMUTE,
        purposeSource = PurposeSource.VOICE,
        businessCategory = null,
        note = null,
        recordedAt = startedAt.plusSeconds(38 * 60),
        answerPending = false,
        syncState = SyncState.PENDING,
        syncErrorCode = null,
        syncedAt = null,
        localVersion = 0,
    )
}

class FakeTripDao : TripDao {
    val rows = MutableStateFlow<Map<String, TripEntity>>(emptyMap())
    /** 업로드 요청과 응답 사이에 실행할 동작(업로드 중 로컬 수정 재현). */
    var beforeMarkSynced: (suspend () -> Unit)? = null

    private fun put(trip: TripEntity) {
        rows.value = rows.value + (trip.clientUuid to trip)
    }

    override suspend fun insert(trip: TripEntity) = put(trip)
    override suspend fun find(uuid: String) = rows.value[uuid]
    override suspend fun latest() = rows.value.values.maxByOrNull { it.startedAt }
    override fun observeRecent(limit: Int): Flow<List<TripEntity>> =
        rows.map { it.values.sortedByDescending { t -> t.startedAt }.take(limit) }
    override fun observeUnconfirmed(): Flow<List<TripEntity>> =
        rows.map { it.values.filter { t -> t.purpose == Purpose.UNCONFIRMED && !t.answerPending } }
    override fun observePendingCount(): Flow<Int> = rows.map { it.values.count { t -> t.syncState == SyncState.PENDING } }
    override suspend fun countPendingForOtherVehicles(vehicleId: Long) =
        rows.value.values.count { it.syncState == SyncState.PENDING && it.vehicleId != vehicleId }
    override suspend fun pendingUploads(limit: Int) =
        rows.value.values.filter { it.syncState == SyncState.PENDING && !it.answerPending }.sortedBy { it.startedAt }.take(limit)
    override suspend fun between(from: Instant, to: Instant) =
        rows.value.values.filter { it.startedAt >= from && it.startedAt < to }.sortedBy { it.startedAt }
    override fun observeBetween(from: Instant, to: Instant): Flow<List<TripEntity>> =
        rows.map { it.values.filter { t -> t.startedAt >= from && t.startedAt < to }.sortedBy { t -> t.startedAt } }

    override suspend fun updatePurpose(uuid: String, purpose: Purpose, source: PurposeSource, category: BusinessCategory?) {
        val t = rows.value[uuid] ?: return
        put(t.copy(purpose = purpose, purposeSource = source, businessCategory = category, answerPending = false, localVersion = t.localVersion + 1))
    }

    override suspend fun extendEnd(
        uuid: String, endedAt: Instant, odoEndKm: Double, destType: EndpointType, destPlaceId: Long?, destLabel: String?,
    ): Int {
        val t = rows.value[uuid]?.takeIf { it.syncState == SyncState.PENDING } ?: return 0
        put(t.copy(endedAt = endedAt, odoEndKm = odoEndKm, destType = destType, destPlaceId = destPlaceId, destLabel = destLabel, localVersion = t.localVersion + 1))
        return 1
    }

    override suspend fun applyServerPurpose(uuid: String, purpose: Purpose, source: PurposeSource, category: BusinessCategory?): Int {
        val t = rows.value[uuid] ?: return 0
        put(t.copy(purpose = purpose, purposeSource = source, businessCategory = category, answerPending = false))
        return 1
    }

    override suspend fun clearDriverForRetry(uuid: String) {
        val t = rows.value[uuid] ?: return
        put(t.copy(driverId = null, syncState = SyncState.PENDING, syncErrorCode = null))
    }

    override suspend fun requeueDriverNotFound(): Int {
        val targets = rows.value.values.filter { it.syncState == SyncState.FAILED && it.syncErrorCode == "DRIVER_NOT_FOUND" }
        targets.forEach { put(it.copy(driverId = null, syncState = SyncState.PENDING, syncErrorCode = null)) }
        return targets.size
    }

    override fun observeFailedCount(): Flow<Int> = rows.map { it.values.count { t -> t.syncState == SyncState.FAILED } }

    override suspend fun releaseStaleAnswers() {
        rows.value = rows.value.mapValues { (_, t) -> t.copy(answerPending = false) }
    }

    override suspend fun markSyncedIfUnchanged(uuid: String, version: Int, at: Instant): Int {
        beforeMarkSynced?.invoke()
        beforeMarkSynced = null
        val t = rows.value[uuid]?.takeIf { it.localVersion == version } ?: return 0
        put(t.copy(syncState = SyncState.SYNCED, syncErrorCode = null, syncedAt = at))
        return 1
    }

    override suspend fun markSynced(uuid: String, at: Instant) {
        val t = rows.value[uuid] ?: return
        put(t.copy(syncState = SyncState.SYNCED, syncErrorCode = null, syncedAt = at))
    }

    override suspend fun markFailed(uuid: String, code: String) {
        val t = rows.value[uuid] ?: return
        put(t.copy(syncState = SyncState.FAILED, syncErrorCode = code))
    }

    override suspend fun deleteSyncedBefore(before: Instant): Int {
        val removed = rows.value.values.filter { it.syncState == SyncState.SYNCED && it.syncedAt!! < before }
        rows.value = rows.value - removed.map { it.clientUuid }.toSet()
        return removed.size
    }
}

class FakeEditDao : TripEditDao {
    val rows = linkedMapOf<String, TripEditEntity>()
    override suspend fun insert(edit: TripEditEntity) {
        rows[edit.clientEditUuid] = edit
    }
    override suspend fun pending() = rows.values.filter { it.syncState == SyncState.PENDING }
    override suspend fun markSynced(uuid: String) {
        rows[uuid] = rows.getValue(uuid).copy(syncState = SyncState.SYNCED)
    }
    override suspend fun markFailed(uuid: String, code: String) {
        rows[uuid] = rows.getValue(uuid).copy(syncState = SyncState.FAILED, syncErrorCode = code)
    }
}

class FakePlaceDao : PlaceDao {
    val rows = mutableListOf<PlaceEntity>()
    override suspend fun active() = rows.filter { !it.deleted }
    override fun observeActive(): Flow<List<PlaceEntity>> = MutableStateFlow(rows.toList())
    override suspend fun findByServerId(serverId: Long) = rows.firstOrNull { it.serverId == serverId }
    override suspend fun upsert(place: PlaceEntity) {
        val id = if (place.id == 0L) (rows.maxOfOrNull { it.id } ?: 0) + 1 else place.id
        rows.removeAll { it.id == id }
        rows += place.copy(id = id)
    }
    override suspend fun deleteHome() {
        rows.removeAll { it.type == EndpointType.HOME }
    }
    override suspend fun deleteServerPlaces() {
        rows.removeAll { it.serverId != null }
    }
}

class FakeDriverDao : DriverDao {
    val rows = linkedMapOf<Long, DriverEntity>()
    override suspend fun upsertAll(drivers: List<DriverEntity>) = drivers.forEach { rows[it.id] = it }
    override fun observeActive(): Flow<List<DriverEntity>> = MutableStateFlow(rows.values.filter { it.active })
    override suspend fun find(id: Long) = rows[id]
    override suspend fun deleteAll() = rows.clear()
}

class FakeConsentDao : ConsentDao {
    val rows = mutableListOf<ConsentEntity>()
    override suspend fun insert(consent: ConsentEntity) {
        if (rows.none { it.driverId == consent.driverId && it.consentVersion == consent.consentVersion }) rows += consent
    }
    override suspend fun exists(driverId: Long, version: String) = rows.any { it.driverId == driverId && it.consentVersion == version }
    override suspend fun pending() = rows.filter { !it.synced }
    override suspend fun markSynced(driverId: Long, version: String) {
        val i = rows.indexOfFirst { it.driverId == driverId && it.consentVersion == version }
        if (i >= 0) rows[i] = rows[i].copy(synced = true)
    }
}

class FakeSettings(var token: String? = "device-token") : SyncSettings {
    var since: String? = null
    var consentVersion: String? = null
    var vehicle: PairedVehicleInfo? = null
    var currentDriver: Long? = null
    override suspend fun deviceToken() = token
    override suspend fun clearDeviceToken() {
        token = null
    }
    override suspend fun configSince() = since
    override suspend fun saveConfigSince(serverTime: String, consentVersion: String) {
        since = serverTime
        this.consentVersion = consentVersion
    }
    override suspend fun saveVehicle(info: PairedVehicleInfo) {
        vehicle = info
    }
    override suspend fun currentDriverNow() = currentDriver
    override suspend fun setCurrentDriver(driverId: Long?) {
        currentDriver = driverId
    }
}
