package com.firstride.engine.voice

import com.firstride.engine.course.ModelCourseText
import com.firstride.engine.course.StepText
import com.firstride.engine.judge.CourseRunner
import com.firstride.engine.judge.Phase
import com.firstride.engine.judge.StepRunner
import com.firstride.engine.judge.MemStore
import com.firstride.engine.judge.ac
import com.firstride.engine.judge.crs01Step
import com.firstride.engine.judge.defrost
import com.firstride.engine.judge.source
import com.firstride.engine.judge.twoStepCourse
import com.firstride.engine.signal.SignalValue.Token
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** FR-05: 상태가 바뀔 때 화면과 같은 문구를 읽는다. 화면 문구와 음성 문구는 같은 곳(StepView)에서 온다(POL-V-1). */
@OptIn(ExperimentalCoroutinesApi::class)
class NarrationTest {
    private val text = StepText(instruction = "버튼을 누르세요.", locatorText = "왼쪽 위에 있어요.", locatorAsset = "a.webp")

    private fun TestScope.voice(tts: FakeTts) = VoiceCoordinator(tts, clock = { testScheduler.currentTime })

    @Test fun `AC-05-1 단계 시작, 부분 충족, 완료의 문구를 차례로 읽는다`() = runTest {
        val src = source(ac = "OFF")
        val tts = FakeTts(durationMillis = 500)
        val v = voice(tts)
        val runner = StepRunner(crs01Step(), src)
        backgroundScope.launch { v.run() }
        backgroundScope.launch { narrateStep(runner, crs01Step(), text, v) }
        backgroundScope.launch { runner.run() }
        runCurrent()
        assertEquals(listOf("버튼을 누르세요."), tts.started)

        advanceTimeBy(600); src.set(defrost, Token("ON")); runCurrent()
        assertEquals("앞유리 제상 확인. 에어컨이 남았어요.", tts.started.last())

        advanceTimeBy(600); src.set(ac, Token("ON")); runCurrent()
        assertEquals(listOf("버튼을 누르세요.", "앞유리 제상 확인. 에어컨이 남았어요.", "좋아요."), tts.started)
    }

    @Test fun `AC-05-1 이미 목표 상태와 확인 불가의 문구를 읽는다`() = runTest {
        val tts = FakeTts(durationMillis = 100)
        val v = voice(tts)
        val runner = StepRunner(crs01Step(), source(defrost = "ON"))
        backgroundScope.launch { v.run() }
        backgroundScope.launch { narrateStep(runner, crs01Step(), text, v) }
        backgroundScope.launch { runner.run() }
        runCurrent()
        assertEquals(listOf("이미 목표 상태예요. 다른 상태로 바꾼 뒤 다시 해 보세요. 버튼을 누르세요."), tts.started)

        val broken = FakeTts(durationMillis = 100)
        val v2 = voice(broken)
        val failing = StepRunner(crs01Step(), com.firstride.engine.signal.FakeSignalSource())   // 값이 없다 → 확인 불가
        backgroundScope.launch { v2.run() }
        backgroundScope.launch { narrateStep(failing, crs01Step(), text, v2) }
        backgroundScope.launch { failing.run() }
        runCurrent()
        assertEquals(listOf("지금은 차량 상태를 확인할 수 없어요."), broken.started)
    }

    @Test fun `같은 상태에서 조건의 값만 바뀌면 다시 읽지 않는다`() = runTest {
        val src = source(ac = "OFF")
        val tts = FakeTts(durationMillis = 100)
        val v = voice(tts)
        val runner = StepRunner(crs01Step(), src)
        backgroundScope.launch { v.run() }
        backgroundScope.launch { narrateStep(runner, crs01Step(), text, v) }
        backgroundScope.launch { runner.run() }
        runCurrent()
        // WAITING 중에 유지 조건이 바뀌어도 같은 안내를 다시 읽지 않는다
        src.set(ac, Token("OFF")); src.set(com.firstride.engine.judge.recirc, Token("FRESH")); runCurrent()
        advanceTimeBy(5_000); runCurrent()
        assertEquals(listOf("버튼을 누르세요."), tts.started)
    }

    @Test fun `POL-V-5 힌트는 15초 뒤 위치 문구를 한 번 읽는다`() = runTest {
        val tts = FakeTts(durationMillis = 100)
        val v = voice(tts)
        val runner = StepRunner(crs01Step(), source(ac = "OFF"))
        backgroundScope.launch { v.run() }
        backgroundScope.launch { narrateStep(runner, crs01Step(), text, v) }
        backgroundScope.launch { runner.run() }
        runCurrent()
        advanceTimeBy(14_999); runCurrent()
        assertEquals(listOf("버튼을 누르세요."), tts.started)
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf("버튼을 누르세요.", "왼쪽 위에 있어요."), tts.started)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(2, tts.started.size)
    }

    @Test fun `AC-05-4 음성이 실패해도 판정과 진행은 막히지 않는다`() = runTest {
        val src = source(ac = "OFF")
        val tts = FakeTts().apply { failWith = SpeakResult.ERROR }
        val v = voice(tts)
        val runner = StepRunner(crs01Step(), src)
        backgroundScope.launch { v.run() }
        backgroundScope.launch { narrateStep(runner, crs01Step(), text, v) }
        backgroundScope.launch { runner.run() }
        runCurrent()
        src.set(defrost, Token("ON")); src.set(ac, Token("ON")); runCurrent()
        assertEquals(Phase.DONE, runner.state.value.phase)
        assertEquals(false, v.available.value)
    }

    @Test fun `코스를 따라가며 단계마다 그 단계의 안내를 읽는다`() = runTest {
        val src = source(ac = "OFF")
        val tts = FakeTts(durationMillis = 100)
        val v = voice(tts)
        val course = twoStepCourse()
        val texts = ModelCourseText(
            course.id,
            mapOf(
                "CRS-01-S1" to StepText("1단계 안내", locatorText = "l1", locatorAsset = "a"),
                "T-S2" to StepText("2단계 안내", locatorText = "l2", locatorAsset = "a"),
            ),
        )
        val runner = CourseRunner(course, src, MemStore())
        backgroundScope.launch { v.run() }
        backgroundScope.launch { narrateCourse(runner, course, texts, v) }
        backgroundScope.launch { runner.run() }
        runCurrent()
        assertEquals(listOf("1단계 안내"), tts.started)

        src.set(ac, Token("ON")); src.set(defrost, Token("ON")); runCurrent()
        advanceTimeBy(2_000); runCurrent()
        assertEquals(listOf("1단계 안내", "좋아요.", "2단계 안내"), tts.started)
    }
}
