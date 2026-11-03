package com.autolog.app.trip

import android.util.Log
import com.autolog.app.data.TripRepository
import com.autolog.app.data.settings.OngoingTrip
import com.autolog.app.data.settings.PairedVehicleInfo
import com.autolog.app.domain.BusinessCategory
import com.autolog.app.domain.DetectedTrip
import com.autolog.app.domain.Endpoint
import com.autolog.app.domain.EndpointType
import com.autolog.app.domain.Gear
import com.autolog.app.domain.GeoPoint
import com.autolog.app.domain.Purpose
import com.autolog.app.domain.PurposeSource
import com.autolog.app.domain.VehicleSnapshot
import com.autolog.app.location.AreaNameSource
import com.autolog.app.location.LocationSource
import com.autolog.app.purpose.ArrivalChoice
import com.autolog.app.purpose.PlaceMatcher
import com.autolog.app.purpose.PurposeDecision
import com.autolog.app.purpose.PurposeEstimate
import com.autolog.app.purpose.PurposeEstimator
import com.autolog.app.purpose.VoiceAnswerParser
import com.autolog.app.vehicle.VehicleSource
import com.autolog.app.voice.ListenResult
import com.autolog.app.voice.VoiceIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
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

    /** 다음 시동 때 다시 물어본 마지막 운행(같은 운행을 두 번 묻지 않는다). */
    suspend fun lastReaskedTrip(): String?
    suspend fun saveLastReaskedTrip(uuid: String)
}

