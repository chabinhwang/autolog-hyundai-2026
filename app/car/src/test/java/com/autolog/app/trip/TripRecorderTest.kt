package com.autolog.app.trip

import com.autolog.app.data.TripRepository
import com.autolog.app.data.local.PlaceEntity
import com.autolog.app.data.settings.OngoingTrip
import com.autolog.app.data.settings.PairedVehicleInfo
import com.autolog.app.domain.EndpointType
import com.autolog.app.domain.Gear
import com.autolog.app.domain.GeoPoint
import com.autolog.app.domain.Purpose
import com.autolog.app.domain.PurposeSource
import com.autolog.app.location.LocationSource
import com.autolog.app.purpose.ArrivalChoice
import com.autolog.app.sync.FakeEditDao
import com.autolog.app.sync.FakePlaceDao
import com.autolog.app.sync.FakeTripDao
import com.autolog.app.vehicle.VehicleSource
import com.autolog.app.voice.ListenResult
import com.autolog.app.voice.VoiceIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class TripRecorderTest {
    /** 2026-11-03(화) 08:00 KST에서 시작해 실제 시간만큼 흐르는 시계. */
    private class RunningClock(private val base: Instant) : Clock() {
        private val startNanos = System.nanoTime()
        override fun instant(): Instant = base.plusNanos(System.nanoTime() - startNanos)
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
    }

    private class FakeVehicle : VehicleSource {
        val gears = MutableSharedFlow<Gear>(replay = 1)
        val odometer = MutableSharedFlow<Double>(replay = 1)
        var current: Double? = 100.0
        override fun gear(): Flow<Gear> = gears
        override fun odometerKm(): Flow<Double> = odometer
        override suspend fun currentOdometerKm() = current
        override fun release() = Unit
    }

    private class FakeLocation : LocationSource {
        val points = MutableSharedFlow<GeoPoint>(replay = 1)
        var last: GeoPoint? = HOME
        override fun updates(): Flow<GeoPoint> = points
        override suspend fun lastKnown() = last
        override fun release() = Unit
    }

    private class FakeVoice(vararg results: ListenResult) : VoiceIO {
        constructor(answer: String?) : this(answer?.let { ListenResult.Heard(it) } ?: ListenResult.NoAnswer)

        private val queue = ArrayDeque(results.toList())
        val spoken = mutableListOf<String>()
        var listens = 0
        /** 실제 STT처럼 듣는 데 시간이 걸리게 한다(질문 중 상태를 관찰하는 테스트용). */
        var listenDelayMs = 0L
        override suspend fun speak(text: String): Boolean {
            spoken += text
            return true
        }
        override suspend fun listen(timeoutMs: Long): ListenResult {
            listens++
            delay(listenDelayMs)
            return queue.removeFirstOrNull() ?: ListenResult.NoAnswer
        }
        override fun release() = Unit
    }

    private class FakeSettings(var vehicle: PairedVehicleInfo?) : RecorderSettings {
        var ongoing: OngoingTrip? = null
        var reasked: String? = null
        override suspend fun lastReaskedTrip() = reasked
        override suspend fun saveLastReaskedTrip(uuid: String) {
            reasked = uuid
        }
        override suspend fun vehicleNow() = vehicle
        override suspend fun currentDriverNow() = 11L
        override suspend fun ongoingTrip() = ongoing
        override suspend fun saveOngoingTrip(trip: OngoingTrip?) {
            ongoing = trip
        }
    }

    companion object {
        val HOME = GeoPoint(37.40, 127.10)
        val COMPANY = GeoPoint(37.497942, 127.027621)
    }

    private val scope = CoroutineScope(Dispatchers.Default)
    private val trips = FakeTripDao()
    private val places = FakePlaceDao()
    private val vehicle = FakeVehicle()
    private val location = FakeLocation()
    private var syncRequests = 0

    @After
    fun tearDown() = scope.cancel()

    private fun recorder(
        voice: VoiceIO,
        settings: RecorderSettings,
        taps: ArrivalTaps = ArrivalTaps(),
        undoWindowMs: Long = 150,
        manual: ManualTripControl = ManualTripControl(),
        reaskDelayMs: Long = 60_000,
        heartbeatMs: Long = 30_000,
        notifications: ArrivalNotifications = ArrivalNotifications.None,
        areaNames: com.autolog.app.location.AreaNameSource = com.autolog.app.location.AreaNameSource.None,
    ): TripRecorder {
        runBlocking {
            places.upsert(PlaceEntity(0, 1, "본사", EndpointType.COMPANY, COMPANY.lat, COMPANY.lng, 200, false))
            places.upsert(PlaceEntity(0, null, "집", EndpointType.HOME, HOME.lat, HOME.lng, 200, false))
        }
        val clock = RunningClock(Instant.parse("2026-11-02T23:00:00Z"))
        return TripRecorder(
            vehicle = vehicle, location = location, voice = voice,
            repository = TripRepository(trips, FakeEditDao(), places, clock),
            settings = settings, requestSync = { syncRequests++ },
            parkConfirm = Duration.ofMillis(300), taps = taps, clock = clock, tickMs = 50, answerTimeoutMs = 100,
            undoWindowMs = undoWindowMs, manual = manual, reaskDelayMs = reaskDelayMs, heartbeatMs = heartbeatMs,
            notifications = notifications, areaNames = areaNames,
        )
    }

    private suspend fun drive(fromKm: Double, toKm: Double, to: GeoPoint) {
        vehicle.odometer.emit(fromKm)
        vehicle.gears.emit(Gear.PARK)
        delay(100)
        vehicle.gears.emit(Gear.DRIVE)
        delay(100)
        vehicle.odometer.emit(toKm)
        location.points.emit(to)
        delay(100)
        vehicle.gears.emit(Gear.PARK)
    }

    @Test
    fun `집에서 회사로 가서 응이라고 하면 출근으로 확정하고 동기화를 요청한다`() = runBlocking {
        val voice = FakeVoice("응")
        recorder(voice, FakeSettings(PairedVehicleInfo(3, "12가3456", null, 11))).start(scope)
        delay(200)

        drive(100.0, 112.4, COMPANY)

        val saved = withTimeout(5_000) { trips.rows.first { rows -> rows.values.any { !it.answerPending } } }.values.single()
        assertEquals(Purpose.COMMUTE, saved.purpose)
        assertEquals(PurposeSource.VOICE, saved.purposeSource)
        assertEquals(Purpose.COMMUTE, saved.autoPurpose)
        assertEquals(EndpointType.HOME, saved.originType)
        assertEquals(EndpointType.COMPANY, saved.destType)
        assertEquals(1L, saved.destPlaceId)
        assertEquals(12.4, saved.distanceKm, 1e-9)
        assertEquals(3L, saved.vehicleId)
        assertEquals(11L, saved.driverId)
        withTimeout(2_000) { while (voice.spoken.size < 2) delay(20) }
        assertEquals("본사 도착, 12.4킬로미터. 출근으로 기록할까요?", voice.spoken[0])
        assertEquals("출퇴근으로 기록했어요.", voice.spoken[1])
        // 주행 시작 때 한 번(설정 미리 받기), 저장 후 한 번
        assertTrue("syncRequests=$syncRequests", syncRequests >= 2)
    }

    @Test
    fun `도착 질문 중에는 질문 문장을 화면 상태로 내보내고 끝나면 확정 안내를 남긴다`() = runBlocking {
        val voice = FakeVoice("응").apply { listenDelayMs = 300 }
        val recorder = recorder(voice, FakeSettings(PairedVehicleInfo(3, "12가3456", null, 11)))
        recorder.start(scope)
        delay(200)

        drive(100.0, 112.4, COMPANY)

        val asking = withTimeout(5_000) { recorder.status.first { it.phase == TripRecorder.Phase.ASKING } }
        assertEquals("본사 도착, 12.4킬로미터. 출근으로 기록할까요?", asking.prompt)
        val done = withTimeout(5_000) { recorder.status.first { it.phase == TripRecorder.Phase.IDLE && it.lastMessage != null } }
        assertEquals("출퇴근으로 기록했어요.", done.lastMessage)
    }

    @Test
    fun `P에 세우면 정차 확인 단계가 되고 0점5km 미만이면 기록하지 않았다고 알린다`() = runBlocking {
        val recorder = recorder(FakeVoice("응"), FakeSettings(PairedVehicleInfo(3, "12가3456", null, 11)))
        recorder.start(scope)
        delay(200)

        drive(100.0, 100.2, COMPANY)

        withTimeout(5_000) { recorder.status.first { it.phase == TripRecorder.Phase.PARKED } }
        val done = withTimeout(5_000) { recorder.status.first { it.lastMessage != null } }
        assertEquals(TripRecorder.Phase.IDLE, done.phase)
        assertEquals(TripRecorder.DROPPED_MESSAGE, done.lastMessage)
        assertTrue(trips.rows.value.isEmpty())
    }

    @Test
    fun `대답이 없으면 미확인으로 남긴다`() = runBlocking {
        val voice = FakeVoice(null)
        recorder(voice, FakeSettings(PairedVehicleInfo(3, "12가3456", null, 11))).start(scope)
        delay(200)

        drive(100.0, 105.0, GeoPoint(37.6, 127.2))

        val saved = withTimeout(5_000) { trips.rows.first { rows -> rows.values.any { !it.answerPending } } }.values.single()
        assertEquals(Purpose.UNCONFIRMED, saved.purpose)
        assertEquals(PurposeSource.AUTO, saved.purposeSource)
        assertEquals(EndpointType.UNREGISTERED, saved.destType)
        withTimeout(2_000) { while (voice.spoken.size < 2) delay(20) }
        assertEquals("미확인으로 남겨 둘게요. 정차 중에 화면에서 골라 주세요.", voice.spoken[1])
        assertEquals(1, voice.listens)
    }

    @Test
    fun `STT 오류가 나면 한 번 다시 묻고 그 응답으로 확정한다`() = runBlocking {
        val voice = FakeVoice(ListenResult.EngineError, ListenResult.Heard("응"))
        recorder(voice, FakeSettings(PairedVehicleInfo(3, "12가3456", null, 11))).start(scope)
        delay(200)

        drive(100.0, 112.4, COMPANY)

        val saved = withTimeout(5_000) { trips.rows.first { rows -> rows.values.any { !it.answerPending } } }.values.single()
        assertEquals(Purpose.COMMUTE, saved.purpose)
        assertEquals(PurposeSource.VOICE, saved.purposeSource)
        withTimeout(2_000) { while (voice.spoken.size < 3) delay(20) }
        assertEquals("잘 듣지 못했어요. 한 번 더 말씀해 주세요.", voice.spoken[1])
        assertEquals(2, voice.listens)
    }

    @Test
    fun `다시 물어도 STT 오류면 미확인으로 남기고 더 묻지 않는다`() = runBlocking {
        val voice = FakeVoice(ListenResult.EngineError, ListenResult.EngineError, ListenResult.Heard("응"))
        recorder(voice, FakeSettings(PairedVehicleInfo(3, "12가3456", null, 11))).start(scope)
        delay(200)

        drive(100.0, 112.4, COMPANY)

        val saved = withTimeout(5_000) { trips.rows.first { rows -> rows.values.any { !it.answerPending } } }.values.single()
        assertEquals(Purpose.UNCONFIRMED, saved.purpose)
        withTimeout(2_000) { while (voice.spoken.size < 3) delay(20) }
        assertEquals("미확인으로 남겨 둘게요. 정차 중에 화면에서 골라 주세요.", voice.spoken[2])
        assertEquals(2, voice.listens)
    }

    @Test
    fun `질문 중 팝업에서 업무를 누르면 음성 대답을 기다리지 않고 화면 선택으로 확정한다`() = runBlocking {
        val voice = FakeVoice("응").apply { listenDelayMs = 3_000 }
        val taps = ArrivalTaps()
        val recorder = recorder(voice, FakeSettings(PairedVehicleInfo(3, "12가3456", null, 11)), taps)
        recorder.start(scope)
        delay(200)

        drive(100.0, 112.4, COMPANY)

        val asking = withTimeout(5_000) { recorder.status.first { it.phase == TripRecorder.Phase.ASKING } }
        assertEquals(Purpose.COMMUTE, asking.suggested)
        taps.send(ArrivalChoice.BUSINESS)

        val saved = withTimeout(1_500) { trips.rows.first { rows -> rows.values.any { !it.answerPending } } }.values.single()
        assertEquals(Purpose.BUSINESS, saved.purpose)
        assertEquals(PurposeSource.MANUAL, saved.purposeSource)
        withTimeout(1_500) { while (voice.spoken.size < 2) delay(20) }
        assertEquals("업무로 기록했어요.", voice.spoken[1])
        val done = withTimeout(1_500) { recorder.status.first { it.phase == TripRecorder.Phase.IDLE } }
        assertEquals("업무로 기록했어요.", done.lastMessage)
    }

    @Test
    fun `팝업에서 나중에를 누르면 미확인으로 남긴다`() = runBlocking {
        val voice = FakeVoice("응").apply { listenDelayMs = 3_000 }
        val taps = ArrivalTaps()
        val recorder = recorder(voice, FakeSettings(PairedVehicleInfo(3, "12가3456", null, 11)), taps)
        recorder.start(scope)
        delay(200)

        drive(100.0, 112.4, COMPANY)

        withTimeout(5_000) { recorder.status.first { it.phase == TripRecorder.Phase.ASKING } }
        taps.send(ArrivalChoice.LATER)

        val saved = withTimeout(1_500) { trips.rows.first { rows -> rows.values.any { !it.answerPending } } }.values.single()
        assertEquals(Purpose.UNCONFIRMED, saved.purpose)
        assertEquals(PurposeSource.AUTO, saved.purposeSource)
    }

    @Test
    fun `질문 전에 눌러 둔 버튼은 다음 질문에 쓰지 않는다`() = runBlocking {
        val voice = FakeVoice("응")
        val taps = ArrivalTaps().apply { send(ArrivalChoice.PERSONAL) }
        recorder(voice, FakeSettings(PairedVehicleInfo(3, "12가3456", null, 11)), taps).start(scope)
        delay(200)

        drive(100.0, 112.4, COMPANY)

        val saved = withTimeout(5_000) { trips.rows.first { rows -> rows.values.any { !it.answerPending } } }.values.single()
        assertEquals(Purpose.COMMUTE, saved.purpose)
        assertEquals(PurposeSource.VOICE, saved.purposeSource)
    }

    @Test
    fun `용도를 정하면 전송 대기 동안 업로드에서 빼 두었다가 대기가 끝나면 전송한다`() = runBlocking {
        val voice = FakeVoice("응")
        val recorder = recorder(voice, FakeSettings(PairedVehicleInfo(3, "12가3456", null, 11)), undoWindowMs = 600)
        recorder.start(scope)
        delay(200)

        drive(100.0, 112.4, COMPANY)

        val confirming = withTimeout(5_000) { recorder.status.first { it.phase == TripRecorder.Phase.CONFIRMING } }
        assertEquals("출퇴근으로 기록했어요.", confirming.lastMessage)
        assertTrue(confirming.sendAtMillis != null)
        val held = trips.rows.value.values.single()
        assertEquals(Purpose.COMMUTE, held.purpose)
        assertTrue("전송 대기 중에는 업로드 대상이 아니다", held.answerPending)
        val syncBefore = syncRequests

        val released = withTimeout(3_000) { trips.rows.first { rows -> rows.values.any { !it.answerPending } } }.values.single()
        assertEquals(Purpose.COMMUTE, released.purpose)
        withTimeout(1_000) { while (syncRequests == syncBefore) delay(20) }
    }

    @Test
    fun `전송 대기 중 다시 선택을 누르면 다시 골라 그 값으로 전송한다`() = runBlocking {
        val voice = FakeVoice("응").apply { listenDelayMs = 300 }
        val taps = ArrivalTaps()
        val recorder = recorder(voice, FakeSettings(PairedVehicleInfo(3, "12가3456", null, 11)), taps, undoWindowMs = 2_000)
        recorder.start(scope)
        delay(200)

        drive(100.0, 112.4, COMPANY)

        withTimeout(5_000) { recorder.status.first { it.phase == TripRecorder.Phase.CONFIRMING } }
        taps.reselect()
        val again = withTimeout(1_000) { recorder.status.first { it.phase == TripRecorder.Phase.ASKING } }
        assertEquals("본사 도착, 12.4킬로미터. 출근으로 기록할까요?", again.prompt)
        taps.send(ArrivalChoice.PERSONAL)
        val confirming = withTimeout(1_000) { recorder.status.first { it.phase == TripRecorder.Phase.CONFIRMING } }
        assertEquals("개인 운행으로 기록했어요.", confirming.lastMessage)

        val saved = withTimeout(4_000) { trips.rows.first { rows -> rows.values.any { !it.answerPending } } }.values.single()
        assertEquals(Purpose.PERSONAL, saved.purpose)
        assertEquals(PurposeSource.MANUAL, saved.purposeSource)
        assertEquals("다시 고를 때 질문을 다시 읽지 않는다", 1, voice.spoken.count { it.startsWith("본사 도착") })
    }

    /** 50분 전 회사에 P로 세우고 시동을 끈 상태(정차 확인 전)로 저장돼 있던 운행. */
    private fun parkedLongAgo() = FakeSettings(PairedVehicleInfo(3, "12가3456", null, 11)).apply {
        ongoing = OngoingTrip(
            Instant.parse("2026-11-02T22:00:00Z").toEpochMilli(), 90.0, HOME.lat, HOME.lng,
            parkedAtMillis = Instant.parse("2026-11-02T22:10:00Z").toEpochMilli(), odoParkedKm = 110.0,
            parkedLat = COMPANY.lat, parkedLng = COMPANY.lng,
        )
    }

    @Test
    fun `오래 서 있다가 다시 출발하면 이어 붙이지 않고 지난 운행을 먼저 기록한다`() = runBlocking {
        val voice = FakeVoice("응")
        val recorder = recorder(voice, parkedLongAgo())
        vehicle.current = 110.0
        recorder.start(scope)
        vehicle.odometer.emit(110.0)
        vehicle.gears.emit(Gear.DRIVE)

        val saved = withTimeout(5_000) { trips.rows.first { rows -> rows.values.any { !it.answerPending } } }.values.single()
        assertEquals(90.0, saved.odoStartKm, 0.0)
        assertEquals(110.0, saved.odoEndKm, 0.0)
        assertEquals(Purpose.UNCONFIRMED, saved.purpose)
        withTimeout(2_000) { recorder.status.first { it.phase == TripRecorder.Phase.DRIVING } }
        assertTrue("다음 운행 중에는 지난 운행을 묻지 않는다", voice.spoken.none { it.contains("도착") })
    }

    @Test
    fun `시동을 바로 끈 운행은 다음 시동 때 P에서 지난 운행으로 한 번 묻는다`() = runBlocking {
        val voice = FakeVoice("응").apply { listenDelayMs = 300 }
        val settings = parkedLongAgo()
        vehicle.current = 110.0
        vehicle.gears.emit(Gear.PARK)
        val recorder = recorder(voice, settings)
        recorder.start(scope)

        val asking = withTimeout(5_000) { recorder.status.first { it.phase == TripRecorder.Phase.ASKING } }
        assertTrue(asking.previousTrip)
        assertEquals("지난 운행, 본사 도착, 20킬로미터. 출근으로 기록할까요?", asking.prompt)

        val saved = withTimeout(5_000) { trips.rows.first { rows -> rows.values.any { it.purpose != Purpose.UNCONFIRMED } } }.values.single()
        assertEquals(Purpose.COMMUTE, saved.purpose)
        assertEquals(PurposeSource.VOICE, saved.purposeSource)
        assertEquals(saved.clientUuid, settings.reasked)
    }

    @Test
    fun `지난 운행을 묻는 중에 출발하면 묻기를 멈추고 미확인으로 남긴다`() = runBlocking {
        val voice = FakeVoice("응").apply { listenDelayMs = 3_000 }
        vehicle.current = 110.0
        vehicle.gears.emit(Gear.PARK)
        val recorder = recorder(voice, parkedLongAgo())
        recorder.start(scope)

        withTimeout(5_000) { recorder.status.first { it.phase == TripRecorder.Phase.ASKING && it.previousTrip } }
        vehicle.gears.emit(Gear.DRIVE)

        withTimeout(2_000) { recorder.status.first { it.phase == TripRecorder.Phase.DRIVING } }
        delay(500)
        assertEquals(Purpose.UNCONFIRMED, trips.rows.value.values.single().purpose)
    }

    @Test
    fun `운행 시작 버튼으로 시작하면 P를 무시하고 종료 버튼을 누를 때 묻는다`() = runBlocking {
        val voice = FakeVoice("응")
        val manual = ManualTripControl()
        vehicle.gears.emit(Gear.PARK)
        val recorder = recorder(voice, FakeSettings(PairedVehicleInfo(3, "12가3456", null, 11)), manual = manual)
        recorder.start(scope)
        delay(200)

        manual.start()
        val driving = withTimeout(2_000) { recorder.status.first { it.phase == TripRecorder.Phase.DRIVING } }
        assertTrue(driving.manual)
        vehicle.odometer.emit(105.5)
        location.points.emit(COMPANY)
        vehicle.gears.emit(Gear.PARK)
        delay(600)
        assertEquals(TripRecorder.Phase.DRIVING, recorder.status.value.phase)

        manual.stop()
        val saved = withTimeout(5_000) { trips.rows.first { rows -> rows.values.any { !it.answerPending } } }.values.single()
        assertEquals(5.5, saved.distanceKm, 1e-9)
        assertEquals(Purpose.COMMUTE, saved.purpose)
        assertEquals(PurposeSource.VOICE, saved.purposeSource)
    }

    @Test
    fun `수동 운행 중에는 마지막 계기판 거리와 위치를 주기적으로 저장한다`() = runBlocking {
        val manual = ManualTripControl()
        val settings = FakeSettings(PairedVehicleInfo(3, "12가3456", null, 11))
        recorder(FakeVoice("응"), settings, manual = manual, heartbeatMs = 100).start(scope)
        delay(200)

        manual.start()
        withTimeout(2_000) { while (settings.ongoing?.manual != true) delay(20) }
        vehicle.odometer.emit(104.0)
        location.points.emit(COMPANY)

        withTimeout(3_000) { while (settings.ongoing?.lastSeenOdoKm != 104.0) delay(20) }
        assertEquals(COMPANY.lat, settings.ongoing?.lastSeenLat)
    }

    @Test
    fun `수동 운행 중 시동이 꺼졌다 켜지면 마지막 확인 시점에서 끝내고 지난 운행으로 묻는다`() = runBlocking {
        val settings = FakeSettings(PairedVehicleInfo(3, "12가3456", null, 11)).apply {
            ongoing = OngoingTrip(
                Instant.parse("2026-11-02T22:00:00Z").toEpochMilli(), 90.0, HOME.lat, HOME.lng, manual = true,
                lastSeenAtMillis = Instant.parse("2026-11-02T22:10:00Z").toEpochMilli(), lastSeenOdoKm = 110.0,
                lastSeenLat = COMPANY.lat, lastSeenLng = COMPANY.lng,
            )
        }
        vehicle.current = 110.0
        vehicle.gears.emit(Gear.PARK)
        val recorder = recorder(FakeVoice("응").apply { listenDelayMs = 300 }, settings)
        recorder.start(scope)

        val asking = withTimeout(5_000) { recorder.status.first { it.phase == TripRecorder.Phase.ASKING } }
        assertTrue(asking.previousTrip)
        assertEquals("지난 운행, 본사 도착, 20킬로미터. 출근으로 기록할까요?", asking.prompt)
        val saved = withTimeout(5_000) { trips.rows.first { rows -> rows.values.any { it.purpose == Purpose.COMMUTE } } }.values.single()
        assertEquals(Instant.parse("2026-11-02T22:10:00Z"), saved.endedAt)
    }

    private class FakeNotifications : ArrivalNotifications {
        val events = java.util.concurrent.CopyOnWriteArrayList<String>()
        override fun ask(tripUuid: String, prompt: String) {
            events += "ask:$prompt"
        }
        override fun confirmed(tripUuid: String, message: String) {
            events += "confirmed:$message"
        }
        override fun cancel(tripUuid: String) {
            events += "cancel"
        }
    }

    @Test
    fun `도착 질문을 알림에도 남기고 정해지면 안내로 바꾼다`() = runBlocking {
        val notifications = FakeNotifications()
        recorder(FakeVoice("응"), FakeSettings(PairedVehicleInfo(3, "12가3456", null, 11)), notifications = notifications).start(scope)
        delay(200)

        drive(100.0, 112.4, COMPANY)

        withTimeout(5_000) { trips.rows.first { rows -> rows.values.any { !it.answerPending } } }
        assertEquals(
            listOf("ask:본사 도착, 12.4킬로미터. 출근으로 기록할까요?", "confirmed:출퇴근으로 기록했어요."),
            notifications.events.toList(),
        )
    }

    @Test
    fun `대답이 없으면 알림을 남겨 두어 나중에 고를 수 있게 한다`() = runBlocking {
        val notifications = FakeNotifications()
        recorder(FakeVoice(null), FakeSettings(PairedVehicleInfo(3, "12가3456", null, 11)), notifications = notifications).start(scope)
        delay(200)

        drive(100.0, 105.0, GeoPoint(37.6, 127.2))

        withTimeout(5_000) { trips.rows.first { rows -> rows.values.any { !it.answerPending } } }
        delay(200)
        assertEquals(1, notifications.events.size)
        assertTrue(notifications.events.single().startsWith("ask:"))
    }

    @Test
    fun `등록 장소가 아닌 출발지와 도착지에는 내비 주소의 동 이름을 붙인다`() = runBlocking {
        val names = ArrayDeque(listOf("봉천동", "신림동"))
        val areas = object : com.autolog.app.location.AreaNameSource {
            override suspend fun currentAreaName(): String? = names.removeFirstOrNull()
        }
        location.last = GeoPoint(37.48, 126.95)
        val voice = FakeVoice("개인")
        recorder(voice, FakeSettings(PairedVehicleInfo(3, "12가3456", null, 11)), areaNames = areas).start(scope)
        delay(200)

        drive(100.0, 105.0, GeoPoint(37.6, 127.2))

        val saved = withTimeout(5_000) { trips.rows.first { rows -> rows.values.any { !it.answerPending } } }.values.single()
        assertEquals("봉천동", saved.originLabel)
        assertEquals("신림동", saved.destLabel)
        assertEquals("신림동 도착, 5킬로미터. 업무였나요, 개인 용도였나요?", voice.spoken.first())
    }

    @Test
    fun `동 이름을 못 받으면 미등록 장소로 둔다`() = runBlocking {
        val voice = FakeVoice("개인")
        recorder(voice, FakeSettings(PairedVehicleInfo(3, "12가3456", null, 11))).start(scope)
        delay(200)

        drive(100.0, 105.0, GeoPoint(37.6, 127.2))

        val saved = withTimeout(5_000) { trips.rows.first { rows -> rows.values.any { !it.answerPending } } }.values.single()
        assertEquals(null, saved.destLabel)
        assertEquals("미등록 장소 도착, 5킬로미터. 업무였나요, 개인 용도였나요?", voice.spoken.first())
    }

    @Test
    fun `페어링 전에는 기록하지 않는다`() = runBlocking {
        val voice = FakeVoice("응")
        recorder(voice, FakeSettings(null)).start(scope)
        delay(200)
        drive(100.0, 112.0, COMPANY)
        delay(800)
        assertTrue(trips.rows.value.isEmpty())
        assertTrue(voice.spoken.isEmpty())
    }

    @Test
    fun `주행 중 앱이 다시 시작돼도 저장된 출발 값으로 이어서 기록한다`() = runBlocking {
        val settings = FakeSettings(PairedVehicleInfo(3, "12가3456", null, 11))
        settings.ongoing = OngoingTrip(Instant.parse("2026-11-02T22:50:00Z").toEpochMilli(), 90.0, HOME.lat, HOME.lng)
        vehicle.current = 110.0
        recorder(FakeVoice("응"), settings).start(scope)
        delay(200)

        location.points.emit(COMPANY)
        vehicle.odometer.emit(110.0)
        vehicle.gears.emit(Gear.PARK)

        val saved = withTimeout(5_000) { trips.rows.first { rows -> rows.values.any { !it.answerPending } } }.values.single()
        assertEquals(90.0, saved.odoStartKm, 0.0)
        assertEquals(110.0, saved.odoEndKm, 0.0)
        assertEquals(Instant.parse("2026-11-02T22:50:00Z"), saved.startedAt)
        withTimeout(2_000) { while (settings.ongoing != null) delay(20) }
    }
}
