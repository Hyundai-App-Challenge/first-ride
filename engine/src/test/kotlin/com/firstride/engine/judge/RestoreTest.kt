package com.firstride.engine.judge

import com.firstride.engine.signal.FailReason
import com.firstride.engine.signal.Reading
import com.firstride.engine.signal.SignalValue.Token
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
}
