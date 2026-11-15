package com.firstride.engine.judge

import com.firstride.engine.course.CourseDefinition
import com.firstride.engine.progress.CourseResult
import com.firstride.engine.progress.ProgressStore
import com.firstride.engine.signal.SignalId
import com.firstride.engine.signal.SignalSource
import com.firstride.engine.signal.SignalValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 화면이 지금 단계를 그리고 건너뛰기·다시 확인을 보내는 데 쓴다. 화면의 "1/3"은 [index] + 1과 [total]이다(POL-R-18). */
data class StepProgress(val index: Int, val total: Int, val runner: StepRunner)

/**
 * 코스를 단계 순서대로 돌린다(POL-R-1). 단계마다 새 [StepRunner]라서 조건 신호를 매번 새로 읽는다(POL-J-2).
 * 끝까지 가야 결과를 기록한다. 중간에 취소되면 기록하지 않는다(POL-R-6).
 */
class CourseRunner(
    private val course: CourseDefinition,
    private val source: SignalSource,
    private val store: ProgressStore,
    private val config: JudgeConfig = JudgeConfig(),
) {
    private val _step = MutableStateFlow<StepProgress?>(null)
    val step: StateFlow<StepProgress?> = _step

    /** 같은 시도의 시작 전 값. 성공한 조회만 담고, 준비 완료 전에 취소되면 비어 있다. */
    var initialValues: Map<SignalId, SignalValue> = emptyMap()
        private set

    private val running = Mutex()
    /** 중단 시 다음에 다시 판정할 단계. 단계 안의 신호·충족 상태는 보관하지 않는다. */
    @Volatile var nextStepIndex = 0
        private set
    private var skipped = false
    private var prepared = false

    suspend fun run(): CourseResult = running.withLock {
        // 완료한 인스턴스를 다시 실행하는 경우도 새 시도는 처음부터 시작한다.
        if (nextStepIndex == course.steps.size) {
            nextStepIndex = 0
            skipped = false
            prepared = false
            initialValues = emptyMap()
        }
        try {
            if (!prepared) {
                // 준비가 중단되면 재개 시 새 값을 시작 전 값으로 바꾸지 않는다.
                prepared = true
                initialValues = snapshotBefore(course, source, config)
            }
            while (nextStepIndex < course.steps.size) {
                val i = nextStepIndex
                val def = course.steps[i]
                val runner = StepRunner(def, source, config)
                _step.value = StepProgress(i, course.steps.size, runner)
                if (runner.run() == Phase.SKIPPED) skipped = true
                nextStepIndex = i + 1
            }
        } finally {
            _step.value = null
        }
        val result = if (skipped) CourseResult.HAS_SKIPPED else CourseResult.COMPLETED   // POL-R-5
        store.record(course.id, result)
        result
    }
}
