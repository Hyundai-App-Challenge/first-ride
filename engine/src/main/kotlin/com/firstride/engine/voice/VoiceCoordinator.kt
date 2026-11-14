package com.firstride.engine.voice

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield

/** 조율기가 밖으로 낼 수 있는 동작의 전부다. 그만하기·기록 지우기는 종류 자체가 없다(POL-V-16). */
sealed interface VoiceAction {
    /** 완료된 단계에서 "다음". 바로 다음 단계로. */
    data object Next : VoiceAction
    /** "다시". 현재 안내를 다시 읽는다. */
    data object Repeat : VoiceAction
    /** "어디야". 위치 문구를 읽는다. */
    data object Where : VoiceAction
    /** 미완료 단계에서 되물은 뒤 6초 안에 "다음"을 한 번 더. 건너뛴다. */
    data object ConfirmSkip : VoiceAction
}

const val CONFIRM_SKIP_TEXT = "아직 확인되지 않았어요. 건너뛸까요?"

/**
 * 음성 안내의 순서를 잡는다(기술 설계 7-2절, 정책서 5절). 말하기(TTS)와 듣기(STT)와 건너뛰기 되묻기.
 *
 * - 새 안내가 오면 재생 중인 것을 끊는다. 쌓아 두지 않는다(POL-V-2). 듣는 중이었다면 듣기도 끊는다.
 * - 같은 문구를 3초 안에 다시 읽지 않는다(POL-V-3). 사용자가 "다시"를 요청한 것은 예외다([announce]의 force).
 * - 연달아 요청이 오면 마지막 것만 읽는다(POL-V-4).
 * - 재생이 끝난 뒤에만 듣는다(POL-V-11). 어휘가 아니거나 무음이면 아무 동작 없이 다시 듣는다(POL-V-10).
 * - 음성이 실패해도 진행을 막지 않는다. 실패는 [available], [sttAvailable]에만 남는다(POL-E-4, AC-05-4).
 * - [voiceEnabled]가 false면 읽지도 듣지도 않는다(POL-V-6).
 *
 * [run]을 도는 코루틴이 취소되면(실습 범위 취소, 화면 이탈) 재생과 듣기를 멈춘다(AC-05-3).
 */
class VoiceCoordinator(
    private val tts: TtsPort,
    /** 밀리초 단위 시계. 테스트에서는 가상 시간을 넘긴다. */
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val repeatGuardMillis: Long = 3_000,
    /** null이면 듣지 않는다(STT 어댑터가 없을 때). */
    private val stt: SttPort? = null,
    /** 설정의 음성 켜기/끄기. 읽고 듣기 직전마다 확인한다. */
    private val voiceEnabled: () -> Boolean = { true },
    /** 재생이 끝나면 자동으로 들을지. 마이크 버튼 방식으로 바꾸면(PRD D5) 이 한 곳만 바꾼다. */
    private val listenEnabled: Boolean = true,
    private val confirmMillis: Long = 6_000,
    /** 지금 단계가 완료인지. 완료면 "다음"은 바로 넘어가고, 아니면 되묻는다. */
    private val isStepDone: () -> Boolean = { false },
    private val onAction: (VoiceAction) -> Unit = {},
) {
    private val requests = Channel<String>(Channel.CONFLATED)
    private val _available = MutableStateFlow(true)
    private val _sttAvailable = MutableStateFlow(true)
    private val _listening = MutableStateFlow(false)

    /** 마지막 재생이 오류로 끝났으면 false. 화면의 "음성 꺼짐" 표시에 쓴다. 성공하면 다시 true. */
    val available: StateFlow<Boolean> = _available

    /** 듣기가 오류로 끝났으면 false(마이크 거부 등). 듣기를 멈춘다. */
    val sttAvailable: StateFlow<Boolean> = _sttAvailable

    /** 듣는 동안 true. 화면의 "듣는 중" 표시에 쓴다(POL-V-13). */
    val listening: StateFlow<Boolean> = _listening

    private var lastText: String? = null
    private var lastAt = 0L

    /** 안내를 요청한다. 어느 스레드에서 불러도 된다. [force]는 사용자가 다시 듣기를 눌렀을 때. */
    @Synchronized
    fun announce(text: String, force: Boolean = false) {
        if (text.isBlank() || !voiceEnabled()) return
        val now = clock()
        if (!force && text == lastText && now - lastAt < repeatGuardMillis) return
        lastText = text
        lastAt = now
        // 먼저 끊고 나서 보낸다. 반대로 하면 이미 재생을 시작한 새 문구를 곧바로 끊을 수 있다.
        tts.stop()
        requests.trySend(text)
    }

    /** 요청을 읽고 듣는다. 새 요청이 오면 하던 것을 끊고 새로 시작한다. 끝나지 않는다. 취소해서 멈춘다. */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun run() {
        try {
            requests.receiveAsFlow().collectLatest { text ->
                yield()         // 연달아 온 요청은 마지막 것만 읽는다(POL-V-4). 앞의 것은 시작 전에 취소된다
                when (tts.speak(text)) {
                    SpeakResult.DONE -> _available.value = true
                    SpeakResult.ERROR -> _available.value = false
                    SpeakResult.INTERRUPTED -> return@collectLatest
                }
                if (listenEnabled) listen()
            }
        } finally {
            tts.stop()
            stt?.stop()
            _listening.value = false
        }
    }

    /** LISTENING과 CONFIRM_SKIP. 새 안내나 취소로 끊길 때까지 듣고 또 듣는다. */
    private suspend fun listen() {
        val stt = stt ?: return
        var confirmUntil: Long? = null      // null이 아니면 되묻기 중이고, 이 시각까지 "다음"을 기다린다
        try {
            while (voiceEnabled()) {
                _listening.value = true
                val until = confirmUntil
                val result = if (until == null) stt.listenOnce()
                else withTimeoutOrNull((until - clock()).coerceAtLeast(0)) { stt.listenOnce() }
                if (result == null) {           // 6초 경과(POL-V-12): 되묻기를 취소하고 원래대로 듣는다
                    stt.stop()
                    confirmUntil = null
                    continue
                }
                when (result) {
                    ListenResult.Error -> { _sttAvailable.value = false; return }
                    ListenResult.Silence -> _sttAvailable.value = true
                    is ListenResult.Text -> {
                        _sttAvailable.value = true
                        when (matchVocabulary(result.text)) {
                            null -> confirmUntil = null
                            Vocab.REPEAT -> { confirmUntil = null; onAction(VoiceAction.Repeat) }
                            Vocab.WHERE -> { confirmUntil = null; onAction(VoiceAction.Where) }
                            Vocab.NEXT -> when {
                                isStepDone() -> { confirmUntil = null; onAction(VoiceAction.Next) }
                                confirmUntil != null -> { confirmUntil = null; onAction(VoiceAction.ConfirmSkip) }
                                else -> {
                                    // 되묻는 문구는 안내가 아니라 조율기가 직접 읽는다. 끝난 뒤부터 6초를 센다.
                                    _listening.value = false
                                    if (tts.speak(CONFIRM_SKIP_TEXT) == SpeakResult.INTERRUPTED) return
                                    confirmUntil = clock() + confirmMillis
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            stt.stop()
            _listening.value = false
        }
    }
}
