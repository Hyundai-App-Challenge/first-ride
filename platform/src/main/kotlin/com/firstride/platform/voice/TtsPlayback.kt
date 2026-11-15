package com.firstride.platform.voice

import com.firstride.engine.voice.SpeakResult
import com.firstride.engine.voice.TtsPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal interface TtsSpeaker {
    interface Events {
        fun onStart()
        fun onDone()
        fun onStop()
        fun onError()
    }
    fun initialize(onReady: (Boolean) -> Unit)
    fun play(text: String, events: Events)
    fun stop()
    fun release()
}

/** SDK 연결은 유지하고 문장별 결과와 실제 중지 확인을 분리한다. */
internal class TtsPlayback(private val create: () -> TtsSpeaker) : TtsPort {
    private val sequential = Mutex()
    private val lock = Any()
    private var current: Request? = null
    private var connection: Connection? = null
    private var pendingStop: CompletableDeferred<Unit>? = null
    private var released = false

    override suspend fun speak(text: String): SpeakResult = coroutineScope {
        val request = Request()
        val previous = synchronized(lock) {
            if (released) return@coroutineScope SpeakResult.ERROR
            current.also { current = request }
        }
        previous?.finish(SpeakResult.INTERRUPTED)
        // 대기 중 교체된 문장도 SDK를 만들지 않고 바로 끝낸다.
        val playback = launch {
            sequential.withLock {
                try {
                    request.play(text)
                } finally {
                    request.finish(SpeakResult.ERROR)
                    withContext(NonCancellable) { request.awaitStopped() }
                }
            }
        }
        try {
            request.result.await()
        } finally {
            request.finish(SpeakResult.ERROR)
            withContext(NonCancellable) { playback.cancelAndJoin() }
            synchronized(lock) { if (current === request) current = null }
            disposeIfReleased()
        }
    }

    override fun stop() { synchronized(lock) { current }?.finish(SpeakResult.INTERRUPTED) }

    fun release() {
        val request = synchronized(lock) { released = true; current }
        request?.finish(SpeakResult.INTERRUPTED)
        disposeIfReleased()
    }

    private fun getConnection(): Connection? = synchronized(lock) {
        if (released) null else connection ?: runCatching {
            Connection(create(), ::disposeIfReleased)
        }.getOrNull()?.also { connection = it }
    }

    private suspend fun awaitPreviousStop(): Boolean {
        val ack = synchronized(lock) { pendingStop } ?: return true
        if (withTimeoutOrNull(STOP_TIMEOUT_MILLIS) { ack.await(); true } != true) return false
        synchronized(lock) { if (pendingStop === ack) pendingStop = null }
        return true
    }

    private fun trackStop(ack: CompletableDeferred<Unit>) {
        synchronized(lock) { pendingStop = ack }
        ack.invokeOnCompletion { disposeIfReleased() }
    }

    private fun discardFailedConnection(failed: Connection) {
        val owned = synchronized(lock) { (connection === failed).also { if (it) connection = null } }
        if (owned) runCatching { failed.speaker.release() }
    }

    private fun disposeIfReleased() {
        val owned = synchronized(lock) {
            val c = connection ?: return
            if (!released || !c.ready.isCompleted || pendingStop?.isCompleted == false) return
            connection = null
            c.speaker
        }
        runCatching { owned.release() }
    }

    private inner class Request {
        private val lock = Any()
        val result = CompletableDeferred<SpeakResult>()
        private var speaker: TtsSpeaker? = null
        private var closed = false
        private var played = false
        private var started = false
        private var terminal = false
        private var stopWanted = false
        private var stopSent = false
        private var stopped: CompletableDeferred<Unit>? = null
        private val events = object : TtsSpeaker.Events {
            override fun onStart() = synchronized(lock) { started = true; stopIfStarted() }
            override fun onDone() = ended(SpeakResult.DONE)
            override fun onStop() = ended(SpeakResult.INTERRUPTED)
            override fun onError() = ended(SpeakResult.ERROR)
        }

        suspend fun play(text: String) {
            try {
                if (!awaitPreviousStop()) { finish(SpeakResult.ERROR); return }
                if (synchronized(lock) { closed }) return
                val c = getConnection() ?: run { finish(SpeakResult.ERROR); return }
                val initialized = withTimeoutOrNull(INIT_TIMEOUT_MILLIS) { c.ready.await() }
                if (initialized != true) {
                    // 미응답 연결은 다시 bind하지 않는다. 늦은 성공은 다음 요청이 같은 연결로 쓴다.
                    if (initialized == false) discardFailedConnection(c)
                    finish(SpeakResult.ERROR)
                    return
                }
                synchronized(lock) {
                    if (!closed) {
                        speaker = c.speaker
                        played = true
                        c.speaker.play(text, events)
                    }
                }
                if (withTimeoutOrNull(limitFor(text)) { result.await() } == null) finish(SpeakResult.ERROR)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                finish(SpeakResult.ERROR)
            }
        }

        fun finish(value: SpeakResult, interrupt: Boolean = true) {
            synchronized(lock) {
                if (closed) return
                closed = true
                if (interrupt && played && !terminal) {
                    stopWanted = true
                    val ack = CompletableDeferred<Unit>()
                    stopped = ack
                    trackStop(ack)
                    stopIfStarted()
                }
            }
            result.complete(value)
        }

        private fun stopIfStarted() {
            // SDK의 play도 비동기다. 시작 전 stop을 보내면 뒤늦은 play가 중지를 추월할 수 있다.
            if (stopWanted && started && !terminal && !stopSent) {
                stopSent = true
                runCatching { speaker?.stop() }
            }
        }

        private fun ended(value: SpeakResult) {
            val ack = synchronized(lock) {
                terminal = true
                // stop을 보냈다면 DONE보다 실제 STOP 응답을 기다려야 다음 문장을 끊지 않는다.
                stopped.takeIf { !stopSent || value == SpeakResult.INTERRUPTED }
            }
            ack?.complete(Unit)
            finish(value, interrupt = false)
        }

        suspend fun awaitStopped() {
            result.await()
            val ack = synchronized(lock) { stopped } ?: return
            // 확인이 없으면 다음 재생도 같은 ack를 기다린다. 시간 경과로 안전하다고 추측하지 않는다.
            withTimeoutOrNull(STOP_TIMEOUT_MILLIS) { ack.await() }
        }
    }

    private class Connection(val speaker: TtsSpeaker, onReady: () -> Unit) {
        val ready = CompletableDeferred<Boolean>()
        init {
            try {
                speaker.initialize { ok -> ready.complete(ok); onReady() }
            } catch (_: Exception) {
                ready.complete(false)
            }
        }
    }

    private companion object {
        const val INIT_TIMEOUT_MILLIS = 5_000L
        const val STOP_TIMEOUT_MILLIS = 5_000L
        fun limitFor(text: String): Long = 3_000L + 150L * text.length
    }
}
