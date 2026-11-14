package com.firstride.engine.judge

import com.firstride.engine.course.Kind
import com.firstride.engine.course.MisoperationDef
import com.firstride.engine.signal.Reading

/**
 * 판정 규칙 전부. 입력이 같으면 출력이 같고 시간·코루틴·I/O가 없다.
 * 허위 완료가 생길 수 있는 유일한 곳이므로 변경은 상대의 승인을 받는다.
 */
fun reduce(state: StepState, event: StepEvent): StepState {
    // POL-J-8: 끝난 단계는 되돌리지 않는다.
    if (state.phase == Phase.DONE || state.phase == Phase.SKIPPED) return state

    return when (event) {
        StepEvent.Skip -> state.copy(phase = Phase.SKIPPED, correction = null)

        // "다시 확인"은 UNVERIFIABLE에서만. 충족 상태는 버리고 처음부터 다시 읽는다(POL-J-20, J-22).
        StepEvent.Retry ->
            if (state.phase != Phase.UNVERIFIABLE) state
            else StepState(
                Phase.PRECHECK,
                state.conditions.map { it.copy(initial = null, current = null, failed = false, seenOffTarget = false, max = null) },
                state.misoperations.map { it.copy(initial = null, current = null) },
            )

        StepEvent.PrecheckTimedOut ->
            if (state.phase == Phase.PRECHECK) state.copy(phase = Phase.UNVERIFIABLE, correction = null) else state

        is StepEvent.Prechecked ->
            if (state.phase != Phase.PRECHECK) state else decide(precheck(state, event))

        is StepEvent.Changed -> when (state.phase) {
            Phase.PRECHECK, Phase.UNVERIFIABLE -> state     // 확인이 끝나기 전, 확인 불가 뒤의 변화는 쓰지 않는다
            else -> decide(change(state, event))
        }
    }
}

private fun precheck(state: StepState, e: StepEvent.Prechecked): StepState = state.copy(
    conditions = state.conditions.map { c ->
        when (val r = e.readings[c.def.signal]) {
            is Reading.Value -> c.copy(
                initial = r.value,
                current = r.value,
                failed = false,
                max = e.ranges[c.def.signal]?.last,
            ).let { it.copy(seenOffTarget = !it.atTarget) }
            // 값이 없는 것도 실패다. "꺼짐"으로 읽지 않는다(POL-P-4, J-21).
            is Reading.Failed, null -> c.copy(failed = true)
        }
    },
    misoperations = state.misoperations.map { m ->
        val v = (e.readings[m.def.signal] as? Reading.Value)?.value
        m.copy(initial = v, current = v)
    },
)

private fun change(state: StepState, e: StepEvent.Changed): StepState = state.copy(
    conditions = state.conditions.map { c ->
        if (c.def.signal != e.id) return@map c
        when (val r = e.reading) {
            is Reading.Value -> c.copy(current = r.value).let { it.copy(seenOffTarget = it.seenOffTarget || !it.atTarget) }
            is Reading.Failed -> c.copy(failed = true)
        }
    },
    misoperations = state.misoperations.map { m ->
        val r = e.reading
        if (m.def.signal == e.id && r is Reading.Value) m.copy(current = r.value) else m
    },
)

/** 5-4절의 표. 위에서부터 처음 맞는 것이 답이다. */
private fun decide(s: StepState): StepState {
    val cs = s.conditions
    val actions = cs.filter { it.def.kind == Kind.ACTION }

    val phase: Phase
    var correction: MisoperationDef? = null
    if (cs.any { it.failed }) phase = Phase.UNVERIFIABLE                               // POL-J-21, J-22
    else if (cs.all { it.satisfied }) phase = Phase.DONE                               // POL-J-6
    else {
        // POL-J-15, J-16: 조작 조건이 충족됐거나 오조작 신호가 시작 값에서 바뀐 경우에만 교정을 낸다.
        val anyActionSatisfied = actions.any { it.satisfied }
        correction = s.misoperations.firstOrNull { it.active && (anyActionSatisfied || it.changedSinceStart) }?.def
        phase = when {
            correction != null -> Phase.CORRECTION
            actions.any { !it.seenOffTarget && it.atTarget } -> Phase.ALREADY_AT_TARGET   // POL-J-11
            actions.all { it.satisfied } -> Phase.PARTIAL
            else -> Phase.WAITING
        }
    }
    return s.copy(phase = phase, correction = correction)
}
