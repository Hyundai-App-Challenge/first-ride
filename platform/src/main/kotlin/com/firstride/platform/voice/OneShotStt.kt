package com.firstride.platform.voice

import com.firstride.engine.voice.ListenResult
import com.firstride.engine.voice.SttPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** 요청 ID를 주지 않는 SDK의 콜백을 요청별 인식기와 함께 소유한다. */
internal interface SttRecognizer {
    interface Events {
        fun onText(text: String, completed: Boolean)
        fun onEnded()
        fun onError()
    }

    fun initialize(onReady: (Boolean) -> Unit)
    fun request(events: Events)
    fun release()
}

internal class OneShotStt(private val create: () -> SttRecognizer) : SttPort {
    private val sequential = Mutex()
    private val lock = Any()
    private var current: Request? = null
    private var released = false

    override suspend fun listenOnce(): ListenResult = sequential.withLock {
        val request = synchronized(lock) {
            if (released) null else runCatching { Request(create()) }.getOrNull()?.also { current = it }
        } ?: return@withLock ListenResult.Error
        try {
            request.listen()
        } finally {
            request.finish(ListenResult.Error)
            withContext(NonCancellable) { request.awaitFinished() }
            synchronized(lock) { if (current === request) current = null }
        }
    }

    override fun stop() { synchronized(lock) { current }?.finish(ListenResult.Silence) }

    fun release() {
        val request = synchronized(lock) { released = true; current }
        request?.finish(ListenResult.Silence)
    }

    private class Request(private val recognizer: SttRecognizer) {
        private val lock = Any()
        private val ready = CompletableDeferred<Boolean>()
        private val result = CompletableDeferred<ListenResult>()
        private var closed = false
        private val events = object : SttRecognizer.Events {
            override fun onText(text: String, completed: Boolean) {
                if (completed) finish(if (text.isBlank()) ListenResult.Silence else ListenResult.Text(text))
            }
            // 종료 뒤 최종 텍스트가 온다. 여기서 완료하면 결과가 다음 요청으로 넘어간다(실측 T9).
            override fun onEnded() = Unit
            override fun onError() = finish(ListenResult.Error)
        }

        suspend fun listen(): ListenResult {
            try {
                synchronized(lock) {
                    if (!closed) recognizer.initialize { ok -> if (ok) ready.complete(true) else finish(ListenResult.Error) }
                }
                val initialized = withTimeoutOrNull(INIT_TIMEOUT_MILLIS) { ready.await() } ?: false
                if (!initialized) finish(ListenResult.Error)
                synchronized(lock) { if (!closed) recognizer.request(events) }
                return withTimeoutOrNull(LISTEN_TIMEOUT_MILLIS) { result.await() }
                    ?: run { finish(ListenResult.Error); result.await() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                finish(ListenResult.Error)
                return result.await()
            }
        }

        fun finish(value: ListenResult) {
            synchronized(lock) { if (closed) return; closed = true }
            // 결과를 반환하기 전에 이전 SDK를 정리해 다음 요청의 SDK와 겹치지 않게 한다.
            runCatching { recognizer.release() }
            ready.complete(false)
            result.complete(value)
        }

        suspend fun awaitFinished() { result.await() }
    }

    private companion object {
        const val INIT_TIMEOUT_MILLIS = 5_000L
        /** 기존 T9 측정의 제한 시간. 최종 결과가 없을 때 완료로 간주하지 않는다. */
        const val LISTEN_TIMEOUT_MILLIS = 15_000L
    }
}
