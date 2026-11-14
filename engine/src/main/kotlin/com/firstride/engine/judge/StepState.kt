package com.firstride.engine.judge

import com.firstride.engine.course.ConditionDef
import com.firstride.engine.course.Kind
import com.firstride.engine.course.MisoperationDef
import com.firstride.engine.course.StepDefinition
import com.firstride.engine.signal.SignalValue

enum class Phase { PRECHECK, WAITING, ALREADY_AT_TARGET, PARTIAL, CORRECTION, DONE, UNVERIFIABLE, SKIPPED }

data class ConditionState(
    val def: ConditionDef,                     // 조작 조건 또는 유지 조건
    val initial: SignalValue? = null,          // 단계 시작 시 읽은 값
    val current: SignalValue? = null,
    val failed: Boolean = false,
    val seenOffTarget: Boolean = false,        // 이번 단계에서 목표가 아닌 값이었던 적이 있는가
    val polled: Boolean = false,               // 화면의 "확인 중" 표시용. StepRunner가 채운다
    val max: Int? = null,                      // 숫자 신호의 지원 범위 상한 (Target.AtMax용)
) {
    val atTarget: Boolean get() = current != null && def.target.matches(current, initial, max)
    val satisfied: Boolean
        get() = when (def.kind) {
            Kind.ACTION -> seenOffTarget && atTarget     // POL-J-3, POL-J-4
            Kind.HOLD -> atTarget                        // POL-J-5
        }
}

/** 오조작 규칙과 그 신호의 값. 신호를 읽지 못해도 단계를 UNVERIFIABLE로 만들지 않고 "해당 없음"으로 본다. */
data class MisoState(
    val def: MisoperationDef,
    val initial: SignalValue? = null,
    val current: SignalValue? = null,
) {
    val active: Boolean get() = current != null && def.target.matches(current, initial)
    val changedSinceStart: Boolean get() = current != initial
}

data class StepState(
    val phase: Phase,
    val conditions: List<ConditionState>,
    val misoperations: List<MisoState> = emptyList(),
    val correction: MisoperationDef? = null,   // phase == CORRECTION일 때
)

/** 단계를 시작할 때의 상태. `Prechecked`가 오기 전까지 PRECHECK다. */
fun initialState(step: StepDefinition) = StepState(
    Phase.PRECHECK,
    (step.action + step.hold).map { ConditionState(it) },
    step.misoperations.map { MisoState(it) },
)
