package com.firstride.app

import com.firstride.engine.course.CourseDefinition
import com.firstride.engine.course.CourseParser
import com.firstride.engine.course.Kind
import com.firstride.engine.course.StepDefinition
import com.firstride.engine.course.Target
import com.firstride.engine.judge.Phase
import com.firstride.engine.judge.StepRunner
import com.firstride.engine.judge.followUpStep
import com.firstride.engine.signal.FailReason
import com.firstride.engine.signal.FakeSignalSource
import com.firstride.engine.signal.Reading
import com.firstride.engine.signal.SignalValue.Token
import com.firstride.engine.signal.Signals
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * debug의 "지금 단계 조작"이 실제 코스 정의의 모든 단계를 신호로 완료시키는지 본다.
 * 값만 바꾸고 판정은 실행기가 한다. 가짜 신호원의 시작 값은 debug 빌드와 같다([seed]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StepOperatorTest {
    private val courses: List<CourseDefinition> = File("src/main/assets/courses").listFiles { f -> f.extension == "json" }!!
        .sortedBy { it.name }.map { CourseParser.parseCourse(it.readText()) }

    private fun fake() = FakeSignalSource().also(::seed)

    private fun TestScope.start(def: StepDefinition, src: FakeSignalSource): Pair<StepRunner, Deferred<Phase>> {
        val runner = StepRunner(def, src)
        val result = backgroundScope.async { runner.run() }
        runCurrent()
        return runner to result
    }

    @Test fun `모든 코스의 모든 단계가 조작 한 번으로 완료된다`() = runTest {
        assertEquals(6, courses.size)
        for (course in courses) {
            val src = fake()                       // 코스 하나를 이어서 한다. 앞 단계가 바꾼 값에서 다음 단계가 시작한다
            for (def in course.steps) {
                val (runner, result) = start(def, src)
                assertTrue(StepOperator.operate(runner, src), "${def.id}: 조작할 수 없음 (${runner.state.value.phase})")
                assertEquals(Phase.DONE, result.await(), def.id)
                // 완료로 바꾼 것이 아니라 신호 값이 목표가 됐다
                for (c in def.action) (c.target as? Target.Equals)?.let { assertEquals(Reading.Value(it.value), src.read(c.signal), def.id) }
            }
        }
    }

    @Test fun `후속 조치 단계도 조작 한 번으로 완료된다`() = runTest {
        val followUps = courses.flatMap { it.followUps }
        assertTrue(followUps.isNotEmpty())
        for (f in followUps) {
            val src = fake()
            val (runner, result) = start(followUpStep(f), src)
            assertTrue(StepOperator.operate(runner, src), "${f.id}: 조작할 수 없음 (${runner.state.value.phase})")
            assertEquals(Phase.DONE, result.await(), f.id)
        }
    }

    @Test fun `이미 목표 상태면 벗어났다가 돌아와 전이로 완료된다`() = runTest {
        val def = courses.first { it.id == "CRS-01" }.steps.first()
        val action = def.action.single()
        val src = fake().apply { set(action.signal, (action.target as Target.Equals).value) }
        val (runner, result) = start(def, src)
        assertEquals(Phase.ALREADY_AT_TARGET, runner.state.value.phase)

        val plan = StepOperator.plan(runner.state.value)!!
        assertEquals(setOf(action.signal), plan.away.keys)
        assertTrue(StepOperator.operate(runner, src))
        assertEquals(Phase.DONE, result.await())
        assertTrue(runner.state.value.conditions.filter { it.def.kind == Kind.ACTION }.all { it.seenOffTarget })
    }

    @Test fun `확인 불가에서는 아무 값도 쓰지 않는다`() = runTest {
        val def = courses.first { it.id == "CRS-01" }.steps.first()
        val src = fake().apply { fail(def.hold.first().signal, FailReason.TIMEOUT) }
        val (runner, _) = start(def, src)
        assertEquals(Phase.UNVERIFIABLE, runner.state.value.phase)

        assertNull(StepOperator.plan(runner.state.value))
        assertFalse(StepOperator.operate(runner, src))
        assertEquals(Reading.Value(Token("OFF")), src.read(Signals.HVAC_FRONT_DEFROSTER))
    }

    @Test fun `끝난 단계에는 아무 값도 쓰지 않는다`() = runTest {
        val def = courses.first { it.id == "CRS-02" }.steps.first()
        val src = fake()
        val (runner, result) = start(def, src)
        assertTrue(StepOperator.operate(runner, src))
        assertEquals(Phase.DONE, result.await())
        assertFalse(StepOperator.operate(runner, src))
    }
}
