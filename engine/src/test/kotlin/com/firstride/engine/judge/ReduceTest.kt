package com.firstride.engine.judge

import com.firstride.engine.course.ConditionDef
import com.firstride.engine.course.Direction
import com.firstride.engine.course.Kind
import com.firstride.engine.course.MisoperationDef
import com.firstride.engine.course.StepDefinition
import com.firstride.engine.course.Target
import com.firstride.engine.signal.FailReason
import com.firstride.engine.signal.Reading
import com.firstride.engine.signal.SignalId
import com.firstride.engine.signal.SignalValue
import com.firstride.engine.signal.SignalValue.Number
import com.firstride.engine.signal.SignalValue.Token
import com.firstride.engine.signal.Signals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** L1: reduce의 규칙을 정책 하나에 테스트 하나 이상으로 확인한다. */
class ReduceTest {
    private val defrost = Signals.HVAC_FRONT_DEFROSTER
    private val ac = Signals.HVAC_AC
    private val recirc = Signals.HVAC_RECIRCULATION

    private val recircFix = MisoperationDef(
        "recirc", recirc, Target.Equals(Token("RECIRCULATE")), "내기 순환이 켜져 있어요.",
    )

    // CRS-01의 S1
    private val crs01 = StepDefinition(
        id = "CRS-01-S1",
        action = listOf(cond(defrost, Target.Equals(Token("ON")), Kind.ACTION)),
        hold = listOf(
            cond(ac, Target.Equals(Token("ON")), Kind.HOLD),
            cond(recirc, Target.Equals(Token("FRESH")), Kind.HOLD),
        ),
        misoperations = listOf(recircFix),
        doneText = "좋아요.",
    )

    private fun cond(id: SignalId, t: Target, kind: Kind) = ConditionDef(id, t, id.key, kind)
    private fun tok(s: String) = Token(s)
    private fun value(v: SignalValue): Reading = Reading.Value(v)

    private fun start(
        step: StepDefinition = crs01,
        ranges: Map<SignalId, IntRange?> = emptyMap(),
        init: Array<Pair<SignalId, SignalValue>>,
    ): StepState = reduce(
        initialState(step),
        StepEvent.Prechecked(init.associate { (id, v) -> id to value(v) }, ranges),
    )

    private fun crs01Start(defrost: String, ac: String, recirc: String) =
        start(init = arrayOf(this.defrost to tok(defrost), this.ac to tok(ac), this.recirc to tok(recirc)))

    private fun StepState.set(id: SignalId, v: SignalValue) = reduce(this, StepEvent.Changed(id, value(v)))
    private fun StepState.set(id: SignalId, v: String) = set(id, tok(v))
    private fun StepState.cond(id: SignalId) = conditions.first { it.def.signal == id }

    // ── 전이와 완료 ──

    @Test fun `POL-J-3 AC-02-3 껐다가 다시 켜야 충족이다`() {
        var s = crs01Start("ON", "ON", "FRESH")
        assertEquals(Phase.ALREADY_AT_TARGET, s.phase)
        s = s.set(defrost, "OFF")
        assertEquals(Phase.WAITING, s.phase)
        s = s.set(defrost, "ON")
        assertEquals(Phase.DONE, s.phase)
    }

    @Test fun `POL-J-4 AC-02-12 충족은 고정되지 않는다`() {
        var s = crs01Start("OFF", "OFF", "FRESH").set(defrost, "ON")
        assertEquals(Phase.PARTIAL, s.phase)
        s = s.set(defrost, "OFF")
        assertEquals(Phase.WAITING, s.phase)
        s = s.set(ac, "ON").set(defrost, "ON")
        assertEquals(Phase.DONE, s.phase)
    }

    @Test fun `POL-J-4 켠 뒤 다시 끈 것은 완료가 아니다`() {
        // 유지 조건이 남아 있는 동안 끄면 완료로 굳지 않는다 (유지 조건이 다 맞으면 켠 순간 DONE이라 이 경우가 아니다)
        val s = crs01Start("OFF", "OFF", "FRESH").set(defrost, "ON").set(defrost, "OFF").set(ac, "ON")
        assertEquals(Phase.WAITING, s.phase)
    }

    @Test fun `POL-J-6 유지 조건이 나중에 맞아도 같은 순간 모두 참이면 DONE이다`() {
        val s = crs01Start("OFF", "OFF", "FRESH").set(defrost, "ON").set(ac, "ON")
        assertEquals(Phase.DONE, s.phase)
    }

    @Test fun `POL-J-7 유지 조건이 없는 단계는 전이 순간 완료다`() {
        val step = crs01.copy(hold = emptyList(), misoperations = emptyList())
        val s = start(step, init = arrayOf(defrost to tok("OFF"))).set(defrost, "ON")
        assertEquals(Phase.DONE, s.phase)
    }

    @Test fun `POL-J-8 AC-02-13 완료는 되돌리지 않는다`() {
        val done = crs01Start("OFF", "ON", "FRESH").set(defrost, "ON")
        assertEquals(Phase.DONE, done.phase)
        assertEquals(done, done.set(defrost, "OFF").set(recirc, "RECIRCULATE"))
        assertEquals(done, reduce(done, StepEvent.Skip))
        assertEquals(done, reduce(done, StepEvent.PrecheckTimedOut))
    }

