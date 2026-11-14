package com.firstride.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton as DialogTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.firstride.R
import com.firstride.app.start.HideReason
import com.firstride.app.start.StartupResult


/**
 * SCR-10 설정(최소): 기록 지우기와 숨긴 코스·이유. 음성 켜기/끄기는 W-30에서 더한다.
 * 기록 지우기는 실행 전에 한 번 확인받고 되돌릴 수 없음을 알린다(POL-S-5, AC-11-3).
 */
@Composable
fun SettingsScreen(
    catalog: StartupResult.Ready?,
    voiceEnabled: Boolean,
    onVoiceEnabled: (Boolean) -> Unit,
    onClear: (onDone: () -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    var cleared by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        TopBar(title = stringResource(R.string.settings_title), left = { TextButton(stringResource(R.string.back), onBack) })
        ScreenBody {
            // 음성 켜기/끄기(POL-V-6). 설정값이라 기록 지우기로 지워지지 않는다(POL-S-5).
            ListRow(
                stringResource(R.string.settings_voice),
                description = stringResource(R.string.settings_voice_hint),
                onClick = { onVoiceEnabled(!voiceEnabled) },
                trailing = { Switch(checked = voiceEnabled, onCheckedChange = onVoiceEnabled) },
            )
            SecondaryButton(stringResource(R.string.clear_records), { confirming = true })
            if (cleared) Text(stringResource(R.string.clear_records_done), style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)

            val hidden = catalog?.hidden.orEmpty()
            if (hidden.isNotEmpty()) {
                Column {
                    Text(stringResource(R.string.hidden_courses_title), style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                    hidden.forEach { h ->
                        val reason = when (h.reason) {
                            HideReason.UNSUPPORTED -> R.string.hidden_reason_unsupported
                            HideReason.PERMISSION_DENIED -> R.string.hidden_reason_permission
                        }
                        ListRow(h.course.name, description = stringResource(reason))
                    }
                }
            }
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.clear_records_confirm_title)) },
            text = { Text(stringResource(R.string.clear_records_confirm_body)) },
            confirmButton = {
                DialogTextButton(onClick = {
                    confirming = false
                    onClear { cleared = true }
                }) { Text(stringResource(R.string.clear_records_confirm)) }
            },
            dismissButton = { DialogTextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
