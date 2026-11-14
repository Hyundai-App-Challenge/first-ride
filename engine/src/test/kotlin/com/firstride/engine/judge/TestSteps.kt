package com.firstride.engine.judge

import com.firstride.engine.course.ConditionDef
import com.firstride.engine.course.CourseDefinition
import com.firstride.engine.course.Kind
import com.firstride.engine.course.MisoperationDef
import com.firstride.engine.course.StepDefinition
import com.firstride.engine.course.Target
import com.firstride.engine.progress.CourseResult
import com.firstride.engine.progress.ProgressStore
import com.firstride.engine.signal.FakeSignalSource
import com.firstride.engine.signal.SignalValue.Number
import com.firstride.engine.signal.SignalValue.Token
import com.firstride.engine.signal.Signals

internal val defrost = Signals.HVAC_FRONT_DEFROSTER
internal val ac = Signals.HVAC_AC
internal val recirc = Signals.HVAC_RECIRCULATION
internal val fan = Signals.HVAC_FAN_SPEED

/** CRS-01의 S1: 제상 Off→On, 유지 에어컨 On·외기, 오조작 내기. */
internal fun crs01Step() = StepDefinition(
    id = "CRS-01-S1",
    action = listOf(ConditionDef(defrost, Target.Equals(Token("ON")), "앞유리 제상", Kind.ACTION)),
    hold = listOf(
        ConditionDef(ac, Target.Equals(Token("ON")), "에어컨", Kind.HOLD),
        ConditionDef(recirc, Target.Equals(Token("FRESH")), "외기 유입", Kind.HOLD),
    ),
    misoperations = listOf(MisoperationDef("recirc", recirc, Target.Equals(Token("RECIRCULATE")), "내기 순환이 켜져 있어요.")),
    doneText = "좋아요.",
)

/** 풍량을 5로: 단계 두 개짜리 코스의 두 번째 단계. */
internal fun fanStep() = StepDefinition(
    id = "T-S2",
    action = listOf(ConditionDef(fan, Target.Equals(Number(5)), "풍량", Kind.ACTION)),
    hold = emptyList(),
    misoperations = emptyList(),
    doneText = "좋아요.",
)

internal fun twoStepCourse() = CourseDefinition("T-01", "테스트", 60, listOf(crs01Step(), fanStep()))

internal fun source(defrost: String = "OFF", ac: String = "ON", recirc: String = "FRESH", fan: Int = 3) = FakeSignalSource().apply {
    set(com.firstride.engine.judge.defrost, Token(defrost))
    set(com.firstride.engine.judge.ac, Token(ac))
    set(com.firstride.engine.judge.recirc, Token(recirc))
    set(com.firstride.engine.judge.fan, Number(fan))
}

/** 저장 규칙은 POL-R-7(max)을 따른다. record 호출을 그대로 남겨 둔다. */
internal class MemStore : ProgressStore {
    val calls = mutableListOf<Pair<String, CourseResult>>()
    private val saved = HashMap<String, CourseResult>()
    override suspend fun result(courseId: String) = saved[courseId] ?: CourseResult.NOT_STARTED
    override suspend fun record(courseId: String, attempt: CourseResult) {
        calls += courseId to attempt
        saved[courseId] = maxOf(result(courseId), attempt)
    }
    override suspend fun badges(): Set<String> = emptySet()
    override suspend fun clearAll() { saved.clear() }
}
