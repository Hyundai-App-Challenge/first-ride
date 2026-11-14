package com.firstride.engine.judge

import com.firstride.engine.signal.Reading
import com.firstride.engine.signal.SignalId

sealed interface StepEvent {
    /** 초기 상태 확인의 결과. [ranges]는 숫자 신호의 지원 범위(없으면 null). */
    data class Prechecked(
        val readings: Map<SignalId, Reading>,
        val ranges: Map<SignalId, IntRange?> = emptyMap(),
    ) : StepEvent

    data class Changed(val id: SignalId, val reading: Reading) : StepEvent
    data object PrecheckTimedOut : StepEvent
    data object Skip : StepEvent
    data object Retry : StepEvent        // "다시 확인" → PRECHECK로
}
