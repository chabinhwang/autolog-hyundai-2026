package com.autolog.app.location

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** 지금 위치의 동 이름(예: "신림동"). 등록 장소가 아닌 출발·도착지 이름으로 쓴다. 모르면 null. */
interface AreaNameSource {
    suspend fun currentAreaName(): String?

    fun release() = Unit

    companion object {
        val None = object : AreaNameSource {
            override suspend fun currentAreaName(): String? = null
        }
    }
}

/** 내비 주소("관악구 신림동")에서 마지막 행정구역(동·읍·면 등)만 남긴다. */
object AreaName {
    fun from(address: String?): String? =
        address?.trim()?.split(Regex("\\s+"))?.lastOrNull { it.isNotBlank() }?.takeIf { it.length in 2..20 }
}

/**
 * Pleos NaviHelper 3.0.5 `getCurrentLocationInfo` → `onCurrentLocationInfo.address`. 권한 NAVI_ROUTE.
 * 내비 앱이 준비되지 않았으면 응답이 없으므로("navi not ready") 시간 안에 못 받으면 null.
 * AVD에서 내비 앱을 띄운 뒤 '수정구 시흥동' 수신 확인(2026-11-03).
 */
class PleosAreaNameSource(context: Context) : AreaNameSource {
    private val appContext = context.applicationContext
    private var helper: NaviHelper? = null
    private var connected: CompletableDeferred<Boolean>? = null
    @Volatile private var pending: CompletableDeferred<String?>? = null
    private val busy = Mutex()

    private val listener = object : NaviHelperEventListener {
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            pending?.complete(currentLocationInfo.address)
        }

        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "NaviHelper 오류: $errorCode")
            pending?.complete(null)
        }
    }

    private suspend fun connect(): NaviHelper? {
        val ready = connected ?: withContext(Dispatchers.Main) {
            val created = NaviHelper(appContext)
            helper = created
            CompletableDeferred<Boolean>().also { deferred ->
                connected = deferred
                created.initialize(
                    onConnected = {
                        created.addListener(listener)
                        deferred.complete(true)
                    },
                    onFailed = {
                        Log.w(TAG, "NaviHelper 연결 실패: ${it.message}")
                        deferred.complete(false)
                    },
                )
            }
        }
        return if (withTimeoutOrNull(TIMEOUT_MS) { ready.await() } == true) helper else null
    }

    override suspend fun currentAreaName(): String? = busy.withLock {
        val navi = connect() ?: return null
        val answer = CompletableDeferred<String?>()
        pending = answer
        withContext(Dispatchers.Main) { runCatching { navi.getCurrentLocationInfo() } }
        val address = withTimeoutOrNull(TIMEOUT_MS) { answer.await() }
        pending = null
        AreaName.from(address).also { if (it == null) Log.i(TAG, "동 이름 없음(내비 응답: $address)") }
    }

    override fun release() {
        helper?.let { navi ->
            runCatching { navi.removeListener(listener) }
            runCatching { navi.release() }
        }
        helper = null
        connected = null
    }

    private companion object {
        const val TAG = "AutoLogArea"
        const val TIMEOUT_MS = 3_000L
    }
}
