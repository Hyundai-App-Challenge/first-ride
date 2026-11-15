package com.firstride.platform.voice

import com.firstride.engine.voice.SpeakResult
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TtsPlaybackTest {
    private class Fake(val ready: Boolean? = true) : TtsSpeaker {
        lateinit var initialized: (Boolean) -> Unit
        val events = mutableListOf<TtsSpeaker.Events>()
        val texts = mutableListOf<String>()
        var autoStart = true
        var autoStop = true
        var stops = 0
        var releases = 0
        var playFailure: Exception? = null
        override fun initialize(onReady: (Boolean) -> Unit) { initialized = onReady; ready?.let(onReady) }
        override fun play(text: String, events: TtsSpeaker.Events) {
            this.events += events
            texts += text
            if (autoStart) events.onStart()
            playFailure?.let { throw it }
        }
        override fun stop() { stops++; if (autoStop) events.last().onStop() }
        override fun release() { releases++ }
    }
    private val clients = mutableListOf<Fake>()
    private fun source(ready: Boolean? = true) = TtsPlayback { Fake(ready).also { clients += it } }
    private fun event(index: Int) = clients.flatMap { it.events }[index]

    @Test fun `T8 정상 완료 뒤 불필요한 비동기 stop을 보내지 않는다`() = runTest {
        val tts = source()
        val result = async { tts.speak("안내") }; runCurrent()
        assertFalse(result.isCompleted)
        event(0).onDone()
        assertEquals(SpeakResult.DONE, result.await())
        assertEquals(0, clients[0].stops)
        assertEquals(0, clients[0].releases)
        tts.release()
        assertEquals(1, clients[0].releases)
    }

    @Test fun `POL-D-3 초기화 중 stop 뒤 늦은 성공도 이전 문장을 재생하지 않는다`() = runTest {
        val tts = source(null)
        val first = async { tts.speak("첫 안내") }; runCurrent()
        tts.stop(); runCurrent()
        assertEquals(SpeakResult.INTERRUPTED, first.await())
        clients[0].initialized(true); runCurrent()
        assertTrue(clients[0].events.isEmpty())
        val second = async { tts.speak("다음 안내") }; runCurrent(); event(0).onDone()
        assertEquals(SpeakResult.DONE, second.await())
        assertEquals(listOf("다음 안내"), clients[0].texts)
        assertEquals(1, clients.size)
    }

    @Test fun `POL-V-2 이전 오류는 새 문장을 실패시키지 않는다`() = runTest {
        val tts = source()
        val first = async { tts.speak("첫 안내") }; runCurrent()
        tts.stop(); assertEquals(SpeakResult.INTERRUPTED, first.await())
        val second = async { tts.speak("다음 안내") }; runCurrent()
        event(0).onError(); runCurrent()
        assertFalse(second.isCompleted)
        event(1).onDone()
        assertEquals(SpeakResult.DONE, second.await())
    }

    @Test fun `POL-V-2 이전 완료도 새 문장을 완료하지 않는다`() = runTest {
        val tts = source()
        val first = async { tts.speak("첫 안내") }; runCurrent()
        tts.stop(); assertEquals(SpeakResult.INTERRUPTED, first.await())
        val second = async { tts.speak("다음 안내") }; runCurrent()
        event(0).onDone(); runCurrent()
        assertFalse(second.isCompleted)
        event(1).onDone()
        assertEquals(SpeakResult.DONE, second.await())
    }

    @Test fun `POL-V-2 새 재생은 이전 문장을 중단하고 연결은 재사용한다`() = runTest {
        val tts = source()
        val first = async { tts.speak("첫 안내") }; runCurrent()
        val second = async { tts.speak("다음 안내") }; runCurrent()
        assertEquals(SpeakResult.INTERRUPTED, first.await())
        event(0).onStop(); event(0).onError(); runCurrent()
        assertFalse(second.isCompleted)
        event(1).onDone()
        assertEquals(SpeakResult.DONE, second.await())
        assertEquals(1, clients.size)
        assertEquals(1, clients[0].stops)
    }

    @Test fun `POL-D-3 SDK를 만들기 전 stop도 요청을 중단한다`() = runTest {
        val tts = source()
        val result = async(start = CoroutineStart.UNDISPATCHED) { tts.speak("안내") }
        tts.stop(); runCurrent()
        assertEquals(SpeakResult.INTERRUPTED, result.await())
        assertTrue(clients.isEmpty())
    }

    @Test fun `POL-D-3 초기화 취소 뒤에는 같은 연결로 다음 문장만 재생한다`() = runTest {
        val tts = source(null)
        val first = async { tts.speak("첫 안내") }; runCurrent(); first.cancelAndJoin()
        val second = async { tts.speak("다음 안내") }; runCurrent()
        clients[0].initialized(true); runCurrent(); event(0).onDone()
        assertEquals(SpeakResult.DONE, second.await())
        assertEquals(listOf("다음 안내"), clients[0].texts)
        assertEquals(0, clients[0].stops)
    }

    @Test fun `POL-D-3 재생 중 취소는 중지 확인 뒤 다음 문장을 재생한다`() = runTest {
        val tts = source()
        val first = async { tts.speak("첫 안내") }; runCurrent(); first.cancelAndJoin()
        val second = async { tts.speak("다음 안내") }; runCurrent()
        event(0).onDone(); runCurrent()
        assertFalse(second.isCompleted)
        event(1).onDone()
        assertEquals(SpeakResult.DONE, second.await())
        assertEquals(1, clients[0].stops)
    }

    @Test fun `POL-E-4 초기화 미응답 뒤 늦은 성공은 다음 요청이 같은 연결로 쓴다`() = runTest {
        val tts = source(null)
        val first = async { tts.speak("첫 안내") }; runCurrent()
        advanceTimeBy(5_000); runCurrent()
        assertEquals(SpeakResult.ERROR, first.await())
        assertEquals(0, clients[0].releases)
        clients[0].initialized(true)
        val second = async { tts.speak("다음 안내") }; runCurrent(); event(0).onDone()
        assertEquals(SpeakResult.DONE, second.await())
        assertEquals(1, clients.size)
    }

    @Test fun `POL-E-4 명시적 초기화 실패는 정리 후 새 연결로 복구한다`() = runTest {
        val tts = TtsPlayback { Fake(ready = clients.isNotEmpty()).also { clients += it } }
        assertEquals(SpeakResult.ERROR, tts.speak("첫 안내"))
        assertEquals(1, clients[0].releases)
        val second = async { tts.speak("다음 안내") }; runCurrent(); event(0).onDone()
        assertEquals(SpeakResult.DONE, second.await())
        assertEquals(2, clients.size)
    }

    @Test fun `POL-E-4 문장 제한은 실제 중지를 확인하고 다음 안내를 허용한다`() = runTest {
        val tts = source()
        val first = async { tts.speak("x") }; runCurrent()
        advanceTimeBy(3_150); runCurrent()
        assertEquals(SpeakResult.ERROR, first.await())
        val second = async { tts.speak("다음 안내") }; runCurrent()
        event(0).onDone(); runCurrent(); assertFalse(second.isCompleted)
        event(1).onDone()
        assertEquals(SpeakResult.DONE, second.await())
        assertEquals(1, clients[0].stops)
    }

    @Test fun `POL-E-4 SDK 생성 예외 다음 요청도 실행할 수 있다`() = runTest {
        var attempts = 0
        val tts = TtsPlayback {
            if (attempts++ == 0) error("creation failed")
            Fake().also { clients += it }
        }
        assertEquals(SpeakResult.ERROR, tts.speak("첫 안내"))
        val second = async { tts.speak("다음 안내") }; runCurrent(); event(0).onDone()
        assertEquals(SpeakResult.DONE, second.await())
        assertEquals(2, attempts)
    }

    @Test fun `POL-E-4 재생 예외도 중지 경계를 지킨다`() = runTest {
        val tts = TtsPlayback { Fake().apply { playFailure = IllegalStateException("play failed") }.also { clients += it } }
        assertEquals(SpeakResult.ERROR, tts.speak("첫 안내"))
        assertEquals(1, clients[0].stops)
        clients[0].playFailure = null
        val second = async { tts.speak("다음 안내") }; runCurrent(); event(1).onDone()
        assertEquals(SpeakResult.DONE, second.await())
    }

    @Test fun `POL-D-3 release는 중지 응답 전 SDK를 끊지 않는다`() = runTest {
        val tts = source()
        val result = async { tts.speak("안내") }; runCurrent(); clients[0].autoStop = false
        tts.release(); runCurrent()
        assertFalse(result.isCompleted)
        assertEquals(0, clients[0].releases)
        event(0).onStop(); runCurrent()
        assertEquals(SpeakResult.INTERRUPTED, result.await())
        assertEquals(1, clients[0].releases)
        assertEquals(SpeakResult.ERROR, tts.speak("다음 안내"))
    }

    @Test fun `정리 대기 중 교체한 중간 문장은 SDK에 보내지 않는다`() = runTest {
        val tts = source()
        val first = async { tts.speak("첫 안내") }; runCurrent(); clients[0].autoStop = false
        val second = async { tts.speak("중간 안내") }; runCurrent()
        val third = async { tts.speak("마지막 안내") }; runCurrent()
        assertEquals(SpeakResult.INTERRUPTED, second.await())
        assertEquals(1, clients[0].events.size)
        event(0).onStop(); runCurrent()
        assertEquals(SpeakResult.INTERRUPTED, first.await())
        assertEquals(listOf("첫 안내", "마지막 안내"), clients[0].texts)
        event(1).onDone()
        assertEquals(SpeakResult.DONE, third.await())
    }

    @Test fun `비동기 play 시작 전 취소는 START 뒤 STOP을 보내 추월을 막는다`() = runTest {
        val tts = TtsPlayback { Fake().apply { autoStart = false }.also { clients += it } }
        val first = async { tts.speak("첫 안내") }; runCurrent()
        val second = async { tts.speak("다음 안내") }; runCurrent()
        assertEquals(0, clients[0].stops)
        assertEquals(1, clients[0].events.size)
        event(0).onStart(); runCurrent()
        assertEquals(1, clients[0].stops)
        assertEquals(SpeakResult.INTERRUPTED, first.await())
        event(1).onStart(); event(1).onDone()
        assertEquals(SpeakResult.DONE, second.await())
    }

    @Test fun `START 전 종료한 문장에는 추가 STOP 없이 다음 문장을 보낸다`() = runTest {
        val tts = TtsPlayback { Fake().apply { autoStart = false }.also { clients += it } }
        val first = async { tts.speak("첫 안내") }; runCurrent()
        val second = async { tts.speak("다음 안내") }; runCurrent()
        event(0).onDone(); runCurrent()
        assertEquals(SpeakResult.INTERRUPTED, first.await())
        assertEquals(0, clients[0].stops)
        event(1).onDone()
        assertEquals(SpeakResult.DONE, second.await())
    }

    @Test fun `STOP을 보낸 뒤 DONE만 오면 아직 다음 문장을 시작하지 않는다`() = runTest {
        val tts = source()
        val first = async { tts.speak("첫 안내") }; runCurrent(); clients[0].autoStop = false
        val second = async { tts.speak("다음 안내") }; runCurrent()
        event(0).onDone(); runCurrent()
        assertEquals(1, clients[0].events.size)
        assertFalse(second.isCompleted)
        event(0).onStop(); runCurrent()
        assertEquals(SpeakResult.INTERRUPTED, first.await())
        event(1).onDone()
        assertEquals(SpeakResult.DONE, second.await())
    }

    @Test fun `중지 미응답은 새 재생을 막고 늦은 STOP 뒤 복구한다`() = runTest {
        val tts = source()
        val first = async { tts.speak("첫 안내") }; runCurrent(); clients[0].autoStop = false
        val second = async { tts.speak("다음 안내") }; runCurrent()
        advanceTimeBy(10_000); runCurrent()
        assertEquals(SpeakResult.INTERRUPTED, first.await())
        assertEquals(SpeakResult.ERROR, second.await())
        assertEquals(1, clients[0].events.size)
        event(0).onStop()
        val third = async { tts.speak("복구 안내") }; runCurrent(); event(1).onDone()
        assertEquals(SpeakResult.DONE, third.await())
    }

    @Test fun `SDK가 스스로 중단한 경우 STOP을 다시 보내지 않는다`() = runTest {
        val tts = source()
        val first = async { tts.speak("첫 안내") }; runCurrent(); event(0).onStop()
        assertEquals(SpeakResult.INTERRUPTED, first.await())
        assertEquals(0, clients[0].stops)
        val second = async { tts.speak("다음 안내") }; runCurrent(); event(1).onDone()
        assertEquals(SpeakResult.DONE, second.await())
    }

    @Test fun `SDK 오류는 ERROR이며 다음 문장을 막지 않는다`() = runTest {
        val tts = source()
        val first = async { tts.speak("첫 안내") }; runCurrent(); event(0).onError()
        assertEquals(SpeakResult.ERROR, first.await())
        val second = async { tts.speak("다음 안내") }; runCurrent(); event(1).onDone()
        assertEquals(SpeakResult.DONE, second.await())
    }

    @Test fun `초기화 중 release는 연결 완료 뒤 정리하며 재생하지 않는다`() = runTest {
        val tts = source(null)
        val first = async { tts.speak("첫 안내") }; runCurrent(); tts.release()
        assertEquals(SpeakResult.INTERRUPTED, first.await())
        assertEquals(0, clients[0].releases)
        clients[0].initialized(true); runCurrent()
        assertEquals(1, clients[0].releases)
        assertTrue(clients[0].events.isEmpty())
        assertEquals(SpeakResult.ERROR, tts.speak("다음 안내"))
    }
}
