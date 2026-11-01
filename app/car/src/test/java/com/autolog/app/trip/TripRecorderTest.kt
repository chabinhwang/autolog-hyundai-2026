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
        override suspend fun speak(text: String): Boolean {
            spoken += text
            return true
        }
        override suspend fun listen(timeoutMs: Long): ListenResult {
            listens++
            return queue.removeFirstOrNull() ?: ListenResult.NoAnswer
        }
        override fun release() = Unit
    }

    private class FakeSettings(var vehicle: PairedVehicleInfo?) : RecorderSettings {
        var ongoing: OngoingTrip? = null
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

    private fun recorder(voice: VoiceIO, settings: RecorderSettings): TripRecorder {
        runBlocking {
            places.upsert(PlaceEntity(0, 1, "본사", EndpointType.COMPANY, COMPANY.lat, COMPANY.lng, 200, false))
            places.upsert(PlaceEntity(0, null, "집", EndpointType.HOME, HOME.lat, HOME.lng, 200, false))
        }
        val clock = RunningClock(Instant.parse("2026-11-02T23:00:00Z"))
        return TripRecorder(
            vehicle = vehicle, location = location, voice = voice,
            repository = TripRepository(trips, FakeEditDao(), places, clock),
            settings = settings, requestSync = { syncRequests++ },
            parkConfirm = Duration.ofMillis(300), clock = clock, tickMs = 50, answerTimeoutMs = 100,
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
