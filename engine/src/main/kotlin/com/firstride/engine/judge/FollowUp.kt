package com.firstride.engine.judge

import com.firstride.engine.course.ConditionDef
import com.firstride.engine.course.CourseDefinition
import com.firstride.engine.course.FollowUpDef
import com.firstride.engine.course.Kind
import com.firstride.engine.course.StepDefinition
import com.firstride.engine.course.Target
import com.firstride.engine.signal.FailReason
import com.firstride.engine.signal.Reading
import com.firstride.engine.signal.SignalId
import com.firstride.engine.signal.SignalSource
import com.firstride.engine.signal.Support
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull

/** 후속 조치(FR-16)에서 다음에 낼 것 (기술 설계 5-8절). */
sealed interface FollowUpNext {
    data class RestoreBase(val step: StepDefinition) : FollowUpNext   // 기본 조합이 어긋남 (POL-F-11)
    data class Offer(val followUp: FollowUpDef) : FollowUpNext        // 아직 안 된 조치
    data object Exhausted : FollowUpNext                              // 남은 조치 없음 (POL-F-7)
}

/** 기본 조합 복구 단계의 ID. alreadyOffered에 넣으면 같은 안내에서 다시 내지 않는다. */
fun restoreStepId(course: CourseDefinition): String? = course.steps.lastOrNull()?.let { "${it.id}-restore" }

/** 조건이 지금 값과 맞는가. 읽지 못했거나 판단할 수 없는 목표(상대 목표, 범위 모르는 AtMax)면 null. */
private fun metNow(c: ConditionDef, reading: Reading?, range: IntRange?): Boolean? {
    val value = (reading as? Reading.Value)?.value ?: return null
    if (c.target is Target.Relative) return null
    if (c.target == Target.AtMax && range == null) return null
    return c.target.matches(value, null, range?.last)
}

/**
 * 순수 함수. 방금 읽은 값으로 다음에 할 일을 고른다.
 * 1. 기본 조합(코스 마지막 단계의 목표 상태)이 어긋나 있으면 그것부터(POL-F-11). 읽지 못한 것은 어긋났다고 단정하지 않는다.
 * 2. 후속 조치를 정의 순서대로 보며, 이미 낸 것(완료·건너뜀), 미지원, 이미 되어 있는 것(POL-F-4)을 뺀 첫 번째.
 *    읽지 못한 조치는 낸다. 단계가 확인 불가로 보여 주고 사용자가 다시 확인하거나 건너뛴다(AC-16-12).
 */
fun chooseNext(
    course: CourseDefinition,
    readings: Map<SignalId, Reading>,
    ranges: Map<SignalId, IntRange?>,
    support: Map<SignalId, Support>,
    alreadyOffered: Set<String>,
): FollowUpNext {
    val base = course.steps.lastOrNull()
    val restoreId = restoreStepId(course)
    if (base != null && restoreId != null && restoreId !in alreadyOffered) {
        val conditions = base.action + base.hold
        val unmet = conditions.filter { metNow(it, readings[it.signal], ranges[it.signal]) == false }
        if (unmet.isNotEmpty()) {
            return FollowUpNext.RestoreBase(
                StepDefinition(
                    id = restoreId,
                    action = unmet.map { it.copy(kind = Kind.ACTION) },
                    hold = conditions.filterNot { it in unmet }.map { it.copy(kind = Kind.HOLD) },
                    misoperations = base.misoperations,
                    doneText = base.doneText,
                ),
            )
        }
    }
    for (f in course.followUps) {
        val c = f.condition
        if (f.id in alreadyOffered) continue
        if (support[c.signal] != Support.SUPPORTED) continue
        if (c.target == Target.AtMax && ranges[c.signal] == null) continue
        if (metNow(c, readings[c.signal], ranges[c.signal]) == true) continue
        return FollowUpNext.Offer(f)
    }
    return FollowUpNext.Exhausted
}

/** 고른 조치를 단계로 바꾼다. 조치는 상태를 만드는 것이 목적이라 조작 조건 하나짜리다. */
fun followUpStep(f: FollowUpDef): StepDefinition {
    val label = f.condition.label.ifBlank { f.condition.signal.key }
    return StepDefinition(
        id = f.id,
        action = listOf(f.condition.copy(kind = Kind.ACTION)),
        hold = emptyList(),
        misoperations = emptyList(),
        doneText = "$label${josa(label, "이", "가")} 맞춰졌어요.",
    )
}

