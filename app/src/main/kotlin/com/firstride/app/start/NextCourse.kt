package com.firstride.app.start

import com.firstride.engine.course.CourseDefinition
import com.firstride.engine.progress.CourseResult

/** 대표 코스. 기록이 하나도 없을 때 홈이 "여기서 시작"으로 권한다(POL-C-6). */
const val REPRESENTATIVE_COURSE = "CRS-01"

/** 기록이 하나도 없는가. 결과가 없는 코스는 시작 전으로 본다. */
fun isFirstRun(courses: List<CourseDefinition>, results: Map<String, CourseResult>): Boolean =
    courses.all { results.resultOf(it) == CourseResult.NOT_STARTED }

/**
 * 홈에서 권할 다음 코스. 모두 완료했거나 목록이 비면 null. [courses]는 홈 목록 순서(POL-C-5).
 * 1. 기록이 하나도 없으면 대표 코스, 목록에 없으면 첫 코스.
 * 2. 아니면 첫 "시작 전" 코스.
 * 3. "시작 전"이 없으면 첫 "건너뜀 있음" 코스.
 */
fun nextCourse(courses: List<CourseDefinition>, results: Map<String, CourseResult>): CourseDefinition? {
    if (courses.isEmpty()) return null
    if (isFirstRun(courses, results)) return courses.firstOrNull { it.id == REPRESENTATIVE_COURSE } ?: courses.first()
    return courses.firstOrNull { results.resultOf(it) == CourseResult.NOT_STARTED }
        ?: courses.firstOrNull { results.resultOf(it) == CourseResult.HAS_SKIPPED }
}

private fun Map<String, CourseResult>.resultOf(course: CourseDefinition) = this[course.id] ?: CourseResult.NOT_STARTED
