package com.firstride.engine.voice

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 음성 안내의 순서를 잡는다(기술 설계 7-2절, 정책서 5절). 지금은 말하기(TTS)만 있고, 듣기(STT)와 되묻기는 W-24에서 더한다.
 *
 * - 새 안내가 오면 재생 중인 것을 끊는다. 쌓아 두지 않는다(POL-V-2).
 * - 같은 문구를 3초 안에 다시 읽지 않는다(POL-V-3). 사용자가 "다시"를 요청한 것은 예외다([announce]의 force).
 * - 연달아 요청이 오면 마지막 것만 읽는다(POL-V-4).
 * - 음성이 실패해도 진행을 막지 않는다. 실패는 [available]에만 남는다(POL-E-4, AC-05-4).
 *
 * [run]을 도는 코루틴이 취소되면(실습 범위 취소, 화면 이탈) 재생을 멈춘다(AC-05-3).
 */
class VoiceCoordinator(
    private val tts: TtsPort,
    /** 밀리초 단위 시계. 테스트에서는 가상 시간을 넘긴다. */
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val repeatGuardMillis: Long = 3_000,
) {
    private val requests = Channel<String>(Channel.CONFLATED)
    private val _available = MutableStateFlow(true)

    /** 마지막 재생이 오류로 끝났으면 false. 화면의 "음성 꺼짐" 표시에 쓴다. 성공하면 다시 true. */
    val available: StateFlow<Boolean> = _available

    private var lastText: String? = null
    private var lastAt = 0L

    /** 안내를 요청한다. 어느 스레드에서 불러도 된다. [force]는 사용자가 다시 듣기를 눌렀을 때. */
    @Synchronized
    fun announce(text: String, force: Boolean = false) {
        if (text.isBlank()) return
        val now = clock()
        if (!force && text == lastText && now - lastAt < repeatGuardMillis) return
        lastText = text
        lastAt = now
        // 먼저 끊고 나서 보낸다. 반대로 하면 이미 재생을 시작한 새 문구를 곧바로 끊을 수 있다.
        tts.stop()
        requests.trySend(text)
    }

    /** 요청을 하나씩 읽는다. 끝나지 않는다. 취소해서 멈춘다. */
    suspend fun run() {
        try {
            for (text in requests) {
                when (tts.speak(text)) {
                    SpeakResult.DONE -> _available.value = true
                    SpeakResult.ERROR -> _available.value = false
                    SpeakResult.INTERRUPTED -> Unit
                }
            }
        } finally {
            tts.stop()
        }
    }
}
