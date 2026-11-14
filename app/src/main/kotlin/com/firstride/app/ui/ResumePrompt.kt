package com.firstride.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.firstride.R

/** 정차 복귀 후에만 표시한다. 색·글꼴·누름 피드백은 앱의 현재 테마를 따른다. */
@Composable
fun ResumePrompt(courseName: String, stepNumber: Int, total: Int, onResume: () -> Unit, onHome: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    AlertDialog(
        onDismissRequest = onHome,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text(stringResource(R.string.resume_title), style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (courseName.isNotBlank() && total > 0) {
                    Text(stringResource(R.string.resume_course_step, courseName, stepNumber, total), style = MaterialTheme.typography.titleMedium)
                }
                Text(stringResource(R.string.resume_description), style = MaterialTheme.typography.bodyLarge)
            }
        },
        confirmButton = {
            Button(
                onClick = onResume,
                modifier = Modifier.heightIn(min = 88.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onSurface,
                    contentColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Text(stringResource(R.string.resume_continue), style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onHome,
                modifier = Modifier.heightIn(min = 88.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            ) {
                Text(stringResource(R.string.resume_home), style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}
