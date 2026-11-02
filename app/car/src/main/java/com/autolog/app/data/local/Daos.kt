package com.autolog.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.autolog.app.domain.BusinessCategory
import com.autolog.app.domain.Purpose
import com.autolog.app.domain.PurposeSource
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface TripDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(trip: TripEntity)

    @Query("SELECT * FROM trip WHERE clientUuid = :uuid")
    suspend fun find(uuid: String): TripEntity?

    @Query("SELECT * FROM trip ORDER BY startedAt DESC LIMIT 1")
    suspend fun latest(): TripEntity?

    @Query("SELECT * FROM trip ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TripEntity>>

    @Query("SELECT * FROM trip WHERE purpose = 'UNCONFIRMED' AND answerPending = 0 ORDER BY startedAt DESC")
    fun observeUnconfirmed(): Flow<List<TripEntity>>

    @Query("SELECT COUNT(*) FROM trip WHERE syncState = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM trip WHERE syncState = 'PENDING' AND vehicleId != :vehicleId")
    suspend fun countPendingForOtherVehicles(vehicleId: Long): Int

    @Query("SELECT * FROM trip WHERE syncState = 'PENDING' AND answerPending = 0 ORDER BY startedAt LIMIT :limit")
    suspend fun pendingUploads(limit: Int): List<TripEntity>

    @Query("SELECT * FROM trip WHERE startedAt >= :from AND startedAt < :to ORDER BY startedAt")
    suspend fun between(from: Instant, to: Instant): List<TripEntity>

    @Query("SELECT * FROM trip WHERE startedAt >= :from AND startedAt < :to ORDER BY startedAt")
    fun observeBetween(from: Instant, to: Instant): Flow<List<TripEntity>>

    @Query(
        """UPDATE trip SET purpose = :purpose, purposeSource = :source, businessCategory = :category,
           answerPending = 0, localVersion = localVersion + 1 WHERE clientUuid = :uuid""",
    )
    suspend fun updatePurpose(uuid: String, purpose: Purpose, source: PurposeSource, category: BusinessCategory?)

    @Query(
        """UPDATE trip SET endedAt = :endedAt, odoEndKm = :odoEndKm, destType = :destType, destPlaceId = :destPlaceId,
           destLabel = :destLabel, localVersion = localVersion + 1 WHERE clientUuid = :uuid AND syncState = 'PENDING'""",
    )
    suspend fun extendEnd(
        uuid: String,
        endedAt: Instant,
        odoEndKm: Double,
        destType: com.autolog.app.domain.EndpointType,
        destPlaceId: Long?,
        destLabel: String?,
    ): Int

    /** 서버(관리자 수정)의 현재 값으로 덮어쓴다. 수정 이력을 만들지 않고 동기화 상태도 바꾸지 않는다. */
    @Query(
        """UPDATE trip SET purpose = :purpose, purposeSource = :source, businessCategory = :category,
           answerPending = 0 WHERE clientUuid = :uuid""",
    )
    suspend fun applyServerPurpose(uuid: String, purpose: Purpose, source: PurposeSource, category: BusinessCategory?): Int

    /** 서버가 직원을 찾지 못한 트립은 이용자 없이 다시 보낸다(기록 누락 방지). */
    @Query("UPDATE trip SET driverId = NULL, syncState = 'PENDING', syncErrorCode = NULL WHERE clientUuid = :uuid")
    suspend fun clearDriverForRetry(uuid: String)

    /** 이전에 직원 없음으로 실패 처리된 트립(이 처리 도입 전 기록 포함)도 이용자 없이 다시 보낸다. */
    @Query(
        """UPDATE trip SET driverId = NULL, syncState = 'PENDING', syncErrorCode = NULL
           WHERE syncState = 'FAILED' AND syncErrorCode = 'DRIVER_NOT_FOUND'""",
    )
    suspend fun requeueDriverNotFound(): Int

    @Query("SELECT COUNT(*) FROM trip WHERE syncState = 'FAILED'")
    fun observeFailedCount(): Flow<Int>

    /** 앱이 질문 중에 꺼졌던 트립은 미확인으로 남기고 업로드 대상으로 돌린다. */
    @Query("UPDATE trip SET answerPending = 0 WHERE answerPending = 1")
    suspend fun releaseStaleAnswers()

    /** 업로드 당시 버전과 같을 때만 SYNCED로 바꾼다. 0이면 업로드 중 로컬 수정이 있었던 것. */
    @Query("UPDATE trip SET syncState = 'SYNCED', syncErrorCode = NULL, syncedAt = :at WHERE clientUuid = :uuid AND localVersion = :version")
    suspend fun markSyncedIfUnchanged(uuid: String, version: Int, at: Instant): Int

    @Query("UPDATE trip SET syncState = 'SYNCED', syncErrorCode = NULL, syncedAt = :at WHERE clientUuid = :uuid")
    suspend fun markSynced(uuid: String, at: Instant)

    @Query("UPDATE trip SET syncState = 'FAILED', syncErrorCode = :code WHERE clientUuid = :uuid")
    suspend fun markFailed(uuid: String, code: String)

    /** 보유 기간(동기화 후 90일, rules §11)이 지난 동기화 완료 트립 삭제. */
    @Query("DELETE FROM trip WHERE syncState = 'SYNCED' AND syncedAt < :before")
    suspend fun deleteSyncedBefore(before: Instant): Int
}

