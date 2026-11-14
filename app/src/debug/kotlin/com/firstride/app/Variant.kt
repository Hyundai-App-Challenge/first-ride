package com.firstride.app

import android.content.Context
import com.firstride.engine.signal.FakeSignalSource
import com.firstride.engine.signal.SignalSource
import java.io.File

/**
 * debug 빌드: 기본은 가짜 신호원(개발자 패널로 값을 바꾼다). 앱 파일 폴더에 [REAL_FLAG] 파일이 있으면 차량 SDK를 쓴다.
 *
 *   tools/signals.sh real   # 차량 SDK
 *   tools/signals.sh fake   # 가짜 신호원

 */
internal object Variant {
    const val REAL_FLAG = "real_signals"

    /** 개발자 패널이 값을 바꿀 때 쓰는 가짜 신호원. 차량 SDK를 쓰는 중이면 null. */
    var fake: FakeSignalSource? = null
        private set

    fun signalSource(context: Context, vehicle: () -> SignalSource): SignalSource =
        if (File(context.filesDir, REAL_FLAG).exists()) vehicle()
        else FakeSignalSource().also { fake = it }
}
