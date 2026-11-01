package com.autolog.app.trip

import com.autolog.app.domain.DetectedTrip
import com.autolog.app.domain.Gear
import com.autolog.app.domain.GeoPoint
import com.autolog.app.domain.VehicleSnapshot
import java.time.Duration
import java.time.Instant

/**
 * 트립 경계 상태 머신 (docs/product/car-app-rules.md 트립 경계 규칙).
 *
 * - 시작: 기어 P(또는 모름) → D·R·N. 그 시점의 Odometer·위치·시각을 기록
 * - 종료 후보: 주행 중 기어 P 진입. 그 시점 값을 임시 저장
 * - 종료 확정: P가 [parkConfirm] 동안 유지. 그 전에 다시 출발하면 같은 트립으로 이어감
 *
 * SDK에 의존하지 않는다. 호출 쪽(Foreground Service)이 이벤트를 순서대로 넣는다.
 * 같은 스레드에서만 호출한다.
 */
class TripDetector(
    private val parkConfirm: Duration,
    initial: State = State.Idle,
) {
    sealed interface State {
        data object Idle : State
        data class Driving(val start: VehicleSnapshot) : State
        data class Parked(val start: VehicleSnapshot, val end: VehicleSnapshot) : State
    }

    sealed interface Event {
        data class TripStarted(val start: VehicleSnapshot) : Event
        data class TripCompleted(val trip: DetectedTrip) : Event
    }

    var state: State = initial
        private set

    private var lastOdometerKm: Double? = null
    private var lastLocation: GeoPoint? = null

    /** 최신 Odometer 값. 기어 변경 시점의 값으로 쓴다. */
    fun onOdometer(km: Double) {
        lastOdometerKm = km
    }

    fun onLocation(point: GeoPoint) {
        lastLocation = point
    }

    /** 기어 변경. Odometer 값을 아직 모르면 판단을 미룬다(null 반환). */
    fun onGear(gear: Gear, at: Instant): Event? {
        val odo = lastOdometerKm ?: return null
        val snapshot = VehicleSnapshot(at, odo, lastLocation)
        return when (val current = state) {
            State.Idle -> if (gear.isMoving()) {
                state = State.Driving(snapshot)
                Event.TripStarted(snapshot)
            } else {
                null
            }
            is State.Driving -> {
                if (gear == Gear.PARK) state = State.Parked(current.start, snapshot)
                null
            }
            is State.Parked -> {
                if (gear.isMoving()) state = State.Driving(current.start)
                null
            }
        }
    }

    /** 주기적으로 호출. P 유지 시간이 지나면 트립을 확정한다. */
    fun onTick(now: Instant): Event? {
        val current = state as? State.Parked ?: return null
        if (Duration.between(current.end.at, now) < parkConfirm) return null
        state = State.Idle
        return Event.TripCompleted(DetectedTrip(current.start, current.end))
    }

    private fun Gear.isMoving(): Boolean = this == Gear.DRIVE || this == Gear.REVERSE || this == Gear.NEUTRAL
}
