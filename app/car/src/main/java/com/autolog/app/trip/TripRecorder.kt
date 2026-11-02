package com.autolog.app.trip

import android.util.Log
import com.autolog.app.data.TripRepository
import com.autolog.app.data.settings.OngoingTrip
import com.autolog.app.data.settings.PairedVehicleInfo
import com.autolog.app.domain.BusinessCategory
import com.autolog.app.domain.DetectedTrip
import com.autolog.app.domain.GeoPoint
import com.autolog.app.domain.Purpose
import com.autolog.app.domain.VehicleSnapshot
import com.autolog.app.location.LocationSource
import com.autolog.app.purpose.PlaceMatcher
import com.autolog.app.purpose.PurposeDecision
import com.autolog.app.purpose.PurposeEstimator
import com.autolog.app.purpose.VoiceAnswerParser
import com.autolog.app.vehicle.VehicleSource
import com.autolog.app.voice.ListenResult
import com.autolog.app.voice.VoiceIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** 트립 기록에 필요한 설정값. [com.autolog.app.data.settings.SettingsStore]가 구현한다. */
interface RecorderSettings {
    suspend fun vehicleNow(): PairedVehicleInfo?
    suspend fun currentDriverNow(): Long?
    suspend fun ongoingTrip(): OngoingTrip?
    suspend fun saveOngoingTrip(trip: OngoingTrip?)
}

/**
 * 트립 기록 흐름 (architecture.md 2절 ②):
 * 기어·Odometer·위치 → [TripDetector] → 장소·용도 판정 → 미확인으로 먼저 저장 → 음성 확인 → 결과 반영 → 동기화 요청.
 * Foreground Service가 [start]를 호출한다.
 */
