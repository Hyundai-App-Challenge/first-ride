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
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Pleos SpeechToText를 [SttPort]로. 기기 안의 엔진(`Mode.ON_DEVICE`)과 한국어만 쓴다(기술 설계 7-4절, POL-P-5).
 *
 * - [listenOnce]는 한 번 듣고 최종 결과만 돌려준다: `onUpdated(text, completed = true)` → Text.
 *   중간 결과는 쓰지 않는다. 텍스트 없이 인식이 끝나면 Silence, 오류·초기화 실패·시간 초과는 Error(POL-E-4).
 * - 듣기를 기다리던 코루틴이 취소되면(실습 범위 취소) 듣기를 멈춘다(POL-P-1, POL-D-3).
 */
class PleosStt(context: Context) : SttPort {
    private val ready = CompletableDeferred<Boolean>()
    private val lock = Any()
    private var pending: CancellableContinuation<ListenResult>? = null

    private val results = object : ResultListener {
        override fun onUpdated(p0: String, p1: Boolean) {
            if (p1 && p0.isNotBlank()) finish(ListenResult.Text(p0))
        }
        override fun onEndedRecognition() = finish(ListenResult.Silence)
        override fun onError(p0: ErrorType) {
            Log.w(TAG, "listen error $p0")
            finish(ListenResult.Error)
        }
        override fun onUpdatedEpdData(p0: Long, p1: Long) = Unit
        override fun onStartedRecognition() = Unit
        override fun onReady() = Unit
        override fun onUpdatedRms(p0: Double) = Unit
    }

    // 로케일과 리스너는 초기화가 끝난 뒤에만 건다(TTS와 같다, 실측 T8).
    private val stt = SpeechToText(context.applicationContext, Mode.ON_DEVICE).apply {
        try {
            initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    val ok = runCatching {
                        setLocale(Locale.KOREA)
                        addListener(results)
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

    override suspend fun listenOnce(): ListenResult {
        val initialized = withTimeoutOrNull(INIT_TIMEOUT_MILLIS) { ready.await() } ?: false
        if (!initialized) return ListenResult.Error
        return withTimeoutOrNull(LISTEN_TIMEOUT_MILLIS) { requestAndWait() } ?: ListenResult.Error
    }

    private suspend fun requestAndWait(): ListenResult = suspendCancellableCoroutine { cont ->
        val previous = synchronized(lock) { pending.also { pending = cont } }
        previous?.resumeIfActive(ListenResult.Error)
        cont.invokeOnCancellation {
            val mine = synchronized(lock) { (pending === cont).also { if (it) pending = null } }
            if (mine) runCatching { stt.stop() }
        }
        try {
            stt.request()
        } catch (e: Exception) {
            Log.w(TAG, "request threw", e)
            finish(ListenResult.Error)
        }
    }

    override fun stop() {
        val cont = synchronized(lock) { pending.also { pending = null } }
        runCatching { stt.stop() }
        cont?.resumeIfActive(ListenResult.Silence)
    }

    /** 앱이 끝날 때만 부른다. */
    fun release() {
        stop()
        runCatching { stt.release() }
    }

    private fun finish(result: ListenResult) {
        val cont = synchronized(lock) { pending.also { pending = null } }
        cont?.resumeIfActive(result)
    }

    private companion object {
        const val TAG = "PleosStt"
        const val INIT_TIMEOUT_MILLIS = 5_000L
        /** 한 번 듣기의 시간 제한 [가정]. 엔진이 끝을 알리지 않을 때를 막는다. 실측 T9에서 고친다. */
        const val LISTEN_TIMEOUT_MILLIS = 15_000L
    }
}

private fun <T> CancellableContinuation<T>.resumeIfActive(value: T) {
    if (isActive) resume(value)
}
