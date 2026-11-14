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
 * - 모든 SDK 호출(영역 확인, 조회, 등록, 해제, 지원 여부)은 [sdkDispatcher]에서 한다. SDK 콜백은 binder 스레드에서 온다.
 * - 구독 순서는 영역 확정 → 등록 → 조회다. 조회 응답 전에 콜백이 오면 조회 값(더 오래된 값)은 버린다.
 * - 구독이나 폴링 중 실패가 오면 `Failed`를 한 번 내보내고 흐름을 끝낸다. 자동으로 다시 붙지 않는다(POL-E-3, POL-J-22).
 *   다시 확인은 호출하는 쪽이 `observe`를 새로 수집해서 한다.
 * - 모르는 신호는 지원하지 않는 것으로 답한다.
 */
internal class VehicleSignalSource(
    bindings: Map<SignalId, SignalBinding<*>>,
    private val sdkDispatcher: CoroutineDispatcher,
    private val pollIntervalMillis: Long = POLL_INTERVAL_MILLIS,
    private val callTimeoutMillis: Long = CALL_TIMEOUT_MILLIS,
    private val capabilityTimeoutMillis: Long = CAPABILITY_TIMEOUT_MILLIS,
) : SignalSource {

    @Suppress("UNCHECKED_CAST")
    private val bindings = bindings as Map<SignalId, SignalBinding<Any>>

    override suspend fun support(id: SignalId): Support {
        val b = bindings[id] ?: return Support.UNSUPPORTED
        return withTimeoutOrNull(capabilityTimeoutMillis) {
            callSdk(onThrow = { it.toSupport() }) { done -> b.support(done) { e -> done(e.toSupport()) } }
        } ?: Support.UNKNOWN
    }

    override suspend fun read(id: SignalId): Reading {
        val b = bindings[id] ?: return Reading.Failed(FailReason.UNSUPPORTED)
        return when (val a = area(b)) {
            is AreaResult.Found -> readAt(b, a.area)
            is AreaResult.Failed -> a.reading
        }
    }

    override fun observe(id: SignalId): Flow<Reading> {
        val b = bindings[id] ?: return flowOf(Reading.Failed(FailReason.UNSUPPORTED))
        val subscribe = b.subscribe ?: return poll(id)
        return callbackFlow {
            val area = when (val a = area(b)) {
                is AreaResult.Found -> a.area
                is AreaResult.Failed -> { send(a.reading); close(); awaitClose(); return@callbackFlow }
            }
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
                subscribe(area, { deliver(it.toReading()) }, { deliver(Reading.Failed(it.toFailReason())) })
            } catch (e: Exception) {
                send(Reading.Failed(e.toFailReason())); close(); awaitClose(); return@callbackFlow
            }
            // 조회 중에 취소돼도 리스너가 남지 않게 finally에서 푼다.
            try {
                val current = readAt(b, area)
                synchronized(lock) {
                    if (!callbackArrived) {
                        trySend(current)
                        if (current is Reading.Failed) close()
                    }
                }
                awaitClose()
            } finally {
                runCatching { unregister() }
            }
        }.buffer(Channel.UNLIMITED).flowOn(sdkDispatcher).distinctUntilChanged()
    }

    private fun poll(id: SignalId): Flow<Reading> = flow {
        while (true) {
            val r = withTimeoutOrNull(callTimeoutMillis) { read(id) } ?: Reading.Failed(FailReason.TIMEOUT)
            emit(r)
            if (r is Reading.Failed) break
            delay(pollIntervalMillis)
        }
    }.distinctUntilChanged()

    override fun isPolled(id: SignalId): Boolean = bindings[id]?.subscribe == null

    override suspend fun range(id: SignalId): IntRange? {
        val r = bindings[id]?.range ?: return null
        return withTimeoutOrNull(capabilityTimeoutMillis) { callSdk(onThrow = { null }) { done -> r(done) { done(null) } } }
    }

    private sealed interface AreaResult {
        data class Found(val area: Any) : AreaResult
        data class Failed(val reading: Reading.Failed) : AreaResult
    }

    private suspend fun area(b: SignalBinding<Any>): AreaResult =
        callSdk<AreaResult>(onThrow = { AreaResult.Failed(Reading.Failed(it.toFailReason())) }) { done ->
            b.area({ done(AreaResult.Found(it)) }, { done(AreaResult.Failed(Reading.Failed(it.toFailReason()))) })
        }

    private suspend fun readAt(b: SignalBinding<Any>, area: Any): Reading =
        callSdk<Reading>(onThrow = { Reading.Failed(it.toFailReason()) }) { done ->
            b.read(area, { done(it.toReading()) }, { done(Reading.Failed(it.toFailReason())) })
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
        const val CALL_TIMEOUT_MILLIS = 3_000L
        /**
         * 지원 여부·범위 조회의 시간 제한. 앱 시작 직후에는 SDK가 차량 서비스에 붙는 동안 첫 응답이 2~4초 걸려
         * 3초로는 연결 오류로 잘못 갈 수 있었다(11-14 실측). 홈 표시 10초 안(AC-01-1)에 들도록 8초.
         */
        const val CAPABILITY_TIMEOUT_MILLIS = 8_000L
    }
}

private fun SignalValue?.toReading(): Reading =
    if (this == null) Reading.Failed(FailReason.EMPTY) else Reading.Value(this)

private fun Exception.toSupport(): Support =
    if (toFailReason() == FailReason.PERMISSION) Support.DENIED else Support.UNKNOWN

private fun <T> CancellableContinuation<T>.resumeIfActive(value: T) {
    if (isActive) resume(value)
}
