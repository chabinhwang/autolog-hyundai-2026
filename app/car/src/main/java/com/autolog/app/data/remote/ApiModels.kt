package com.autolog.app.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** 서버 공통 응답 `{ success, data, error }` (docs/contracts/README.md). */
@Serializable
data class ApiEnvelope<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiErrorBody? = null,
)

@Serializable
data class ApiErrorBody(val code: String, val message: String? = null)

@Serializable
data class PairingCodeRequest(val installationId: String, val tokenHash: String)

@Serializable
data class PairingCodeResponse(val pairingId: String, val code: String, val expiresAt: String)

@Serializable
data class PairingStatusResponse(
    val status: String,
    val expiresAt: String? = null,
    val vehicle: PairedVehicle? = null,
)

@Serializable
data class PairedVehicle(val regNo: String, val modelName: String? = null)

@Serializable
data class ConfigResponse(
    val serverTime: String,
    val consentVersion: String,
    val vehicle: ConfigVehicle,
    val drivers: List<ConfigDriver>,
    val places: List<ConfigPlace>,
)

@Serializable
data class ConfigVehicle(val id: Long, val regNo: String, val modelName: String? = null, val defaultDriverId: Long? = null)

@Serializable
data class ConfigDriver(val id: Long, val dept: String, val name: String, val active: Boolean)

@Serializable
data class ConfigPlace(
    val id: Long,
    val name: String,
    val type: String,
    val lat: Double,
    val lng: Double,
    val radiusM: Int,
    val deleted: Boolean,
)

@Serializable
data class ConsentRequest(val driverId: Long, val consentVersion: String, val consentedAt: String)

@Serializable
data class TripBatchRequest(val appVersion: String, val trips: List<TripUploadItem>)

@Serializable
data class TripUploadItem(
    val clientUuid: String,
    val vehicleId: Long,
    val startedAt: String,
    val endedAt: String,
    val odoStartKm: Double,
    val odoEndKm: Double,
    val origin: EndpointInput,
    val dest: EndpointInput,
    val autoPurpose: String,
    val purpose: String,
    val purposeSource: String,
    val businessCategory: String?,
    val note: String?,
    val driverId: Long?,
    val recordedAt: String,
)

@Serializable
data class EndpointInput(val type: String, val placeId: Long?, val label: String?)

@Serializable
data class TripBatchResponse(val results: List<UploadResult>)

@Serializable
data class UploadResult(val clientUuid: String, val result: String, val errorCode: String? = null)

/** 차량 수정 요청. changes는 바꿀 필드만 담는다(키 유무가 의미를 가짐). */
@Serializable
data class TripEditRequest(
    val clientEditUuid: String,
    val editedAt: String,
    val purposeSource: String,
    val changes: Map<String, JsonElement>,
)