@Dao
interface TripEditDao {
    @Insert
    suspend fun insert(edit: TripEditEntity)

    @Query("SELECT * FROM trip_edit WHERE syncState = 'PENDING' ORDER BY editedAt")
    suspend fun pending(): List<TripEditEntity>

    @Query("UPDATE trip_edit SET syncState = 'SYNCED', syncErrorCode = NULL WHERE clientEditUuid = :uuid")
    suspend fun markSynced(uuid: String)

    @Query("UPDATE trip_edit SET syncState = 'FAILED', syncErrorCode = :code WHERE clientEditUuid = :uuid")
    suspend fun markFailed(uuid: String, code: String)
}

@Dao
interface PlaceDao {
    @Query("SELECT * FROM place WHERE deleted = 0")
    suspend fun active(): List<PlaceEntity>

    @Query("SELECT * FROM place WHERE deleted = 0 ORDER BY type, name")
    fun observeActive(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM place WHERE serverId = :serverId")
    suspend fun findByServerId(serverId: Long): PlaceEntity?

    @Upsert
    suspend fun upsert(place: PlaceEntity)

    @Transaction
    suspend fun upsertServerPlaces(places: List<PlaceEntity>) {
        places.forEach { incoming ->
            val existing = incoming.serverId?.let { findByServerId(it) }
            upsert(incoming.copy(id = existing?.id ?: 0))
        }
    }

    @Query("DELETE FROM place WHERE type = 'HOME'")
    suspend fun deleteHome()

    @Query("DELETE FROM place WHERE serverId IS NOT NULL")
    suspend fun deleteServerPlaces()
}

@Dao
interface DriverDao {
    @Upsert
    suspend fun upsertAll(drivers: List<DriverEntity>)

    @Query("SELECT * FROM driver WHERE active = 1 ORDER BY dept, name")
    fun observeActive(): Flow<List<DriverEntity>>

    @Query("SELECT * FROM driver WHERE id = :id")
    suspend fun find(id: Long): DriverEntity?

    @Query("DELETE FROM driver")
    suspend fun deleteAll()
}

@Dao
interface ConsentDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(consent: ConsentEntity)

    @Query("SELECT COUNT(*) > 0 FROM consent WHERE driverId = :driverId AND consentVersion = :version")
    suspend fun exists(driverId: Long, version: String): Boolean

    @Query("SELECT * FROM consent WHERE synced = 0")
    suspend fun pending(): List<ConsentEntity>

    @Query("UPDATE consent SET synced = 1 WHERE driverId = :driverId AND consentVersion = :version")
    suspend fun markSynced(driverId: Long, version: String)
}
