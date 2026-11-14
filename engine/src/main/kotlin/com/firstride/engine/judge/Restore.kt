package com.firstride.engine.judge

import com.firstride.engine.course.CourseDefinition
import com.firstride.engine.signal.Reading
import com.firstride.engine.signal.SignalId
import com.firstride.engine.signal.SignalSource
import com.firstride.engine.signal.SignalValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/** 실습 후 원상 복귀 안내(FR-14)의 목록. 이름은 코스 정의의 조건 라벨이다. */
data class RestoreReport(
    /** 코스 시작 전과 값이 다른 것(AC-14-1). */
    val changed: List<String>,
    /** 시작 전 또는 지금 값을 읽지 못해 알 수 없는 것. 달라졌다고 단정하지 않는다. */
    val unverified: List<String>,
) {
    val isEmpty: Boolean get() = changed.isEmpty() && unverified.isEmpty()
}

/** 코스가 다룬 신호(모든 단계의 조작·유지 조건)와 그 라벨. */
fun coveredSignals(course: CourseDefinition): Map<SignalId, String> =
    course.steps.flatMap { it.action + it.hold }
        .groupBy { it.signal }
        .mapValues { (id, defs) -> defs.first().label.ifBlank { id.key } }

/** 순수 함수. before는 코스 시작 전에 읽은 값(읽지 못한 신호는 없음), now는 지금 값. 앱은 되돌리지 않는다(AC-14-2). */
fun restoreReport(course: CourseDefinition, before: Map<SignalId, SignalValue>, now: Map<SignalId, Reading>): RestoreReport {
    val changed = mutableListOf<String>()
    val unverified = mutableListOf<String>()
    coveredSignals(course).forEach { (id, label) ->
        val b = before[id]
        val n = (now[id] as? Reading.Value)?.value
        when {
            b == null || n == null -> unverified += label
            b != n -> changed += label
        }
    }
    return RestoreReport(changed, unverified)
}

/** 지금 값을 구독해 목록을 낸다. 사용자가 되돌리면 값 변화가 바로 목록에 반영된다(AC-14-3). 수집을 멈추면 구독이 해제된다. */
fun observeRestore(course: CourseDefinition, before: Map<SignalId, SignalValue>, source: SignalSource): Flow<RestoreReport> {
    val ids = coveredSignals(course).keys.toList()
    if (ids.isEmpty()) return flowOf(RestoreReport(emptyList(), emptyList()))
    return combine(ids.map { id -> source.observe(id) }) { readings ->
        restoreReport(course, before, ids.zip(readings).toMap())
    }
}

/** 코스 시작 전의 값을 읽는다. 읽지 못한 신호는 담지 않는다. */
suspend fun snapshotBefore(course: CourseDefinition, source: SignalSource): Map<SignalId, SignalValue> =
    coveredSignals(course).keys.mapNotNull { id ->
        (source.read(id) as? Reading.Value)?.let { id to it.value }
    }.toMap()
