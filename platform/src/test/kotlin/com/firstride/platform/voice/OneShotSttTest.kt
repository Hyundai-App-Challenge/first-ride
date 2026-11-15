package com.firstride.platform.voice

import com.firstride.engine.voice.ListenResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OneShotSttTest {
    private class Fake(private val ready: Boolean?) : SttRecognizer {
        lateinit var events: SttRecognizer.Events
        lateinit var onReady: (Boolean) -> Unit
        var requests = 0
        var releases = 0
        var requestFailure: Exception? = null
        var releaseStarted: CountDownLatch? = null
        var releaseGate: CountDownLatch? = null
        override fun initialize(onReady: (Boolean) -> Unit) {
            this.onReady = onReady
            ready?.let(onReady)
        }
        override fun request(events: SttRecognizer.Events) {
            requestFailure?.let { throw it }
            this.events = events; requests++
        }
        override fun release() {
            releases++
            releaseStarted?.countDown()
            check(releaseGate?.await(2, TimeUnit.SECONDS) != false)
        }
        fun final(text: String) = events.onText(text, true)
    }

    private val clients = mutableListOf<Fake>()
    private fun source(ready: Boolean? = true) = OneShotStt { Fake(ready).also { clients += it } }

    @Test fun `T9 종료 뒤 최종 텍스트도 같은 요청이 받는다`() = runTest {
        val stt = source()
        val first = async { stt.listenOnce() }; runCurrent()
        clients[0].events.onEnded(); runCurrent()
        assertFalse(first.isCompleted)
        advanceTimeBy(5)
        clients[0].final("다음")
        assertEquals(ListenResult.Text("다음"), first.await())
        assertEquals(1, clients[0].releases)
    }

    @Test fun `T9 중간 결과는 무시하고 빈 최종 결과는 무음이다`() = runTest {
        val stt = source()
        val first = async { stt.listenOnce() }; runCurrent()
        clients[0].events.onText("다", false); runCurrent()
        assertFalse(first.isCompleted)
        clients[0].final("")
        assertEquals(ListenResult.Silence, first.await())
    }

    @Test fun `POL-E-4 시간 초과의 늦은 결과는 다음 요청을 완료하지 않는다`() = runTest {
        val stt = source()
        val first = async { stt.listenOnce() }; runCurrent()
        advanceTimeBy(15_000); runCurrent()
        assertEquals(ListenResult.Error, first.await())
        assertEquals(1, clients[0].releases)
        val second = async { stt.listenOnce() }; runCurrent()
        clients[0].final("다음"); runCurrent()
        assertFalse(second.isCompleted)
        clients[1].final("다시")
        assertEquals(ListenResult.Text("다시"), second.await())
    }

    @Test fun `POL-D-3 취소한 요청의 콜백은 다음 요청을 완료하지 않는다`() = runTest {
        val stt = source()
        val first = async { stt.listenOnce() }; runCurrent()
        first.cancelAndJoin()
        assertEquals(1, clients[0].releases)
        val second = async { stt.listenOnce() }; runCurrent()
        clients[0].events.onEnded(); clients[0].final("다음"); runCurrent()
        assertFalse(second.isCompleted)
        clients[1].final("어디야")
        assertEquals(ListenResult.Text("어디야"), second.await())
    }

    @Test fun `T9 완료한 요청의 중복 결과도 다음 요청을 완료하지 않는다`() = runTest {
        val stt = source()
        val first = async { stt.listenOnce() }; runCurrent()
        clients[0].final("다음"); first.await()
        val second = async { stt.listenOnce() }; runCurrent()
        clients[0].final("다음"); clients[0].events.onError(); runCurrent()
        assertFalse(second.isCompleted)
        clients[1].final("다시")
        assertEquals(ListenResult.Text("다시"), second.await())
    }

    @Test fun `POL-E-4 SDK 오류는 정리 후 Error다`() = runTest {
        val stt = source()
        val first = async { stt.listenOnce() }; runCurrent()
        clients[0].events.onError()
        assertEquals(ListenResult.Error, first.await())
        assertEquals(1, clients[0].releases)
    }

    @Test fun `POL-E-4 초기화 실패면 요청하지 않고 정리한다`() = runTest {
        val stt = source(false)
        assertEquals(ListenResult.Error, stt.listenOnce())
        assertEquals(0, clients[0].requests)
        assertEquals(1, clients[0].releases)
    }

    @Test fun `POL-E-4 초기화 제한 뒤 늦은 성공도 요청하지 않는다`() = runTest {
        val stt = source(null)
        val first = async { stt.listenOnce() }; runCurrent()
        advanceTimeBy(5_000); runCurrent()
        assertEquals(ListenResult.Error, first.await())
        clients[0].onReady(true); runCurrent()
        assertEquals(0, clients[0].requests)
        assertEquals(1, clients[0].releases)
    }

    @Test fun `POL-D-3 초기화 중 취소해도 정리한다`() = runTest {
        val stt = source(null)
        val first = async { stt.listenOnce() }; runCurrent()
        first.cancelAndJoin()
        clients[0].onReady(true); runCurrent()
        assertEquals(0, clients[0].requests)
        assertEquals(1, clients[0].releases)
    }

    @Test fun `T9 stop은 무음으로 끝내고 release 뒤에는 새 SDK를 만들지 않는다`() = runTest {
        val stt = source()
        val first = async { stt.listenOnce() }; runCurrent()
        stt.stop()
        assertEquals(ListenResult.Silence, first.await())
        stt.release(); stt.release()
        assertEquals(ListenResult.Error, stt.listenOnce())
        assertEquals(1, clients.size)
        assertEquals(1, clients[0].releases)
    }

    @Test fun `T9 동시 요청도 이전 SDK 정리 후 시작한다`() = runTest {
        val stt = source()
        val first = async { stt.listenOnce() }
        val second = async { stt.listenOnce() }; runCurrent()
        assertEquals(1, clients.size)
        clients[0].final("다음"); runCurrent()
        assertEquals(1, clients[0].releases)
        assertEquals(2, clients.size)
        clients[1].final("다시")
        assertEquals(ListenResult.Text("다음"), first.await())
        assertEquals(ListenResult.Text("다시"), second.await())
    }

    @Test fun `POL-D-3 release가 진행 중인 요청도 정리한다`() = runTest {
        val stt = source()
        val first = async { stt.listenOnce() }; runCurrent()
        stt.release()
        assertEquals(ListenResult.Silence, first.await())
        assertEquals(1, clients[0].releases)
        assertEquals(ListenResult.Error, stt.listenOnce())
        assertEquals(1, clients.size)
    }

    @Test fun `POL-E-4 요청 예외도 SDK를 정리하고 Error다`() = runTest {
        val client = Fake(true).apply { requestFailure = IllegalStateException("request failed") }
        val stt = OneShotStt { client }
        assertEquals(ListenResult.Error, stt.listenOnce())
        assertEquals(1, client.releases)
    }

    @Test fun `POL-D-3 다른 스레드가 정리 중 취소해도 다음 SDK는 정리를 기다린다`() = runTest {
        val stt = source()
        val first = async { stt.listenOnce() }; runCurrent()
        val started = CountDownLatch(1)
        val gate = CountDownLatch(1)
        clients[0].releaseStarted = started
        clients[0].releaseGate = gate
        val callback = thread { clients[0].final("다음") }
        try {
            assertTrue(started.await(1, TimeUnit.SECONDS))
            first.cancel()
            val second = async { stt.listenOnce() }; runCurrent()
            assertEquals(1, clients.size)
            gate.countDown(); callback.join(1_000); runCurrent()
            assertEquals(2, clients.size)
            clients[1].final("다시")
            assertEquals(ListenResult.Text("다시"), second.await())
        } finally {
            gate.countDown(); callback.join(1_000)
        }
    }
}
