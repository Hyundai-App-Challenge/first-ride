package com.firstride.platform.vehicle

import com.firstride.engine.signal.FailReason
import com.firstride.engine.signal.Reading
import com.firstride.engine.signal.SignalId
import com.firstride.engine.signal.SignalValue.Token
import com.firstride.engine.signal.Support
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 기술 설계 4-1절의 observe 계약 다섯 가지를 SDK 없이 가짜 바인딩으로 확인한다. */
@OptIn(ExperimentalCoroutinesApi::class)
class VehicleSignalSourceTest {
    private val id = SignalId("hvac.ac")
    private val on = Token("ON")
    private val off = Token("OFF")

    /** SDK의 HVAC 한 신호를 흉내 낸다. 값, 등록된 리스너, 다음 조회의 동작을 바꿀 수 있다. */
    private class FakeSdk {
        var value: Token? = null
        var readError: Exception? = null
        var answerReads = true
        var registerThrows: Exception? = null
        val listeners = mutableListOf<Pair<ValueCallback, ErrorCallback>>()
        var pendingRead: ValueCallback? = null

        fun binding(subscribable: Boolean = true) = SignalBinding(
            support = { ok, err -> readError?.let(err) ?: ok(Support.SUPPORTED) },
            read = { ok, err ->
                when {
                    readError != null -> err(readError!!)
                    answerReads -> ok(value)
                    else -> pendingRead = ok
                }
            },
            subscribe = if (!subscribable) null else { ok, err ->
                registerThrows?.let { throw it }
                val l = ok to err
                listeners += l
                ({ listeners -= l })
            },
        )

        fun change(v: Token?) { value = v; listeners.toList().forEach { it.first(v) } }
    }

    private fun TestScope.source(sdk: FakeSdk, subscribable: Boolean = true) =
        VehicleSignalSource(mapOf(id to sdk.binding(subscribable)), StandardTestDispatcher(testScheduler))

    @Test
    fun `계약 1 수집을 시작하면 현재 값을 먼저 내보낸다`() = runTest {
        val sdk = FakeSdk().apply { value = on }
        assertEquals(Reading.Value(on), source(sdk).observe(id).first())
    }

    @Test
    fun `계약 1 조회보다 콜백이 먼저 오면 오래된 조회 값을 내보내지 않는다`() = runTest {
        val sdk = FakeSdk().apply { value = off; answerReads = false }
        val got = mutableListOf<Reading>()
        val job = launch { source(sdk).observe(id).collect { got += it } }
        runCurrent()
        sdk.change(on)                 // 등록 뒤, 조회 응답 전에 바뀜
        sdk.pendingRead!!(off)         // 조회는 바뀌기 전 값을 늦게 돌려줌
        runCurrent()
        assertEquals(listOf<Reading>(Reading.Value(on)), got)
        job.cancel()
    }

    @Test
    fun `계약 2 같은 값은 연달아 내보내지 않는다`() = runTest {
        val sdk = FakeSdk().apply { value = off }
        val got = mutableListOf<Reading>()
        val job = launch { source(sdk).observe(id).collect { got += it } }
        runCurrent()
        sdk.change(off); sdk.change(on); sdk.change(on); sdk.change(off)
        runCurrent()
        assertEquals(listOf(off, on, off).map<Token, Reading> { Reading.Value(it) }, got)
        job.cancel()
    }

    @Test
    fun `계약 3 수집을 취소하면 리스너가 남지 않는다`() = runTest {
        val sdk = FakeSdk().apply { value = off }
        val job = launch { source(sdk).observe(id).collect {} }
        runCurrent()
        assertEquals(1, sdk.listeners.size)
        job.cancel()
        runCurrent()
        assertEquals(0, sdk.listeners.size)
    }

    @Test
    fun `계약 4 구독이 없는 신호는 폴링으로 값의 변화를 낸다`() = runTest {
        val sdk = FakeSdk().apply { value = off }
        val src = source(sdk, subscribable = false)
        assertTrue(src.isPolled(id))
        val got = mutableListOf<Reading>()
        val job = launch { src.observe(id).collect { got += it } }
        runCurrent()
        sdk.value = on
        advanceTimeBy(VehicleSignalSource.POLL_INTERVAL_MILLIS + 1)
        runCurrent()
        assertEquals(listOf(off, on).map<Token, Reading> { Reading.Value(it) }, got)
        job.cancel()
    }

    @Test
    fun `계약 4 폴링 조회가 응답하지 않으면 TIMEOUT으로 끝난다`() = runTest {
        val sdk = FakeSdk().apply { answerReads = false }
        val got = source(sdk, subscribable = false).observe(id).toList()
        assertEquals(listOf<Reading>(Reading.Failed(FailReason.TIMEOUT)), got)
    }

    @Test
    fun `계약 5 POL-P-4 SDK가 null을 주면 꺼짐이 아니라 EMPTY 실패다`() = runTest {
        val sdk = FakeSdk().apply { value = null }
        assertEquals(Reading.Failed(FailReason.EMPTY), source(sdk).read(id))
    }

    @Test
    fun `계약 5 구독 중 실패가 오면 Failed를 내고 끝나며 리스너를 푼다`() = runTest {
        val sdk = FakeSdk().apply { value = on }
        val got = mutableListOf<Reading>()
        val job = launch { source(sdk).observe(id).collect { got += it } }
        runCurrent()
        sdk.listeners.single().second(SecurityException("need Permission"))
        runCurrent()
        assertEquals(listOf(Reading.Value(on), Reading.Failed(FailReason.PERMISSION)), got)
        assertTrue(job.isCompleted)
        assertEquals(0, sdk.listeners.size)
    }

    @Test
    fun `계약 5 등록이 예외를 던지면 Failed 하나로 끝난다`() = runTest {
        val sdk = FakeSdk().apply { value = on; registerThrows = SecurityException("need Permission") }
        assertEquals(listOf<Reading>(Reading.Failed(FailReason.PERMISSION)), source(sdk).observe(id).toList())
    }

    @Test
    fun `조회 콜백이 두 번 와도 첫 값만 쓴다`() = runTest {
        val binding = SignalBinding(support = { ok, _ -> ok(Support.SUPPORTED) }, read = { ok, _ -> ok(on); ok(off) })
        val src = VehicleSignalSource(mapOf(id to binding), StandardTestDispatcher(testScheduler))
        assertEquals(Reading.Value(on), src.read(id))
    }

    @Test
    fun `퍼미션 오류의 지원 여부는 DENIED다`() = runTest {
        val sdk = FakeSdk().apply { readError = SecurityException("need Permission") }
        assertEquals(Support.DENIED, source(sdk).support(id))
    }

    @Test
    fun `모르는 신호는 지원하지 않음으로 답한다`() = runTest {
        val src = source(FakeSdk())
        val other = SignalId("light.lowBeam")
        assertEquals(Support.UNSUPPORTED, src.support(other))
        assertEquals(listOf<Reading>(Reading.Failed(FailReason.UNSUPPORTED)), src.observe(other).take(1).toList())
    }
}
