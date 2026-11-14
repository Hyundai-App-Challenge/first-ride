package com.firstride.engine.judge

import com.firstride.engine.course.CourseDefinition
import com.firstride.engine.progress.CourseResult
import com.firstride.engine.progress.ProgressStore
import com.firstride.engine.signal.SignalSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

    suspend fun run(): CourseResult {
        var skipped = false
        try {
            course.steps.forEachIndexed { i, def ->
                val runner = StepRunner(def, source, config)
                _step.value = StepProgress(i, course.steps.size, runner)
                if (runner.run() == Phase.SKIPPED) skipped = true
            }
        } finally {
            _step.value = null
        }
        val result = if (skipped) CourseResult.HAS_SKIPPED else CourseResult.COMPLETED   // POL-R-5
        store.record(course.id, result)
        return result
    }
}