class TripRecorder(
    private val vehicle: VehicleSource,
    private val location: LocationSource,
    private val voice: VoiceIO,
    private val repository: TripRepository,
    private val settings: RecorderSettings,
    private val requestSync: () -> Unit,
    private val parkConfirm: Duration,
    private val clock: Clock = Clock.systemUTC(),
    private val tickMs: Long = 2_000,
    private val answerTimeoutMs: Long = 10_000,
) {
    /** 화면 표시용 단계. 주행 중엔 플랫폼이 화면을 가리므로 정차 후 단계(PARKED·ASKING)를 보여주는 게 핵심이다. */
    enum class Phase { IDLE, DRIVING, PARKED, ASKING }

    /** 화면 표시용 상태. [prompt]는 도착 질문 중일 때 읽어 준 문장(화면에도 같이 띄운다). */
    data class Status(val phase: Phase, val lastMessage: String?, val prompt: String? = null) {
        val driving: Boolean get() = phase != Phase.IDLE

        companion object {
            val Idle = Status(Phase.IDLE, lastMessage = null)
        }
    }

    private val _status = MutableStateFlow(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val lock = Mutex()
    private lateinit var detector: TripDetector

    fun start(scope: CoroutineScope) {
        scope.launch {
            detector = TripDetector(parkConfirm, restoreState())
            _status.value = _status.value.copy(phase = phaseOf(detector.state))
            vehicle.currentOdometerKm()?.let { km -> lock.withLock { detector.onOdometer(km) } }
            location.lastKnown()?.let { point -> lock.withLock { detector.onLocation(point) } }
            launch { vehicle.odometerKm().collect { km -> lock.withLock { detector.onOdometer(km) } } }
            launch { location.updates().collect { point -> lock.withLock { detector.onLocation(point) } } }
            launch { vehicle.gear().collect { gear -> handle(lock.withLock { detector.onGear(gear, clock.instant()) }) } }
            launch {
                while (true) {
                    delay(tickMs)
                    handle(lock.withLock { detector.onTick(clock.instant()) })
                }
            }
        }
    }

    private suspend fun handle(event: TripDetector.Event?) {
        persistState()
        when (event) {
            null -> {
                // D → P 전환처럼 이벤트 없이 단계만 바뀌는 경우(정차 확인 시작·재출발).
                val phase = phaseOf(lock.withLock { detector.state })
                _status.value.let { if (it.phase != Phase.ASKING && it.phase != phase) _status.value = it.copy(phase = phase) }
            }
            is TripDetector.Event.TripStarted -> {
                _status.value = Status(Phase.DRIVING, lastMessage = null)
                // 주행 중에 설정(관리자가 추가한 장소·직원 변경)을 받아 두어 도착 판정에 쓴다.
                requestSync()
            }
            is TripDetector.Event.TripCompleted -> {
                complete(event.trip)
            }
        }
    }

    private suspend fun complete(trip: DetectedTrip) {
        val vehicleInfo = settings.vehicleNow()
        if (vehicleInfo == null) {
            Log.w(TAG, "페어링 전이라 트립을 기록하지 않음")
            _status.value = Status(Phase.IDLE, NOT_PAIRED_MESSAGE)
            return
        }
        val places = repository.places()
        val origin = PlaceMatcher.match(trip.start.location, places)
        val dest = PlaceMatcher.match(trip.end.location, places)
        val estimate = PurposeEstimator.estimate(origin, dest, trip.start.at)
        when (val result = repository.record(trip, origin, dest, estimate, vehicleInfo.id, settings.currentDriverNow())) {
            TripRepository.RecordResult.Dropped -> {
                Log.i(TAG, "0.5km 미만 트립, 기록하지 않음")
                _status.value = Status(Phase.IDLE, DROPPED_MESSAGE)
            }
            is TripRepository.RecordResult.Merged -> {
                _status.value = Status(Phase.IDLE, MERGED_MESSAGE)
                requestSync()
            }
            is TripRepository.RecordResult.Saved -> {
                val prompt = PurposeEstimator.arrivalPrompt(dest, trip.distanceKm, estimate)
                _status.value = Status(Phase.ASKING, lastMessage = null, prompt = prompt)
                voice.speak(prompt)
                val decision = VoiceAnswerParser.resolve(estimate, listenWithRetry())
                repository.applyVoiceDecision(result.trip.clientUuid, decision)
                val message = confirmation(decision)
                _status.value = Status(Phase.IDLE, message)
                voice.speak(message)
                requestSync()
            }
        }
    }

    /** STT 오류면 한 번만 다시 묻는다. 무응답은 다시 묻지 않는다 (car-app.md §3). */
    private suspend fun listenWithRetry(): String? {
        var heard = voice.listen(answerTimeoutMs)
        if (heard == ListenResult.EngineError) {
            _status.value = _status.value.copy(prompt = RETRY_PROMPT)
            voice.speak(RETRY_PROMPT)
            heard = voice.listen(answerTimeoutMs)
        }
        return (heard as? ListenResult.Heard)?.text
    }

    private fun phaseOf(state: TripDetector.State): Phase = when (state) {
        TripDetector.State.Idle -> Phase.IDLE
        is TripDetector.State.Driving -> Phase.DRIVING
        is TripDetector.State.Parked -> Phase.PARKED
    }

    private suspend fun persistState() {
        val ongoing = when (val state = lock.withLock { detector.state }) {
            TripDetector.State.Idle -> null
            is TripDetector.State.Driving -> state.start.toOngoing()
            is TripDetector.State.Parked -> state.start.toOngoing().copy(
                parkedAtMillis = state.end.at.toEpochMilli(),
                odoParkedKm = state.end.odometerKm,
                parkedLat = state.end.location?.lat,
                parkedLng = state.end.location?.lng,
            )
        }
        if (ongoing != settings.ongoingTrip()) settings.saveOngoingTrip(ongoing)
    }

    private suspend fun restoreState(): TripDetector.State {
        val saved = settings.ongoingTrip() ?: return TripDetector.State.Idle
        val start = VehicleSnapshot(Instant.ofEpochMilli(saved.startedAtMillis), saved.odoStartKm, point(saved.lat, saved.lng))
        val parkedAt = saved.parkedAtMillis ?: return TripDetector.State.Driving(start)
        val end = VehicleSnapshot(Instant.ofEpochMilli(parkedAt), saved.odoParkedKm ?: saved.odoStartKm, point(saved.parkedLat, saved.parkedLng))
        return TripDetector.State.Parked(start, end)
    }

    private fun VehicleSnapshot.toOngoing() = OngoingTrip(at.toEpochMilli(), odometerKm, location?.lat, location?.lng)

    private fun point(lat: Double?, lng: Double?) = if (lat != null && lng != null) GeoPoint(lat, lng) else null

    companion object {
        private const val TAG = "AutoLogRecorder"
        const val RETRY_PROMPT = "잘 듣지 못했어요. 한 번 더 말씀해 주세요."
        const val DROPPED_MESSAGE = "0.5km 미만 운행이라 기록하지 않았어요."
        const val MERGED_MESSAGE = "잠깐 멈췄다 다시 출발해 이전 운행에 이어서 기록했어요."
        const val NOT_PAIRED_MESSAGE = "차량 등록 전이라 운행을 기록하지 않았어요."

        fun confirmation(decision: PurposeDecision): String = when (decision.purpose) {
            Purpose.COMMUTE -> "출퇴근으로 기록했어요."
            Purpose.BUSINESS -> when (decision.businessCategory) {
                BusinessCategory.VISIT -> "거래처 방문 업무로 기록했어요."
                BusinessCategory.MEETING -> "회의 업무로 기록했어요."
                BusinessCategory.PROMOTION -> "판촉 업무로 기록했어요."
                BusinessCategory.TRAINING -> "교육 업무로 기록했어요."
                else -> "업무로 기록했어요."
            }
            Purpose.PERSONAL -> "개인 운행으로 기록했어요."
            Purpose.UNCONFIRMED -> "미확인으로 남겨 둘게요. 정차 중에 화면에서 골라 주세요."
        }
    }
}
