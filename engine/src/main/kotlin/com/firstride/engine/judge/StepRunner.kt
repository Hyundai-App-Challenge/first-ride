package com.firstride.engine.judge

import com.firstride.engine.course.Direction
import com.firstride.engine.course.StepDefinition
import com.firstride.engine.course.Target
import com.firstride.engine.signal.SignalId
import com.firstride.engine.signal.SignalSource
import com.firstride.engine.signal.SignalValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 단계 하나를 돌린다. 신호 구독을 [StepEvent]로 바꿔 [reduce]에 넘기고, 시간 제한(3초), 힌트(15초),
 * 자동 진행(2초)을 건다. 한 번만 쓴다. 규칙은 reduce에 있고 여기에는 시간과 구독의 수명만 있다.
 *
 * [run]을 호출한 코루틴이 취소되면 구독이 모두 해제된다(AC-02-10).
 */
class StepRunner(
    private val step: StepDefinition,
    private val source: SignalSource,
    private val config: JudgeConfig = JudgeConfig(),
) {
    private val _state = MutableStateFlow(initialState(step))
    val state: StateFlow<StepState> = _state

    /** 위치 안내를 다시 보여 줄 때. 단계마다 한 번이다(POL-V-5). */
    private val _hints = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val hints: SharedFlow<Unit> = _hints

    private val events = Channel<StepEvent>(Channel.UNLIMITED)

    private val signals = (step.action + step.hold).map { it.signal } + step.misoperations.map { it.signal }
    // 지원 범위가 필요한 신호: AtMax의 최댓값, Relative의 끝값(POL-J-10).
    private val rangeSignals = (step.action + step.hold).filter { it.target == Target.AtMax || it.target is Target.Relative }.map { it.signal }

    fun skip() { events.trySend(StepEvent.Skip) }
    fun retry() { events.trySend(StepEvent.Retry) }

    /** 단계가 끝날 때까지 돌고 마지막 상태(DONE 또는 SKIPPED)를 돌려준다. DONE은 자동 진행 시간만큼 기다린 뒤다. */
    suspend fun run(): Phase = coroutineScope {
        var attempt: Job? = null
        var hint: Job? = null
        var hintShown = false
        fun begin() {
            attempt?.cancel()
            attempt = launch { attemptOnce() }
        }

        try {
            begin()
            for (e in events) {
                val prev = _state.value
                var next = reduce(prev, e)
                if (e is StepEvent.Prechecked && next != prev) {
                    next = next.copy(conditions = next.conditions.map { it.copy(polled = source.isPolled(it.def.signal)) })
                    next = flipRelativeAtLimit(next, e.ranges)
                }
                if (e is StepEvent.Retry && prev.phase == Phase.UNVERIFIABLE) begin()
                _state.value = next
                if (next == prev) continue

                hint?.cancel()          // 상태가 바뀌면 15초를 다시 센다
                hint = null
                when (next.phase) {
                    Phase.WAITING -> if (!hintShown) hint = launch {
                        delay(config.hintDelay)
                        hintShown = true
                        _hints.tryEmit(Unit)
                    }
                    Phase.UNVERIFIABLE -> attempt?.cancel()
                    Phase.DONE -> {
                        attempt?.cancel()
                        launch { delay(config.autoAdvance); events.close() }
                    }
                    Phase.SKIPPED -> {
                        attempt?.cancel()
                        events.close()
                    }
                    else -> {}
                }
            }
            _state.value.phase
        } finally {
            attempt?.cancel()
            hint?.cancel()
        }
    }

    /**
     * POL-J-10: 상대 목표인데 시작 값이 이미 그 방향의 끝값이면(풍량이 최대인데 "올리기") 방향을 뒤집는다.
     * 판정 규칙(reduce)은 그대로이고 상태의 목표만 바꾼다. 정의의 원래 목표에서 매번 다시 정하므로 다시 확인해도 겹치지 않는다.
     * 화면은 원래 목표와 달라진 것을 보고 instructionAtLimit을 보여 준다([stepView]). 범위를 모르면 뒤집지 않는다.
     */
    private fun flipRelativeAtLimit(state: StepState, ranges: Map<SignalId, IntRange?>): StepState {
        val originals = step.action + step.hold
        return state.copy(conditions = state.conditions.mapIndexed { i, c ->
            val orig = originals[i]
            val t = orig.target
            val initial = (c.initial as? SignalValue.Number)?.value
            val range = ranges[orig.signal]
            val atLimit = t is Target.Relative && initial != null && range != null &&
                initial == (if (t.direction == Direction.UP) range.last else range.first)
            val target = if (t is Target.Relative && atLimit) {
                Target.Relative(if (t.direction == Direction.UP) Direction.DOWN else Direction.UP)
            } else t
            c.copy(def = orig.copy(target = target))
        })
    }

    /** 초기 상태 확인 → 구독. 취소되면 구독이 해제된다. */
    private suspend fun attemptOnce() = coroutineScope {
        val pre = precheck()
        events.send(pre)
        // 확인이 끝난 뒤에 구독한다. observe가 현재 값을 먼저 내보내므로 그 사이의 변화를 놓치지 않는다.
        if (pre is StepEvent.Prechecked) {
            signals.distinct().forEach { id ->
                launch { source.observe(id).collect { events.send(StepEvent.Changed(id, it)) } }
            }
        }
    }

    private suspend fun precheck(): StepEvent = withTimeoutOrNull(config.precheckTimeout) {
        coroutineScope {
            val reads = signals.distinct().map { id -> async { id to source.read(id) } }
            val ranges = rangeSignals.distinct().map { id -> async { id to source.range(id) } }
            StepEvent.Prechecked(reads.awaitAll().toMap(), ranges.awaitAll().toMap())
        }
    } ?: StepEvent.PrecheckTimedOut
}
