package com.firstride.engine.judge

import com.firstride.engine.progress.CourseResult
import com.firstride.engine.signal.Reading
import com.firstride.engine.signal.SignalId
import com.firstride.engine.signal.SignalSource
import com.firstride.engine.signal.SignalValue.Number
import com.firstride.engine.signal.SignalValue.Token
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class CourseRunnerTest {
    @Test fun `FR-14 시작 전 값 조회가 끝나기 전에 단계를 시작하지 않는다`() = runTest {
        val base = source()
        val gate = CompletableDeferred<Unit>()
        val requested = mutableSetOf<SignalId>()
        val src = object : SignalSource by base {
            override suspend fun read(id: SignalId): Reading {
                requested += id
                gate.await()
                return base.read(id)
            }
        }
        val course = CourseRunner(twoStepCourse(), src, MemStore())
        val job = backgroundScope.launch { course.run() }; runCurrent()
        assertNull(course.step.value)
        assertEquals(coveredSignals(twoStepCourse()).keys, requested)
        assertEquals(0, base.activeSubscriptions)
        gate.complete(Unit); runCurrent()
        assertEquals(Token("OFF"), course.initialValues[defrost])
        assertEquals(0, course.step.value?.index)
        job.cancelAndJoin()
        assertEquals(0, base.activeSubscriptions)
    }

    @Test fun `POL-E-6 준비 취소는 읽기도 취소하고 재개는 기준을 새 값으로 덮지 않는다`() = runTest {
        val base = source()
        var active = 0
        var stall = true
        val calls = mutableListOf<SignalId>()
        val src = object : SignalSource by base {
            override suspend fun read(id: SignalId): Reading {
                calls += id; active++
                try {
                    if (stall) awaitCancellation()
                    return base.read(id)
                } finally { active-- }
            }
        }
        val course = CourseRunner(twoStepCourse(), src, MemStore())
        val first = backgroundScope.launch { course.run() }; runCurrent()
        assertEquals(coveredSignals(twoStepCourse()).size, active)
        first.cancelAndJoin()
        assertEquals(0, active)
        assertTrue(course.initialValues.isEmpty())
        base.set(defrost, Token("ON"))
        stall = false
        val resumed = backgroundScope.launch { course.run() }; runCurrent()
        // 재개한 1단계의 초기 조회는 유지하되, 2단계 풍량까지 기준 값으로 다시 읽지 않는다.
        assertEquals(1, calls.count { it == fan })
        assertEquals(Phase.ALREADY_AT_TARGET, course.step.value!!.runner.state.value.phase)
        assertTrue(course.initialValues.isEmpty())
        resumed.cancelAndJoin()
        assertEquals(0, base.activeSubscriptions)
    }

    @Test fun `FR-14 준비 미응답은 3초 후 기준 미확인으로 남기고 단계를 시작한다`() = runTest {
        val base = source()
        val src = object : SignalSource by base {
            override suspend fun read(id: SignalId): Reading {
                if (id == defrost) awaitCancellation()
                return base.read(id)
            }
        }
        val course = CourseRunner(twoStepCourse(), src, MemStore())
        val job = backgroundScope.launch { course.run() }; runCurrent()
        assertNull(course.step.value)
        advanceTimeBy(2_999); runCurrent()
        assertNull(course.step.value)
        advanceTimeBy(1); runCurrent()
        assertEquals(0, course.step.value?.index)
        assertEquals(coveredSignals(twoStepCourse()).keys - defrost, course.initialValues.keys)
        job.cancelAndJoin()
        assertEquals(0, base.activeSubscriptions)
    }

    @Test fun `POL-R-1 POL-R-5 단계를 순서대로 돌려 모두 DONE이면 완료를 기록한다`() = runTest {
        val src = source()
        val store = MemStore()
        val course = CourseRunner(twoStepCourse(), src, store)
        val result = backgroundScope.async { course.run() }
        runCurrent()
        assertEquals(0, course.step.value?.index)
        assertEquals(2, course.step.value?.total)

        src.set(defrost, Token("ON")); advanceTimeBy(2_000); runCurrent()
        assertEquals(1, course.step.value?.index)

        src.set(fan, Number(5)); advanceTimeBy(2_000); runCurrent()
        assertEquals(CourseResult.COMPLETED, result.await())
        assertEquals(listOf("T-01" to CourseResult.COMPLETED), store.calls)
        assertNull(course.step.value)
    }

    @Test fun `POL-R-2 POL-R-5 건너뛴 단계가 있으면 건너뜀 있음으로 기록한다`() = runTest {
        val src = source()
        val store = MemStore()
        val course = CourseRunner(twoStepCourse(), src, store)
        val result = backgroundScope.async { course.run() }
        runCurrent()
        course.step.value!!.runner.skip(); runCurrent()
        assertEquals(1, course.step.value?.index)
        src.set(fan, Number(5)); advanceTimeBy(2_000); runCurrent()
        assertEquals(CourseResult.HAS_SKIPPED, result.await())
        assertEquals(listOf("T-01" to CourseResult.HAS_SKIPPED), store.calls)
    }

    @Test fun `POL-R-6 끝까지 가지 않고 취소하면 기록하지 않는다`() = runTest {
        val src = source()
        val store = MemStore()
        val course = CourseRunner(twoStepCourse(), src, store)
        val job = backgroundScope.launch { course.run() }
        runCurrent()
        src.set(defrost, Token("ON")); advanceTimeBy(2_000); runCurrent()      // 1단계는 완료
        job.cancel(); runCurrent()
        assertEquals(emptyList(), store.calls)
        assertNull(course.step.value)
        assertEquals(0, src.activeSubscriptions)
    }

    @Test fun `POL-J-2 단계를 시작할 때마다 신호를 새로 읽는다`() = runTest {
        val src = source()
        val course = CourseRunner(twoStepCourse(), src, MemStore())
        backgroundScope.launch { course.run() }
        runCurrent()
        src.set(fan, Number(5))                          // 1단계 중에 2단계의 신호가 이미 목표가 됨
        src.set(defrost, Token("ON")); advanceTimeBy(2_000); runCurrent()
        assertEquals(Phase.ALREADY_AT_TARGET, course.step.value!!.runner.state.value.phase)
    }

    @Test fun `POL-R-7 완료한 코스를 다시 해서 건너뛰어도 저장 상태는 완료로 남는다`() = runTest {
        val store = MemStore()
        store.record("T-01", CourseResult.COMPLETED)
        val src = source()
        val course = CourseRunner(twoStepCourse(), src, store)
        val result = backgroundScope.async { course.run() }
        runCurrent()
        course.step.value!!.runner.skip(); runCurrent()
        course.step.value!!.runner.skip(); runCurrent()
        assertEquals(CourseResult.HAS_SKIPPED, result.await())
        assertEquals(CourseResult.COMPLETED, store.result("T-01"))
    }

    @Test fun `POL-R-11 재개는 같은 단계에서 새로 조회해 이미 목표인 값을 완료로 치지 않는다`() = runTest {
        val src = source()
        val store = MemStore()
        val course = CourseRunner(twoStepCourse(), src, store)
        val first = backgroundScope.launch { course.run() }; runCurrent()
        src.set(defrost, Token("ON")); advanceTimeBy(2_000); runCurrent()
        src.set(fan, Number(5)); runCurrent()
        first.cancelAndJoin()
        assertEquals(1, course.nextStepIndex)
        assertEquals(0, src.activeSubscriptions)
        val resumed = backgroundScope.async { course.run() }; runCurrent()
        assertEquals(1, course.step.value?.index)
        assertEquals(Phase.ALREADY_AT_TARGET, course.step.value!!.runner.state.value.phase)
        assertEquals(emptyList(), store.calls)
        src.set(fan, Number(0)); runCurrent()
        src.set(fan, Number(5)); advanceTimeBy(2_000); runCurrent()
        assertEquals(CourseResult.COMPLETED, resumed.await())
        assertEquals(Token("OFF"), course.initialValues[defrost])
        assertEquals(Number(3), course.initialValues[fan])
    }

    @Test fun `POL-R-5 재개 전 건너뜀을 보존해 완료로 바꾸지 않는다`() = runTest {
        val src = source()
        val store = MemStore()
        val course = CourseRunner(twoStepCourse(), src, store)
        val first = backgroundScope.launch { course.run() }; runCurrent()
        course.step.value!!.runner.skip(); runCurrent()
        first.cancelAndJoin()
        val resumed = backgroundScope.async { course.run() }; runCurrent()
        src.set(fan, Number(5)); advanceTimeBy(2_000); runCurrent()
        assertEquals(CourseResult.HAS_SKIPPED, resumed.await())
        assertEquals(listOf("T-01" to CourseResult.HAS_SKIPPED), store.calls)
    }

    @Test fun `POL-R-8 완료한 실행기를 다시 돌리면 처음부터 새 시도다`() = runTest {
        val src = source()
        val store = MemStore()
        val course = CourseRunner(twoStepCourse(), src, store)
        val first = backgroundScope.async { course.run() }; runCurrent()
        src.set(defrost, Token("ON")); advanceTimeBy(2_000); runCurrent()
        src.set(fan, Number(5)); advanceTimeBy(2_000); runCurrent()
        first.await()
        assertEquals(Token("OFF"), course.initialValues[defrost])
        val again = backgroundScope.launch { course.run() }; runCurrent()
        assertEquals(Token("ON"), course.initialValues[defrost])
        assertEquals(0, course.step.value?.index)
        assertEquals(Phase.ALREADY_AT_TARGET, course.step.value!!.runner.state.value.phase)
        assertEquals(1, store.calls.size)
        again.cancelAndJoin()
    }

    @Test fun `POL-R-10 동시 재개도 이전 구독 정리 후 실행한다`() = runTest {
        val src = source()
        val course = CourseRunner(twoStepCourse(), src, MemStore())
        val first = backgroundScope.launch { course.run() }
        val second = backgroundScope.launch { course.run() }; runCurrent()
        assertEquals(3, src.activeSubscriptions)  // 첫 단계는 제상·에어컨·순환 세 신호를 쓴다.
        first.cancelAndJoin(); runCurrent()
        assertEquals(3, src.activeSubscriptions)
        second.cancelAndJoin()
        assertEquals(0, src.activeSubscriptions)
    }
}
