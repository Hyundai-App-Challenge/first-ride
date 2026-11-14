package com.firstride.engine.course

import com.firstride.engine.signal.SignalId
import com.firstride.engine.signal.SignalValue.Number
import com.firstride.engine.signal.SignalValue.Token
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CourseParserTest {
    private val crs01 = """
    {
      "id": "CRS-01", "name": "김서림 제거", "estimatedSeconds": 60,
      "steps": [{
        "id": "CRS-01-S1",
        "action": [ { "signal": "hvac.frontDefroster", "equals": "ON", "label": "앞유리 제상" } ],
        "hold":   [ { "signal": "hvac.ac", "equals": "ON", "label": "에어컨" },
                    { "signal": "hvac.recirculation", "equals": "FRESH", "label": "외기 유입" } ],
        "misoperations": [
          { "id": "recirc", "when": { "signal": "hvac.recirculation", "equals": "RECIRCULATE" },
            "correction": "내기 순환이 켜져 있어요." } ],
        "doneText": "좋아요."
      }],
      "followUps": [
        { "id": "CRS-01-F1", "condition": { "signal": "hvac.maxDefrost", "equals": "ON", "label": "최대 제상" } },
        { "id": "CRS-01-F2", "condition": { "signal": "hvac.fanSpeed", "atMax": true, "label": "풍량" } }
      ],
      "exhaustedText": "모두 했어요."
    }"""

    @Test fun `차종 공통 정의를 읽는다`() {
        val c = CourseParser.parseCourse(crs01)
        val s = c.steps.single()
        assertEquals(Target.Equals(Token("ON")), s.action.single().target)
        assertEquals(Kind.ACTION, s.action.single().kind)
        assertEquals(listOf(Kind.HOLD, Kind.HOLD), s.hold.map { it.kind })
        assertEquals(SignalId("hvac.recirculation"), s.misoperations.single().signal)
        assertEquals(listOf(Target.Equals(Token("ON")), Target.AtMax), c.followUps.map { it.condition.target })
    }

    @Test fun `숫자 equals와 relative를 읽는다`() {
        val c = CourseParser.parseCourse(
            """{"id":"X","name":"x","estimatedSeconds":1,"steps":[{"id":"X-S1","doneText":"d","action":[
              {"signal":"hvac.fanSpeed","equals":3},{"signal":"window.driverPosition","relative":"down"}]}]}""",
        )
        assertEquals(
            listOf(Target.Equals(Number(3)), Target.Relative(Direction.DOWN)),
            c.steps[0].action.map { it.target },
        )
    }

    @Test fun `목표가 없거나 둘 이상이면 오류다`() {
        fun course(cond: String) =
            """{"id":"X","name":"x","estimatedSeconds":1,"steps":[{"id":"S","doneText":"d","action":[$cond]}]}"""
        assertFailsWith<CourseFormatException> { CourseParser.parseCourse(course("""{"signal":"hvac.ac"}""")) }
        assertFailsWith<CourseFormatException> {
            CourseParser.parseCourse(course("""{"signal":"hvac.ac","equals":"ON","atMax":true}"""))
        }
    }

    @Test fun `빠진 필수 필드와 모르는 필드는 오류다`() {
        assertFailsWith<CourseFormatException> { CourseParser.parseCourse("""{"id":"X"}""") }
        assertFailsWith<CourseFormatException> {
            CourseParser.parseCourse("""{"id":"X","name":"x","estimatedSeconds":1,"steps":[],"typo":1}""")
        }
    }

    @Test fun `차종별 문구 정의를 읽는다`() {
        val m = CourseParser.parseModelText(
            """{"courseId":"CRS-01","steps":{"CRS-01-S1":{"instruction":"누르세요.","locatorText":"왼쪽 위",
              "locatorAsset":"assets/a.webp","conditionLocators":{"hvac.ac":"assets/b.webp"}}}}""",
        )
        val t = m.steps.getValue("CRS-01-S1")
        assertEquals("assets/b.webp", t.conditionLocators[SignalId("hvac.ac")])
        assertTrue(t.misoperationLocators.isEmpty())
    }

    @Test fun `Target 일치 판정`() {
        assertTrue(Target.Relative(Direction.UP).matches(Number(4), Number(3)))
        assertTrue(!Target.Relative(Direction.UP).matches(Number(3), Number(3)))
        assertTrue(!Target.Relative(Direction.UP).matches(Number(4), null))
        assertTrue(Target.AtMax.matches(Number(8), null, max = 8))
        assertTrue(!Target.AtMax.matches(Number(8), null, max = null))
    }
}
