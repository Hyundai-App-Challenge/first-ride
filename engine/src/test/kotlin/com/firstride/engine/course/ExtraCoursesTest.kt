package com.firstride.engine.course

import com.firstride.engine.judge.Phase
import com.firstride.engine.judge.StepRunner
import com.firstride.engine.judge.stepView
import com.firstride.engine.signal.FakeSignalSource
import com.firstride.engine.signal.SignalValue.Number
import com.firstride.engine.signal.SignalValue.Token
import com.firstride.engine.signal.Signals
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * CRS-02~06 정의가 수용 기준대로 판정되는지 가짜 신호원으로 본다. 판정 규칙은 reduce에 있고
 * 여기서는 "정의 파일이 그 규칙에 올바른 목표를 건넸는가"를 본다. 정의는 실제 assets에서 읽는다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExtraCoursesTest {
    private val assets = File("../app/src/main/assets")
    private fun course(id: String) = CourseParser.parseCourse(File(assets, "courses/$id.json").readText())
    private fun text(id: String) = CourseParser.parseModelText(File(assets, "models/reference/$id.json").readText())

    private val ON = Token("ON")
    private val OFF = Token("OFF")

    private fun TestScope.start(courseId: String, step: Int, src: FakeSignalSource): StepRunner {
        val runner = StepRunner(course(courseId).steps[step], src)
        backgroundScope.launch { runner.run() }
        runCurrent()
        return runner
    }

    private fun StepRunner.phase() = state.value.phase
    private fun StepRunner.message(courseId: String, step: Int): String {
        val def = course(courseId).steps[step]
        return stepView(state.value, def, text(courseId).steps[def.id]).message
    }

    private fun FakeSignalSource.change(runner: StepRunner, scope: TestScope, id: com.firstride.engine.signal.SignalId, v: com.firstride.engine.signal.SignalValue) {
        set(id, v)
        scope.runCurrent()
    }

    // ── CRS-03 ──

    @Test fun `AC-CRS03-1 하향등이 켜진 채로 S2를 시작하면 ALREADY_AT_TARGET이고 껐다 켜면 DONE이다`() = runTest {
        val src = FakeSignalSource().apply { set(Signals.LIGHT_TAIL_LAMP, ON); set(Signals.LIGHT_LOW_BEAM, ON) }
        val r = start("CRS-03", 1, src)
        assertEquals(Phase.ALREADY_AT_TARGET, r.phase())
        src.change(r, this, Signals.LIGHT_LOW_BEAM, OFF)
        assertEquals(Phase.WAITING, r.phase())
        src.change(r, this, Signals.LIGHT_LOW_BEAM, ON)
        assertEquals(Phase.DONE, r.phase())
    }

    @Test fun `AC-CRS03-2 하향등 모드가 미등과 하향등을 함께 켜도 S1과 S2가 각각 판정된다`() = runTest {
        // S1: 둘 다 꺼진 채 시작해 모드를 Low beam으로 바꾸면 미등이 켜진 것으로 완료다
        val a = FakeSignalSource().apply { set(Signals.LIGHT_TAIL_LAMP, OFF); set(Signals.LIGHT_LOW_BEAM, OFF) }
        val s1 = start("CRS-03", 0, a)
        assertEquals(Phase.WAITING, s1.phase())
        a.set(Signals.LIGHT_TAIL_LAMP, ON); a.set(Signals.LIGHT_LOW_BEAM, ON); runCurrent()
        assertEquals(Phase.DONE, s1.phase())

        // S2: 미등만 켜진 채 시작(미등 모드)해 하향등을 켜면 완료다
        val b = FakeSignalSource().apply { set(Signals.LIGHT_TAIL_LAMP, ON); set(Signals.LIGHT_LOW_BEAM, OFF) }
        val s2 = start("CRS-03", 1, b)
        assertEquals(Phase.WAITING, s2.phase())
        b.change(s2, this, Signals.LIGHT_LOW_BEAM, ON)
        assertEquals(Phase.DONE, s2.phase())
    }

    @Test fun `AC-CRS03-2 S3은 하향등이 꺼지면 완료이고 미등은 보지 않는다`() = runTest {
        val src = FakeSignalSource().apply { set(Signals.LIGHT_TAIL_LAMP, ON); set(Signals.LIGHT_LOW_BEAM, ON) }
        val r = start("CRS-03", 2, src)
        assertEquals(Phase.WAITING, r.phase())
        src.change(r, this, Signals.LIGHT_LOW_BEAM, OFF)
        assertEquals(Phase.DONE, r.phase())
    }

    // ── CRS-04 ──

    @Test fun `AC-CRS04-1 S1에서 High나 Auto로 바꾸면 완료가 아니라 Low 안내가 나온다`() = runTest {
        val src = FakeSignalSource().apply { set(Signals.WIPER_STATE, Token("OFF")) }
        val r = start("CRS-04", 0, src)
        assertEquals(Phase.WAITING, r.phase())
        src.change(r, this, Signals.WIPER_STATE, Token("HIGH"))
        assertEquals(Phase.CORRECTION, r.phase())
        assertTrue("LOW" in r.message("CRS-04", 0))
        src.change(r, this, Signals.WIPER_STATE, Token("AUTO"))
        assertEquals(Phase.CORRECTION, r.phase())
        src.change(r, this, Signals.WIPER_STATE, Token("LOW"))
        assertEquals(Phase.DONE, r.phase())
    }

    @Test fun `AC-CRS04-1 간헐 등 그 밖의 단은 완료도 오조작도 아니다`() = runTest {
        val src = FakeSignalSource().apply { set(Signals.WIPER_STATE, Token("OFF")) }
        val r = start("CRS-04", 0, src)
        src.change(r, this, Signals.WIPER_STATE, Token("INT"))
        assertEquals(Phase.WAITING, r.phase())
    }

    @Test fun `CRS-04 S2는 와이퍼가 꺼지면 완료다`() = runTest {
        val src = FakeSignalSource().apply { set(Signals.WIPER_STATE, Token("LOW")) }
        val r = start("CRS-04", 1, src)
        assertEquals(Phase.WAITING, r.phase())
        src.change(r, this, Signals.WIPER_STATE, Token("OFF"))
        assertEquals(Phase.DONE, r.phase())
    }

    // ── CRS-05 ──

    @Test fun `AC-CRS05-1 S1에서 오른쪽을 켜면 완료가 아니라 교정 안내가 나온다`() = runTest {
        val src = FakeSignalSource().apply { set(Signals.TURN_SIGNAL_STATE, Token("OFF")) }
        val r = start("CRS-05", 0, src)
        src.change(r, this, Signals.TURN_SIGNAL_STATE, Token("RIGHT"))
        assertEquals(Phase.CORRECTION, r.phase())
        assertTrue("반대쪽" in r.message("CRS-05", 0))
        src.change(r, this, Signals.TURN_SIGNAL_STATE, Token("LEFT"))
        assertEquals(Phase.DONE, r.phase())
    }

    @Test fun `AC-CRS05-1 S2는 왼쪽이 켜진 채 시작해도 교정을 내지 않고 오른쪽이 되면 완료다`() = runTest {
        val src = FakeSignalSource().apply { set(Signals.TURN_SIGNAL_STATE, Token("LEFT")) }
        val r = start("CRS-05", 1, src)
        assertEquals(Phase.WAITING, r.phase())          // POL-J-16: 시작 값 그대로면 교정하지 않는다
        src.change(r, this, Signals.TURN_SIGNAL_STATE, Token("RIGHT"))
        assertEquals(Phase.DONE, r.phase())
    }

    // ── CRS-02 ──

    @Test fun `AC-CRS02-1 S2에서 풍량이 시작 값보다 낮아지면 완료되지 않고 높아지면 완료다`() = runTest {
        val src = FakeSignalSource().apply { set(Signals.HVAC_FAN_SPEED, Number(3)); setRange(Signals.HVAC_FAN_SPEED, 1..8) }
        val r = start("CRS-02", 1, src)
        assertEquals(Phase.WAITING, r.phase())
        assertEquals(text("CRS-02").steps["CRS-02-S2"]!!.instruction, r.message("CRS-02", 1))
        src.change(r, this, Signals.HVAC_FAN_SPEED, Number(2))
        assertEquals(Phase.WAITING, r.phase())
        src.change(r, this, Signals.HVAC_FAN_SPEED, Number(4))
        assertEquals(Phase.DONE, r.phase())
    }

    @Test fun `POL-J-10 AC-CRS02-1 풍량이 최대인 채 시작하면 내리는 안내로 바뀌고 내리면 완료다`() = runTest {
        val src = FakeSignalSource().apply { set(Signals.HVAC_FAN_SPEED, Number(8)); setRange(Signals.HVAC_FAN_SPEED, 1..8) }
        val r = start("CRS-02", 1, src)
        assertEquals(Phase.WAITING, r.phase())
        assertEquals(text("CRS-02").steps["CRS-02-S2"]!!.instructionAtLimit, r.message("CRS-02", 1))
        src.change(r, this, Signals.HVAC_FAN_SPEED, Number(7))
        assertEquals(Phase.DONE, r.phase())
    }

    @Test fun `POL-J-10 범위를 모르면 방향을 뒤집지 않고 원래 안내를 보인다`() = runTest {
        val src = FakeSignalSource().apply { set(Signals.HVAC_FAN_SPEED, Number(8)) }
        val r = start("CRS-02", 1, src)
        assertEquals(text("CRS-02").steps["CRS-02-S2"]!!.instruction, r.message("CRS-02", 1))
        src.change(r, this, Signals.HVAC_FAN_SPEED, Number(7))
        assertEquals(Phase.WAITING, r.phase())
    }

    @Test fun `POL-J-10 최소 값에서 시작해도 올리는 안내 그대로다`() = runTest {
        val src = FakeSignalSource().apply { set(Signals.HVAC_FAN_SPEED, Number(1)); setRange(Signals.HVAC_FAN_SPEED, 1..8) }
        val r = start("CRS-02", 1, src)
        assertEquals(text("CRS-02").steps["CRS-02-S2"]!!.instruction, r.message("CRS-02", 1))
        src.change(r, this, Signals.HVAC_FAN_SPEED, Number(2))
        assertEquals(Phase.DONE, r.phase())
    }

    @Test fun `AC-CRS02-2 S3의 풍향은 폴링이라 확인 중으로 표시되고 앞유리로 바뀌면 완료다`() = runTest {
        val src = FakeSignalSource().apply {
            set(Signals.HVAC_FAN_DIRECTION, Token("FACE")); setPolled(Signals.HVAC_FAN_DIRECTION, true)
        }
        val r = start("CRS-02", 2, src)
        assertTrue(r.state.value.conditions.single().polled)
        src.change(r, this, Signals.HVAC_FAN_DIRECTION, Token("FLOOR"))
        assertEquals(Phase.WAITING, r.phase())
        src.change(r, this, Signals.HVAC_FAN_DIRECTION, Token("DEFROST"))
        assertEquals(Phase.DONE, r.phase())
    }

    @Test fun `CRS-02 S1은 뒷유리 열선을 껐다 켜야 완료다`() = runTest {
        val src = FakeSignalSource().apply { set(Signals.HVAC_REAR_DEFROSTER, ON) }
        val r = start("CRS-02", 0, src)
        assertEquals(Phase.ALREADY_AT_TARGET, r.phase())
        src.change(r, this, Signals.HVAC_REAR_DEFROSTER, OFF)
        src.change(r, this, Signals.HVAC_REAR_DEFROSTER, ON)
        assertEquals(Phase.DONE, r.phase())
    }

    // ── CRS-06 ──

    @Test fun `AC-CRS06-1 운전석 창문 값이 그대로면 완료되지 않고 열림 방향으로 커지면 완료다`() = runTest {
        // 동승석 창문을 바꿔도 운전석 값은 오지 않는다(실측 T6). 그래서 운전석 신호가 안 바뀐 채다.
        val src = FakeSignalSource().apply { set(Signals.WINDOW_DRIVER_POSITION, Number(5)); setRange(Signals.WINDOW_DRIVER_POSITION, 0..10) }
        val r = start("CRS-06", 0, src)
        assertEquals(Phase.WAITING, r.phase())
        src.change(r, this, Signals.WINDOW_DRIVER_POSITION, Number(4))   // 닫는 쪽
        assertEquals(Phase.WAITING, r.phase())
        src.change(r, this, Signals.WINDOW_DRIVER_POSITION, Number(6))
        assertEquals(Phase.DONE, r.phase())
    }

    @Test fun `POL-J-10 운전석 창문이 끝까지 열린 채 시작하면 올리는 안내로 바뀐다`() = runTest {
        val src = FakeSignalSource().apply { set(Signals.WINDOW_DRIVER_POSITION, Number(10)); setRange(Signals.WINDOW_DRIVER_POSITION, 0..10) }
        val r = start("CRS-06", 0, src)
        assertEquals(text("CRS-06").steps["CRS-06-S1"]!!.instructionAtLimit, r.message("CRS-06", 0))
        src.change(r, this, Signals.WINDOW_DRIVER_POSITION, Number(9))
        assertEquals(Phase.DONE, r.phase())
    }

    @Test fun `CRS-06 S2는 창문 잠금이 켜지면, S3은 도어가 잠기면 완료다`() = runTest {
        val a = FakeSignalSource().apply { set(Signals.WINDOW_LOCK, OFF) }
        val s2 = start("CRS-06", 1, a)
        a.change(s2, this, Signals.WINDOW_LOCK, ON)
        assertEquals(Phase.DONE, s2.phase())

        val b = FakeSignalSource().apply { set(Signals.DOOR_LOCK, Token("UNLOCKED")) }
        val s3 = start("CRS-06", 2, b)
        assertEquals(Phase.WAITING, s3.phase())
        b.change(s3, this, Signals.DOOR_LOCK, Token("LOCKED"))
        assertEquals(Phase.DONE, s3.phase())
    }

    // ── 정의 ──

    @Test fun `FR-08 새 코스 5개가 assets에 있고 단계 수가 명세와 같다`() {
        val steps = listOf("CRS-02", "CRS-03", "CRS-04", "CRS-05", "CRS-06").associateWith { course(it).steps.size }
        assertEquals(mapOf("CRS-02" to 3, "CRS-03" to 3, "CRS-04" to 2, "CRS-05" to 2, "CRS-06" to 3), steps)
    }

    @Test fun `POL-J-10 상대 목표 단계에 instructionAtLimit이 없으면 검증 오류다`() {
        val c = course("CRS-02")
        val broken = ModelCourseText("CRS-02", text("CRS-02").steps.mapValues { it.value.copy(instructionAtLimit = null) })
        val errors = CourseValidator.validate(c, broken) { true }
        assertEquals(listOf("CRS-02-S2"), errors.map { it.where })
        assertNotEquals(emptyList(), errors)
    }
}
