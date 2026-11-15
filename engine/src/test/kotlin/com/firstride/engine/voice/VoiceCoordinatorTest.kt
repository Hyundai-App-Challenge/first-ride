package com.firstride.engine.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceCoordinatorTest {
    private fun TestScope.voice(tts: FakeTts) = VoiceCoordinator(tts, clock = { testScheduler.currentTime })

    @Test fun `AC-06-6 STT 미연결은 진행 불가로 표시하되 안내는 읽는다`() = runTest {
        val tts = FakeTts(100)
        val v = voice(tts)
        assertFalse(v.sttAvailable.value)
        backgroundScope.launch { v.run() }
        v.announce("안내"); advanceTimeBy(100); runCurrent()
        assertEquals(listOf("안내"), tts.started)
        assertTrue(v.available.value)
        assertFalse(v.listening.value)
    }

    @Test fun `듣기 비활성은 어댑터가 있어도 진행 불가이고 요청하지 않는다`() = runTest {
        val tts = FakeTts(100)
        val stt = FakeStt()
        val v = VoiceCoordinator(tts, clock = { testScheduler.currentTime }, stt = stt, listenEnabled = false)
        assertFalse(v.sttAvailable.value)
        backgroundScope.launch { v.run() }
        v.announce("안내"); advanceTimeBy(100); runCurrent()
        assertEquals(listOf("안내"), tts.started)
        assertTrue(v.available.value)
        assertFalse(v.listening.value)
        assertEquals(0, stt.listenCalls)
    }

    @Test fun `AC-05-1 요청한 문구를 읽는다`() = runTest {
        val tts = FakeTts()
        val v = voice(tts)
        backgroundScope.launch { v.run() }
        v.announce("안녕하세요"); runCurrent()
        assertEquals(listOf("안녕하세요"), tts.started)
    }

    @Test fun `POL-V-2 AC-05-2 새 안내가 오면 재생 중인 것을 끊고 겹쳐 재생하지 않는다`() = runTest {
        val tts = FakeTts(durationMillis = 5_000)
        val v = voice(tts)
        backgroundScope.launch { v.run() }
        v.announce("A"); runCurrent()
        advanceTimeBy(1_000); runCurrent()
        v.announce("B"); runCurrent()
        assertEquals(listOf("A", "B"), tts.started)
        assertEquals(1, tts.maxConcurrent)
        advanceTimeBy(5_000); runCurrent()
        assertEquals(listOf("A", "B"), tts.started)       // 쌓아 둔 것이 없다
    }

    @Test fun `POL-V-4 연달아 오면 마지막 것만 읽는다`() = runTest {
        val tts = FakeTts()
        val v = voice(tts)
        backgroundScope.launch { v.run() }
        v.announce("A"); v.announce("B"); v.announce("C"); runCurrent()
        assertEquals(listOf("C"), tts.started)
    }

    @Test fun `POL-V-4 재생 중에 연달아 와도 마지막 것만 읽는다`() = runTest {
        val tts = FakeTts(durationMillis = 5_000)
        val v = voice(tts)
        backgroundScope.launch { v.run() }
        v.announce("A"); runCurrent()
        v.announce("B"); v.announce("C"); runCurrent()
        assertEquals(listOf("A", "C"), tts.started)
    }

    @Test fun `POL-V-3 AC-05-5 같은 문구는 3초 안에 다시 읽지 않는다`() = runTest {
        val tts = FakeTts(durationMillis = 100)
        val v = voice(tts)
        backgroundScope.launch { v.run() }
        v.announce("A"); runCurrent()
        advanceTimeBy(2_999); runCurrent()
        v.announce("A"); runCurrent()
        assertEquals(listOf("A"), tts.started)
        advanceTimeBy(1); runCurrent()
        v.announce("A"); runCurrent()
        assertEquals(listOf("A", "A"), tts.started)
    }

    @Test fun `POL-V-3 다른 문구는 바로 읽고 막은 요청은 재생을 끊지 않는다`() = runTest {
        val tts = FakeTts(durationMillis = 5_000)
        val v = voice(tts)
        backgroundScope.launch { v.run() }
        v.announce("A"); runCurrent()
        val stopsBefore = tts.stopCalls
        v.announce("A"); runCurrent()                   // 같은 문구: 막힌다
        assertEquals(stopsBefore, tts.stopCalls)
        assertEquals(listOf("A"), tts.started)
    }

    @Test fun `FR-06 사용자가 다시 듣기를 요청하면 3초 안이어도 읽는다`() = runTest {
        val tts = FakeTts(durationMillis = 100)
        val v = voice(tts)
        backgroundScope.launch { v.run() }
        v.announce("A"); runCurrent()
        v.announce("A", force = true); runCurrent()
        assertEquals(listOf("A", "A"), tts.started)
    }

    @Test fun `빈 문구는 읽지 않는다`() = runTest {
        val tts = FakeTts()
        val v = voice(tts)
        backgroundScope.launch { v.run() }
        v.announce(""); v.announce("  "); runCurrent()
        assertEquals(emptyList(), tts.started)
    }

    @Test fun `AC-05-3 실습 범위가 취소되면 재생을 멈추고 더 읽지 않는다`() = runTest {
        val tts = FakeTts(durationMillis = 5_000)
        val v = voice(tts)
        val job = backgroundScope.launch { v.run() }
        v.announce("A"); runCurrent()
        val before = tts.stopCalls
        job.cancel(); runCurrent()
        assertTrue(tts.stopCalls > before)
        advanceTimeBy(10_000); runCurrent()
        assertEquals(listOf("A"), tts.started)
    }

    @Test fun `POL-E-4 AC-05-4 재생 오류는 available에만 남고 다음 안내는 계속 시도한다`() = runTest {
        val tts = FakeTts(durationMillis = 100).apply { failWith = SpeakResult.ERROR }
        val v = voice(tts)
        backgroundScope.launch { v.run() }
        v.announce("A"); runCurrent()
        assertEquals(false, v.available.value)

        tts.failWith = null
        v.announce("B"); runCurrent()
        advanceTimeBy(100); runCurrent()
        assertEquals(listOf("A", "B"), tts.started)
        assertEquals(true, v.available.value)
    }
}
