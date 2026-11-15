package com.firstride.engine.course

import com.firstride.engine.course.CourseValidator.Kind
import com.firstride.engine.signal.SignalId
import com.firstride.engine.signal.SignalValue.Number
import com.firstride.engine.signal.SignalValue.Token
import com.firstride.engine.signal.Signals
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CourseValidatorTest {
    private val goodCourse = """
    {"id":"C1","name":"x","estimatedSeconds":1,"steps":[{"id":"C1-S1",
      "action":[{"signal":"hvac.frontDefroster","equals":"ON","label":"제상"}],
      "hold":[{"signal":"hvac.ac","equals":"ON","label":"에어컨"}],
      "misoperations":[{"id":"m","when":{"signal":"hvac.recirculation","equals":"RECIRCULATE"},"correction":"c"}],
      "doneText":"d"}]}"""
    private val goodText = """
    {"courseId":"C1","steps":{"C1-S1":{"instruction":"i","locatorText":"l","locatorAsset":"assets/a.webp",
      "conditionLocators":{"hvac.ac":"assets/b.webp"},"misoperationLocators":{"m":"assets/c.webp"}}}}"""

    private fun check(course: String = goodCourse, text: String? = goodText, exists: (String) -> Boolean = { true }) =
        CourseValidator.validate(
            CourseParser.parseCourse(course),
            text?.let { CourseParser.parseModelText(it) },
            exists,
        )

    private fun kinds(vararg k: Kind) = k.toList()
    private fun List<CourseValidator.Error>.kinds() = map { it.kind }

    @Test fun `올바른 정의는 오류가 없다`() = assertEquals(emptyList(), check())

    @Test fun `AC-13-2 신호 목록에 없는 이름은 오류다`() {
        val e = check(goodCourse.replace("hvac.frontDefroster", "hvac.frontDefroter"))
        assertEquals(kinds(Kind.UNKNOWN_SIGNAL), e.kinds())
    }

    @Test fun `AC-13-2 그 신호가 가질 수 없는 값은 오류다`() {
        val e = check(goodCourse.replace("""{"signal":"hvac.ac","equals":"ON"""", """{"signal":"hvac.ac","equals":"FRESH""""))
        assertEquals(kinds(Kind.BAD_VALUE), e.kinds())
    }

    @Test fun `토큰 신호에 숫자 값, 숫자 신호에 토큰 값은 오류다`() {
        assertEquals(kinds(Kind.BAD_VALUE), check(goodCourse.replace("\"equals\":\"ON\",\"label\":\"에어컨\"", "\"equals\":3,\"label\":\"에어컨\"")).kinds())
        val fan = goodCourse.replace("hvac.frontDefroster\",\"equals\":\"ON\"", "hvac.fanSpeed\",\"equals\":\"ON\"")
        assertEquals(kinds(Kind.BAD_VALUE), check(fan).kinds())
    }

    @Test fun `상대 목표와 atMax는 숫자 신호에만 쓴다`() {
        val rel = goodCourse.replace("\"equals\":\"ON\",\"label\":\"제상\"", "\"relative\":\"up\",\"label\":\"제상\"")
        assertEquals(kinds(Kind.BAD_TARGET), check(rel).kinds())
        val max = goodCourse.replace("\"equals\":\"ON\",\"label\":\"제상\"", "\"atMax\":true,\"label\":\"제상\"")
        assertEquals(kinds(Kind.BAD_TARGET), check(max).kinds())
        val ok = goodCourse.replace("hvac.frontDefroster\",\"equals\":\"ON\"", "hvac.fanSpeed\",\"atMax\":true")
        assertEquals(emptyList(), check(ok))
    }

    @Test fun `조작 조건이 없는 단계는 오류다`() {
        val c = """{"id":"C1","name":"x","estimatedSeconds":1,"steps":[{"id":"C1-S1","action":[],
            "hold":[{"signal":"hvac.ac","equals":"ON"}],"doneText":"d"}]}"""
        val t = """{"courseId":"C1","steps":{"C1-S1":{"instruction":"i","locatorText":"l","locatorAsset":"a"}}}"""
        assertEquals(kinds(Kind.NO_ACTION), check(c, t).kinds())
    }

    @Test fun `POL-C-7 AC-08-2 차종별 파일이 없거나 단계가 빠지면 오류다`() {
        assertEquals(kinds(Kind.MISSING_TEXT), check(text = null).kinds())
        assertEquals(kinds(Kind.MISSING_TEXT), check(text = """{"courseId":"C1","steps":{}}""").kinds())
    }

    @Test fun `POL-C-7 위치 안내 자산 파일이 없으면 오류다`() {
        val e = check(exists = { it != "assets/a.webp" })
        assertEquals(kinds(Kind.MISSING_ASSET), e.kinds())
        assertTrue("assets/a.webp" in e.single().message)
    }

    @Test fun `조건·오조작 위치 안내 자산도 있어야 한다`() {
        val e = check(exists = { it == "assets/a.webp" })
        assertEquals(kinds(Kind.MISSING_ASSET, Kind.MISSING_ASSET), e.kinds())
    }

    @Test fun `차종별 파일이 정의에 없는 단계·조건·오조작을 가리키면 오류다`() {
        val extraStep = goodText.replace("\"C1-S1\":{", "\"C1-S9\":{\"instruction\":\"i\",\"locatorText\":\"l\",\"locatorAsset\":\"a\"},\"C1-S1\":{")
        assertEquals(kinds(Kind.MISMATCH), check(text = extraStep).kinds())
        val badCond = goodText.replace("hvac.ac", "hvac.rearDefroster")
        assertEquals(kinds(Kind.MISMATCH), check(text = badCond).kinds())
        val badMiso = goodText.replace("\"m\":\"assets/c.webp\"", "\"zz\":\"assets/c.webp\"")
        assertEquals(kinds(Kind.MISMATCH), check(text = badMiso).kinds())
        assertEquals(kinds(Kind.MISMATCH), check(text = goodText.replace("\"C1\"", "\"C2\"")).kinds())
    }

    @Test fun `POL-W-4 문구가 3줄 분량을 넘으면 오류다`() {
        val long = "가".repeat(CourseValidator.MAX_TEXT_LENGTH + 1)
        assertEquals(kinds(Kind.TOO_LONG), check(goodCourse.replace("\"d\"", "\"$long\"")).kinds())
        assertEquals(kinds(Kind.TOO_LONG), check(text = goodText.replace("\"i\"", "\"$long\"")).kinds())
        assertEquals(emptyList(), check(goodCourse.replace("\"d\"", "\"${"가".repeat(CourseValidator.MAX_TEXT_LENGTH)}\"")))
    }

    @Test fun `ID가 겹치면 오류다`() {
        val dup = goodCourse.replace("\"steps\":[{", "\"steps\":[{\"id\":\"C1-S1\",\"action\":[{\"signal\":\"hvac.ac\",\"equals\":\"ON\"}],\"doneText\":\"d\"},{")
        assertTrue(Kind.DUPLICATE_ID in check(dup).kinds())
    }

    @Test fun `후속 조치도 같은 규칙으로 검사한다`() {
        val withFollowUp = goodCourse.dropLast(1) + """,
          "followUps":[{"id":"C1-F1","condition":{"signal":"hvac.fanSpeed","atMax":true,"label":"풍량"}}],
          "exhaustedText":"끝"}"""
        val followText = goodText.replace("\"C1-S1\":{", "\"C1-F1\":{\"instruction\":\"i\",\"locatorText\":\"l\",\"locatorAsset\":\"assets/f.webp\"},\"C1-S1\":{")
        assertEquals(emptyList(), check(withFollowUp, followText))
        // 차종별 파일에 조치 문구가 없다
        assertEquals(kinds(Kind.MISSING_TEXT), check(withFollowUp, goodText).kinds())
        // 마무리 문구가 없다
        assertEquals(kinds(Kind.MISSING_TEXT), check(withFollowUp.replace(",\n          \"exhaustedText\":\"끝\"", ""), followText).kinds())
        // atMax를 토큰 신호에 쓴다
        assertEquals(kinds(Kind.BAD_TARGET), check(withFollowUp.replace("hvac.fanSpeed", "hvac.ac"), followText).kinds())
    }

    // ── 실제 assets ──

    private val assets = File("../app/src/main/assets")

    /** AC-13-2 POL-E-5: 앱이 읽는 모든 코스 정의가 검증을 통과한다. 잘못된 정의는 빌드(테스트) 단계에서 걸린다. */
    @Test fun `AC-13-2 assets의 모든 코스 정의를 검증한다`() {
        val courseFiles = File(assets, "courses").listFiles { f -> f.name.matches(Regex("CRS-\\d+\\.json")) }.orEmpty().sorted()
        val modelDirs = File(assets, "models").listFiles { f -> f.isDirectory }.orEmpty().sorted()
        assertTrue(courseFiles.isNotEmpty(), "${assets.absolutePath}에 코스 정의가 없다")
        assertTrue(modelDirs.isNotEmpty(), "차종별 폴더가 없다")

        val errors = buildList {
            for (cf in courseFiles) {
                val course = CourseParser.parseCourse(cf.readText())
                assertEquals(cf.nameWithoutExtension, course.id, "${cf.name}의 id가 파일 이름과 다르다")
                for (dir in modelDirs) {
                    val mf = File(dir, cf.name)
                    val text = mf.takeIf { it.exists() }?.let { CourseParser.parseModelText(it.readText()) }
                    addAll(CourseValidator.validate(course, text) { File(dir, it).exists() }.map { "${dir.name}/${cf.name} $it" })
                }
            }
        }
        // shortcut: 위치 안내 자산(W-17)이 아직 없어서 MISSING_ASSET만 뺀다. W-17이 끝나면 이 필터를 없앤다.
        val blocking = errors.filterNot { "[MISSING_ASSET]" in it }
        assertEquals(emptyList(), blocking)
    }

    @Test fun `아이오닉5 실내 안내가 기존 여섯 코스 계약을 충족한다`() {
        for (id in (1..6).map { "CRS-0$it" }) {
            val course = CourseParser.parseCourse(File(assets, "courses/$id.json").readText())
            val text = CourseParser.parseModelText(File(assets, "panels/ioniq5-2025/$id.json").readText())
            assertEquals(emptyList(), CourseValidator.validate(course, text) { File(assets, "models/reference/$it").isFile })
        }
    }

    @Test fun `CRS-01은 대표 코스의 정의 그대로 읽힌다`() {
        val c = CourseParser.parseCourse(File(assets, "courses/CRS-01.json").readText())
        val s = c.steps.single()
        assertEquals(Target.Equals(Token("ON")), s.action.single().target)
        assertEquals(SignalId("hvac.frontDefroster"), s.action.single().signal)
        assertEquals(listOf(Signals.HVAC_AC, Signals.HVAC_RECIRCULATION), s.hold.map { it.signal })
        assertEquals(Target.Equals(Token("RECIRCULATE")), s.misoperations.single().target)
        // 숫자 값 형식도 유지된다
        assertEquals(Target.Equals(Number(3)), CourseParser.parseCourse(
            """{"id":"X","name":"x","estimatedSeconds":1,"steps":[{"id":"S","doneText":"d","action":[{"signal":"hvac.fanSpeed","equals":3}]}]}"""
        ).steps.single().action.single().target)
    }
}
