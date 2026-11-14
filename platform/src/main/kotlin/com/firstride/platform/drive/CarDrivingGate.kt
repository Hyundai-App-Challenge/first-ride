package com.firstride.platform.drive

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log
import com.firstride.engine.drive.DriveState
import com.firstride.engine.drive.DrivingGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AAOS의 [CarUxRestrictionsManager]로 주행 제한을 감지한다(기술 설계 8-1절).
 * 주의 분산 최적화가 필요하다(`isRequiresDistractionOptimization`)면 RESTRICTED.
 * 첫 값을 받기 전이나 연결·등록에 실패하면 RESTRICTED로 남는다(POL-D-7). 앱 수명 동안 하나 둔다.
 */
class CarDrivingGate(context: Context) : DrivingGate {
    private val _state = MutableStateFlow(DriveState.RESTRICTED)
    override val state: StateFlow<DriveState> = _state.asStateFlow()

    private val car: Car? = try {
        Car.createCar(context.applicationContext)
    } catch (e: Exception) {
        Log.w(TAG, "car connection failed", e)
        null
    }

    init {
        try {
            val manager = car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager?
            if (manager != null) {
                manager.registerListener { update(it) }
                update(manager.currentCarUxRestrictions)
            }
        } catch (e: Exception) {
            Log.w(TAG, "ux restrictions unavailable", e)
        }
    }

    private fun update(r: CarUxRestrictions?) {
        _state.value = if (r != null && !r.isRequiresDistractionOptimization) DriveState.PARKED else DriveState.RESTRICTED
    }

    private companion object { const val TAG = "CarDrivingGate" }
}