    // ── 이미 목표 상태 ──

    @Test fun `POL-J-11 J-12 AC-02-2 AC-CRS01-4 시작할 때 이미 목표면 ALREADY_AT_TARGET이다`() {
        val s = crs01Start("ON", "ON", "FRESH")
        assertEquals(Phase.ALREADY_AT_TARGET, s.phase)
        // 같은 값이 다시 들어와도, 관계없는 신호가 바뀌어도 완료되지 않는다
        assertEquals(Phase.ALREADY_AT_TARGET, s.set(defrost, "ON").set(Signals.LIGHT_CABIN, "ON").phase)
    }

    @Test fun `POL-J-13 AC-02-5 AC-CRS01-3 유지 조건만 이미 맞은 것은 ALREADY_AT_TARGET이 아니다`() {
        val s = crs01Start("OFF", "ON", "FRESH")
        assertEquals(Phase.WAITING, s.phase)
        assertTrue(s.cond(ac).satisfied)
        assertEquals(Phase.DONE, s.set(defrost, "ON").phase)
    }

    // ── 조합 ──

    @Test fun `AC-02-4 AC-CRS01-1 조작은 됐고 유지가 남으면 PARTIAL이고 조건별 충족이 구분된다`() {
        val s = crs01Start("OFF", "OFF", "FRESH").set(defrost, "ON")
        assertEquals(Phase.PARTIAL, s.phase)
        assertTrue(s.cond(defrost).satisfied)
        assertTrue(!s.cond(ac).satisfied)
        assertTrue(s.cond(recirc).satisfied)
    }

    @Test fun `POL-J-9 AC-02-11 단계와 무관한 신호는 상태를 바꾸지 않는다`() {
        val s = crs01Start("OFF", "OFF", "FRESH")
        assertEquals(s, s.set(Signals.WIPER_STATE, "HIGH"))
        assertEquals(s, s.set(Signals.HVAC_FAN_SPEED, Number(5)))
    }

    // ── 오조작 ──

    @Test fun `POL-J-15a AC-CRS01-2 조작이 충족됐는데 오조작이 남아 있으면 CORRECTION이다`() {
        val s = crs01Start("OFF", "OFF", "RECIRCULATE").set(defrost, "ON")
        assertEquals(Phase.CORRECTION, s.phase)
        assertEquals(recircFix, s.correction)
    }

    @Test fun `POL-J-15b AC-CRS01-2 진행 중 신호가 오조작 값으로 바뀌면 CORRECTION이다`() {
        val partial = crs01Start("OFF", "OFF", "FRESH").set(defrost, "ON")
        assertEquals(Phase.CORRECTION, partial.set(recirc, "RECIRCULATE").phase)
        // 조작 전이 없어도 오조작 신호가 시작 값에서 바뀌었으면 낸다
        assertEquals(Phase.CORRECTION, crs01Start("OFF", "ON", "FRESH").set(recirc, "RECIRCULATE").phase)
    }

    @Test fun `POL-J-16 AC-CRS01-2c 시작부터 오조작이고 조작 전이가 없으면 교정을 내지 않는다`() {
        val s = crs01Start("OFF", "ON", "RECIRCULATE")
        assertEquals(Phase.WAITING, s.phase)
        assertNull(s.correction)
    }

    @Test fun `POL-J-18 AC-02-6 AC-CRS01-2b 오조작이 풀리면 교정이 사라지고 조건이 맞으면 DONE이다`() {
        var s = crs01Start("OFF", "OFF", "FRESH").set(defrost, "ON").set(recirc, "RECIRCULATE")
        assertEquals(Phase.CORRECTION, s.phase)
        s = s.set(recirc, "FRESH")
        assertEquals(Phase.PARTIAL, s.phase)
        assertNull(s.correction)
        assertEquals(Phase.DONE, s.set(ac, "ON").phase)
    }

    @Test fun `POL-J-14 정의에 없는 조작에는 반응하지 않는다`() {
        val s = crs01Start("OFF", "ON", "FRESH").set(Signals.HVAC_FAN_SPEED, Number(8)).set(Signals.LIGHT_LOW_BEAM, "ON")
        assertEquals(Phase.WAITING, s.phase)
        assertNull(s.correction)
    }

    @Test fun `POL-J-6 DONE은 오조작보다 우선한다`() {
        // 유지 조건이 오조작 값과 겹치지 않는 경우: 모두 충족이면 교정 없이 DONE
        val s = crs01Start("OFF", "ON", "FRESH").set(defrost, "ON")
        assertEquals(Phase.DONE, s.phase)
        assertNull(s.correction)
    }

    // ── 확인 불가 ──

    @Test fun `POL-J-19 AC-02-8 초기 확인이 시간 안에 끝나지 않으면 UNVERIFIABLE이다`() {
        val s = reduce(initialState(crs01), StepEvent.PrecheckTimedOut)
        assertEquals(Phase.UNVERIFIABLE, s.phase)
    }

