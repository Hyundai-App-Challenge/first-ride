package com.firstride.app.start

import com.firstride.engine.course.CourseParser
import com.firstride.engine.signal.FakeSignalSource
import com.firstride.engine.signal.SignalId
import com.firstride.engine.signal.SignalSource
import com.firstride.engine.signal.Signals
import com.firstride.engine.signal.Support
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CourseAvailabilityTest {
    private val crs01 = CourseParser.parseCourse(
        """
        {"id":"CRS-01","name":"김서림 제거","estimatedSeconds":60,"steps":[{"id":"CRS-01-S1",
          "action":[{"signal":"hvac.frontDefroster","equals":"ON","label":"앞유리 제상"}],
          "hold":[{"signal":"hvac.ac","equals":"ON","label":"에어컨"}],
          "misoperations":[{"id":"recirc","when":{"signal":"hvac.recirculation","equals":"RECIRCULATE"},"correction":"외기로 바꿔요."}],
          "doneText":"좋아요."}]}
        """.trimIndent()
    )
    private val crs03 = CourseParser.parseCourse(
        """
        {"id":"CRS-03","name":"전조등","estimatedSeconds":60,"steps":[{"id":"CRS-03-S1",
          "action":[{"signal":"light.lowBeam","equals":"ON","label":"전조등"}],"hold":[],"misoperations":[],"doneText":"좋아요."}]}
        """.trimIndent()
    )

    @Test
    fun `모든 필수 신호가 지원되면 홈에 보인다`() = runTest {
        val result = checkCourses(listOf(crs01, crs03), FakeSignalSource())
        assertEquals(StartupResult.Ready(listOf(crs01, crs03), emptyList()), result)
    }

    @Test
    fun `AC-01-2 POL-C-1 필수 신호가 하나라도 미지원이면 코스를 숨긴다`() = runTest {
        // 오조작 규칙의 신호도 판정에 쓰므로 필수다
        val signals = FakeSignalSource().apply { setSupport(Signals.HVAC_RECIRCULATION, Support.UNSUPPORTED) }
        val result = checkCourses(listOf(crs01, crs03), signals) as StartupResult.Ready
        assertEquals(listOf(crs03), result.visible)
        assertEquals(listOf(HiddenCourse(crs01, HideReason.UNSUPPORTED)), result.hidden)
    }

    @Test
    fun `POL-C-2 퍼미션이 거부된 도메인의 코스도 숨기고 이유를 남긴다`() = runTest {
        val signals = FakeSignalSource().apply { setSupport(Signals.LIGHT_LOW_BEAM, Support.DENIED) }
        val result = checkCourses(listOf(crs01, crs03), signals) as StartupResult.Ready
        assertEquals(listOf(crs01), result.visible)
        assertEquals(listOf(HiddenCourse(crs03, HideReason.PERMISSION_DENIED)), result.hidden)
    }

    @Test
    fun `AC-01-3 지원되는 코스가 없으면 보일 코스가 비어 있다`() = runTest {
        val signals = FakeSignalSource().apply {
            setSupport(Signals.HVAC_AC, Support.UNSUPPORTED)
            setSupport(Signals.LIGHT_LOW_BEAM, Support.UNSUPPORTED)
        }
        val result = checkCourses(listOf(crs01, crs03), signals) as StartupResult.Ready
        assertTrue(result.visible.isEmpty())
        assertEquals(2, result.hidden.size)
    }

    @Test
    fun `AC-01-4 지원 여부를 확인하지 못하면 연결 실패다`() = runTest {
        val signals = FakeSignalSource().apply { setSupport(Signals.HVAC_AC, Support.UNKNOWN) }
        assertEquals(StartupResult.ConnectionFailed, checkCourses(listOf(crs01, crs03), signals))
    }

    @Test
    fun `POL-C-4 신호마다 지원 여부를 한 번씩 묻는다`() = runTest {
        val asked = mutableListOf<SignalId>()
        val counting = object : SignalSource by FakeSignalSource() {
            override suspend fun support(id: SignalId): Support { asked += id; return Support.SUPPORTED }
        }
        checkCourses(listOf(crs01, crs01, crs03), counting)
        assertEquals(asked.toSet().size, asked.size)
        assertEquals(setOf(Signals.HVAC_FRONT_DEFROSTER, Signals.HVAC_AC, Signals.HVAC_RECIRCULATION, Signals.LIGHT_LOW_BEAM), asked.toSet())
    }
}
