package com.firstride.platform.vehicle

import ai.pleos.playground.vehicle.Vehicle
import android.content.Context
import android.util.Log
import com.firstride.engine.signal.FailReason
import com.firstride.engine.signal.Reading
import com.firstride.engine.signal.SignalId
import com.firstride.engine.signal.SignalSource
import com.firstride.engine.signal.Support
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * 앱 수명 동안 하나만 두는 Vehicle SDK 연결. 실습 범위가 취소될 때는 구독만 풀리고 연결은 남는다.
 * [close]는 앱이 끝날 때만 부른다. 화면이나 ViewModel에서 부르지 않는다
 * (비동기 콜백이 남은 채 release하지 말라는 SDK 문서, 기술 설계 10-3절).
 *
 * SDK 연결 자체가 실패하면(생성·initialize가 예외) 앱을 멈추지 않고, 모든 신호가 연결 실패로 답한다.
 * 시작 화면이 이것을 보고 연결 오류 화면으로 간다(AC-01-4).
 */
class VehicleConnection(context: Context) : AutoCloseable {
    private val vehicle: Vehicle? = try {
        Vehicle(context.applicationContext).apply { initialize() }
    } catch (e: Exception) {
        Log.w(TAG, "vehicle connection failed", e)
        null
    }

    val signals: SignalSource = vehicle?.let {
        VehicleSignalSource(bindings = hvacBindings(it.getHVAC()), sdkDispatcher = Dispatchers.Main)
    } ?: Disconnected

    override fun close() {
        runCatching { vehicle?.release() }
    }

    /** 연결되지 않았을 때의 신호원. 지원 여부는 확인할 수 없고(UNKNOWN), 값은 연결 실패다. */
    private object Disconnected : SignalSource {
        override suspend fun support(id: SignalId) = Support.UNKNOWN
        override suspend fun read(id: SignalId): Reading = Reading.Failed(FailReason.CONNECTION)
        override fun observe(id: SignalId): Flow<Reading> = flowOf(Reading.Failed(FailReason.CONNECTION))
        override fun isPolled(id: SignalId) = false
        override suspend fun range(id: SignalId): IntRange? = null
    }

    private companion object { const val TAG = "VehicleConnection" }
}
