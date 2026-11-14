package com.firstride.platform.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.firstride.engine.voice.SpeakResult
import com.firstride.engine.voice.TtsPort
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Pleos TextToSpeech를 [TtsPort]로. 기기 안의 엔진(`Mode.ON_DEVICE`)과 한국어만 쓴다(기술 설계 7-4절, POL-P-5).
 *
 * - [speak]는 재생이 끝날 때까지 기다린다. 끝남 → DONE, 중지 → INTERRUPTED, 오류·초기화 실패 → ERROR.
 * - 재생 중에 새 [speak]가 오면 앞의 것을 멈추고 INTERRUPTED로 끝낸다(POL-V-2).
 * - [speak]를 기다리던 코루틴이 취소되면(실습 범위 취소) 재생을 멈춘다(POL-P-1, POL-E-6).
 * - 어떤 경우에도 무한히 기다리지 않는다. 초기화는 [INIT_TIMEOUT_MILLIS], 문장마다 길이에 비례한 시간 제한을 넘으면 ERROR(POL-E-4).
 * - 끊은 문장의 이벤트(onReady·onStart·onDone·onStop)는 늦게 온다(실측 T8). 끊은 뒤 그 문장의 onStop이 올 때까지(최대
 *   [DRAIN_MILLIS]) 오는 이벤트는 새 문장의 것으로 보지 않는다.
 */
class PleosTts(context: Context) : TtsPort {
    private val ready = CompletableDeferred<Boolean>()
    private val lock = Any()
    private var pending: CancellableContinuation<SpeakResult>? = null
    /** 지금 기다리는 문장의 재생이 시작됐는가(onReady/onStart를 받았는가). */
    private var started = false
    /** 끊은 문장의 onStop을 기다리는 중이면 끊은 시각(elapsedRealtime), 아니면 0. */
    private var drainingSince = 0L

    private val events = object : EventListener {
        override fun onStart() = markStarted()
        override fun onReady() = markStarted()
        override fun onUpdatedRms(p0: Double) = Unit
        // stop() 뒤에 앞 문장의 onStop이 늦게 와서 새 문장을 끝내지 않도록, 시작한 문장의 끝남·중지만 받는다(실측 T8).
        override fun onDone() = finish(SpeakResult.DONE, onlyIfStarted = true)
        override fun onStop() {
            if (synchronized(lock) { draining().also { drainingSince = 0L } }) return
            finish(SpeakResult.INTERRUPTED, onlyIfStarted = true)
        }
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
        val initialized = withTimeoutOrNull(INIT_TIMEOUT_MILLIS) { ready.await() } ?: false
        if (!initialized) return SpeakResult.ERROR
        return withTimeoutOrNull(limitFor(text)) { speakAndWait(text) } ?: SpeakResult.ERROR
    }

    private suspend fun speakAndWait(text: String): SpeakResult = suspendCancellableCoroutine { cont ->
        val previous = synchronized(lock) { pending.also { pending = cont; started = false } }
        if (previous != null) {
            cut()
            previous.resumeIfActive(SpeakResult.INTERRUPTED)
        }
        // 취소(실습 범위 취소, 시간 제한)되면 이 문장을 멈춘다.
        cont.invokeOnCancellation {
            val mine = synchronized(lock) { (pending === cont).also { if (it) { pending = null; started = false } } }
            if (mine) cut()
        }
        try {
            tts.speak(text, true)
        } catch (e: Exception) {
            Log.w(TAG, "speak threw", e)
            finish(SpeakResult.ERROR)
        }
    }

    override fun stop() {
        val cont = synchronized(lock) { pending.also { pending = null; started = false } }
        if (cont != null) cut()
        cont?.resumeIfActive(SpeakResult.INTERRUPTED)
    }

    /** 보낸 문장을 SDK에서 멈추고, 그 문장의 늦은 이벤트를 걸러 내기 시작한다. */
    private fun cut() {
        synchronized(lock) { drainingSince = SystemClock.elapsedRealtime() }
        runCatching { tts.stop() }
    }

    /** lock 안에서 부른다. */
    private fun draining(): Boolean =
        drainingSince != 0L && SystemClock.elapsedRealtime() - drainingSince < DRAIN_MILLIS

    /** 앱이 끝날 때만 부른다. */
    fun release() {
        stop()
        runCatching { tts.release() }
    }

    private fun markStarted() = synchronized(lock) { if (pending != null && !draining()) started = true }

    private fun finish(result: SpeakResult, onlyIfStarted: Boolean = false) {
        val cont = synchronized(lock) {
            if (onlyIfStarted && (!started || draining())) return
            pending.also { pending = null; started = false }
        }
        cont?.resumeIfActive(result)
    }

    private companion object {
        const val TAG = "PleosTts"
        const val INIT_TIMEOUT_MILLIS = 5_000L
        /** 끊은 문장의 onStop을 기다리는 최대 시간. 실측에서는 5ms 안에 왔다. */
        const val DRAIN_MILLIS = 1_000L

        /** 문장 하나의 시간 제한 [가정]: 3초 + 글자당 150ms. 실측에서 18자 문장이 약 3.7초. */
        fun limitFor(text: String): Long = 3_000L + 150L * text.length
    }
}

private fun <T> CancellableContinuation<T>.resumeIfActive(value: T) {
    if (isActive) resume(value)
}
