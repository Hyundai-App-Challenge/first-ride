package com.firstride.app

import com.firstride.app.ui.DEFAULT_DIRECTIONS
import com.firstride.engine.course.Direction
import com.firstride.engine.course.Kind
import com.firstride.engine.course.Target
import com.firstride.engine.judge.ConditionState
import com.firstride.engine.judge.FollowUpState
import com.firstride.engine.judge.Phase
import com.firstride.engine.judge.StepRunner
import com.firstride.engine.judge.StepState
import com.firstride.engine.signal.FakeSignalSource
import com.firstride.engine.signal.SignalId
import com.firstride.engine.signal.SignalValue
import com.firstride.engine.signal.Signals
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

/**
 * debug 전용: 지금 단계의 목표 조작을 가짜 신호원에 대신 넣는다(개발자 패널의 "지금 단계 조작").
 *
 * 단계를 완료로 바꾸지 않는다. 사람이 버튼을 누른 것처럼 신호 값만 바꾸고, 판정은 평소대로 신호의 전이로 한다(AC-02-9).
 * 그래서 이미 목표 상태인 조작 조건은 목표에서 한 번 벗어났다가 돌아온다(POL-J-3, J-4).
 * 가짜 신호원에만 쓴다. 차량 SDK를 쓰는 중에는 부를 방법이 없고(POL-P-3), release에는 이 파일이 없다.
 */
internal object StepOperator {
    /** [away]를 먼저 쓰고, 실행기가 그 값을 본 뒤에 [toTarget]을 쓴다. */
    data class Plan(val away: Map<SignalId, SignalValue>, val toTarget: Map<SignalId, SignalValue>)

    private val operable = setOf(Phase.WAITING, Phase.ALREADY_AT_TARGET, Phase.PARTIAL, Phase.CORRECTION)

    /** 지금 상태에서 모든 조건을 충족시키는 값. 조작을 기다리는 상태가 아니거나 목표 값을 정할 수 없으면 null. */
    fun plan(state: StepState, tokens: (SignalId) -> Set<String> = ::tokensOf): Plan? {
        if (state.phase !in operable) return null
        val away = LinkedHashMap<SignalId, SignalValue>()
        val toTarget = LinkedHashMap<SignalId, SignalValue>()
        for (c in state.conditions) {
            if (c.satisfied) continue
            val id = c.def.signal
            toTarget[id] = targetValue(c) ?: return null
            // 조작 조건은 전이가 있어야 한다. 시작부터 목표였다면 먼저 벗어나게 한다.
            if (c.def.kind == Kind.ACTION && !c.seenOffTarget) away[id] = awayValue(c, tokens(id)) ?: return null
        }
        return Plan(away, toTarget)
    }

    /** 지금 단계의 목표 조작을 한다. 값을 썼으면 true. 판정 결과는 [StepRunner.state]로 나온다. */
    suspend fun operate(runner: StepRunner, fake: FakeSignalSource): Boolean {
        val plan = plan(runner.state.value) ?: return false
        if (plan.away.isNotEmpty()) {
            plan.away.forEach { (id, v) -> fake.set(id, v) }
            // 바로 이어 쓰면 두 값이 하나로 합쳐져 실행기가 전이를 보지 못한다. 벗어난 값을 볼 때까지 기다린다.
            withTimeoutOrNull(SETTLE) {
                runner.state.first { s -> s.conditions.none { it.def.kind == Kind.ACTION && it.def.signal in plan.away && !it.seenOffTarget } }
            } ?: return false
        }
        plan.toTarget.forEach { (id, v) -> fake.set(id, v) }
        return true
    }

    /** 지금 화면이 돌리고 있는 단계(코스 단계 또는 후속 조치 단계). 없으면 null. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun currentStep(vm: AppViewModel): Flow<StepRunner?> = combine(
        vm.runner.flatMapLatest { it?.step ?: flowOf(null) }.map { it?.runner },
        vm.followUp.flatMapLatest { it?.state ?: flowOf(null) }.map { (it as? FollowUpState.Doing)?.progress?.runner },
    ) { course, followUp -> course ?: followUp }

    private fun targetValue(c: ConditionState): SignalValue? = when (val t = c.def.target) {
        is Target.Equals -> t.value
        is Target.Relative -> (c.initial as? SignalValue.Number)?.value?.let { start ->
            val max = c.max
            if (t.direction == Direction.UP) (start + 1).takeIf { max == null || it <= max } else (start - 1).takeIf { it >= 0 }
        }?.let(SignalValue::Number)
        Target.AtMax -> c.max?.let(SignalValue::Number)
    }

    private fun awayValue(c: ConditionState, tokens: Set<String>): SignalValue? = when (val t = c.def.target) {
        is Target.Equals -> when (val v = t.value) {
            is SignalValue.Token -> tokens.firstOrNull { it != v.name }?.let(SignalValue::Token)
            is SignalValue.Number -> SignalValue.Number(if (v.value > 0) v.value - 1 else v.value + 1)
        }
        is Target.Relative -> c.initial
        Target.AtMax -> c.max?.let { SignalValue.Number(it - 1) }
    }

    private fun tokensOf(id: SignalId): Set<String> = Signals.tokens[id] ?: DEFAULT_DIRECTIONS

    private val SETTLE = 1.seconds
}
