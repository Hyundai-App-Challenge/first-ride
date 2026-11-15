package com.firstride.app.ui

import com.firstride.engine.course.ConditionDef
import com.firstride.engine.course.Kind
import com.firstride.engine.course.MisoperationDef
import com.firstride.engine.course.StepDefinition
import com.firstride.engine.course.StepText
import com.firstride.engine.course.Target
import com.firstride.engine.judge.Phase
import com.firstride.engine.judge.StepEvent
import com.firstride.engine.judge.initialState
import com.firstride.engine.judge.reduce
import com.firstride.engine.judge.stepView
import com.firstride.engine.signal.Reading
import com.firstride.engine.signal.SignalValue.Token
import com.firstride.engine.signal.Signals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FollowUpTextTest {
    private val defrost = Signals.HVAC_FRONT_DEFROSTER
    private val ac = Signals.HVAC_AC
    private val recirc = Signals.HVAC_RECIRCULATION
    private val base = StepDefinition(
        "base",
        listOf(ConditionDef(defrost, Target.Equals(Token("ON")), "제상", Kind.ACTION)),
        listOf(ConditionDef(ac, Target.Equals(Token("ON")), "에어컨", Kind.HOLD),
            ConditionDef(recirc, Target.Equals(Token("FRESH")), "외기", Kind.HOLD)),
        listOf(MisoperationDef("recirc", recirc, Target.Equals(Token("RECIRCULATE")), "외기로 바꿔 주세요.")),
        "맞춰졌어요.",
    )
    private val restoring = base.copy(
        id = "restore",
        action = listOf(base.hold.first().copy(kind = Kind.ACTION)),
        hold = listOf(base.action.single().copy(kind = Kind.HOLD), base.hold.last()),
    )
    private val text = StepText(
        "제상을 켜 주세요.", locatorText = "공조 조작부를 확인해 주세요.", locatorAsset = "defrost.webp",
        conditionLocators = mapOf(ac to "ac.webp", recirc to "recirc.webp"),
        misoperationLocators = mapOf("recirc" to "wrong-recirc.webp"),
    )
    private val instruction = "에어컨 상태를 다시 맞춰 주세요."

    @Test fun `에어컨만 복구할 때는 제상 대신 에어컨 그림과 공조 위치를 쓴다`() {
        val actual = restoreStepText(restoring, base, text, instruction)!!
        assertEquals("ac.webp", actual.locatorAsset)
        assertEquals(instruction, actual.instruction)
        assertEquals(text.locatorText, actual.locatorText)
    }

    @Test fun `기본 조작 복구는 기본 그림을 쓰며 명시적 조건 그림을 우선한다`() {
        val actual = restoreStepText(base, base, text, instruction)!!
        assertEquals("defrost.webp", actual.locatorAsset)
        val specific = text.copy(conditionLocators = text.conditionLocators + (defrost to "specific-defrost.webp"))
        assertEquals("specific-defrost.webp", restoreStepText(base, base, specific, instruction)!!.locatorAsset)
    }

    @Test fun `에어컨 복구 중 제상이 남으면 PARTIAL 그림은 제상으로 바뀐다`() {
        val values = mapOf(defrost to Reading.Value(Token("ON")), ac to Reading.Value(Token("OFF")), recirc to Reading.Value(Token("FRESH")))
        var state = reduce(initialState(restoring), StepEvent.Prechecked(values))
        state = reduce(state, StepEvent.Changed(defrost, Reading.Value(Token("OFF"))))
        state = reduce(state, StepEvent.Changed(ac, Reading.Value(Token("ON"))))
        val view = stepView(state, restoring, restoreStepText(restoring, base, text, instruction))
        assertEquals(Phase.PARTIAL, view.phase)
        assertEquals("defrost.webp", view.locatorAsset)
    }

    @Test fun `복구 단계도 오조작 교정 그림을 유지한다`() {
        val values = mapOf(defrost to Reading.Value(Token("ON")), ac to Reading.Value(Token("OFF")), recirc to Reading.Value(Token("FRESH")))
        var state = reduce(initialState(restoring), StepEvent.Prechecked(values))
        state = reduce(state, StepEvent.Changed(recirc, Reading.Value(Token("RECIRCULATE"))))
        val view = stepView(state, restoring, restoreStepText(restoring, base, text, instruction))
        assertEquals(Phase.CORRECTION, view.phase)
        assertEquals("wrong-recirc.webp", view.locatorAsset)
    }

    @Test fun `복구 조건의 개별 그림이 없으면 기존 기본 그림으로 안내한다`() {
        val missing = text.copy(conditionLocators = emptyMap())
        assertEquals("defrost.webp", restoreStepText(restoring, base, missing, instruction)!!.locatorAsset)
    }

    @Test fun `모델 문구를 못 읽으면 없는 그림이나 위치를 만들지 않는다`() {
        assertNull(restoreStepText(restoring, base, null, instruction))
    }
}
