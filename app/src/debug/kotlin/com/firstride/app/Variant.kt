package com.firstride.app

import android.content.Context
import android.util.Log
import com.firstride.engine.signal.FakeSignalSource
import com.firstride.engine.signal.SignalId
import com.firstride.engine.signal.SignalSource
import com.firstride.engine.signal.SignalValue
import com.firstride.engine.signal.Signals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * debug 빌드: 기본은 가짜 신호원(개발자 패널로 값을 바꾼다). 앱 파일 폴더에 [REAL_FLAG] 파일이 있으면 차량 SDK를 쓴다.
 *
 *   tools/signals.sh real   # 차량 SDK
 *   tools/signals.sh fake   # 가짜 신호원
 *
 * 어느 쪽이든 구독 수를 logcat(태그 FR-SIGNALS)에 남긴다. 실습 범위 취소 뒤 0이 되는지 본다(AC-02-10, AC-07-5).
 */
internal object Variant {
    const val REAL_FLAG = "real_signals"

    /** debug는 차 이미지가 없어도 무대를 보여, 화면 배치를 확인할 수 있게 한다. */
    const val showEmptyCarStage = true

    /** 개발자 패널이 값을 바꿀 때 쓰는 가짜 신호원. 차량 SDK를 쓰는 중이면 null. */
    var fake: FakeSignalSource? = null
        private set

    fun signalSource(context: Context, vehicle: () -> SignalSource): SignalSource {
        val source = if (File(context.filesDir, REAL_FLAG).exists()) vehicle()
        else FakeSignalSource().also { fake = it; seed(it) }
        return CountingSignalSource(source)
    }

    /** 실습 흐름(화면, 단계 상태)을 logcat FR-TRACE로 남긴다. tools/check-crs01.sh가 읽는다. */
    fun trace(message: String) {
        Log.d("FR-TRACE", message)
    }
}

/** 개발자 패널을 쓰기 전에도 모든 신호가 읽히도록 보통의 시작 값을 둔다. 값이 없으면 확인 불가가 된다. */
private fun seed(f: FakeSignalSource) {
    Signals.tokens.forEach { (id, values) ->
        val v = when (id) {
            Signals.HVAC_RECIRCULATION -> "FRESH"
            Signals.DOOR_LOCK -> "LOCKED"
            else -> if (values == null) "FACE" else if ("OFF" in values) "OFF" else values.first()
        }
        f.set(id, SignalValue.Token(v))
    }
    f.set(Signals.HVAC_FAN_SPEED, SignalValue.Number(3)); f.setRange(Signals.HVAC_FAN_SPEED, 0..8)
    f.set(Signals.HVAC_TEMPERATURE, SignalValue.Number(225)); f.setRange(Signals.HVAC_TEMPERATURE, 160..300)
    f.set(Signals.WINDOW_DRIVER_POSITION, SignalValue.Number(0)); f.setRange(Signals.WINDOW_DRIVER_POSITION, 0..100)
    f.setPolled(Signals.HVAC_FAN_DIRECTION, true)
}

private class CountingSignalSource(private val inner: SignalSource) : SignalSource by inner {
    private val active = AtomicInteger()

    override fun observe(id: SignalId): Flow<com.firstride.engine.signal.Reading> = inner.observe(id)
        .onStart { Log.d(TAG, "observe ${id.key} start, active=${active.incrementAndGet()}") }
        .onCompletion { Log.d(TAG, "observe ${id.key} end, active=${active.decrementAndGet()}") }

    private companion object { const val TAG = "FR-SIGNALS" }
}
