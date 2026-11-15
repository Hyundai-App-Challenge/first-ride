package com.firstride.app.start

import com.firstride.engine.course.CourseDefinition
import com.firstride.engine.progress.CourseResult
import com.firstride.engine.progress.CourseResult.COMPLETED
import com.firstride.engine.progress.CourseResult.HAS_SKIPPED
import com.firstride.engine.progress.CourseResult.NOT_STARTED
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NextCourseTest {
    private fun course(id: String) = CourseDefinition(id = id, name = id, estimatedSeconds = 60, steps = emptyList())

    private val crs01 = course("CRS-01")
    private val crs02 = course("CRS-02")
    private val crs03 = course("CRS-03")
    private val crs04 = course("CRS-04")

    /** 홈 목록 순서. 대표 코스가 첫 자리가 아닐 때도 규칙 1이 대표 코스를 고르는지 보려고 일부러 뒤에 둔다. */
    private val list = listOf(crs02, crs01, crs03, crs04)

    private fun results(vararg pairs: Pair<CourseDefinition, CourseResult>) =
        list.associate { it.id to NOT_STARTED } + pairs.associate { (c, r) -> c.id to r }

    @Test
    fun `규칙 1 기록이 하나도 없으면 대표 코스`() {
        assertEquals(crs01, nextCourse(list, results()))
    }

    @Test
    fun `규칙 1 결과가 아직 없는 코스도 시작 전으로 본다`() {
        assertEquals(crs01, nextCourse(list, emptyMap()))
    }

    @Test
    fun `규칙 1 대표 코스가 숨겨진 첫 실행이면 목록의 첫 코스`() {
        val withoutRepresentative = listOf(crs02, crs03, crs04)
        assertEquals(crs02, nextCourse(withoutRepresentative, emptyMap()))
    }

    @Test
    fun `규칙 2 목록 순서에서 첫 시작 전 코스`() {
        assertEquals(crs01, nextCourse(list, results(crs02 to COMPLETED)))
        assertEquals(crs03, nextCourse(list, results(crs02 to COMPLETED, crs01 to HAS_SKIPPED)))
    }

    @Test
    fun `규칙 3 시작 전이 없으면 목록 순서에서 첫 건너뜀 있음 코스`() {
        val r = results(crs02 to COMPLETED, crs01 to COMPLETED, crs03 to HAS_SKIPPED, crs04 to HAS_SKIPPED)
        assertEquals(crs03, nextCourse(list, r))
    }

    @Test
    fun `규칙 4 전부 완료면 null`() {
        assertNull(nextCourse(list, list.associate { it.id to COMPLETED }))
    }

    @Test
    fun `빈 목록이면 null`() {
        assertNull(nextCourse(emptyList(), emptyMap()))
    }

    @Test
    fun `첫 실행 판정은 결과가 없는 코스를 시작 전으로 본다`() {
        assertEquals(true, isFirstRun(list, emptyMap()))
        assertEquals(false, isFirstRun(list, results(crs04 to HAS_SKIPPED)))
    }
}
