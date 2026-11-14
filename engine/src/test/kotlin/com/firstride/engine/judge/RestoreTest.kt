package com.firstride.engine.judge

import com.firstride.engine.signal.FailReason
import com.firstride.engine.signal.Reading
import com.firstride.engine.signal.SignalId
import com.firstride.engine.signal.SignalSource
import com.firstride.engine.signal.SignalValue.Token
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class RestoreTest {
    private val course = twoStepCourse()   // 제상, 에어컨, 외기, 풍량

    @Test fun `AC-14-1 시작 전과 값이 다른 것만 나열한다`() = runTest {
        val src = source(defrost = "OFF")
        val before = snapshotBefore(course, src)
        src.set(defrost, Token("ON"))
        val now = coveredSignals(course).keys.associateWith { src.read(it) }
        assertEquals(RestoreReport(listOf("앞유리 제상"), emptyList()), restoreReport(course, before, now))
    }

    @Test fun `AC-14-1 달라진 것이 없으면 목록이 비어 있다`() = runTest {
        val src = source()
        val before = snapshotBefore(course, src)
        val now = coveredSignals(course).keys.associateWith { src.read(it) }
        assertTrue(restoreReport(course, before, now).isEmpty)
    }

    @Test fun `AC-14-3 사용자가 되돌리면 가상 시간이 흐르지 않아도 목록에서 사라진다`() = runTest {
        val src = source(defrost = "OFF")
        val before = snapshotBefore(course, src)
        src.set(defrost, Token("ON"))
        val seen = mutableListOf<RestoreReport>()
        val job = backgroundScope.launch { observeRestore(course, before, src).collect { seen += it } }
        runCurrent()
        assertEquals(listOf("앞유리 제상"), seen.last().changed)
        src.set(defrost, Token("OFF")); runCurrent()
        assertTrue(seen.last().isEmpty)
        job.cancel(); runCurrent()
        assertEquals(0, src.activeSubscriptions)
    }

    @Test fun `확인하지 못한 것은 달라졌다고 단정하지 않고 따로 낸다`() = runTest {
        val src = source()
        val before = snapshotBefore(course, src)
        src.fail(ac, FailReason.CONNECTION)
        val report = observeRestore(course, before, src).first()
        assertEquals(emptyList(), report.changed)
        assertEquals(listOf("에어컨"), report.unverified)
    }

    @Test fun `시작 전 값을 읽지 못한 신호도 단정하지 않는다`() = runTest {
        val src = source()
        src.fail(fan, FailReason.TIMEOUT)
        val before = snapshotBefore(course, src)
        assertTrue(fan !in before)
        src.set(fan, com.firstride.engine.signal.SignalValue.Number(5))
        val now = coveredSignals(course).keys.associateWith { src.read(it) }
        assertEquals(listOf("풍량"), restoreReport(course, before, now).unverified)
    }

    @Test fun `미응답 조회가 다른 시작 전 값의 확인을 막지 않고 3초에 끝난다`() = runTest {
        val base = source()
        val requested = mutableSetOf<SignalId>()
        var active = 0
        val src = object : SignalSource by base {
            override suspend fun read(id: SignalId): Reading {
                requested += id
                active++
                try {
                    if (id == defrost) awaitCancellation()
                    delay(100)
                    return base.read(id)
                } finally { active-- }
            }
        }
        val snapshot = backgroundScope.async { snapshotBefore(course, src) }
        runCurrent()
        assertEquals(coveredSignals(course).keys, requested)
        advanceTimeBy(3_000); runCurrent()
        assertTrue(snapshot.isCompleted)
        assertEquals(coveredSignals(course).keys - defrost, snapshot.await().keys)
        assertEquals(0, active)
    }

    @Test fun `시작 전 값 조회 취소는 읽기 작업도 모두 취소한다`() = runTest {
        val base = source()
        var active = 0
        val src = object : SignalSource by base {
            override suspend fun read(id: SignalId): Reading {
                active++
                try { awaitCancellation() } finally { active-- }
            }
        }
        val snapshot = backgroundScope.async { snapshotBefore(course, src) }
        runCurrent()
        assertTrue(active > 0)
        snapshot.cancelAndJoin()
        assertEquals(0, active)
    }

    @Test fun `첫 관찰 미응답은 확인 못 함으로 표시하고 늦은 실제 값은 반영한다`() = runTest {
        val base = source(defrost = "OFF")
        val before = snapshotBefore(course, base)
        val changes = MutableSharedFlow<Reading>()
        var active = 0
        val src = object : SignalSource by base {
            override fun observe(id: SignalId): Flow<Reading> = if (id == defrost) flow {
                active++
                try { changes.collect { emit(it) } } finally { active-- }
            } else base.observe(id)
        }
        val seen = mutableListOf<RestoreReport>()
        val job = backgroundScope.launch { observeRestore(course, before, src).collect { seen += it } }
        runCurrent()
        assertTrue(seen.isEmpty())
        advanceTimeBy(3_000); runCurrent()
        assertTrue(seen.isNotEmpty())
        assertEquals(RestoreReport(emptyList(), listOf("앞유리 제상")), seen.last())
        changes.emit(Reading.Value(Token("ON"))); runCurrent()
        assertEquals(RestoreReport(listOf("앞유리 제상"), emptyList()), seen.last())
        job.cancelAndJoin()
        assertEquals(0, active)
        assertEquals(0, base.activeSubscriptions)
    }

    @Test fun `최초 관찰 뒤 값이 오래 같아도 시간 초과로 바꾸지 않는다`() = runTest {
        val src = source()
        val before = snapshotBefore(course, src)
        val seen = mutableListOf<RestoreReport>()
        val job = backgroundScope.launch { observeRestore(course, before, src).collect { seen += it } }
        runCurrent()
        assertTrue(seen.last().isEmpty)
        advanceTimeBy(60_000); runCurrent()
        assertTrue(seen.all { it.isEmpty })
        src.set(defrost, Token("ON")); runCurrent()
        assertEquals(listOf("앞유리 제상"), seen.last().changed)
        job.cancelAndJoin()
        assertEquals(0, src.activeSubscriptions)
    }

    @Test fun `첫 관찰을 기다리다가 취소하면 기한 후에도 안내와 구독이 남지 않는다`() = runTest {
        val base = source()
        val before = snapshotBefore(course, base)
        var active = 0
        val src = object : SignalSource by base {
            override fun observe(id: SignalId): Flow<Reading> = flow {
                active++
                try { awaitCancellation() } finally { active-- }
            }
        }
        val seen = mutableListOf<RestoreReport>()
        val job = backgroundScope.launch { observeRestore(course, before, src).collect { seen += it } }
        runCurrent()
        assertEquals(coveredSignals(course).size, active)
        job.cancelAndJoin()
        advanceTimeBy(3_000); runCurrent()
        assertTrue(seen.isEmpty())
        assertEquals(0, active)
    }

    @Test fun `값 없이 끝난 관찰도 복구됐다고 단정하지 않는다`() = runTest {
        val base = source()
        val before = snapshotBefore(course, base)
        val src = object : SignalSource by base {
            override fun observe(id: SignalId): Flow<Reading> = if (id == defrost) emptyFlow() else base.observe(id)
        }
        val report = observeRestore(course, before, src).first()
        assertEquals(RestoreReport(emptyList(), listOf("앞유리 제상")), report)
        assertEquals(0, base.activeSubscriptions)
    }
}
