package com.autolog.app.location

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.util.Log
import com.autolog.app.domain.GeoPoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** 위치 입력. 좌표는 차량 안에서 장소 판정에만 쓴다. */
interface LocationSource {
    fun updates(): Flow<GeoPoint>
    suspend fun lastKnown(): GeoPoint?
    fun release()
}

/**
 * Pleos FusedLocation SDK 구현. 권한 `pleos.car.permission.FUSED_LOCATION`(dangerous, 런타임 허용 필요).
 * Pleos Connect AVD 2.1.0에서 약 1초 간격 수신 확인(2026-11-02).
 */
class PleosLocationSource(context: Context) : LocationSource {
    private val fused = FusedLocation(context.applicationContext).also { it.initialize() }

    override fun updates(): Flow<GeoPoint> = callbackFlow {
        val listener = object : FusedLocationListener {
            override fun onChanged(location: LocationData?) {
                location?.let { trySend(GeoPoint(it.latitude, it.longitude)) }
            }

            override fun onError(e: Exception) {
                Log.w(TAG, "위치 구독 실패: ${e.message}")
            }
        }
        fused.registerFusedLocationCallback(listener)
        awaitClose { fused.unregisterFusedLocationCallback(listener) }
    }

    override suspend fun lastKnown(): GeoPoint? = withTimeoutOrNull(3_000) {
        suspendCoroutine { cont ->
            var done = false
            fused.getLastKnownLocation(object : FusedLocationListener {
                override fun onChanged(location: LocationData?) {
                    if (done) return
                    done = true
                    cont.resume(location?.let { GeoPoint(it.latitude, it.longitude) })
                }

                override fun onError(e: Exception) {
                    if (done) return
                    done = true
                    cont.resume(null)
                }
            })
        }
    }

    override fun release() = fused.release()

    private companion object {
        const val TAG = "AutoLogLocation"
    }
}
