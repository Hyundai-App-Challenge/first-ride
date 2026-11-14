package com.firstride.engine.judge

import com.firstride.engine.course.StepDefinition
import com.firstride.engine.course.Target
import com.firstride.engine.signal.SignalSource
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
    private val maxSignals = (step.action + step.hold).filter { it.target == Target.AtMax }.map { it.signal }

    fun skip() { events.trySend(StepEvent.Skip) }
    fun retry() { events.trySend(StepEvent.Retry) }

    /** 완료(DONE)한 단계를 자동 진행 시간을 기다리지 않고 끝낸다. 완료가 아니면 아무 일도 하지 않는다(건너뜀으로 기록되지 않는다). */
    fun advance() { if (_state.value.phase == Phase.DONE) events.close() }

    /** 단계가 끝날 때까지 돌고 마지막 상태(DONE 또는 SKIPPED)를 돌려준다. DONE은 자동 진행 시간만큼 기다린 뒤다. */
    suspend fun run(): Phase = coroutineScope {
        var attempt: Job? = null
        var hint: Job? = null
        var auto: Job? = null
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
                        auto = launch { delay(config.autoAdvance); events.close() }
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
            auto?.cancel()      // advance()로 일찍 끝났을 때 남은 대기를 버린다
        }
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
            val ranges = maxSignals.distinct().map { id -> async { id to source.range(id) } }
            StepEvent.Prechecked(reads.awaitAll().toMap(), ranges.awaitAll().toMap())
        }
    } ?: StepEvent.PrecheckTimedOut
}
