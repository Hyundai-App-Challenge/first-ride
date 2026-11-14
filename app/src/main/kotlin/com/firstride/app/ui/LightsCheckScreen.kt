package com.firstride.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.firstride.R
import com.firstride.app.AppViewModel
import com.firstride.app.Screen
import com.firstride.engine.check.LampState
import com.firstride.engine.check.LightsCheck
import com.firstride.engine.check.LightsOutcome
import com.firstride.engine.check.checkLights
import com.firstride.engine.check.supportedLights
import com.firstride.engine.signal.SignalId
import com.firstride.engine.signal.Signals
import kotlinx.coroutines.flow.combine

/**
 * SCR-06 소등 확인(FR-10). 하향등·미등·실내등을 읽어 보여 주고, 화면이 열려 있는 동안 구독해 바뀌면 갱신한다(AC-10-4).
 * 판정은 [checkLights]에 있다. 이 화면은 등을 끄지 않는다(POL-P-3, AC-10-7). 화면에서 나가면 구독과 음성이 함께 끝난다.
 */
@Composable
fun LightsCheckScreen(vm: AppViewModel) {
    val signals = vm.container.signals
    val tts = vm.container.tts

    // null이면 아직 읽는 중. 지원되는 등만 구독한다(AC-10-6). 취소되면 구독이 해제된다.
    val check by produceState<LightsCheck?>(null, signals) {
        val lamps = supportedLights(signals)
        if (lamps.isEmpty()) {
            value = checkLights(emptyMap())
            return@produceState
        }
        combine(lamps.map { signals.observe(it) }) { readings -> checkLights(lamps.zip(readings.toList()).toMap()) }
            .collect { value = it }
    }

    val loaded = check
    val summary = loaded?.let { c ->
        when (c.outcome) {
            LightsOutcome.SOME_ON -> stringResource(R.string.lights_some_on, c.on.map { lampName(it) }.joinToString(", "))
            LightsOutcome.ALL_READABLE_OFF -> stringResource(R.string.lights_all_off)
            LightsOutcome.NONE_READABLE ->
                stringResource(if (c.lamps.isEmpty()) R.string.lights_no_lamps else R.string.lights_none_readable)
        }
    }

    // 요약이 바뀔 때만 읽는다. 바뀌면 앞의 것은 끊긴다. 음성이 실패해도 화면은 그대로다.
    LaunchedEffect(summary) { if (summary != null) tts.speak(summary) }
    DisposableEffect(Unit) { onDispose { tts.stop() } }

    Column(Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.lights_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        if (loaded == null || summary == null) {
            CircularProgressIndicator()
            Text(stringResource(R.string.lights_loading), style = MaterialTheme.typography.bodyLarge)
        } else {
            Text(summary, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            loaded.lamps.forEach { lamp ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(lampName(lamp.signal), style = MaterialTheme.typography.bodyLarge)
                    // 색이 아니라 문구로 구분한다(POL-U-4).
                    Text(
                        stringResource(
                            when (lamp.state) {
                                LampState.ON -> R.string.lamp_on
                                LampState.OFF -> R.string.lamp_off
                                LampState.UNKNOWN -> R.string.lamp_unknown
                            },
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            if (loaded.outcome == LightsOutcome.SOME_ON) {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.lights_how_to_off), style = MaterialTheme.typography.bodyLarge)
            }
        }
        // 이 앱이 읽을 수 없는 등이 있다는 안내는 항상 보인다(AC-10-3).
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.lights_unreadable_notice), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        BigButton(stringResource(R.string.lights_close), { vm.navigate(Screen.Home) }, Modifier.fillMaxWidth(), primary = true)
    }
}

@Composable
private fun lampName(id: SignalId): String = stringResource(
    when (id) {
        Signals.LIGHT_LOW_BEAM -> R.string.lamp_low_beam
        Signals.LIGHT_TAIL_LAMP -> R.string.lamp_tail
        else -> R.string.lamp_cabin
    },
)
