package com.firstride.measure

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.listener.HvacAcStateListener
import ai.pleos.playground.vehicle.listener.HvacDefrosterStateListener
import ai.pleos.playground.vehicle.listener.HvacRecirculationStateListener
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.firstride.engine.signal.Signals
import com.firstride.platform.vehicle.VehicleConnection
import com.firstride.platform.voice.PleosTts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 실측 화면(기술 설계 13-2절). 신호마다 지원 여부, 범위, 현재 값, 마지막 콜백 시각, 누적 횟수를 보여 주고
 * 같은 내용을 logcat 태그 FR-MEASURE로 남긴다. 아래쪽에는 어댑터를 거치지 않은 SDK 원시 기록(T4, 영역 목록)과 창 크기(G1).
 */
class MeasureActivity : ComponentActivity() {
    private lateinit var connection: VehicleConnection
    private lateinit var raw: Vehicle
    private lateinit var tts: PleosTts
    private val rows = mutableStateMapOf<String, String>()
    private val rawLog = mutableStateListOf<String>()
    private val window = mutableStateOf("")
    private val t0 = SystemClock.elapsedRealtime()

    private fun log(line: String) {
        val msg = "+${SystemClock.elapsedRealtime() - t0}ms $line"
        Log.i(TAG, msg)
        rawLog.add(0, msg)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connection = VehicleConnection(this)
        raw = Vehicle(applicationContext).apply { initialize() }
        updateWindow(resources.configuration)
        setContent {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text("창: ${window.value}", fontSize = 14.sp)
                Signals.all.sortedBy { it.key }.forEach { Text(rows[it.key] ?: it.key, fontSize = 14.sp) }
                Text("— 원시 기록 —", fontSize = 14.sp)
                rawLog.take(60).forEach { Text(it, fontSize = 12.sp) }
            }
        }
        val s = connection.signals
        for (id in Signals.all) lifecycleScope.launch {
            val support = s.support(id)
            val range = s.range(id)
            var count = 0
            var head = "${id.key} support=$support range=$range polled=${s.isPolled(id)}"
            rows[id.key] = head
            log(head)
            s.observe(id).collect { r ->
                count++
                val line = "$head | #$count $r @+${SystemClock.elapsedRealtime() - t0}ms"
                rows[id.key] = line
                log("observe ${id.key} #$count $r")
            }
            log("observe ${id.key} ended")
        }
        // 같은 신호를 두 번째로 수집해도 둘 다 값을 받는가(리스너를 여러 개 유지하는가)
        lifecycleScope.launch { s.observe(Signals.HVAC_AC).collect { log("observe#2 hvac.ac $it") } }
        tts = PleosTts(this)
        // T8: 한국어 ON_DEVICE 재생, 끝까지 기다림, 중지. am start에 --ez tts true를 주면 실행한다.
        if (intent.getBooleanExtra("tts", false)) lifecycleScope.launch {
            val t = SystemClock.elapsedRealtime()
            log("tts speak#1 start")
            log("tts speak#1 -> ${tts.speak("앞유리 김서림 제거 버튼을 누르세요.")} after ${SystemClock.elapsedRealtime() - t}ms")
            val job = launch { log("tts speak#2 -> ${tts.speak("공조 조작부 왼쪽 위, 부채꼴 모양 버튼이에요. 버튼을 한 번 누르면 켜져요.")}") }
            delay(1000)
            log("tts stop() at +1000ms")
            tts.stop()
            job.join()
            val t3 = SystemClock.elapsedRealtime()
            val job3 = launch { log("tts speak#3 -> ${tts.speak("좋아요. 김서림 제거가 켜졌어요. 다음 단계로 넘어갈게요.")} after ${SystemClock.elapsedRealtime() - t3}ms") }
            delay(800)
            log("tts cancel speak#3 coroutine at +800ms")
            job3.cancel()
        }
        runCatching { rawProbe() }.onFailure { log("raw probe threw ${it.javaClass.name}: ${it.message}") }
    }

    /** 어댑터를 거치지 않고 SDK를 직접 부른다. T4(등록 직후 현재 값이 오는가)와 영역 목록. */
    private fun rawProbe() {
        val hvac = raw.getHVAC()
        val driver = SeatArea(SeatValue.ROW_1_LEFT)
        val front = WindowArea(WindowValue.FRONT_WINDSHIELD)
        val err: (Exception) -> Unit = { log("raw error ${it.javaClass.name}: ${it.message}") }
        hvac.checkHvacAcCapabilities({ c, a -> log("raw cap ac ${c.accessType} areas=${a.map { it.values.toList() }}") }, err)
        hvac.checkHvacRecirculationCapabilities({ c, a -> log("raw cap recirc ${c.accessType} areas=${a.map { it.values.toList() }}") }, err)
        hvac.checkHvacDefrosterCapabilities({ c, a -> log("raw cap defroster ${c.accessType} areas=${a.map { it.values.toList() }}") }, err)
        hvac.checkHvacFanSpeedCapabilities({ c, m -> log("raw cap fanSpeed ${c.accessType} ${m.map { it.key.values.toList() to it.value }}") }, err)
        hvac.checkHvacTemperatureCapabilities({ c, m -> log("raw cap temp ${c.accessType} ${m.map { it.key.values.toList() to it.value }}") }, err)
        hvac.checkHvacMaxDefrostCapabilities({ c, a -> log("raw cap maxDefrost ${c.accessType} areas=${a.map { it.values.toList() }}") }, err)
        hvac.checkHvacFanDirectionCapabilities({ c, m -> log("raw cap fanDirection ${c.accessType} $m") }, err)
        val all = SeatArea(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_CENTER, SeatValue.ROW_2_RIGHT)
        val errCode: (String) -> (Exception) -> Unit = { what -> { e -> log("raw $what error ${e.javaClass.name} code=${errorCodeOf(e)} msg=${e.message}") } }
        hvac.getHvacAcState(driver, { log("raw get ac(driver) $it") }, errCode("get ac(driver)"))
        hvac.getHvacAcState(all, { log("raw get ac(all) $it") }, errCode("get ac(all)"))
        log("raw register ac/recirc/defroster (no read)")
        hvac.registerHvacAcState(all, object : HvacAcStateListener {
            override fun onHvacAcStateUpdated(p0: Boolean?) = log("raw cb ac $p0 thread=${Thread.currentThread().name}")
            override fun onFailed(p0: Exception) = err(p0)
        })
        hvac.registerHvacRecirculationState(all, object : HvacRecirculationStateListener {
            override fun onHvacAutoRecirculationStateUpdated(p0: Boolean?) = log("raw cb recirc $p0")
            override fun onFailed(p0: Exception) = err(p0)
        })
        hvac.registerHvacDefrosterState(front, object : HvacDefrosterStateListener {
            override fun onHvacDefrosterStateUpdated(p0: Boolean?) = log("raw cb frontDefroster $p0")
            override fun onFailed(p0: Exception) = err(p0)
        })
    }

    private fun errorCodeOf(e: Exception): String? = runCatching {
        e.javaClass.methods.firstOrNull { it.parameterCount == 0 && it.returnType.isEnum }?.invoke(e)?.toString()
    }.getOrNull()

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateWindow(newConfig)
    }

    private fun updateWindow(c: Configuration) {
        val m = resources.displayMetrics
        window.value = "${c.screenWidthDp}x${c.screenHeightDp}dp, ${m.widthPixels}x${m.heightPixels}px, density=${m.density}, uiMode=${c.uiMode}"
        log("window ${window.value}")
    }

    override fun onDestroy() {
        connection.close()
        tts.release()
        raw.release()
        super.onDestroy()
    }

    companion object { const val TAG = "FR-MEASURE" }
}
