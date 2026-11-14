package com.firstride.engine.voice

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 재생에 [durationMillis]가 걸리는 가짜 TTS. 가상 시간에서 돈다. 동시에 두 문장이 재생되면 [maxConcurrent]가 2 이상이 된다. */
class FakeTts(private val durationMillis: Long = 1_000) : TtsPort {
    val started = mutableListOf<String>()
    var stopCalls = 0
        private set
    var maxConcurrent = 0
        private set

    /** null이 아니면 재생하지 않고 이 결과를 곧바로 돌려준다. */
    var failWith: SpeakResult? = null

    private var active = 0
    private var current: CompletableDeferred<SpeakResult>? = null

    override suspend fun speak(text: String): SpeakResult {
        started += text
        failWith?.let { return it }
        val done = CompletableDeferred<SpeakResult>()
        current = done
        active++
        maxConcurrent = maxOf(maxConcurrent, active)
        try {
            return coroutineScope {
                val timer = launch { delay(durationMillis); done.complete(SpeakResult.DONE) }
                done.await().also { timer.cancel() }
            }
        } finally {
            active--
            if (current === done) current = null
        }
    }

    override fun stop() {
        stopCalls++
        current?.complete(SpeakResult.INTERRUPTED)
    }
}
