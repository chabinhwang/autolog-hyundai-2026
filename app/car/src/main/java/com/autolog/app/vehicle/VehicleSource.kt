package com.autolog.app.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import android.util.Log
import com.autolog.app.domain.Gear
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** 차량 상태 입력. 트립 감지가 Pleos SDK에 직접 의존하지 않도록 분리한다. */
interface VehicleSource {
    /** 기어 변경(현재값 포함). */
    fun gear(): Flow<Gear>

    /** 누적 주행거리(km). */
    fun odometerKm(): Flow<Double>

    suspend fun currentOdometerKm(): Double?

    fun release()
}

/**
 * Pleos Vehicle SDK 구현. 권한 `pleos.car.permission.CAR_DRIVE` (Gear·Odometer).
 * Pleos Connect AVD 2.1.0에서 값·변경 알림 수신 확인(2026-11-02).
 */
class PleosVehicleSource(context: Context) : VehicleSource {
    private val vehicle = Vehicle(context.applicationContext).also { it.initialize() }

    override fun gear(): Flow<Gear> = callbackFlow {
        val gearApi = vehicle.getGear()
        val listener = object : GearSelectionListener {
            override fun onSelected(selection: VehicleGear?) {
                trySend(selection.toGear())
            }

            override fun onFailed(e: Exception) {
                Log.w(TAG, "기어 구독 실패: ${e.message}")
            }
        }
        gearApi.getCurrentGearSelectionState({ trySend(it.toGear()) }, { Log.w(TAG, "기어 조회 실패: ${it.message}") })
        gearApi.registerGearSelection(listener)
        awaitClose { gearApi.unregisterGearSelection(listener) }
    }.distinctUntilChanged()

    override fun odometerKm(): Flow<Double> = callbackFlow {
        val odometer = vehicle.getOdometer()
        val listener = object : DistanceDrivenListener {
            override fun onDistanceDrivenUpdated(value: Float?) {
                value?.let { trySend(it.toDouble()) }
            }

            override fun onFailed(e: Exception) {
                Log.w(TAG, "Odometer 구독 실패: ${e.message}")
            }
        }
        odometer.registerDistanceDriven(listener)
        awaitClose { odometer.unregisterDistanceDriven(listener) }
    }.distinctUntilChanged()

    override suspend fun currentOdometerKm(): Double? = suspendCoroutine { cont ->
        vehicle.getOdometer().getCurrentDistanceDriven(
            { km -> cont.resume(km?.toDouble()) },
            { e ->
                Log.w(TAG, "Odometer 조회 실패: ${e.message}")
                cont.resume(null)
            },
        )
    }

    override fun release() = vehicle.release()

    private fun VehicleGear?.toGear(): Gear = when (this) {
        VehicleGear.GEAR_PARK -> Gear.PARK
        VehicleGear.GEAR_REVERSE -> Gear.REVERSE
        VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
        null, VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
        // GEAR_DRIVE, GEAR_1~9: 전진 기어
        else -> Gear.DRIVE
    }

    private companion object {
        const val TAG = "AutoLogVehicle"
    }
}
