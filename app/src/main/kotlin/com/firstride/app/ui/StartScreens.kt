package com.firstride.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.firstride.R
import com.firstride.app.start.HideReason
import com.firstride.app.start.StartupResult
import kotlinx.coroutines.delay

/** SCR-01 시작. 2초를 넘기면 진행 표시를 보인다(AC-01-1). */
@Composable
fun StartScreen() {
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(2_000)
        slow = true
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        if (slow) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.start_checking))
        }
    }
}

/** SCR-08 주행 중 차단. 문구 하나만 있다. 버튼·목록·움직이는 요소가 없다(POL-D-4, AC-07-1). */
@Composable
fun DrivingBlockScreen() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.driving_block), style = MaterialTheme.typography.headlineSmall)
    }
}

/** SCR-09 연결 오류. 무슨 일이 있었는지와 할 수 있는 행동, 다시 시도(FR-12, POL-E-2). */
@Composable
fun ConnectionErrorScreen(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.connection_error_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.connection_error_body))
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

/**
 * SCR-10 설정(최소): 기록 지우기와 숨긴 코스·이유. 음성 켜기/끄기는 W-30에서 더한다.
 * 기록 지우기는 실행 전에 한 번 확인받고 되돌릴 수 없음을 알린다(POL-S-5, AC-11-3).
 */
@Composable
fun SettingsScreen(catalog: StartupResult.Ready?, onClear: (onDone: () -> Unit) -> Unit, onBack: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    var cleared by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState())) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = { confirming = true }) { Text(stringResource(R.string.clear_records)) }
        if (cleared) Text(stringResource(R.string.clear_records_done))

        val hidden = catalog?.hidden.orEmpty()
        if (hidden.isNotEmpty()) {
            Spacer(Modifier.height(32.dp))
            Text(stringResource(R.string.hidden_courses_title), style = MaterialTheme.typography.titleMedium)
            hidden.forEach { h ->
                val reason = when (h.reason) {
                    HideReason.UNSUPPORTED -> R.string.hidden_reason_unsupported
                    HideReason.PERMISSION_DENIED -> R.string.hidden_reason_permission
                }
                Text("${h.course.name} — ${stringResource(reason)}")
            }
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.clear_records_confirm_title)) },
            text = { Text(stringResource(R.string.clear_records_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onClear { cleared = true }
                }) { Text(stringResource(R.string.clear_records_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
