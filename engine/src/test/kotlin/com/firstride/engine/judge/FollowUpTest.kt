package com.firstride.engine.judge

import com.firstride.engine.course.ConditionDef
import com.firstride.engine.course.CourseDefinition
import com.firstride.engine.course.CourseParser
import com.firstride.engine.course.FollowUpDef
import com.firstride.engine.course.Kind
import com.firstride.engine.course.Target
import com.firstride.engine.progress.ProgressStore
import com.firstride.engine.signal.FailReason
import com.firstride.engine.signal.FakeSignalSource
import com.firstride.engine.signal.Reading
import com.firstride.engine.signal.SignalValue.Number
import com.firstride.engine.signal.SignalValue.Token
import com.firstride.engine.signal.Signals
import com.firstride.engine.signal.Support
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private val maxDefrost = Signals.HVAC_MAX_DEFROST
private val temp = Signals.HVAC_TEMPERATURE

/** CRS-01과 같은 모양: S1(제상 + 에어컨·외기) 뒤에 F1 최대 제상, F2 풍량 최대, F3 온도 최고. */
private fun fogCourse() = CourseDefinition(
    "T-FOG", "김서림", 60, listOf(crs01Step()),
    followUps = listOf(
        FollowUpDef("F1", ConditionDef(maxDefrost, Target.Equals(Token("ON")), "최대 제상", Kind.ACTION)),
        FollowUpDef("F2", ConditionDef(fan, Target.AtMax, "풍량 최대", Kind.ACTION)),
        FollowUpDef("F3", ConditionDef(temp, Target.AtMax, "온도 최고", Kind.ACTION)),
    ),
    exhaustedText = "다 했어요.",
)

/** AC-CRS01-5의 시작 상태: 기본 조합 맞음, 최대 제상 Off, 풍량 3, 온도 22℃. */
private fun fogSource() = source(defrost = "ON", ac = "ON", recirc = "FRESH", fan = 3).apply {
    set(maxDefrost, Token("OFF"))
    set(temp, Number(220))
    setRange(fan, 1..7)
    setRange(temp, 160..320)
}

private fun readingsOf(s: FakeSignalSource) = listOf(defrost, ac, recirc, fan, maxDefrost, temp).associateWith { kotlinx.coroutines.runBlocking { s.read(it) } }
private fun rangesOf(s: FakeSignalSource) = listOf(fan, temp).associateWith { kotlinx.coroutines.runBlocking { s.range(it) } }
private fun supportOf(s: FakeSignalSource) = listOf(maxDefrost, fan, temp).associateWith { kotlinx.coroutines.runBlocking { s.support(it) } }

private fun choose(s: FakeSignalSource, offered: Set<String> = emptySet()) =
    chooseNext(fogCourse(), readingsOf(s), rangesOf(s), supportOf(s), offered)

@OptIn(ExperimentalCoroutinesApi::class)
class FollowUpTest {
    @Test fun `AC-CRS01-5 기본 조합이 맞고 최대 제상 Off 풍량 3 온도 22도면 F1이 먼저 제안된다`() {
        val next = choose(fogSource())
        assertEquals("F1", assertIs<FollowUpNext.Offer>(next).followUp.id)
    }

    @Test fun `AC-16-2 POL-F-11 에어컨이 꺼져 있으면 첫 안내는 기본 조합 복구다`() {
        val s = fogSource().apply { set(ac, Token("OFF")) }
        val next = assertIs<FollowUpNext.RestoreBase>(choose(s))
        assertEquals(listOf(ac), next.step.action.map { it.signal })
        assertEquals("에어컨", next.step.action.single().label)
    }

    @Test fun `AC-16-2 복구 단계는 에어컨을 켜면 끝나고 다음은 후속 조치다`() = runTest {
        val s = fogSource().apply { set(ac, Token("OFF")) }
        val r = FollowUpRunner(fogCourse(), s)
        backgroundScope.launch { r.run() }
        runCurrent()
        val doing = assertIs<FollowUpState.Doing>(r.state.value)
        assertTrue(doing.restore)
        s.set(ac, Token("ON")); runCurrent()
        advanceTimeBy(2_000); runCurrent()
        val next = assertIs<FollowUpState.Doing>(r.state.value)
        assertFalse(next.restore)
        assertEquals("F1", next.step.id)
    }

