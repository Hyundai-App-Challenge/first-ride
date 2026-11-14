package com.firstride.platform.vehicle

import ai.pleos.playground.vehicle.Vehicle
import android.content.Context
import com.firstride.engine.signal.SignalSource
import kotlinx.coroutines.Dispatchers

/**
 * 앱 수명 동안 하나만 두는 Vehicle SDK 연결. 실습 범위가 취소될 때는 구독만 풀리고 연결은 남는다.
 * [close]는 앱이 끝날 때만 부른다(비동기 콜백이 남은 채 release하지 말라는 SDK 문서, 기술 설계 10-3절).
 */
class VehicleConnection(context: Context) : AutoCloseable {
    private val vehicle = Vehicle(context.applicationContext).apply { initialize() }

    val signals: SignalSource = VehicleSignalSource(
        bindings = hvacBindings(vehicle.getHVAC()),
        sdkDispatcher = Dispatchers.Main,
    )

    override fun close() = vehicle.release()
}
