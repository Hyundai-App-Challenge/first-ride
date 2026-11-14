package com.firstride.engine.signal

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class FakeSignalSourceTest {
    private val id = Signals.HVAC_AC
    private fun on() = Reading.Value(SignalValue.Token("ON"))
    private fun off() = Reading.Value(SignalValue.Token("OFF"))

    @Test fun `observe는 현재 값을 먼저 내보낸다`() = runTest {
        val s = FakeSignalSource().apply { set(id, SignalValue.Token("ON")) }
        assertEquals(on(), s.observe(id).first())
    }

    @Test fun `같은 값은 연달아 내보내지 않는다`() = runTest(UnconfinedTestDispatcher()) {
        val s = FakeSignalSource().apply { set(id, SignalValue.Token("ON")) }
        val got = mutableListOf<Reading>()
        val job = launch { s.observe(id).toList(got) }
        s.set(id, SignalValue.Token("ON"))
        s.set(id, SignalValue.Token("OFF"))
        job.cancel()
        assertEquals<List<Reading>>(listOf(on(), off()), got)
    }

    @Test fun `수집을 취소하면 구독이 해제된다`() = runTest(UnconfinedTestDispatcher()) {
        val s = FakeSignalSource()
        val job = launch { s.observe(id).toList(mutableListOf<Reading>()) }
        assertEquals(1, s.activeSubscriptions)
        job.cancel()
        assertEquals(0, s.activeSubscriptions)
    }

    @Test fun `값이 없으면 EMPTY 실패이고 꺼짐으로 읽지 않는다`() = runTest {
        assertEquals(Reading.Failed(FailReason.EMPTY), FakeSignalSource().read(id))
    }
}