    @Test fun `POL-J-21 AC-02-14 조건 하나만 읽지 못해도 UNVERIFIABLE이다`() {
        val s = reduce(
            initialState(crs01),
            StepEvent.Prechecked(
                mapOf(
                    defrost to value(tok("OFF")),
                    ac to value(tok("ON")),
                    recirc to Reading.Failed(FailReason.TIMEOUT),
                ),
            ),
        )
        assertEquals(Phase.UNVERIFIABLE, s.phase)
    }

    @Test fun `POL-P-4 POL-J-21 읽은 값이 없는 신호는 꺼짐이 아니라 실패다`() {
        val s = reduce(
            initialState(crs01),
            StepEvent.Prechecked(mapOf(defrost to value(tok("OFF")), ac to value(tok("ON")))),   // recirc 없음
        )
        assertEquals(Phase.UNVERIFIABLE, s.phase)
    }

    @Test fun `POL-J-22 진행 중 신호가 끊기면 충족 상태를 버리고 UNVERIFIABLE이다`() {
        val s = crs01Start("OFF", "ON", "FRESH").set(ac, "OFF")
            .let { reduce(it, StepEvent.Changed(defrost, Reading.Failed(FailReason.CONNECTION))) }
        assertEquals(Phase.UNVERIFIABLE, s.phase)
        // 그 뒤의 변화는 쓰지 않는다
        assertEquals(s, s.set(defrost, "ON"))
    }

    @Test fun `POL-J-20 다시 확인은 처음부터 다시 읽는다`() {
        val failed = reduce(initialState(crs01), StepEvent.PrecheckTimedOut)
        val retry = reduce(failed, StepEvent.Retry)
        assertEquals(Phase.PRECHECK, retry.phase)
        assertTrue(retry.conditions.all { it.current == null && !it.failed && !it.seenOffTarget })
        // 재시도 뒤에 값이 오면 이전 값과 무관하게 새로 판정한다
        val s = reduce(retry, StepEvent.Prechecked(mapOf(defrost to value(tok("ON")), ac to value(tok("ON")), recirc to value(tok("FRESH")))))
        assertEquals(Phase.ALREADY_AT_TARGET, s.phase)
    }

    @Test fun `다시 확인은 UNVERIFIABLE이 아닐 때 아무 일도 하지 않는다`() {
        val s = crs01Start("OFF", "ON", "FRESH")
        assertEquals(s, reduce(s, StepEvent.Retry))
    }

    @Test fun `AC-02-7 건너뛰면 SKIPPED다`() {
        assertEquals(Phase.SKIPPED, reduce(reduce(initialState(crs01), StepEvent.PrecheckTimedOut), StepEvent.Skip).phase)
        assertEquals(Phase.SKIPPED, reduce(crs01Start("ON", "ON", "FRESH"), StepEvent.Skip).phase)
    }

    @Test fun `확인 중에 온 변화와 늦게 온 초기 확인 결과는 무시한다`() {
        val pre = initialState(crs01)
        assertEquals(pre, reduce(pre, StepEvent.Changed(defrost, value(tok("ON")))))
        val timedOut = reduce(pre, StepEvent.PrecheckTimedOut)
        val late = reduce(timedOut, StepEvent.Prechecked(mapOf(defrost to value(tok("OFF")))))
        assertEquals(timedOut, late)
    }

    // ── 목표의 종류 ──

    private val fan = Signals.HVAC_FAN_SPEED

    @Test fun `POL-J-10 상대 목표는 시작 값 기준이다`() {
        val step = StepDefinition(
            "S", listOf(cond(fan, Target.Relative(Direction.UP), Kind.ACTION)), emptyList(), emptyList(), "d",
        )
        val s = start(step, init = arrayOf(fan to Number(3)))
        assertEquals(Phase.WAITING, s.phase)      // 상대 목표는 시작할 때 항상 미충족
        assertEquals(Phase.WAITING, s.set(fan, Number(2)).phase)
        assertEquals(Phase.DONE, s.set(fan, Number(4)).phase)
    }

    @Test fun `AtMax는 지원 범위의 최댓값에서만 충족이다`() {
        val step = StepDefinition("S", listOf(cond(fan, Target.AtMax, Kind.ACTION)), emptyList(), emptyList(), "d")
        val s = start(step, ranges = mapOf(fan to 0..8), init = arrayOf(fan to Number(3)))
        assertEquals(Phase.WAITING, s.phase)
        assertEquals(Phase.WAITING, s.set(fan, Number(7)).phase)
        assertEquals(Phase.DONE, s.set(fan, Number(8)).phase)
    }

    @Test fun `AtMax는 범위를 모르면 충족되지 않는다`() {
        val step = StepDefinition("S", listOf(cond(fan, Target.AtMax, Kind.ACTION)), emptyList(), emptyList(), "d")
        val s = start(step, init = arrayOf(fan to Number(3))).set(fan, Number(8))
        assertEquals(Phase.WAITING, s.phase)
    }
}
