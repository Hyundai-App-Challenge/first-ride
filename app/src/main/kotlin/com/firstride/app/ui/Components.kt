package com.firstride.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.firstride.R
import com.firstride.engine.judge.ConditionStatus
import com.firstride.engine.judge.ConditionView

/** 누르기 쉬운 큰 버튼. 최소 높이 64dp. */
@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    val m = modifier.heightIn(min = 64.dp)
    if (primary) Button(onClick = onClick, modifier = m) { Text(text, style = MaterialTheme.typography.labelLarge) }
    else OutlinedButton(onClick = onClick, modifier = m) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/**
 * 조건 하나의 상태(AC-04-3). 색이 아니라 기호와 문구로 구분한다(POL-U-4).
 * 폴링하는 조건은 "확인 중"을 덧붙인다(AC-04-7).
 */
@Composable
fun ConditionRow(view: ConditionView, modifier: Modifier = Modifier) {
    val status = stringResource(
        when (view.status) {
            ConditionStatus.MET -> R.string.condition_met
            ConditionStatus.ALREADY_MET -> R.string.condition_already_met
            ConditionStatus.UNMET -> R.string.condition_unmet
            ConditionStatus.TOGGLE_NEEDED -> R.string.condition_toggle
            ConditionStatus.UNKNOWN -> R.string.condition_unknown
        },
    )
    Row(
        modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(view.label, style = MaterialTheme.typography.bodyLarge)
        val polling = if (view.polling) " ${stringResource(R.string.condition_polling)}" else ""
        Text(status + polling, style = MaterialTheme.typography.bodyLarge)
    }
}
