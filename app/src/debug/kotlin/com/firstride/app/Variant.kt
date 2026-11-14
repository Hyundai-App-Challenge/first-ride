package com.firstride.app

import android.content.Context
import android.util.Log
import com.firstride.engine.signal.FakeSignalSource
import com.firstride.engine.signal.SignalId
import com.firstride.engine.signal.SignalSource
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

    /** 개발자 패널이 값을 바꿀 때 쓰는 가짜 신호원. 차량 SDK를 쓰는 중이면 null. */
    var fake: FakeSignalSource? = null
        private set

    fun signalSource(context: Context, vehicle: () -> SignalSource): SignalSource {
        val source = if (File(context.filesDir, REAL_FLAG).exists()) vehicle()
        else FakeSignalSource().also { fake = it }
        return CountingSignalSource(source)
    }

    /** 실습 흐름(화면, 단계 상태)을 logcat FR-TRACE로 남긴다. tools/check-crs01.sh가 읽는다. */
    fun trace(message: String) {
        Log.d("FR-TRACE", message)
    }
}

private class CountingSignalSource(private val inner: SignalSource) : SignalSource by inner {
    private val active = AtomicInteger()

    override fun observe(id: SignalId): Flow<com.firstride.engine.signal.Reading> = inner.observe(id)
        .onStart { Log.d(TAG, "observe ${id.key} start, active=${active.incrementAndGet()}") }
        .onCompletion { Log.d(TAG, "observe ${id.key} end, active=${active.decrementAndGet()}") }

    private companion object { const val TAG = "FR-SIGNALS" }
}
