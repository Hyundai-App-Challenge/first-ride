package com.firstride.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.firstride.app.AppViewModel
import com.firstride.app.StepOperator
import com.firstride.app.Variant
import com.firstride.engine.signal.FailReason
import com.firstride.engine.signal.FakeSignalSource
import com.firstride.engine.signal.Reading
import com.firstride.engine.signal.SignalId
import com.firstride.engine.signal.SignalValue
import com.firstride.engine.signal.Signals
import kotlinx.coroutines.launch

/**
 * debug 개발자 패널: 가짜 신호원의 값을 바꾼다. 에뮬레이터 없이 화면의 모든 상태를 만든다(기술 설계 4-2절).
 * 가짜 신호원을 쓰지 않을 때(실제 차량 신호)는 아무것도 그리지 않는다. release에는 이 파일이 없다.
 */
@Composable
fun DevPanel() {
    val fake = Variant.fake ?: return
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        if (!open) {
            Row {
                OperateStepButton(fake)
                TextButton(onClick = { open = true }) { Text("DEV", fontSize = 14.sp) }
            }
        } else {
            Surface(Modifier.width(520.dp).heightIn(max = 560.dp).padding(8.dp), tonalElevation = 8.dp, shadowElevation = 8.dp) {
                Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                    Row {
                        TextButton(onClick = { open = false }) { Text("닫기", fontSize = 14.sp) }
                        TextButton(onClick = { DevSplit.on = !DevSplit.on }) {
                            Text(if (DevSplit.on) "내비 mock 끄기" else "내비 mock 켜기", fontSize = 14.sp)
                        }
                    }
                    Signals.all.sortedBy { it.key }.forEach { id -> SignalRow(fake, id) }
                }
            }
        }
    }
}

/**
 * "지금 단계 조작": 지금 단계의 목표 조작을 가짜 신호원에 대신 넣는다([StepOperator]). 단계가 돌고 있을 때만 보인다.
 * 신호 이름을 몰라도 단계를 넘길 수 있다. 완료로 바꾸는 버튼이 아니라 값만 바꾸고, 판정은 신호로 한다(AC-02-9).
 * 글자로 누를 수 있다: tools/tap.sh "지금 단계 조작"
 */
@Composable
private fun OperateStepButton(fake: FakeSignalSource) {
    val vm: AppViewModel = viewModel()      // MainActivity가 만든 것을 받는다
    val runner by remember(vm) { StepOperator.currentStep(vm) }.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val step = runner ?: return
    TextButton(onClick = {
        scope.launch { Variant.trace("dev operate ${if (StepOperator.operate(step, fake)) "written" else "nothing"}") }
    }) { Text("지금 단계 조작", fontSize = 14.sp) }
}

@Composable
private fun SignalRow(fake: FakeSignalSource, id: SignalId) {
    val reading by fake.observe(id).collectAsState(initial = null)
    val shown = when (val r = reading) {
        is Reading.Value -> when (val v = r.value) {
            is SignalValue.Token -> v.name
            is SignalValue.Number -> v.value.toString()
        }
        is Reading.Failed -> "실패 ${r.reason}"
        null -> "-"
    }
    val now = ((reading as? Reading.Value)?.value as? SignalValue.Number)?.value ?: 0
    Column(Modifier.padding(vertical = 4.dp)) {
        Text("${id.key} = $shown", fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (id in Signals.numeric) {
                val step = if (id == Signals.HVAC_TEMPERATURE) 5 else 1
                TextButton(onClick = { fake.set(id, SignalValue.Number((now - step).coerceAtLeast(0))) }) { Text("−", fontSize = 14.sp) }
                TextButton(onClick = { fake.set(id, SignalValue.Number(now + step)) }) { Text("+", fontSize = 14.sp) }
            } else {
                (Signals.tokens[id] ?: DEFAULT_DIRECTIONS).forEach { v ->
                    TextButton(onClick = { fake.set(id, SignalValue.Token(v)) }) { Text(v, fontSize = 14.sp) }
                }
            }
            TextButton(onClick = { fake.fail(id, FailReason.TIMEOUT) }) { Text("실패", fontSize = 14.sp) }
        }
    }
}

internal val DEFAULT_DIRECTIONS = setOf("FACE", "FLOOR", "DEFROST")
