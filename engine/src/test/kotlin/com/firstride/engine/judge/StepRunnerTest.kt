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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.hours

/** L2: 가짜 신호원과 가상 시간으로 시간 처리와 구독 정리를 확인한다. */
@OptIn(ExperimentalCoroutinesApi::class)
class StepRunnerTest {
    private val on = Token("ON")
    private val off = Token("OFF")

    private class HangingRead(delegate: SignalSource) : SignalSource by delegate {
        override suspend fun read(id: SignalId): Reading = awaitCancellation()
    }

    @Test fun `AC-04-4 AC-02-13 완료 2초 뒤에 끝나고 그동안 신호가 바뀌어도 DONE이다`() = runTest {
        val src = source()
        val runner = StepRunner(crs01Step(), src)
        val result = backgroundScope.async { runner.run() }
        runCurrent()
        assertEquals(Phase.WAITING, runner.state.value.phase)

        src.set(defrost, on); runCurrent()
        assertEquals(Phase.DONE, runner.state.value.phase)

        src.set(defrost, off)
        advanceTimeBy(1_999); runCurrent()
        assertTrue(!result.isCompleted)
        assertEquals(Phase.DONE, runner.state.value.phase)

        advanceTimeBy(1); runCurrent()
        assertEquals(Phase.DONE, result.await())
    }

    @Test fun `AC-02-8 POL-J-19 초기 확인이 3초 안에 끝나지 않으면 UNVERIFIABLE이다`() = runTest {
        val runner = StepRunner(crs01Step(), HangingRead(source()))
        backgroundScope.launch { runner.run() }
        runCurrent()
        advanceTimeBy(2_999); runCurrent()
        assertEquals(Phase.PRECHECK, runner.state.value.phase)
        advanceTimeBy(1); runCurrent()
        assertEquals(Phase.UNVERIFIABLE, runner.state.value.phase)
    }

    @Test fun `AC-02-7 POL-J-20 확인 불가에서 다시 확인하면 처음부터 읽고 진행한다`() = runTest {
        val src = source().apply { fail(recirc, FailReason.TIMEOUT) }
        val runner = StepRunner(crs01Step(), src)
        backgroundScope.launch { runner.run() }
        runCurrent()
        assertEquals(Phase.UNVERIFIABLE, runner.state.value.phase)

        // 자동으로 다시 시도하지 않는다
        advanceTimeBy(60_000); runCurrent()
        assertEquals(Phase.UNVERIFIABLE, runner.state.value.phase)

        src.set(recirc, Token("FRESH"))
        runner.retry(); runCurrent()
        assertEquals(Phase.WAITING, runner.state.value.phase)
        src.set(defrost, on); runCurrent()
        assertEquals(Phase.DONE, runner.state.value.phase)
    }

    @Test fun `AC-02-7 POL-R-2 확인 불가에서 건너뛰면 SKIPPED로 끝난다`() = runTest {
        val src = source().apply { fail(recirc, FailReason.CONNECTION) }
        val runner = StepRunner(crs01Step(), src)
        val result = backgroundScope.async { runner.run() }
        runCurrent()
        runner.skip(); runCurrent()
        assertEquals(Phase.SKIPPED, result.await())
    }

    @Test fun `AC-02-10 단계가 도는 동안만 구독하고 끝나면 모두 해제한다`() = runTest {
        val src = source()
        val runner = StepRunner(crs01Step(), src)
        val result = backgroundScope.async { runner.run() }
        runCurrent()
        assertEquals(3, src.activeSubscriptions)      // 제상, 에어컨, 순환
        src.set(defrost, on); runCurrent()
        assertEquals(0, src.activeSubscriptions)      // DONE 즉시. 2초를 기다리지 않는다
        advanceTimeBy(2_000); runCurrent()
        result.await()
        assertEquals(0, src.activeSubscriptions)
    }

    @Test fun `AC-02-10 건너뛰기와 취소에서도 구독이 해제된다`() = runTest {
        val skipSrc = source()
        val skipRunner = StepRunner(crs01Step(), skipSrc)
        backgroundScope.launch { skipRunner.run() }
        runCurrent()
        skipRunner.skip(); runCurrent()
        assertEquals(0, skipSrc.activeSubscriptions)

        val cancelSrc = source()
        val job = backgroundScope.launch { StepRunner(crs01Step(), cancelSrc).run() }
        runCurrent()
        assertEquals(3, cancelSrc.activeSubscriptions)
        job.cancel(); runCurrent()
        assertEquals(0, cancelSrc.activeSubscriptions)
    }

    @Test fun `POL-J-22 진행 중 신호가 끊기면 UNVERIFIABLE이고 구독을 해제한다`() = runTest {
        val src = source()
        val runner = StepRunner(crs01Step(), src)
        backgroundScope.launch { runner.run() }
        runCurrent()
        src.fail(ac, FailReason.CONNECTION); runCurrent()
        assertEquals(Phase.UNVERIFIABLE, runner.state.value.phase)
        assertEquals(0, src.activeSubscriptions)
    }

    @Test fun `POL-V-5 힌트는 15초 뒤 한 번만 낸다`() = runTest {
        val src = source()
        val runner = StepRunner(crs01Step(), src)
        val hints = mutableListOf<Unit>()
        backgroundScope.launch { runner.hints.collect { hints += it } }
        backgroundScope.launch { runner.run() }
        runCurrent()
        advanceTimeBy(14_999); runCurrent()
        assertEquals(0, hints.size)
        advanceTimeBy(1); runCurrent()
        assertEquals(1, hints.size)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(1, hints.size)
    }

    @Test fun `POL-V-5 상태가 바뀌면 15초를 다시 센다`() = runTest {
        val src = source(ac = "OFF")
        val runner = StepRunner(crs01Step(), src)
        val hints = mutableListOf<Unit>()
        backgroundScope.launch { runner.hints.collect { hints += it } }
        backgroundScope.launch { runner.run() }
        runCurrent()
        advanceTimeBy(10_000); runCurrent()
        src.set(ac, on); runCurrent()                  // 유지 조건이 바뀜 → 타이머 재시작
        advanceTimeBy(14_999); runCurrent()
        assertEquals(0, hints.size)
        advanceTimeBy(1); runCurrent()
        assertEquals(1, hints.size)
    }

    @Test fun `POL-J-12 이미 목표 상태는 시간이 지나도 완료되지 않고 힌트도 없다`() = runTest {
        val src = source(defrost = "ON")
        val runner = StepRunner(crs01Step(), src)
        val hints = mutableListOf<Unit>()
        backgroundScope.launch { runner.hints.collect { hints += it } }
        val result = backgroundScope.async { runner.run() }
        runCurrent()
        assertEquals(Phase.ALREADY_AT_TARGET, runner.state.value.phase)
        advanceTimeBy(1.hours); runCurrent()
        assertEquals(Phase.ALREADY_AT_TARGET, runner.state.value.phase)
        assertTrue(!result.isCompleted)
        assertEquals(0, hints.size)
    }

    @Test fun `POL-J-25 폴링하는 신호는 조건에 표시된다`() = runTest {
        val src = source().apply { setPolled(ac, true) }
        val runner = StepRunner(crs01Step(), src)
        backgroundScope.launch { runner.run() }
        runCurrent()
        val polled = runner.state.value.conditions.associate { it.def.signal to it.polled }
        assertEquals(mapOf(defrost to false, ac to true, recirc to false), polled)
    }
}
