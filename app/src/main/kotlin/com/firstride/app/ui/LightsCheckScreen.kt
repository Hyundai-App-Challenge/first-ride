package com.firstride.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.firstride.R
import com.firstride.app.AppViewModel
import com.firstride.app.Screen
import com.firstride.engine.check.LampState
import com.firstride.engine.check.LightsCheck
import com.firstride.engine.check.LightsOutcome
import com.firstride.engine.check.observeLights
import com.firstride.engine.check.supportedLights
import com.firstride.engine.judge.Phase
import com.firstride.engine.signal.SignalId
import com.firstride.engine.signal.Signals
import kotlinx.coroutines.awaitCancellation

/**
 * SCR-06 소등 확인(FR-10). 하향등·미등·실내등을 읽어 보여 주고, 화면이 열려 있는 동안 구독해 바뀌면 갱신한다(AC-10-4).
 * 판정은 [checkLights]에 있다. 이 화면은 등을 끄지 않는다(POL-P-3, AC-10-7). 화면에서 나가면 구독과 음성이 함께 끝난다.
 */
@Composable
fun LightsCheckScreen(vm: AppViewModel) {
    val signals = vm.container.signals
    val tts = vm.container.tts
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val voiceEnabled by vm.voiceEnabled.collectAsStateWithLifecycle()

    // 백그라운드에서는 구독을 해제하고, 복귀 때 현재 값을 새로 확인한다(POL-E-6).
    val check by produceState<LightsCheck?>(null, signals, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                val lamps = supportedLights(signals)
                observeLights(lamps, signals).collect { value = it }
                // 관찰이 끝나도 확인 불가/미지원 결과는 STOP까지 표시한다.
                awaitCancellation()
            } finally {
                // 복귀 전에 오래된 요약을 다시 읽지 않는다.
                value = null
            }
        }
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

    // 설정과 생명주기를 함께 따른다. 요약 변경·백그라운드 전환은 이전 재생을 취소한다.
    LaunchedEffect(summary, voiceEnabled, lifecycle) {
        if (summary != null && voiceEnabled) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { tts.speak(summary) }
        }
    }
    DisposableEffect(Unit) { onDispose { tts.stop() } }

    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        TopBar(title = stringResource(R.string.lights_title))
        ScreenBody(bottom = { SecondaryButton(stringResource(R.string.lights_close), { vm.navigate(Screen.Home) }, Modifier.fillMaxWidth()) }) {
            if (loaded == null || summary == null) {
                Spinner()
                Text(stringResource(R.string.lights_loading), style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
            } else {
                // 켜진 등이 있으면 주의 색 상자로 알린다. 문구가 내용을 말하고 색은 거든다(POL-U-4).
                if (loaded.outcome == LightsOutcome.SOME_ON) {
                    StateBanner(Phase.CORRECTION, summary, body = stringResource(R.string.lights_how_to_off), showHead = false)
                } else {
                    Text(summary, style = MaterialTheme.typography.titleLarge, color = cs.onSurface)
                }
                Column {
                    loaded.lamps.forEach { lamp ->
                        ListRow(lampName(lamp.signal), trailing = { LampStateText(lamp.state) })
                    }
                }
            }
            // 이 앱이 읽을 수 없는 등이 있다는 안내는 항상 보인다(AC-10-3).
            NoticeBox(stringResource(R.string.lights_unreadable_notice))
        }
    }
}

/** 등 하나의 상태. 기호와 문구로 구분하고 색은 기호에만 쓴다(POL-U-4, DESIGN.md 3절). */
@Composable
private fun LampStateText(state: LampState) {
    val (text, mark) = when (state) {
        LampState.ON -> R.string.lamp_on to Mark.ATTENTION
        LampState.OFF -> R.string.lamp_off to Mark.CONFIRMED
        LampState.UNKNOWN -> R.string.lamp_unknown to Mark.UNKNOWN
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        StatusMark(mark, 32.dp)
        Text(
            stringResource(text),
            style = MaterialTheme.typography.labelMedium,
            color = if (state == LampState.ON) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
