package com.firstride.app.start

import com.firstride.engine.course.CourseDefinition
import com.firstride.engine.signal.SignalId
import com.firstride.engine.signal.SignalSource
import com.firstride.engine.signal.Support
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** 코스를 숨긴 이유(POL-C-1, POL-C-2). 설정 화면에 보인다(POL-C-3). */
enum class HideReason { UNSUPPORTED, PERMISSION_DENIED }

data class HiddenCourse(val course: CourseDefinition, val reason: HideReason)

sealed interface StartupResult {
    /** 홈에 보일 코스와 숨긴 코스. 보일 코스가 없으면 홈은 빈 목록 대신 안내를 보인다(AC-01-3). */
    data class Ready(val visible: List<CourseDefinition>, val hidden: List<HiddenCourse>) : StartupResult
    /** 차량 서비스에 연결하지 못했거나 지원 여부를 확인하지 못했다(AC-01-4). 홈으로 가지 않는다. */
    data object ConnectionFailed : StartupResult
}

/** 코스가 판정에 쓰는 모든 신호: 단계의 조작·유지 조건과 오조작 규칙. 후속 조치 신호는 조치마다 따로 본다(FR-16). */
internal fun CourseDefinition.requiredSignals(): Set<SignalId> =
    steps.flatMap { s -> s.action.map { it.signal } + s.hold.map { it.signal } + s.misoperations.map { it.signal } }.toSet()

/**
 * 앱을 시작할 때마다 지원 여부를 확인한다(POL-C-4, 저장하지 않는다). 신호는 한 번씩만, 동시에 묻는다.
 * - 필수 신호가 하나라도 SUPPORTED가 아니면 코스를 숨긴다(POL-C-1). 퍼미션 거부가 섞여 있으면 이유는 퍼미션.
 * - 확인 자체가 안 된 신호(UNKNOWN)가 하나라도 있으면 연결 실패로 본다. 모르는 것을 지원으로도 미지원으로도 보지 않는다(POL-P-4).
 */
suspend fun checkCourses(courses: List<CourseDefinition>, signals: SignalSource): StartupResult = coroutineScope {
    val ids = courses.flatMap { it.requiredSignals() }.distinct()
    val support = ids.map { id -> async { id to signals.support(id) } }.awaitAll().toMap()
    if (support.values.any { it == Support.UNKNOWN }) return@coroutineScope StartupResult.ConnectionFailed

    val visible = mutableListOf<CourseDefinition>()
    val hidden = mutableListOf<HiddenCourse>()
    for (course in courses) {
        val results = course.requiredSignals().map { support.getValue(it) }
        when {
            results.all { it == Support.SUPPORTED } -> visible += course
            results.any { it == Support.DENIED } -> hidden += HiddenCourse(course, HideReason.PERMISSION_DENIED)
            else -> hidden += HiddenCourse(course, HideReason.UNSUPPORTED)
        }
    }
    StartupResult.Ready(visible, hidden)
}
