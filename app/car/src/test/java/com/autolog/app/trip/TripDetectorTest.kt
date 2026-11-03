package com.autolog.app.trip

import com.autolog.app.domain.Gear
import com.autolog.app.domain.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class TripDetectorTest {
    private val t0 = Instant.parse("2026-11-03T08:00:00Z")
    private fun at(seconds: Long) = t0.plusSeconds(seconds)
    private fun detector() = TripDetector(Duration.ofMinutes(3))

    @Test
    fun `P에서 D로 바꾸면 시작 값으로 트립이 시작된다`() {
        val d = detector()
        d.onOdometer(100.0)
        d.onLocation(GeoPoint(37.5, 127.0))

        val event = d.onGear(Gear.DRIVE, at(0))

        val started = event as TripDetector.Event.TripStarted
        assertEquals(100.0, started.start.odometerKm, 0.0)
        assertEquals(GeoPoint(37.5, 127.0), started.start.location)
        assertTrue(d.state is TripDetector.State.Driving)
    }

    @Test
    fun `Odometer 값을 모르면 판단을 미룬다`() {
        val d = detector()
        assertNull(d.onGear(Gear.DRIVE, at(0)))
        assertEquals(TripDetector.State.Idle, d.state)
    }

    @Test
    fun `P를 3분 유지하면 P 진입 시점 값으로 트립이 확정된다`() {
        val d = detector()
        d.onOdometer(100.0)
        d.onGear(Gear.DRIVE, at(0))
        d.onOdometer(112.4)
        d.onLocation(GeoPoint(37.49, 127.02))
        d.onGear(Gear.PARK, at(600))
        d.onOdometer(112.4)

        assertNull(d.onTick(at(600 + 179)))
        val completed = d.onTick(at(600 + 180)) as TripDetector.Event.TripCompleted

        assertEquals(100.0, completed.trip.start.odometerKm, 0.0)
        assertEquals(112.4, completed.trip.end.odometerKm, 0.0)
        assertEquals(at(600), completed.trip.end.at)
        assertEquals(GeoPoint(37.49, 127.02), completed.trip.end.location)
        assertEquals(12.4, completed.trip.distanceKm, 1e-9)
        assertEquals(TripDetector.State.Idle, d.state)
    }

    @Test
    fun `P 유지 중 다시 출발하면 같은 트립으로 이어진다`() {
        val d = detector()
        d.onOdometer(100.0)
        d.onGear(Gear.DRIVE, at(0))
        d.onOdometer(105.0)
        d.onGear(Gear.PARK, at(300))
        assertNull(d.onGear(Gear.DRIVE, at(360)))
        d.onOdometer(110.0)
        d.onGear(Gear.PARK, at(900))

        assertNull(d.onTick(at(900 + 60)))
        val completed = d.onTick(at(900 + 180)) as TripDetector.Event.TripCompleted
        assertEquals(100.0, completed.trip.start.odometerKm, 0.0)
        assertEquals(110.0, completed.trip.end.odometerKm, 0.0)
        assertEquals(at(0), completed.trip.start.at)
    }

    @Test
    fun `후진·중립도 출발로 본다`() {
        val d = detector()
        d.onOdometer(1.0)
        assertTrue(d.onGear(Gear.REVERSE, at(0)) is TripDetector.Event.TripStarted)
        val d2 = detector()
        d2.onOdometer(1.0)
        assertTrue(d2.onGear(Gear.NEUTRAL, at(0)) is TripDetector.Event.TripStarted)
    }

    @Test
    fun `정차 중 P 재통지나 모름은 무시한다`() {
        val d = detector()
        d.onOdometer(1.0)
        assertNull(d.onGear(Gear.PARK, at(0)))
        assertNull(d.onGear(Gear.UNKNOWN, at(1)))
        assertEquals(TripDetector.State.Idle, d.state)
        assertNull(d.onTick(at(1000)))
    }

    @Test
    fun `주행 중 D 재통지는 시작을 바꾸지 않는다`() {
        val d = detector()
        d.onOdometer(1.0)
        d.onGear(Gear.DRIVE, at(0))
        d.onOdometer(3.0)
        assertNull(d.onGear(Gear.DRIVE, at(10)))
        assertEquals(1.0, (d.state as TripDetector.State.Driving).start.odometerKm, 0.0)
    }

    @Test
    fun `저장된 상태로 다시 시작할 수 있다`() {
        val d = detector()
        d.onOdometer(50.0)
        val start = (d.onGear(Gear.DRIVE, at(0)) as TripDetector.Event.TripStarted).start

        val restored = TripDetector(Duration.ofMinutes(3), TripDetector.State.Driving(start))
        restored.onOdometer(60.0)
        restored.onGear(Gear.PARK, at(100))
        val completed = restored.onTick(at(400)) as TripDetector.Event.TripCompleted
        assertEquals(10.0, completed.trip.distanceKm, 1e-9)
    }

    @Test
    fun `수동 운행은 기어 P를 무시하고 종료 버튼으로만 끝난다`() {
        val d = detector()
        d.onOdometer(100.0)
        assertTrue(d.startManual(at(0)) is TripDetector.Event.TripStarted)
        assertEquals(true, (d.state as TripDetector.State.Driving).manual)

        d.onOdometer(105.5)
        assertNull(d.onGear(Gear.PARK, at(60)))
        assertNull(d.onTick(at(600)))
        assertTrue(d.state is TripDetector.State.Driving)

        val completed = d.stopManual(at(700)) as TripDetector.Event.TripCompleted
        assertEquals(5.5, completed.trip.distanceKm, 1e-9)
        assertEquals(at(700), completed.trip.end.at)
        assertEquals(TripDetector.State.Idle, d.state)
    }

    @Test
    fun `운행 중이거나 Odometer를 모르면 수동 시작하지 않는다`() {
        val unknown = detector()
        assertNull(unknown.startManual(at(0)))

        val driving = detector()
        driving.onOdometer(10.0)
        driving.onGear(Gear.DRIVE, at(0))
        assertNull(driving.startManual(at(5)))
        assertNull(detector().stopManual(at(0)))
    }
}