enum class FollowUpAnswer { RESOLVED, NOT_YET }

/** 후속 조치 안내가 지금 어디에 있는가. 화면은 이것만 보고 그린다. */
sealed interface FollowUpState {
    data object Checking : FollowUpState

    /** 조치 하나를 단계 진행 화면(SCR-04)으로 안내하는 중. restore면 기본 조합 복구다. */
    data class Doing(val progress: StepProgress, val step: StepDefinition, val restore: Boolean) : FollowUpState

    /** "김이 걷혔나요?" */
    data object Asking : FollowUpState
    data object Exhausted : FollowUpState
    data object Resolved : FollowUpState
}

/**
 * 후속 조치 안내를 돌린다(기술 설계 5-8절). 조치를 고를 때마다 신호를 새로 읽고(POL-F-5), 고른 조치는
 * [StepRunner]가 신호로 판정한다. 진행 기록 저장소를 받지 않는다. 코스 결과와 배지를 바꿀 수단이 없다(POL-F-8).
 * 사용자의 답과 한 조치는 이 실행 안에만 있고 어디에도 저장하지 않는다. 취소하면 구독이 모두 해제된다.
 */
class FollowUpRunner(
    private val course: CourseDefinition,
    private val source: SignalSource,
    private val config: JudgeConfig = JudgeConfig(),
) {
    private val _state = MutableStateFlow<FollowUpState>(FollowUpState.Checking)
    val state: StateFlow<FollowUpState> = _state

    private val answers = Channel<FollowUpAnswer>(Channel.CONFLATED)

    /** "걷혔어요"/"아직이요". 묻는 중이 아니면 무시한다. */
    fun answer(a: FollowUpAnswer) {
        if (_state.value == FollowUpState.Asking) answers.trySend(a)
    }

    /** 걷혔다고 답하거나 남은 조치가 없을 때까지 돈다. */
    suspend fun run() {
        val offered = mutableSetOf<String>()
        while (true) {
            _state.value = FollowUpState.Checking
            when (val next = pick(offered)) {
                FollowUpNext.Exhausted -> { _state.value = FollowUpState.Exhausted; return }
                is FollowUpNext.RestoreBase -> {
                    runStep(next.step, restore = true)
                    offered += next.step.id            // 건너뛰어도 다시 내지 않는다
                }
                is FollowUpNext.Offer -> {
                    val phase = runStep(followUpStep(next.followUp), restore = false)
                    offered += next.followUp.id
                    if (phase == Phase.DONE && ask() == FollowUpAnswer.RESOLVED) {
                        _state.value = FollowUpState.Resolved
                        return
                    }
                }
            }
        }
    }

    private suspend fun runStep(def: StepDefinition, restore: Boolean): Phase {
        val runner = StepRunner(def, source, config)
        _state.value = FollowUpState.Doing(StepProgress(0, 1, runner), def, restore)
        return runner.run()
    }

    private suspend fun ask(): FollowUpAnswer {
        answers.tryReceive()                   // 이전 답은 버린다
        _state.value = FollowUpState.Asking
        return answers.receive()
    }

    private suspend fun pick(offered: Set<String>): FollowUpNext = coroutineScope {
        val base = course.steps.lastOrNull()
        val conditions = base?.let { it.action + it.hold }.orEmpty() + course.followUps.map { it.condition }
        val ids = conditions.map { it.signal }.distinct()
        val maxIds = conditions.filter { it.target == Target.AtMax }.map { it.signal }.distinct()
        val followIds = course.followUps.map { it.condition.signal }.distinct()
        val reads = ids.map { id ->
            async { id to (withTimeoutOrNull(config.precheckTimeout) { source.read(id) } ?: Reading.Failed(FailReason.TIMEOUT)) }
        }
        val ranges = maxIds.map { id -> async { id to withTimeoutOrNull(config.precheckTimeout) { source.range(id) } } }
        val supports = followIds.map { id ->
            async { id to (withTimeoutOrNull(config.precheckTimeout) { source.support(id) } ?: Support.UNKNOWN) }
        }
        chooseNext(course, reads.awaitAll().toMap(), ranges.awaitAll().toMap(), supports.awaitAll().toMap(), offered)
    }
}
