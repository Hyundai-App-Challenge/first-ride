package com.firstride.engine.judge

import com.firstride.engine.progress.CourseResult
import com.firstride.engine.signal.SignalValue.Number
import com.firstride.engine.signal.SignalValue.Token
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class CourseRunnerTest {
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
}
