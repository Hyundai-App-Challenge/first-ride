package com.firstride.engine.check

import com.firstride.engine.signal.FakeSignalSource
import com.firstride.engine.signal.Reading
import com.firstride.engine.signal.SignalId
import com.firstride.engine.signal.SignalSource
import com.firstride.engine.signal.SignalValue.Token
import com.firstride.engine.signal.Signals
import com.firstride.engine.signal.Support
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LightsObservationTest {
    @Test fun `지원 여부를 동시에 묻고 응답 순서와 무관하게 등 표시 순서를 유지한다`() = runTest {
        val base = FakeSignalSource()
        val requested = mutableSetOf<SignalId>()
        val src = object : SignalSource by base {
            override suspend fun support(id: SignalId): Support {
                requested += id
                delay(if (id == Signals.LIGHT_LOW_BEAM) 2_000 else 1_000)
                return if (id == Signals.LIGHT_TAIL_LAMP) Support.UNKNOWN else Support.SUPPORTED
            }
        }
        val result = backgroundScope.async { supportedLights(src) }
        runCurrent()
        assertEquals(LIGHTS_TO_CHECK.toSet(), requested)
        advanceTimeBy(2_000); runCurrent()
        assertTrue(result.isCompleted)
        assertEquals(listOf(Signals.LIGHT_LOW_BEAM, Signals.LIGHT_CABIN), result.await())
    }

    @Test fun `한 등의 최초 미응답도 꺼짐으로 오인하지 않고 늦은 ON을 반영한다`() = runTest {
        val base = FakeSignalSource().apply {
            set(Signals.LIGHT_LOW_BEAM, Token("OFF"))
            set(Signals.LIGHT_TAIL_LAMP, Token("OFF"))
        }
        val changes = MutableSharedFlow<Reading>()
        var active = 0
        val src = object : SignalSource by base {
            override fun observe(id: SignalId): Flow<Reading> = if (id == Signals.LIGHT_CABIN) flow {
                active++
                try { changes.collect { emit(it) } } finally { active-- }
            } else base.observe(id)
        }
        val seen = mutableListOf<LightsCheck>()
        val job = backgroundScope.launch { observeLights(LIGHTS_TO_CHECK, src).collect { seen += it } }
        runCurrent()
        assertTrue(seen.isEmpty())
        advanceTimeBy(3_000); runCurrent()
        assertTrue(seen.isNotEmpty())
        assertEquals(listOf(Signals.LIGHT_CABIN), seen.last().unknown)
        assertEquals(LightsOutcome.ALL_READABLE_OFF, seen.last().outcome)
        changes.emit(Reading.Value(Token("ON"))); runCurrent()
        assertEquals(listOf(Signals.LIGHT_CABIN), seen.last().on)
        assertEquals(LightsOutcome.SOME_ON, seen.last().outcome)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(LightsOutcome.SOME_ON, seen.last().outcome)
        job.cancelAndJoin()
        assertEquals(0, active)
        assertEquals(0, base.activeSubscriptions)
    }

    @Test fun `첫 응답 전에 취소하면 등 구독과 대기가 남지 않는다`() = runTest {
        val base = FakeSignalSource()
        var active = 0
        val src = object : SignalSource by base {
            override fun observe(id: SignalId): Flow<Reading> = flow {
                active++
                try { awaitCancellation() } finally { active-- }
            }
        }
        val seen = mutableListOf<LightsCheck>()
        val job = backgroundScope.launch { observeLights(LIGHTS_TO_CHECK, src).collect { seen += it } }
        runCurrent()
        assertEquals(3, active)
        job.cancelAndJoin()
        advanceTimeBy(3_000); runCurrent()
        assertEquals(0, active)
        assertTrue(seen.isEmpty())
    }

    @Test fun `모든 등이 미응답이어도 확인 불가를 내고 모두 꺼짐으로 말하지 않는다`() = runTest {
        val base = FakeSignalSource()
        val src = object : SignalSource by base {
            override fun observe(id: SignalId): Flow<Reading> = flow { awaitCancellation() }
        }
        val seen = mutableListOf<LightsCheck>()
        val job = backgroundScope.launch { observeLights(LIGHTS_TO_CHECK, src).collect { seen += it } }
        advanceTimeBy(3_000); runCurrent()
        assertTrue(seen.isNotEmpty())
        assertEquals(LightsOutcome.NONE_READABLE, seen.last().outcome)
        assertEquals(LIGHTS_TO_CHECK, seen.last().unknown)
        job.cancelAndJoin()
    }

    @Test fun `지원 여부 확인 중 취소하면 세 조회도 모두 정리한다`() = runTest {
        val base = FakeSignalSource()
        var active = 0
        val src = object : SignalSource by base {
            override suspend fun support(id: SignalId): Support {
                active++
                try { awaitCancellation() } finally { active-- }
            }
        }
        val job = backgroundScope.async { supportedLights(src) }
        runCurrent()
        assertEquals(3, active)
        job.cancelAndJoin()
        assertEquals(0, active)
    }

    @Test fun `지원하는 등이 없으면 구독 없이 미지원 결과를 반환한다`() = runTest {
        val src = FakeSignalSource()
        assertEquals(LightsCheck(emptyList(), LightsOutcome.NONE_READABLE), observeLights(emptyList(), src).first())
        assertEquals(0, src.activeSubscriptions)
    }
}
