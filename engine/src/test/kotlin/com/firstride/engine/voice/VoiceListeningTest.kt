package com.firstride.engine.voice

import com.firstride.engine.course.StepText
import com.firstride.engine.judge.MemStore
import com.firstride.engine.judge.Phase
import com.firstride.engine.judge.StepRunner
import com.firstride.engine.judge.ac
import com.firstride.engine.judge.crs01Step
import com.firstride.engine.judge.defrost
import com.firstride.engine.judge.source
import com.firstride.engine.judge.stepView
import com.firstride.engine.signal.SignalValue.Token
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceListeningTest {
    private class Rig(
        val tts: FakeTts,
        val stt: FakeStt,
        val actions: MutableList<VoiceAction>,
        val voice: VoiceCoordinator,
    )

    private fun TestScope.rig(
        stt: FakeStt,
        done: Boolean = false,
        enabled: Boolean = true,
        listen: Boolean = true,
        ttsMillis: Long = 1_000,
    ): Rig {
        val tts = FakeTts(ttsMillis)
        val actions = mutableListOf<VoiceAction>()
        val v = VoiceCoordinator(
            tts, clock = { testScheduler.currentTime }, stt = stt,
            voiceEnabled = { enabled }, listenEnabled = listen, isStepDone = { done }, onAction = { actions += it },
        )
        backgroundScope.launch { v.run() }
        return Rig(tts, stt, actions, v)
    }

    @Test fun `AC-06-2 어휘 매칭 표`() {
        val table = mapOf(
            "다음" to Vocab.NEXT, "다음으로" to Vocab.NEXT, "다음 단계" to Vocab.NEXT, "다음!" to Vocab.NEXT,
            "다시" to Vocab.REPEAT, "다시 해 줘" to Vocab.REPEAT,
            "어디야" to Vocab.WHERE, "어디 야" to Vocab.WHERE, "어디야?" to Vocab.WHERE, "어디 야?" to Vocab.WHERE,
            "다음 말고 다시" to null,       // POL-V-9
            "다음 다시 어디야" to null,
            "" to null, "   " to null, "안녕하세요" to null, "그만하기" to null, "기록 지우기" to null,
        )
        table.forEach { (text, want) -> assertEquals(want, matchVocabulary(text), "'$text'") }
    }

    @Test fun `POL-V-11 AC-06-5 재생 중에는 듣지 않고 재생이 끝난 뒤에 듣는다`() = runTest {
        val r = rig(FakeStt(), ttsMillis = 1_000)
        r.voice.announce("안내"); runCurrent()
        advanceTimeBy(999); runCurrent()
        assertEquals(0, r.stt.listenCalls)
        assertFalse(r.voice.listening.value)
        advanceTimeBy(1); runCurrent()
        assertEquals(1, r.stt.listenCalls)
        assertTrue(r.voice.listening.value)             // POL-V-13: 듣는 동안 표시할 값
    }

    @Test fun `AC-06-2 다시는 Repeat, 어디야는 Where, 완료된 단계의 다음은 Next`() = runTest {
        val r = rig(FakeStt(said("다시"), said("어디 야?"), said("다음으로")), done = true)
        r.voice.announce("안내"); runCurrent()
        advanceTimeBy(1_000 + 300); runCurrent()
        assertEquals(listOf<VoiceAction>(VoiceAction.Repeat, VoiceAction.Where, VoiceAction.Next), r.actions)
    }

    @Test fun `POL-V-10 AC-06-3 어휘가 아닌 말과 무음은 아무 동작 없이 다시 듣는다`() = runTest {
        val r = rig(FakeStt(said("안녕하세요"), silence(), said("음"), silence()))
        r.voice.announce("안내"); runCurrent()
        advanceTimeBy(1_000 + 400); runCurrent()
        assertEquals(emptyList<VoiceAction>(), r.actions)
        assertEquals(listOf("안내"), r.tts.started)       // 오류 문구도 내지 않는다
        assertEquals(5, r.stt.listenCalls)                // 네 번 받고 다섯 번째를 기다린다
        assertTrue(r.voice.listening.value)
    }

    @Test fun `POL-V-9 AC-06-8 어휘가 둘 이상이면 아무 동작도 하지 않는다`() = runTest {
        val r = rig(FakeStt(said("다음 말고 다시"), said("다시 어디야")), done = true)
        r.voice.announce("안내"); runCurrent()
        advanceTimeBy(1_000 + 200); runCurrent()
        assertEquals(emptyList<VoiceAction>(), r.actions)
        assertEquals(listOf("안내"), r.tts.started)
    }

    @Test fun `AC-06-4 미완료 단계에서 다음을 한 번 말하면 되묻기만 하고 건너뛰지 않는다`() = runTest {
        val r = rig(FakeStt(said("다음")), done = false)
        r.voice.announce("안내"); runCurrent()
        advanceTimeBy(1_000 + 100); runCurrent()
        assertEquals(listOf("안내", CONFIRM_SKIP_TEXT), r.tts.started)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(emptyList<VoiceAction>(), r.actions)
    }

    @Test fun `POL-V-12 AC-06-9 되물은 뒤 6초 안에 다음을 다시 말하면 건너뛴다`() = runTest {
        val r = rig(FakeStt(said("다음"), said("다음", after = 5_900)), done = false)
        r.voice.announce("안내"); runCurrent()
        advanceTimeBy(1_000 + 100 + 1_000 + 5_900); runCurrent()
        assertEquals(listOf<VoiceAction>(VoiceAction.ConfirmSkip), r.actions)
    }

    @Test fun `POL-V-12 AC-06-9 6초가 지나면 되묻기가 취소되고 다음을 말하면 다시 되묻는다`() = runTest {
        val r = rig(FakeStt(said("다음"), said("다음", after = 6_500), said("다음")), done = false)
        r.voice.announce("안내"); runCurrent()
        // 되묻기 문구가 끝난 시각(2100)부터 6초 → 8100에 취소된다
        advanceTimeBy(8_150); runCurrent()
        assertEquals(emptyList<VoiceAction>(), r.actions)
        advanceTimeBy(200); runCurrent()                  // 취소 뒤 들은 "다음"은 새로운 첫 번째 "다음"이다
        assertEquals(emptyList<VoiceAction>(), r.actions)
        assertEquals(listOf("안내", CONFIRM_SKIP_TEXT, CONFIRM_SKIP_TEXT), r.tts.started)
    }

    @Test fun `AC-06-10 POL-V-16 그만하기와 기록 지우기는 말해도 아무 동작도 없다`() = runTest {
        val r = rig(FakeStt(said("그만하기"), said("기록 지우기"), said("그만")), done = true)
        r.voice.announce("안내"); runCurrent()
        advanceTimeBy(1_000 + 300); runCurrent()
        assertEquals(emptyList<VoiceAction>(), r.actions)
        // 동작의 종류 자체가 넷뿐이다: 새 종류가 생기면 이 when이 컴파일되지 않는다
        val kinds = listOf<VoiceAction>(VoiceAction.Next, VoiceAction.Repeat, VoiceAction.Where, VoiceAction.ConfirmSkip).map {
            when (it) { VoiceAction.Next -> 1; VoiceAction.Repeat -> 2; VoiceAction.Where -> 3; VoiceAction.ConfirmSkip -> 4 }
        }
        assertEquals(listOf(1, 2, 3, 4), kinds)
    }

    @Test fun `POL-V-6 음성이 꺼져 있으면 읽지도 듣지도 않는다`() = runTest {
        val r = rig(FakeStt(said("다음")), enabled = false)
        r.voice.announce("안내"); runCurrent()
        advanceTimeBy(5_000); runCurrent()
        assertEquals(emptyList(), r.tts.started)
        assertEquals(0, r.stt.listenCalls)
    }

    @Test fun `listenEnabled가 꺼져 있으면 재생이 끝나도 듣지 않는다`() = runTest {
        val r = rig(FakeStt(said("다음")), listen = false)
        r.voice.announce("안내"); runCurrent()
        advanceTimeBy(5_000); runCurrent()
        assertEquals(listOf("안내"), r.tts.started)
        assertEquals(0, r.stt.listenCalls)
    }

    @Test fun `POL-V-2 듣는 중에 새 안내가 오면 듣기를 끊고 재생이 끝난 뒤 다시 듣는다`() = runTest {
        val r = rig(FakeStt())
        r.voice.announce("A"); runCurrent()
        advanceTimeBy(1_000); runCurrent()
        assertEquals(1, r.stt.listenCalls)
        val stops = r.stt.stopCalls
        r.voice.announce("B"); runCurrent()
        assertTrue(r.stt.stopCalls > stops)
        assertFalse(r.voice.listening.value)
        assertEquals(1, r.stt.listenCalls)               // B가 재생되는 동안은 듣지 않는다
        advanceTimeBy(1_000); runCurrent()
        assertEquals(2, r.stt.listenCalls)
    }

    @Test fun `AC-05-3 실습 범위가 취소되면 듣기도 멈춘다`() = runTest {
        val stt = FakeStt()
        val tts = FakeTts(100)
        val v = VoiceCoordinator(tts, clock = { testScheduler.currentTime }, stt = stt)
        val job = launch { v.run() }
        v.announce("A"); runCurrent()
        advanceTimeBy(100); runCurrent()
        assertTrue(v.listening.value)
        val stops = stt.stopCalls
        job.cancel(); runCurrent()
        assertTrue(stt.stopCalls > stops)
        assertFalse(v.listening.value)
    }

    @Test fun `AC-06-6 듣기 오류는 sttAvailable에 남고 듣기를 멈추되 진행을 막지 않는다`() = runTest {
        val r = rig(FakeStt(100L to ListenResult.Error))
        r.voice.announce("A"); runCurrent()
        advanceTimeBy(1_100); runCurrent()
        assertFalse(r.voice.sttAvailable.value)
        assertFalse(r.voice.listening.value)
        assertEquals(1, r.stt.listenCalls)
        r.voice.announce("B"); runCurrent()              // 읽기는 계속된다
        assertEquals(listOf("A", "B"), r.tts.started)
    }

    @Test fun `AC-06-4 AC-06-2 음성 동작이 단계 실행기에 닿는다 - 되묻고 한 번 더 말하면 건너뜀`() = runTest {
        val runner = StepRunner(crs01Step(), source(ac = "OFF"))
        val stepRun = async { runner.run() }
        val text = StepText(instruction = "버튼을 누르세요.", locatorText = "왼쪽 위에 있어요.", locatorAsset = "a.webp")
        val tts = FakeTts(1_000)
        lateinit var v: VoiceCoordinator
        v = VoiceCoordinator(
            tts, clock = { testScheduler.currentTime }, stt = FakeStt(said("다음"), said("다음", after = 500)),
            isStepDone = { runner.state.value.phase == Phase.DONE },
            onAction = { applyVoiceAction(it, runner, stepView(runner.state.value, crs01Step(), text)) { s -> v.announce(s, force = true) } },
        )
        backgroundScope.launch { v.run() }
        runCurrent()
        v.announce("안내"); runCurrent()
        advanceTimeBy(1_100); runCurrent()
        assertEquals(Phase.WAITING, runner.state.value.phase)       // 되물은 것만으로는 건너뛰지 않는다
        advanceTimeBy(1_000 + 500); runCurrent()
        assertEquals(Phase.SKIPPED, stepRun.await())
    }

    @Test fun `AC-06-2 완료된 단계의 다음은 자동 진행을 기다리지 않고 완료로 끝난다`() = runTest {
        val src = source(ac = "ON")
        val runner = StepRunner(crs01Step(), src)
        val stepRun = async { runner.run() }
        runCurrent()
        src.set(defrost, Token("ON")); runCurrent()
        assertEquals(Phase.DONE, runner.state.value.phase)
        runner.advance(); runCurrent()
        assertTrue(stepRun.isCompleted)                  // 2초가 지나기 전에 끝났다
        assertEquals(Phase.DONE, stepRun.await())        // 건너뜀이 아니다
    }

    @Test fun `완료가 아닌 단계에서 advance는 아무 일도 하지 않는다`() = runTest {
        val runner = StepRunner(crs01Step(), source(ac = "OFF"))
        val stepRun = async { runner.run() }
        runCurrent()
        runner.advance(); runCurrent()
        assertFalse(stepRun.isCompleted)
        stepRun.cancel()
    }
}
