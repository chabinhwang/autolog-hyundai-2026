package com.autolog.app.data.remote

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/** API 호출 결과. 네트워크 실패와 서버 오류 코드를 구분한다. */
sealed interface ApiResult<out T> {
    data class Ok<T>(val status: Int, val data: T) : ApiResult<T>
    data class Fail(val status: Int, val code: String, val message: String?) : ApiResult<Nothing>
    data class NetworkError(val cause: IOException) : ApiResult<Nothing>
}

val autologJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = true
    encodeDefaults = true
}

/** Retrofit 응답을 [ApiResult]로 바꾼다. 실패 본문도 공통 응답 구조로 읽는다. */
suspend fun <T> apiCall(block: suspend () -> retrofit2.Response<ApiEnvelope<T>>): ApiResult<T> {
    val response = try {
        block()
    } catch (e: IOException) {
        return ApiResult.NetworkError(e)
    }
    val body = response.body()
    if (response.isSuccessful && body != null && body.success && body.data != null) {
        return ApiResult.Ok(response.code(), body.data)
    }
    val error = body?.error ?: response.errorBody()?.string()?.let { raw ->
        runCatching { autologJson.decodeFromString(ApiEnvelope.serializer(ApiErrorBody.serializer()), raw).error }
            .getOrNull()
    }
    return ApiResult.Fail(response.code(), error?.code ?: "HTTP_${response.code()}", error?.message)
}

/** 기기 토큰이 있으면 Authorization 헤더를 붙인다. 페어링 코드 발급·상태 조회는 토큰 없이 호출된다. */
class DeviceAuthInterceptor(private val tokenProvider: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // 페어링 코드 발급·조회는 인증 없는 API. 폐기된 이전 토큰이 섞여 401이 나지 않게 헤더를 붙이지 않는다.
        val public = chain.request().url.encodedPath.contains("/devices/pairing-codes")
        val token = if (public) null else tokenProvider()
        val request = if (token == null) {
            chain.request()
        } else {
            chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        }
        return chain.proceed(request)
    }
}

object ApiFactory {
    /** @param baseUrl `/api/v1/`까지 포함하고 '/'로 끝나는 주소 */
    fun create(baseUrl: String, tokenProvider: () -> String?): AutologApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(DeviceAuthInterceptor(tokenProvider))
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(autologJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AutologApi::class.java)
    }
}
