package com.firstride.engine.signal

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/** 첫 응답만 제한한다. 기한 뒤에도 실제 값을 관찰하며 수집 취소 시 원본 구독도 정리한다. */
internal fun SignalSource.observeWithInitialTimeout(id: SignalId, timeout: Duration): Flow<Reading> = flow {
    coroutineScope {
        val readings = observe(id).produceIn(this)
        try {
            val first = withTimeoutOrNull(timeout) { readings.receiveCatching() }
            emit(first?.getOrNull() ?: Reading.Failed(if (first == null) FailReason.TIMEOUT else FailReason.EMPTY))
            emitAll(readings)
        } finally { readings.cancel() }
    }
}
