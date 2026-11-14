package com.firstride.engine.judge

import com.firstride.engine.course.Kind
import com.firstride.engine.course.StepDefinition
import com.firstride.engine.course.StepText
import com.firstride.engine.signal.SignalId

enum class ConditionStatus {
    MET,              // 충족
    ALREADY_MET,      // 유지 조건이 시작부터 맞아 있음. 조작을 요구하지 않는다(POL-J-13)
    UNMET,            // 아직 안 맞음
    TOGGLE_NEEDED,    // 조작 조건이 시작부터 목표 값. 껐다가 다시 켜야 한다(POL-J-11)
    UNKNOWN,          // 읽지 못함
}

data class ConditionView(
    val signal: SignalId,
    val label: String,
    val status: ConditionStatus,
    val polling: Boolean,       // "확인 중" 표시(AC-04-7)
)

/**
 * 단계 상태를 화면과 음성이 같은 문구로 쓰게 풀어 놓은 것(FR-04, FR-05).
 * 문구는 모두 코스 정의에서 오고 여기에는 상태별 틀만 있다(POL-W-10).
 */
data class StepView(
    val phase: Phase,
    val message: String,                   // 화면의 상태 문구
    val spoken: String?,                   // 음성으로 읽을 문구. 읽지 않는 상태는 null
    val conditions: List<ConditionView>,
    val locatorAsset: String?,             // 지금 보여 줄 위치 안내 자산(차종 폴더 기준 경로)
    val locatorText: String?,              // "어디 있어요"와 힌트가 읽는 위치 문구
)

private const val ALREADY_AT_TARGET_TEXT = "이미 켜져 있어요. 한 번 껐다가 다시 켜 보세요."
private const val UNVERIFIABLE_TEXT = "지금은 차량 상태를 확인할 수 없어요."
private const val PRECHECK_TEXT = "차량 상태를 확인하고 있어요."
private const val SKIPPED_TEXT = "건너뛰었어요."

/** [text]가 null이면(차종별 문구를 찾지 못함) 상태 문구는 나오지만 안내 문구와 위치 안내는 비어 있다. */
fun stepView(state: StepState, step: StepDefinition?, text: StepText?): StepView {
    val conditions = state.conditions.map { c ->
        ConditionView(
            c.def.signal,
            c.def.label.ifBlank { c.def.signal.key },
            when {
                c.failed || c.current == null -> ConditionStatus.UNKNOWN
                c.def.kind == Kind.HOLD && c.atTarget && !c.seenOffTarget -> ConditionStatus.ALREADY_MET
                c.satisfied -> ConditionStatus.MET
                c.def.kind == Kind.ACTION && c.atTarget && !c.seenOffTarget -> ConditionStatus.TOGGLE_NEEDED
                else -> ConditionStatus.UNMET
            },
            c.polled,
        )
    }
    val unmet = conditions.filter { it.status == ConditionStatus.UNMET || it.status == ConditionStatus.TOGGLE_NEEDED }
    val done = conditions.filter { it.status == ConditionStatus.MET }

    val message: String? = when (state.phase) {
        Phase.PRECHECK -> PRECHECK_TEXT
        Phase.WAITING -> text?.instruction
        Phase.ALREADY_AT_TARGET -> ALREADY_AT_TARGET_TEXT
        Phase.PARTIAL -> partialText(done.map { it.label }, unmet.map { it.label })
        Phase.CORRECTION -> state.correction?.correction
        Phase.DONE -> step?.doneText
        Phase.UNVERIFIABLE -> UNVERIFIABLE_TEXT
        Phase.SKIPPED -> SKIPPED_TEXT
    }

    val asset = when (state.phase) {
        Phase.CORRECTION -> text?.misoperationLocators?.get(state.correction?.id)
        Phase.PARTIAL -> unmet.firstNotNullOfOrNull { text?.conditionLocators?.get(it.signal) }
        else -> null
    } ?: text?.locatorAsset

    val silent = state.phase == Phase.PRECHECK || state.phase == Phase.SKIPPED
    return StepView(
        phase = state.phase,
        message = message.orEmpty(),
        spoken = message.takeIf { !silent && it != null },
        conditions = conditions,
        locatorAsset = asset,
        locatorText = text?.locatorText,
    )
}

/** "앞유리 제상 확인. 에어컨이 남았어요." 충족된 것이 없으면 남은 것만 말한다. */
internal fun partialText(met: List<String>, remaining: List<String>): String {
    val rest = remaining.joinToString(", ")
    val tail = "${rest}${josa(rest, "이", "가")} 남았어요."
    return if (met.isEmpty()) tail else "${met.joinToString(", ")} 확인. $tail"
}

/** 받침이 있으면 [withFinal], 없으면 [without]. 한글이 아닌 마침은 받침이 없는 것으로 본다. */
internal fun josa(word: String, withFinal: String, without: String): String {
    val c = word.lastOrNull { !it.isWhitespace() } ?: return without
    if (c !in '가'..'힣') return without
    return if ((c - '가') % 28 != 0) withFinal else without
}
