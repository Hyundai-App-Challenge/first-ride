package com.firstride.app.practice

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InterruptionTest {
    private val cut = Interruption("CRS-01", stepIndex = 1, atMillis = 1_000L)

    @Test
    fun `POL-R-12 끊긴 지 30분 안이면 이어서 하기를 묻는다`() {
        assertTrue(cut.canResume(1_000L + Interruption.RESUME_WINDOW_MILLIS))
    }

    @Test
    fun `AC-07-7 POL-R-12 30분이 넘으면 묻지 않는다`() {
        assertFalse(cut.canResume(1_000L + Interruption.RESUME_WINDOW_MILLIS + 1))
    }
}
