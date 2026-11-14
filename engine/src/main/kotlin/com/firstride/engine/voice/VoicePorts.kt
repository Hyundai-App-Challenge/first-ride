package com.firstride.engine.voice

enum class SpeakResult { DONE, INTERRUPTED, ERROR }

sealed interface ListenResult {
    data class Text(val text: String) : ListenResult
    data object Silence : ListenResult
    data object Error : ListenResult
}

interface TtsPort {
    /** 재생이 끝날 때까지 기다린다. */
    suspend fun speak(text: String): SpeakResult
    fun stop()
}

interface SttPort {
    /** 최종 결과만 돌려준다. 중간 결과는 쓰지 않는다. */
    suspend fun listenOnce(): ListenResult
    fun stop()
}
