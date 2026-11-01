package com.autolog.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.autolog.app.domain.BusinessCategory
import com.autolog.app.domain.EndpointType
import com.autolog.app.domain.Purpose
import com.autolog.app.domain.PurposeSource
import com.autolog.app.domain.SyncState
import java.time.Instant

/**
 * 트립. 차량 자동값(시각·계기판 거리·추정 용도)은 저장 후 바꾸지 않는다(D3).
 * 좌표는 저장하지 않는다. 장소 판정은 저장 전에 끝낸다(위치 최소 저장).
 */
@Entity(tableName = "trip", indices = [Index("startedAt"), Index("syncState")])
data class TripEntity(
    @PrimaryKey val clientUuid: String,
    /** 기록 당시 페어링된 차량 (D10). */
    val vehicleId: Long,
    val driverId: Long?,
    val startedAt: Instant,
    val endedAt: Instant,
    val odoStartKm: Double,
    val odoEndKm: Double,
    val originType: EndpointType,
    val originPlaceId: Long?,
    val originLabel: String?,
    val destType: EndpointType,
    val destPlaceId: Long?,
    val destLabel: String?,
    val autoPurpose: Purpose,
    val purpose: Purpose,
    val purposeSource: PurposeSource,
    val businessCategory: BusinessCategory?,
    val note: String?,
    val recordedAt: Instant,
    /** 도착 음성 확인을 기다리는 중이면 업로드하지 않는다. */
    val answerPending: Boolean,
    val syncState: SyncState,
    val syncErrorCode: String?,
    val syncedAt: Instant?,
    /** 로컬 수정마다 1씩 올린다. 업로드 중 수정을 감지하는 데 쓴다. */
    val localVersion: Int,
) {
    val distanceKm: Double get() = odoEndKm - odoStartKm
}

/** 이미 서버에 올라간 트립의 용도 수정. 서버 이력 1행이 된다. */
@Entity(tableName = "trip_edit", indices = [Index("tripUuid"), Index("syncState")])
data class TripEditEntity(
    @PrimaryKey val clientEditUuid: String,
    val tripUuid: String,
    val vehicleId: Long,
    val editedAt: Instant,
    val purposeSource: PurposeSource,
    val purpose: Purpose,
    val businessCategory: BusinessCategory?,
    val syncState: SyncState,
    val syncErrorCode: String?,
)

/** 장소. 회사·거래처(serverId 있음)는 서버에서 내려받고, 집(serverId 없음)은 차량에서만 등록한다(D2). */
@Entity(tableName = "place", indices = [Index(value = ["serverId"], unique = true)])
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long?,
    val name: String,
    val type: EndpointType,
    val lat: Double,
    val lng: Double,
    val radiusM: Int,
    val deleted: Boolean,
)

@Entity(tableName = "driver")
data class DriverEntity(
    @PrimaryKey val id: Long,
    val dept: String,
    val name: String,
    val active: Boolean,
)

/** 직원별 개인정보 동의 (rules §11). synced=false면 서버로 보낼 대상. */
@Entity(tableName = "consent", primaryKeys = ["driverId", "consentVersion"])
data class ConsentEntity(
    val driverId: Long,
    val consentVersion: String,
    val consentedAt: Instant,
    val synced: Boolean,
)
