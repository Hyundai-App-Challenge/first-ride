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
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Pleos TextToSpeech를 [TtsPort]로. 기기 안의 엔진(`Mode.ON_DEVICE`)과 한국어만 쓴다(기술 설계 7-4절, POL-P-5).
 *
 * - [speak]는 재생이 끝날 때까지 기다린다. 끝남 → DONE, 중지 → INTERRUPTED, 오류·초기화 실패 → ERROR.
 * - 재생 중에 새 [speak]가 오면 앞의 것은 INTERRUPTED로 끝낸다. 끊는 일([stop])은 호출하는 쪽(음성 조율)이 정한다.
 * - [speak]를 기다리던 코루틴이 취소되면(실습 범위 취소) 재생을 멈춘다(POL-P-1, POL-E-6).
 */
class PleosTts(context: Context) : TtsPort {
    private val ready = CompletableDeferred<Boolean>()
    private val lock = Any()
    private var pending: CancellableContinuation<SpeakResult>? = null
    /** 지금 기다리는 문장의 재생이 시작됐는가(onReady/onStart를 받았는가). */
    private var started = false

    private val events = object : EventListener {
        override fun onStart() = markStarted()
        override fun onReady() = markStarted()
        override fun onUpdatedRms(p0: Double) = Unit
        // stop() 뒤에 앞 문장의 onStop이 늦게 와서 새 문장을 끝내지 않도록, 시작한 문장의 끝남·중지만 받는다(실측 T8).
        override fun onDone() = finish(SpeakResult.DONE, onlyIfStarted = true)
        override fun onStop() = finish(SpeakResult.INTERRUPTED, onlyIfStarted = true)
        override fun onError(p0: ErrorType) {
            Log.w(TAG, "speak error $p0")
            finish(SpeakResult.ERROR)
        }
    }

    // 로케일과 리스너는 초기화가 끝난 뒤에만 걸 수 있다(그 전에 부르면 SDK 안에서 예외, 실측 T8).
    private val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE).apply {
        try {
            initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    val ok = runCatching {
                        setLocale(Locale.KOREA)
                        addEventListener(events)
                    }.onFailure { Log.w(TAG, "setup after initialize failed", it) }.isSuccess
                    ready.complete(ok)
                }
                override fun onFailure(p0: ErrorType) {
                    Log.w(TAG, "initialize failed $p0")
                    ready.complete(false)
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "initialize threw", e)
            ready.complete(false)
        }
    }

    override suspend fun speak(text: String): SpeakResult {
        if (!ready.await()) return SpeakResult.ERROR
        return suspendCancellableCoroutine { cont ->
            synchronized(lock) {
                pending?.resumeIfActive(SpeakResult.INTERRUPTED)
                pending = cont
                started = false
            }
            cont.invokeOnCancellation {
                val mine = synchronized(lock) { (pending === cont).also { if (it) pending = null } }
                if (mine) runCatching { tts.stop() }
            }
            try {
                tts.speak(text, true)
            } catch (e: Exception) {
                Log.w(TAG, "speak threw", e)
                finish(SpeakResult.ERROR)
            }
        }
    }

    override fun stop() {
        runCatching { tts.stop() }
        finish(SpeakResult.INTERRUPTED)
    }

    /** 앱이 끝날 때만 부른다. */
    fun release() {
        stop()
        runCatching { tts.release() }
    }

    private fun markStarted() = synchronized(lock) { if (pending != null) started = true }

    private fun finish(result: SpeakResult, onlyIfStarted: Boolean = false) {
        val cont = synchronized(lock) {
            if (onlyIfStarted && !started) return
            pending.also { pending = null; started = false }
        }
        cont?.resumeIfActive(result)
    }

    private companion object { const val TAG = "PleosTts" }
}

private fun <T> CancellableContinuation<T>.resumeIfActive(value: T) {
    if (isActive) resume(value)
}
