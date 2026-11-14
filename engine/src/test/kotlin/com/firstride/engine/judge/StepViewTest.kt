package com.firstride.engine.judge

import com.firstride.engine.course.StepText
import com.firstride.engine.signal.FailReason
import com.firstride.engine.signal.Reading
import com.firstride.engine.signal.SignalId
import com.firstride.engine.signal.SignalValue
import com.firstride.engine.signal.SignalValue.Token
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 화면과 음성이 같은 상태 문구를 쓴다(FR-04, FR-05). 문구는 코스 정의에서 오고 틀만 코드에 있다. */
class StepViewTest {
    private val step = crs01Step()
    private val text = StepText(
        instruction = "앞유리 김서림 제거 버튼을 누르세요.",
        locatorText = "공조 조작부 왼쪽 위, 부채꼴 모양 버튼이에요.",
        locatorAsset = "assets/defrost.webp",
        conditionLocators = mapOf(ac to "assets/ac.webp", recirc to "assets/recirc.webp"),
        misoperationLocators = mapOf("recirc" to "assets/recirc_wrong.webp"),
    )

    private fun state(defrost: String, ac: String, recirc: String, vararg then: Pair<SignalId, SignalValue>): StepState {
        var s = reduce(
            initialState(step),
            StepEvent.Prechecked(
                mapOf(
                    com.firstride.engine.judge.defrost to Reading.Value(Token(defrost)),
                    com.firstride.engine.judge.ac to Reading.Value(Token(ac)),
                    com.firstride.engine.judge.recirc to Reading.Value(Token(recirc)),
                ),
            ),
        )
        for ((id, v) in then) s = reduce(s, StepEvent.Changed(id, Reading.Value(v)))
        return s
    }

    private fun view(s: StepState) = stepView(s, step, text)

    @Test fun `FR-05 대기 중에는 안내 문구를 화면과 음성에 쓰고 위치 안내는 단계의 것이다`() {
        val v = view(state("OFF", "ON", "FRESH"))
        assertEquals(Phase.WAITING, v.phase)
        assertEquals("앞유리 김서림 제거 버튼을 누르세요.", v.message)
        assertEquals(v.message, v.spoken)
        assertEquals("assets/defrost.webp", v.locatorAsset)
        assertEquals("공조 조작부 왼쪽 위, 부채꼴 모양 버튼이에요.", v.locatorText)
    }

    @Test fun `AC-CRS01-1 PARTIAL은 충족된 것과 남은 것을 말하고 남은 조건의 위치를 보여 준다`() {
        val v = view(state("OFF", "OFF", "FRESH", defrost to Token("ON")))
        assertEquals(Phase.PARTIAL, v.phase)
        assertEquals("앞유리 제상 확인. 에어컨이 남았어요.", v.message)
        assertEquals("assets/ac.webp", v.locatorAsset)
    }

    @Test fun `POL-J-13 AC-02-5 AC-04-3 조건마다 상태가 따로 보이고 이미 맞은 유지 조건은 이미 맞음이다`() {
        val v = view(state("OFF", "ON", "FRESH"))
        assertEquals(
            listOf(ConditionStatus.UNMET, ConditionStatus.ALREADY_MET, ConditionStatus.ALREADY_MET),
            v.conditions.map { it.status },
        )
        val partial = view(state("OFF", "OFF", "FRESH", defrost to Token("ON")))
        assertEquals(
            listOf(ConditionStatus.MET, ConditionStatus.UNMET, ConditionStatus.ALREADY_MET),
            partial.conditions.map { it.status },
        )
    }

    @Test fun `POL-J-11 이미 목표 상태는 껐다 켜기 안내이고 조건이 이미 켜져 있음으로 보인다`() {
        val v = view(state("ON", "ON", "FRESH"))
        assertEquals(Phase.ALREADY_AT_TARGET, v.phase)
        assertEquals("이미 켜져 있어요. 한 번 껐다가 다시 켜 보세요.", v.message)
        assertEquals(ConditionStatus.TOGGLE_NEEDED, v.conditions.first().status)
    }

    @Test fun `AC-02-6 교정은 정의의 교정 문구와 오조작 위치 안내를 쓴다`() {
        // 에어컨이 꺼진 채 시작해야 제상을 켜도 DONE이 되지 않는다
        val v = view(state("OFF", "OFF", "FRESH", defrost to Token("ON"), recirc to Token("RECIRCULATE")))
        assertEquals(Phase.CORRECTION, v.phase)
        assertEquals("내기 순환이 켜져 있어요.", v.message)
        assertEquals("assets/recirc_wrong.webp", v.locatorAsset)
    }

    @Test fun `FR-05 완료는 정의의 완료 문구를 읽는다`() {
        val v = view(state("OFF", "ON", "FRESH", defrost to Token("ON")))
        assertEquals(Phase.DONE, v.phase)
        assertEquals("좋아요.", v.message)
        assertEquals("좋아요.", v.spoken)
    }

    @Test fun `POL-J-19 확인 불가는 단정하지 않는 문구이고 조건은 알 수 없음이다`() {
        val s = reduce(
            initialState(step),
            StepEvent.Prechecked(mapOf(ac to Reading.Failed(FailReason.TIMEOUT))),
        )
        val v = view(s)
        assertEquals(Phase.UNVERIFIABLE, v.phase)
        assertEquals("지금은 차량 상태를 확인할 수 없어요.", v.message)
        assertEquals(v.message, v.spoken)
        assertEquals(ConditionStatus.UNKNOWN, v.conditions.first { it.signal == ac }.status)
    }

    @Test fun `확인 중과 건너뜀은 음성을 내지 않는다`() {
        assertNull(view(initialState(step)).spoken)
        assertNull(view(reduce(initialState(step), StepEvent.Skip)).spoken)
    }

    @Test fun `AC-04-7 폴링하는 조건은 확인 중 표시를 위해 표시된다`() {
        val s = state("OFF", "ON", "FRESH")
        val polled = s.copy(conditions = s.conditions.map { if (it.def.signal == ac) it.copy(polled = true) else it })
        assertEquals(listOf(false, true, false), view(polled).conditions.map { it.polling })
    }

    @Test fun `차종별 문구를 찾지 못해도 상태 문구는 나온다`() {
        val v = stepView(state("ON", "ON", "FRESH"), step, null)
        assertEquals("이미 켜져 있어요. 한 번 껐다가 다시 켜 보세요.", v.message)
        assertNull(v.locatorAsset)
        assertEquals("", stepView(state("OFF", "ON", "FRESH"), step, null).message)
    }

    @Test fun `이 가 조사는 받침으로 고른다`() {
        assertEquals("에어컨이 남았어요.", partialText(emptyList(), listOf("에어컨")))
        assertEquals("외기 순환이 남았어요.", partialText(emptyList(), listOf("외기 순환")))
        assertEquals("풍량이 남았어요.", partialText(emptyList(), listOf("풍량")))
        assertEquals("제상, 순환이 남았어요.", partialText(emptyList(), listOf("제상", "순환")))
        assertEquals("에어컨, 외기 유입이 남았어요.", partialText(emptyList(), listOf("에어컨", "외기 유입")))
        assertEquals("조명이 남았어요.", partialText(emptyList(), listOf("조명")))
        assertEquals("와이퍼가 남았어요.", partialText(emptyList(), listOf("와이퍼")))
        assertEquals("A/C가 남았어요.", partialText(emptyList(), listOf("A/C")))
    }
}
