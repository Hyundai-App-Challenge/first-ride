package com.firstride.platform.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.util.Log
import com.firstride.engine.voice.ListenResult
import com.firstride.engine.voice.SttPort
import java.util.Locale

/**
 * 한국어 ON_DEVICE STT. SDK가 요청 ID를 주지 않아 한 번 듣기마다 별도 인스턴스를 쓴다.
 * 최종 결과·실패·취소 뒤 리스너와 SDK를 정리한 후 다음 요청을 시작한다(실측 T9).
 */
class PleosStt(context: Context) : SttPort {
    private val app = context.applicationContext
    private val requests = OneShotStt { SdkRecognizer(app) }

    override suspend fun listenOnce(): ListenResult = requests.listenOnce()
    override fun stop() = requests.stop()
    fun release() = requests.release()
}

private class SdkRecognizer(context: Context) : SttRecognizer {
    private val stt = SpeechToText(context, Mode.ON_DEVICE)
    private var listener: ResultListener? = null
    @Volatile private var released = false

    override fun initialize(onReady: (Boolean) -> Unit) {
        stt.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                if (released) return
                val ok = runCatching { stt.setLocale(Locale.KOREA) }
                    .onFailure { Log.w(TAG, "locale setup failed", it) }.isSuccess
                onReady(ok)
            }
            override fun onFailure(p0: ErrorType) = onReady(false)
        })
    }

    override fun request(events: SttRecognizer.Events) {
        val resultListener = object : ResultListener {
            override fun onUpdated(p0: String, p1: Boolean) = events.onText(p0, p1)
            override fun onEndedRecognition() = events.onEnded()
            override fun onError(p0: ErrorType) {
                Log.w(TAG, "listen error $p0")
                events.onError()
            }
            override fun onUpdatedEpdData(p0: Long, p1: Long) = Unit
            override fun onStartedRecognition() = Unit
            override fun onReady() = Unit
            override fun onUpdatedRms(p0: Double) = Unit
        }
        listener = resultListener
        stt.addListener(resultListener)
        stt.request()
    }

    override fun release() {
        released = true
        listener?.let { resultListener ->
            runCatching { stt.removeListener(resultListener) }
                .onFailure { Log.w(TAG, "remove listener failed", it) }
        }
        runCatching { stt.stop() }.onFailure { Log.w(TAG, "stop failed", it) }
        runCatching { stt.release() }.onFailure { Log.w(TAG, "release failed", it) }
    }

    private companion object { const val TAG = "PleosStt" }
}
