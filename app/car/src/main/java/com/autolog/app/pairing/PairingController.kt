package com.autolog.app.pairing

import com.autolog.app.data.remote.ApiResult
import com.autolog.app.data.remote.AutologApi
import com.autolog.app.data.remote.PairingCodeRequest
import com.autolog.app.data.remote.apiCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 페어링에 필요한 설정값. [com.autolog.app.data.settings.SettingsStore]가 구현한다. */
interface PairingSettings {
    suspend fun installationId(): String
    suspend fun savePairingToken(token: String)
    suspend fun promotePairingToken()
}

/**
 * 차량 쪽 페어링 흐름 (rules §2): 새 토큰 생성 → 해시로 코드 발급 → 3초마다 상태 조회 →
 * PAIRED면 토큰을 기기 토큰으로 올리고 설정을 내려받는다. EXPIRED면 새 코드를 받는다.
 */
class PairingController(
    private val api: AutologApi,
    private val settings: PairingSettings,
    private val scope: CoroutineScope,
    /** PAIRED 직후 실행(설정 내려받기). 실패해도 페어링은 끝난 것으로 본다. */
    private val onPaired: suspend () -> Unit,
    private val pollIntervalMs: Long = 3_000,
    private val newToken: () -> String = { DeviceTokens.generate() },
) {
    sealed interface State {
        data object Idle : State
        data object Requesting : State
        data class ShowingCode(val code: String, val expiresAt: String) : State
        data class Paired(val regNo: String, val modelName: String?) : State
        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()
    private var job: Job? = null

    fun start() {
        job?.cancel()
        job = scope.launch { runPairing() }
    }

    fun cancel() {
        job?.cancel()
        _state.value = State.Idle
    }

    private suspend fun runPairing() {
        while (true) {
            _state.value = State.Requesting
            val token = newToken()
            settings.savePairingToken(token)
            val issued = apiCall {
                api.createPairingCode(PairingCodeRequest(settings.installationId(), DeviceTokens.sha256Hex(token)))
            }
            val code = when (issued) {
                is ApiResult.Ok -> issued.data
                is ApiResult.Fail -> {
                    _state.value = State.Error(issued.message ?: "페어링 코드를 받지 못했습니다. (${issued.code})")
                    return
                }
                is ApiResult.NetworkError -> {
                    _state.value = State.Error("서버에 연결할 수 없습니다. 네트워크를 확인해 주세요.")
                    return
                }
            }
            _state.value = State.ShowingCode(code.code, code.expiresAt)
            if (pollUntilDone(code.pairingId)) return
        }
    }

    /** @return true면 페어링 완료 또는 중단, false면 만료되어 새 코드가 필요 */
    private suspend fun pollUntilDone(pairingId: String): Boolean {
        while (true) {
            delay(pollIntervalMs)
            when (val status = apiCall { api.pairingStatus(pairingId) }) {
                is ApiResult.Ok -> when (status.data.status) {
                    "PAIRED" -> {
                        settings.promotePairingToken()
                        runCatching { onPaired() }
                        val vehicle = status.data.vehicle
                        _state.value = State.Paired(vehicle?.regNo ?: "", vehicle?.modelName)
                        return true
                    }
                    "EXPIRED" -> return false
                }
                is ApiResult.Fail -> if (status.code == "PAIRING_NOT_FOUND") return false
                // 일시적인 네트워크 오류는 다음 조회에서 다시 시도한다.
                is ApiResult.NetworkError -> Unit
            }
        }
    }
}
