package com.autolog.app.data.remote

import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 차량 앱이 쓰는 서버 API (docs/contracts/api/devices.md, trips.md).
 * 기기 토큰이 필요한 요청은 [DeviceAuthInterceptor]가 헤더를 붙인다.
 */
interface AutologApi {
    @POST("devices/pairing-codes")
    suspend fun createPairingCode(@Body body: PairingCodeRequest): Response<ApiEnvelope<PairingCodeResponse>>

    @GET("devices/pairing-codes/{pairingId}")
    suspend fun pairingStatus(@Path("pairingId") pairingId: String): Response<ApiEnvelope<PairingStatusResponse>>

    @GET("devices/me/config")
    suspend fun config(@Query("since") since: String?): Response<ApiEnvelope<ConfigResponse>>

    @POST("devices/me/consents")
    suspend fun consent(@Body body: ConsentRequest): Response<ApiEnvelope<JsonElement>>

    @PUT("trips/batch")
    suspend fun uploadTrips(@Body body: TripBatchRequest): Response<ApiEnvelope<TripBatchResponse>>

    @POST("trips/{clientUuid}/edits")
    suspend fun editTrip(
        @Path("clientUuid") clientUuid: String,
        @Body body: TripEditRequest,
    ): Response<ApiEnvelope<JsonElement>>
}
