package com.firstride.app

import android.content.Context
import com.firstride.engine.signal.SignalSource

/** release 빌드: 늘 차량 SDK의 신호를 쓴다. 가짜 구현을 참조하지 않는다(기술 설계 4-2절). */
internal object Variant {
    fun signalSource(context: Context, vehicle: () -> SignalSource): SignalSource = vehicle()

    /** release는 진행 기록을 로그에 남기지 않는다(POL-S-7). */
    fun trace(message: String) = Unit
}
