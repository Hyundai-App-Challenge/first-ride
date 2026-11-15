package com.firstride.platform.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.util.Log
import com.firstride.engine.voice.SpeakResult
import com.firstride.engine.voice.TtsPort
import java.util.Locale

/** 한국어 ON_DEVICE TTS. SDK의 비동기 중지를 확인한 뒤 다음 안내를 시작한다. */
class PleosTts(context: Context) : TtsPort {
    private val app = context.applicationContext
    private val playback = TtsPlayback { SdkSpeaker(app) }
    override suspend fun speak(text: String): SpeakResult = playback.speak(text)
    override fun stop() = playback.stop()
    fun release() = playback.release()
}

private class SdkSpeaker(context: Context) : TtsSpeaker {
    private val tts = TextToSpeech(context, Mode.ON_DEVICE)
    @Volatile private var events: TtsSpeaker.Events? = null
    @Volatile private var released = false
    private var listenerAdded = false
    private val listener = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() { events?.onStart() }
        override fun onUpdatedRms(p0: Double) = Unit
        override fun onDone() { events?.onDone() }
        override fun onStop() { events?.onStop() }
        override fun onError(p0: ErrorType) {
            Log.w(TAG, "speak error $p0")
            events?.onError()
        }
    }

    override fun initialize(onReady: (Boolean) -> Unit) {
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                if (released) return
                val ok = runCatching {
                    tts.setLocale(Locale.KOREA)
                    tts.addEventListener(listener)
                    listenerAdded = true
                }.onFailure { Log.w(TAG, "setup failed", it) }.isSuccess
                onReady(ok)
            }
            override fun onFailure(p0: ErrorType) = onReady(false)
        })
    }

    override fun play(text: String, events: TtsSpeaker.Events) {
        this.events = events
        tts.speak(text, true)
    }

    override fun stop() = tts.stop()

    override fun release() {
        released = true
        events = null
        if (listenerAdded) runCatching { tts.removeEventListener(listener) }
            .onFailure { Log.w(TAG, "remove listener failed", it) }
        // 중지 확인 뒤에만 끊는다. 추가 비동기 stop이 다음 연결의 재생을 끊을 수 있어 보내지 않는다.
        runCatching { tts.release() }.onFailure { Log.w(TAG, "release failed", it) }
    }

    private companion object { const val TAG = "PleosTts" }
}
