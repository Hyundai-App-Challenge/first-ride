package com.firstride.engine.voice

import com.firstride.engine.course.CourseDefinition
import com.firstride.engine.course.ModelCourseText
import com.firstride.engine.course.StepDefinition
import com.firstride.engine.course.StepText
import com.firstride.engine.judge.CourseRunner
import com.firstride.engine.judge.StepRunner
import com.firstride.engine.judge.stepView
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 단계의 상태가 바뀔 때 화면과 같은 문구를 읽는다(FR-05, POL-V-1). 읽을 문구는 [stepView]가 정한다.
 * 같은 상태에서 조건의 현재 값만 바뀐 것은 다시 읽지 않는다. 힌트(15초)는 위치 문구를 한 번 읽는다(POL-V-5).
 * 취소되면 끝난다.
 */
suspend fun narrateStep(runner: StepRunner, step: StepDefinition, text: StepText?, voice: VoiceCoordinator) = coroutineScope {
    launch {
        runner.state
            .map { stepView(it, step, text) }
            .map { it.phase to it.spoken }
            .distinctUntilChanged()
            .collect { (_, spoken) -> spoken?.let { voice.announce(it) } }
    }
    launch { runner.hints.collect { text?.locatorText?.let { voice.announce(it) } } }
}

/** 코스의 지금 단계를 따라가며 읽는다. 단계가 바뀌면 앞 단계의 읽기는 끝난다. */
suspend fun narrateCourse(runner: CourseRunner, course: CourseDefinition, texts: ModelCourseText?, voice: VoiceCoordinator) {
    runner.step.collectLatest { p ->
        if (p != null) {
            val step = course.steps[p.index]
            narrateStep(p.runner, step, texts?.steps?.get(step.id), voice)
        }
    }
}