    @Test fun `POL-F-11 복구 단계를 건너뛰면 같은 안내에서 다시 내지 않는다`() = runTest {
        val s = fogSource().apply { set(ac, Token("OFF")) }
        val r = FollowUpRunner(fogCourse(), s)
        backgroundScope.launch { r.run() }
        runCurrent()
        assertIs<FollowUpState.Doing>(r.state.value).progress.runner.skip(); runCurrent()
        assertEquals("F1", assertIs<FollowUpState.Doing>(r.state.value).step.id)
    }

    @Test fun `AC-16-3 POL-F-4 풍량이 이미 최댓값이면 풍량 조치는 제안되지 않는다`() {
        val s = fogSource().apply { set(fan, Number(7)) }
        assertEquals("F1", assertIs<FollowUpNext.Offer>(choose(s)).followUp.id)
        assertEquals("F3", assertIs<FollowUpNext.Offer>(choose(s, setOf("F1"))).followUp.id)
    }

    @Test fun `AC-16-4 최대 제상이 미지원이면 그 조치는 목록에 없다`() {
        val s = fogSource().apply { setSupport(maxDefrost, Support.UNSUPPORTED) }
        assertEquals("F2", assertIs<FollowUpNext.Offer>(choose(s)).followUp.id)
    }

    @Test fun `지원 여부를 모르거나 권한이 없는 조치도 내지 않는다`() {
        val s = fogSource().apply { setSupport(maxDefrost, Support.UNKNOWN); setSupport(fan, Support.DENIED) }
        assertEquals("F3", assertIs<FollowUpNext.Offer>(choose(s)).followUp.id)
    }

    @Test fun `AtMax 조치는 범위를 모르면 내지 않는다`() {
        val s = fogSource().apply { setRange(fan, null) }
        assertEquals("F3", assertIs<FollowUpNext.Offer>(choose(s, setOf("F1"))).followUp.id)
    }

    @Test fun `AC-16-8 POL-F-7 이미 낸 조치만 남으면 마무리다`() {
        assertEquals(FollowUpNext.Exhausted, choose(fogSource(), setOf("F1", "F2", "F3")))
    }

    @Test fun `기본 조합 신호를 읽지 못하면 어긋났다고 단정하지 않는다`() {
        val s = fogSource().apply { fail(ac, FailReason.CONNECTION) }
        assertIs<FollowUpNext.Offer>(choose(s))
    }

    @Test fun `AC-16-5 고른 조치는 WAITING으로 시작하고 신호 전이 없이는 완료되지 않는다`() = runTest {
        val s = fogSource()
        val r = FollowUpRunner(fogCourse(), s)
        backgroundScope.launch { r.run() }
        runCurrent()
        val doing = assertIs<FollowUpState.Doing>(r.state.value)
        assertEquals(Phase.WAITING, doing.progress.runner.state.value.phase)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(Phase.WAITING, doing.progress.runner.state.value.phase)
        assertEquals(doing, r.state.value)
    }

    @Test fun `AC-16-6 AC-16-7 AC-CRS01-6 완료 뒤 묻고 아직이요면 다시 읽어 이미 된 F2를 건너뛰고 F3을 낸다`() = runTest {
        val s = fogSource()
        val r = FollowUpRunner(fogCourse(), s)
        backgroundScope.launch { r.run() }
        runCurrent()
        // F1 완료. 차가 최대 제상과 함께 풍량도 최대로 바꾼다.
        s.set(maxDefrost, Token("ON")); s.set(fan, Number(7)); runCurrent()
        advanceTimeBy(2_000); runCurrent()
        assertEquals(FollowUpState.Asking, r.state.value)
        r.answer(FollowUpAnswer.NOT_YET); runCurrent()
        assertEquals("F3", assertIs<FollowUpState.Doing>(r.state.value).step.id)
    }

    @Test fun `POL-F-1 POL-F-2 걷혔어요면 끝난다`() = runTest {
        val s = fogSource()
        val r = FollowUpRunner(fogCourse(), s)
        val job = backgroundScope.launch { r.run() }
        runCurrent()
        s.set(maxDefrost, Token("ON")); runCurrent(); advanceTimeBy(2_000); runCurrent()
        r.answer(FollowUpAnswer.RESOLVED); runCurrent()
        assertEquals(FollowUpState.Resolved, r.state.value)
        assertTrue(job.isCompleted)
        assertEquals(0, s.activeSubscriptions)
    }

