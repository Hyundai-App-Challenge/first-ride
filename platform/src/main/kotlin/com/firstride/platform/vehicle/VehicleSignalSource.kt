package com.firstride.platform.vehicle

import com.firstride.engine.signal.FailReason
import com.firstride.engine.signal.Reading
import com.firstride.engine.signal.SignalId
import com.firstride.engine.signal.SignalSource
import com.firstride.engine.signal.SignalValue
import com.firstride.engine.signal.Support
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Vehicle SDK를 [SignalSource] 계약(기술 설계 4-1절)에 맞춘다. 신호별 SDK 호출은 [SignalBinding]에 있다.
 *
 * - 모든 SDK 호출(조회, 등록, 해제, 지원 여부)은 [sdkDispatcher]에서 한다.
 * - 구독이나 폴링 중 실패가 오면 `Failed`를 한 번 내보내고 흐름을 끝낸다. 자동으로 다시 붙지 않는다(POL-E-3, POL-J-22).
 *   다시 확인은 호출하는 쪽이 `observe`를 새로 수집해서 한다.
 * - 모르는 신호는 지원하지 않는 것으로 답한다.
 */
internal class VehicleSignalSource(
    private val bindings: Map<SignalId, SignalBinding>,
    private val sdkDispatcher: CoroutineDispatcher,
    private val pollIntervalMillis: Long = POLL_INTERVAL_MILLIS,
    private val pollTimeoutMillis: Long = POLL_TIMEOUT_MILLIS,
) : SignalSource {

    override suspend fun support(id: SignalId): Support {
        val b = bindings[id] ?: return Support.UNSUPPORTED
        return callSdk(onThrow = { it.toSupport() }) { done -> b.support(done) { e -> done(e.toSupport()) } }
    }

    override suspend fun read(id: SignalId): Reading {
        val b = bindings[id] ?: return Reading.Failed(FailReason.UNSUPPORTED)
        return callSdk<Reading>(onThrow = { Reading.Failed(it.toFailReason()) }) { done ->
            b.read({ done(it.toReading()) }, { done(Reading.Failed(it.toFailReason())) })
        }
    }

    override fun observe(id: SignalId): Flow<Reading> {
        val b = bindings[id] ?: return flowOf(Reading.Failed(FailReason.UNSUPPORTED))
        val subscribe = b.subscribe ?: return poll(id)
        return callbackFlow {
            // 구독을 먼저 걸고 현재 값을 읽는다. 그 사이에 콜백이 먼저 오면 읽은 값(더 오래된 값)은 버린다.
            // 콜백 스레드와 이 코루틴이 같은 판단을 하도록 lock 안에서 확인하고 보낸다.
            val lock = Any()
            var callbackArrived = false
            fun deliver(r: Reading) {
                synchronized(lock) {
                    callbackArrived = true
                    if (trySend(r).isFailure || r is Reading.Failed) close()
                }
            }
            val unregister = try {
                subscribe({ deliver(it.toReading()) }, { deliver(Reading.Failed(it.toFailReason())) })
            } catch (e: Exception) {
                send(Reading.Failed(e.toFailReason()))
                close()
                null
            }
            if (unregister != null) {
                val current = read(id)
                synchronized(lock) {
                    if (!callbackArrived) {
                        trySend(current)
                        if (current is Reading.Failed) close()
                    }
                }
            }
            awaitClose { runCatching { unregister?.invoke() } }
        }.buffer(Channel.UNLIMITED).flowOn(sdkDispatcher).distinctUntilChanged()
    }

    private fun poll(id: SignalId): Flow<Reading> = flow {
        while (true) {
            val r = withTimeoutOrNull(pollTimeoutMillis) { read(id) } ?: Reading.Failed(FailReason.TIMEOUT)
            emit(r)
            if (r is Reading.Failed) break
            delay(pollIntervalMillis)
        }
    }.distinctUntilChanged()

    override fun isPolled(id: SignalId): Boolean = bindings[id]?.subscribe == null

    override suspend fun range(id: SignalId): IntRange? {
        val r = bindings[id]?.range ?: return null
        return callSdk(onThrow = { null }) { done -> r(done) { done(null) } }
    }

    /** 콜백 한 번을 기다리는 SDK 호출. 콜백이 두 번 와도 한 번만 재개하고, 호출이 바로 던지면 [onThrow]의 값을 쓴다. */
    private suspend fun <T> callSdk(onThrow: (Exception) -> T, call: (done: (T) -> Unit) -> Unit): T =
        withContext(sdkDispatcher) {
            suspendCancellableCoroutine { cont ->
                val resumed = AtomicBoolean(false)
                val done: (T) -> Unit = { if (resumed.compareAndSet(false, true)) cont.resumeIfActive(it) }
                try {
                    call(done)
                } catch (e: Exception) {
                    done(onThrow(e))
                }
            }
        }

    companion object {
        /** 구독이 없는 신호의 폴링 간격 [가정]. */
        const val POLL_INTERVAL_MILLIS = 500L
        /** 폴링 한 번의 시간 제한. 초기 상태 확인 시간(POL-J-19, 3초)과 같게 둔다. */
        const val POLL_TIMEOUT_MILLIS = 3_000L
    }
}

private fun SignalValue?.toReading(): Reading =
    if (this == null) Reading.Failed(FailReason.EMPTY) else Reading.Value(this)

private fun Exception.toSupport(): Support =
    if (toFailReason() == FailReason.PERMISSION) Support.DENIED else Support.UNKNOWN

private fun <T> CancellableContinuation<T>.resumeIfActive(value: T) {
    if (isActive) resume(value)
}
