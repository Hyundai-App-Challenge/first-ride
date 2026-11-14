package com.firstride.engine.voice

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

/** 정해 둔 순서대로 결과를 돌려주는 가짜 STT. 각 항목은 (걸리는 시간, 결과). 다 쓰면 취소될 때까지 기다린다. */
class FakeStt(vararg script: Pair<Long, ListenResult>) : SttPort {
    private val queue = ArrayDeque(script.toList())
    var listenCalls = 0
        private set
    var stopCalls = 0
        private set

    override suspend fun listenOnce(): ListenResult {
        listenCalls++
        val (millis, result) = queue.removeFirstOrNull() ?: awaitCancellation()
        delay(millis)
        return result
    }

    override fun stop() { stopCalls++ }
}

fun said(text: String, after: Long = 100) = after to ListenResult.Text(text)
fun silence(after: Long = 100) = after to ListenResult.Silence