    @Test fun `묻는 중이 아닐 때의 답은 무시한다`() = runTest {
        val s = fogSource()
        val r = FollowUpRunner(fogCourse(), s)
        backgroundScope.launch { r.run() }
        runCurrent()
        r.answer(FollowUpAnswer.RESOLVED); runCurrent()
        assertIs<FollowUpState.Doing>(r.state.value)
    }

    @Test fun `AC-16-8 모든 조치를 마치고도 아직이요면 마무리가 된다`() = runTest {
        val s = fogSource()
        val r = FollowUpRunner(fogCourse(), s)
        val job = backgroundScope.launch { r.run() }
        runCurrent()
        s.set(maxDefrost, Token("ON")); runCurrent(); advanceTimeBy(2_000); runCurrent()
        r.answer(FollowUpAnswer.NOT_YET); runCurrent()
        s.set(fan, Number(7)); runCurrent(); advanceTimeBy(2_000); runCurrent()
        r.answer(FollowUpAnswer.NOT_YET); runCurrent()
        s.set(temp, Number(320)); runCurrent(); advanceTimeBy(2_000); runCurrent()
        r.answer(FollowUpAnswer.NOT_YET); runCurrent()
        assertEquals(FollowUpState.Exhausted, r.state.value)
        assertTrue(job.isCompleted)
    }

    @Test fun `AC-16-12 조치를 건너뛰면 묻지 않고 다음 조치로 간다`() = runTest {
        val s = fogSource()
        val r = FollowUpRunner(fogCourse(), s)
        backgroundScope.launch { r.run() }
        runCurrent()
        assertIs<FollowUpState.Doing>(r.state.value).progress.runner.skip(); runCurrent()
        assertEquals("F2", assertIs<FollowUpState.Doing>(r.state.value).step.id)
    }

    @Test fun `AC-16-12 신호를 읽지 못하면 UNVERIFIABLE이다`() = runTest {
        val s = fogSource().apply { fail(maxDefrost, FailReason.CONNECTION) }
        val r = FollowUpRunner(fogCourse(), s)
        backgroundScope.launch { r.run() }
        runCurrent()
        val doing = assertIs<FollowUpState.Doing>(r.state.value)
        assertEquals("F1", doing.step.id)
        assertEquals(Phase.UNVERIFIABLE, doing.progress.runner.state.value.phase)
    }

    @Test fun `AC-16-13 POL-F-10 취소하면 구독이 모두 풀리고 이어서 하지 않는다`() = runTest {
        val s = fogSource()
        val r = FollowUpRunner(fogCourse(), s)
        val job = backgroundScope.launch { r.run() }
        runCurrent()
        assertTrue(s.activeSubscriptions > 0)
        job.cancel(); runCurrent()
        assertEquals(0, s.activeSubscriptions)
        // 새 실행은 점검부터 다시 한다
        val again = FollowUpRunner(fogCourse(), s)
        assertEquals(FollowUpState.Checking, again.state.value)
    }

    @Test fun `AC-16-9 POL-F-8 실행기는 진행 기록 저장소를 받지 않는다`() {
        val params = FollowUpRunner::class.java.constructors.flatMap { it.parameterTypes.toList() }
        assertTrue(params.none { ProgressStore::class.java.isAssignableFrom(it) })
    }

    @Test fun `AC-16-8 POL-F-7 CRS-01의 마무리 문구에는 고장이나 이상이라는 단정이 없다`() {
        val course = CourseParser.parseCourse(File("../app/src/main/assets/courses/CRS-01.json").readText())
        val text = course.exhaustedText.orEmpty()
        assertTrue(text.isNotBlank())
        assertFalse("고장" in text || "이상" in text)
        val all = File("../app/src/main/assets/models/reference/CRS-01.json").readText() + text
        assertFalse(Regex("고장|이상").containsMatchIn(all))
    }

    @Test fun `AC-CRS01-5 CRS-01 정의의 후속 조치는 F1 F2 F3 순서다`() {
        val course = CourseParser.parseCourse(File("../app/src/main/assets/courses/CRS-01.json").readText())
        assertEquals(listOf("CRS-01-F1", "CRS-01-F2", "CRS-01-F3"), course.followUps.map { it.id })
        assertEquals(listOf(Target.Equals(Token("ON")), Target.AtMax, Target.AtMax), course.followUps.map { it.condition.target })
    }
}