/**
 * 트립 기록 흐름 (architecture.md 2절 ②):
 * 기어·Odometer·위치 → [TripDetector] → 장소·용도 판정 → 미확인으로 먼저 저장 → 음성·팝업 확인 → 결과 반영 → 동기화 요청.
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
    private val taps: ArrivalTaps = ArrivalTaps(),
    private val manual: ManualTripControl = ManualTripControl(),
    private val notifications: ArrivalNotifications = ArrivalNotifications.None,
    private val areaNames: AreaNameSource = AreaNameSource.None,
    private val clock: Clock = Clock.systemUTC(),
    private val tickMs: Long = 2_000,
    private val answerTimeoutMs: Long = 10_000,
    private val undoWindowMs: Long = 5_000,
    private val reaskDelayMs: Long = 4_000,
    private val heartbeatMs: Long = 30_000,
) {
    /**
     * 화면 표시용 단계. 주행 중엔 플랫폼이 화면을 가리므로 정차 후 단계(PARKED·ASKING·CONFIRMING)를 보여주는 게 핵심이다.
     * CONFIRMING: 용도를 정했고 [Status.sendAtMillis]까지 '다시 선택'을 기다린 뒤 전송한다.
     */
    enum class Phase { IDLE, DRIVING, PARKED, ASKING, CONFIRMING }

    /**
     * 화면 표시용 상태. [prompt]는 도착 질문 중일 때 읽어 준 문장(팝업에도 같이 띄운다).
     * [suggested]는 추정 용도(미확인이면 null). 팝업에서 그 버튼을 '네, ○○'로 강조한다.
     * [sendAtMillis]는 CONFIRMING일 때 전송 예정 시각(epoch ms).
     * [manual]은 '운행 시작' 버튼으로 시작한 운행, [previousTrip]은 다음 시동 때 지난 운행을 묻는 중.
     * [asksChoice]는 [lastMessage]가 미확인 운행을 화면에서 골라 달라는 안내라는 뜻(다 고르면 화면이 안내를 내린다).
     */
    data class Status(
        val phase: Phase,
        val lastMessage: String?,
        val prompt: String? = null,
        val suggested: Purpose? = null,
        val sendAtMillis: Long? = null,
        val manual: Boolean = false,
        val previousTrip: Boolean = false,
        /** 지금 묻고 있는 운행(알림 버튼이 이 운행이면 팝업 버튼과 같은 경로로 넘긴다). */
        val tripUuid: String? = null,
        val asksChoice: Boolean = false,
    ) {
        val driving: Boolean get() = phase != Phase.IDLE

        companion object {
            val Idle = Status(Phase.IDLE, lastMessage = null)
        }
    }

    private val _status = MutableStateFlow(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val lock = Mutex()
    private lateinit var detector: TripDetector
    private lateinit var scope: CoroutineScope
    @Volatile private var lastGear: Gear? = null
    private var reaskJob: Job? = null
    /** 이번 운행 출발지의 동 이름(출발 때 내비에서 받음). */
    @Volatile private var originArea: String? = null

    fun start(scope: CoroutineScope) {
        this.scope = scope
        scope.launch {
            detector = TripDetector(parkConfirm, restoreState())
            _status.value = statusOf(detector.state)
            vehicle.currentOdometerKm()?.let { km -> lock.withLock { detector.onOdometer(km) } }
            location.lastKnown()?.let { point -> lock.withLock { detector.onLocation(point) } }
            launch { vehicle.odometerKm().collect { km -> lock.withLock { detector.onOdometer(km) } } }
            launch { location.updates().collect { point -> lock.withLock { detector.onLocation(point) } } }
            launch {
                vehicle.gear().collect { gear ->
                    lastGear = gear
                    // P 확인 시간이 이미 지난 정차(시동을 껐다 켠 경우 등)는 다시 출발해도 이어 붙이지 않고 먼저 확정한다.
                    handle(lock.withLock { detector.onTick(clock.instant()) })
                    handle(lock.withLock { detector.onGear(gear, clock.instant()) })
                }
            }
            launch {
                while (true) {
                    delay(tickMs)
                    handle(lock.withLock { detector.onTick(clock.instant()) })
                }
            }
            launch { handleManualCommands() }
            launch {
                delay(reaskDelayMs)
                launchReask()
            }
        }
    }

    private suspend fun handleManualCommands() {
        while (true) {
            val command = manual.receive()
            val now = clock.instant()
            val event = lock.withLock {
                when (command) {
                    ManualTripControl.Command.START -> detector.startManual(now)
                    ManualTripControl.Command.STOP -> detector.stopManual(now)
                }
            }
            if (command == ManualTripControl.Command.START && event == null && lock.withLock { detector.state } == TripDetector.State.Idle) {
                _status.value = Status(Phase.IDLE, NO_ODOMETER_MESSAGE)
            }
            handle(event)
        }
    }

    private suspend fun handle(event: TripDetector.Event?) {
        persistState()
        when (event) {
            null -> {
                // D → P 전환처럼 이벤트 없이 단계만 바뀌는 경우(정차 확인 시작·재출발). 질문·전송 대기 화면은 건드리지 않는다.
                val next = statusOf(lock.withLock { detector.state })
                _status.value.let {
                    if (it.phase != Phase.ASKING && it.phase != Phase.CONFIRMING && it.phase != next.phase) {
                        _status.value = it.copy(phase = next.phase, manual = next.manual)
                    }
                }
            }
            is TripDetector.Event.TripStarted -> {
                // 출발하면 지난 운행 질문은 그만둔다(주행 중에는 묻지 않음). 미확인으로 목록에 남는다.
                reaskJob?.cancel()
                _status.value = statusOf(lock.withLock { detector.state })
                originArea = null
                scope.launch {
                    originArea = areaNames.currentAreaName()
                    persistState()
                }
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
        // 등록 장소가 아니면 내비 주소의 동 이름을 붙인다(예: "신림동"). 못 받으면 '미등록 장소'.
        val origin = PlaceMatcher.match(trip.start.location, places).withArea(originArea)
        val matchedDest = PlaceMatcher.match(trip.end.location, places)
        val dest = if (matchedDest.needsArea()) matchedDest.withArea(areaNames.currentAreaName()) else matchedDest
        originArea = null
        val estimate = PurposeEstimator.estimate(origin, dest, trip.start.at)
        // 확정이 정차보다 한참 늦었다면(시동을 바로 끈 뒤 다음 시동 때 확정) 지금 '도착'이라고 묻지 않는다.
        val late = Duration.between(trip.end.at, clock.instant()) > parkConfirm.plus(LATE_SLACK)
        when (val result = repository.record(trip, origin, dest, estimate, vehicleInfo.id, settings.currentDriverNow())) {
            TripRepository.RecordResult.Dropped -> {
                Log.i(TAG, "0.5km 미만 트립, 기록하지 않음")
                _status.value = Status(Phase.IDLE, DROPPED_MESSAGE)
            }
            is TripRepository.RecordResult.Merged -> {
                _status.value = Status(Phase.IDLE, MERGED_MESSAGE)
                requestSync()
            }
            is TripRepository.RecordResult.Saved -> if (late) {
                repository.applyArrivalDecision(result.trip.clientUuid, PurposeDecision(Purpose.UNCONFIRMED, PurposeSource.AUTO, null))
                _status.value = Status(Phase.IDLE, PREVIOUS_SAVED_MESSAGE, asksChoice = true)
                requestSync()
                launchReask()
            } else {
                val prompt = PurposeEstimator.arrivalPrompt(dest, trip.distanceKm, estimate)
                val uuid = result.trip.clientUuid
                val decision = confirmArrival(uuid, prompt, estimate, dest) { repository.holdArrivalDecision(uuid, it) }
                repository.applyArrivalDecision(result.trip.clientUuid, decision)
                val message = confirmation(decision)
                _status.value = Status(Phase.IDLE, message, asksChoice = decision.purpose == Purpose.UNCONFIRMED)
                // 정한 용도는 대기 중에 이미 읽어 줬다. 미확인일 때만 여기서 안내한다.
                if (decision.purpose == Purpose.UNCONFIRMED) voice.speak(message)
                requestSync()
            }
        }
    }

    /**
     * 도착 확인 (car-app.md §3). 용도를 정하면 기기에 저장해 두고(업로드 제외) [undoWindowMs] 동안 '다시 선택'을 기다린다.
     * 다시 고르면 팝업·음성으로 다시 받는다(질문은 다시 읽지 않음). 미확인이면 기다리지 않는다.
     */
    private suspend fun confirmArrival(
        tripUuid: String,
        prompt: String,
        estimate: PurposeEstimate,
        dest: Endpoint,
        previousTrip: Boolean = false,
        hold: suspend (PurposeDecision) -> Unit,
    ): PurposeDecision {
        val suggested = estimate.purpose.takeIf { it != Purpose.UNCONFIRMED }
        var speakPrompt = true
        while (true) {
            taps.clear()
            _status.value = Status(
                Phase.ASKING, lastMessage = null, prompt = prompt, suggested = suggested, previousTrip = previousTrip, tripUuid = tripUuid,
            )
            notifications.ask(tripUuid, prompt)
            // 대답이 없으면 알림은 남겨 두어 나중에 알림 센터에서 고를 수 있게 한다.
            val decision = askArrival(prompt, estimate, dest, speakPrompt)
            if (decision.purpose == Purpose.UNCONFIRMED) return decision
            hold(decision)
            val message = confirmation(decision)
            notifications.confirmed(tripUuid, message)
            val sendAt = clock.instant().plusMillis(undoWindowMs).toEpochMilli()
            _status.value = Status(Phase.CONFIRMING, message, prompt, suggested, sendAt, previousTrip = previousTrip, tripUuid = tripUuid)
            if (!waitForReselect(message)) return decision
            speakPrompt = false
        }
    }

    /** 확정 안내를 읽으며 [undoWindowMs] 동안 '다시 선택'을 기다린다. 눌리면 true(안내도 끊는다). */
    private suspend fun waitForReselect(message: String): Boolean = coroutineScope {
        val speaking = launch { voice.speak(message) }
        val reselected = withTimeoutOrNull(undoWindowMs) { taps.awaitReselect() } != null
        if (reselected) speaking.cancel()
        reselected
    }

    /**
     * 음성 대답과 팝업 버튼을 동시에 받고 먼저 들어온 쪽으로 정한다 (car-app.md §3).
     * 버튼을 누르면 읽기·듣기를 멈춘다. 음성이 무응답·해석 불가로 끝나면 미확인이고 팝업도 닫힌다.
     */
    private suspend fun askArrival(prompt: String, estimate: PurposeEstimate, dest: Endpoint, speakPrompt: Boolean): PurposeDecision = coroutineScope {
        val byVoice = async {
            if (speakPrompt) voice.speak(prompt)
            VoiceAnswerParser.resolve(estimate, listenWithRetry())
        }
        val byTap = async { taps.receive().decide(estimate, dest) }
        select {
            byVoice.onAwait { decision -> byTap.cancel(); decision }
            byTap.onAwait { decision -> byVoice.cancel(); decision }
        }
    }

    /** 지난 운행 질문을 한 번만 띄운다(이미 진행 중이면 그대로 둔다). */
    private fun launchReask() {
        if (reaskJob?.isActive == true) return
        reaskJob = scope.launch { reaskLatest() }
    }

    /**
     * 다음 시동 때(정차 P, 운행 중 아님) 가장 최근 미확인 운행을 '지난 운행'으로 한 번 묻는다.
     * 시동을 바로 꺼 도착 질문을 못 했거나, 대답 없이 끝난 운행이 대상이다. 같은 운행은 다시 묻지 않는다.
     * 이미 서버에 올라간 운행일 수 있으므로 화면 수정과 같은 경로(수정 이력)로 반영한다.
     */
    private suspend fun reaskLatest() {
        if (lastGear != Gear.PARK || lock.withLock { detector.state } != TripDetector.State.Idle) return
        if (_status.value.phase == Phase.ASKING || _status.value.phase == Phase.CONFIRMING) return
        val trip = repository.latestUnconfirmed() ?: return
        if (Duration.between(trip.endedAt, clock.instant()) > REASK_WITHIN) return
        if (settings.lastReaskedTrip() == trip.clientUuid) return
        settings.saveLastReaskedTrip(trip.clientUuid)
        val origin = Endpoint(trip.originType, trip.originPlaceId, trip.originLabel)
        val dest = Endpoint(trip.destType, trip.destPlaceId, trip.destLabel)
        val estimate = PurposeEstimator.estimate(origin, dest, trip.startedAt)
        val prompt = "지난 운행, " + PurposeEstimator.arrivalPrompt(dest, trip.distanceKm, estimate)
        val decision = confirmArrival(trip.clientUuid, prompt, estimate, dest, previousTrip = true) { }
        if (decision.purpose != Purpose.UNCONFIRMED) {
            repository.changePurpose(trip.clientUuid, decision.purpose, decision.businessCategory, decision.source)
            requestSync()
        }
        val message = confirmation(decision)
        _status.value = Status(Phase.IDLE, message, asksChoice = decision.purpose == Purpose.UNCONFIRMED)
        if (decision.purpose == Purpose.UNCONFIRMED) voice.speak(message)
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

    private fun statusOf(state: TripDetector.State): Status = when (state) {
        TripDetector.State.Idle -> Status(Phase.IDLE, lastMessage = null)
        is TripDetector.State.Driving -> Status(Phase.DRIVING, lastMessage = null, manual = state.manual)
        is TripDetector.State.Parked -> Status(Phase.PARKED, lastMessage = null)
    }

    private fun Endpoint.needsArea() = type == EndpointType.UNREGISTERED && label == null

    private fun Endpoint.withArea(name: String?) = if (needsArea() && name != null) copy(label = name) else this

    private suspend fun persistState() {
        val ongoing = lock.withLock { ongoingOf(detector.state) }?.copy(originArea = originArea)
        val saved = settings.ongoingTrip()
        if (ongoing == saved) return
        // 수동 운행의 마지막 확인 값만 바뀐 경우는 [heartbeatMs]마다 한 번만 쓴다.
        if (ongoing != null && saved != null && ongoing.withoutLastSeen() == saved.withoutLastSeen() &&
            (ongoing.lastSeenAtMillis ?: 0) - (saved.lastSeenAtMillis ?: 0) < heartbeatMs
        ) {
            return
        }
        settings.saveOngoingTrip(ongoing)
    }

    private fun OngoingTrip.withoutLastSeen() = copy(lastSeenAtMillis = null, lastSeenOdoKm = null, lastSeenLat = null, lastSeenLng = null)

    private fun ongoingOf(state: TripDetector.State): OngoingTrip? = when (state) {
        TripDetector.State.Idle -> null
        is TripDetector.State.Driving -> {
            val trip = state.start.toOngoing().copy(manual = state.manual)
            if (!state.manual) {
                trip
            } else {
                trip.copy(
                    lastSeenAtMillis = clock.millis(),
                    lastSeenOdoKm = detector.lastOdometerKm,
                    lastSeenLat = detector.lastLocation?.lat,
                    lastSeenLng = detector.lastLocation?.lng,
                )
            }
        }
        is TripDetector.State.Parked -> state.start.toOngoing().copy(
            parkedAtMillis = state.end.at.toEpochMilli(),
            odoParkedKm = state.end.odometerKm,
            parkedLat = state.end.location?.lat,
            parkedLng = state.end.location?.lng,
        )
    }

    private suspend fun restoreState(): TripDetector.State {
        val saved = settings.ongoingTrip() ?: return TripDetector.State.Idle
        originArea = saved.originArea
        val start = VehicleSnapshot(Instant.ofEpochMilli(saved.startedAtMillis), saved.odoStartKm, point(saved.lat, saved.lng))
        val parkedAt = saved.parkedAtMillis
        if (parkedAt == null) {
            val lastSeen = saved.lastSeenAtMillis?.let(Instant::ofEpochMilli)
            // 수동 운행 중 시동이 꺼졌다 켜졌으면 마지막으로 확인한 시각·거리·위치에서 정차한 것으로 본다(곧 늦은 확정 → 지난 운행 질문).
            if (saved.manual && lastSeen != null && Duration.between(lastSeen, clock.instant()) > parkConfirm.plus(LATE_SLACK)) {
                val end = VehicleSnapshot(lastSeen, saved.lastSeenOdoKm ?: saved.odoStartKm, point(saved.lastSeenLat, saved.lastSeenLng))
                return TripDetector.State.Parked(start, end)
            }
            return TripDetector.State.Driving(start, saved.manual)
        }
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
        const val NO_ODOMETER_MESSAGE = "계기판 거리를 아직 받지 못해 운행을 시작하지 못했어요."
        const val PREVIOUS_SAVED_MESSAGE = "지난 운행을 기록했어요. 용도를 골라 주세요."
        /** 정차 확인 시간보다 이만큼 더 늦게 확정되면 시동을 껐다 켠 것으로 본다. */
        private val LATE_SLACK: Duration = Duration.ofSeconds(60)
        /** 이보다 오래된 미확인 운행은 다음 시동 때 다시 묻지 않는다(목록에서 정리). */
        private val REASK_WITHIN: Duration = Duration.ofHours(24)

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
