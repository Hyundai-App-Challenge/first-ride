package com.firstride.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.firstride.R
import com.firstride.engine.course.CourseDefinition
import com.firstride.engine.judge.RestoreReport
import com.firstride.engine.progress.CourseResult
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** SCR-03 코스 소개. 무엇을 익히는지, 몇 단계인지 알리고 시작한다. */
@Composable
fun CourseIntroScreen(course: CourseDefinition, onStart: () -> Unit, onBack: () -> Unit, onFollowUp: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState())) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back), style = MaterialTheme.typography.labelLarge) }
        Spacer(Modifier.height(16.dp))
        Text(course.name, style = MaterialTheme.typography.headlineMedium)
        if (course.summary.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(course.summary, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "${stringResource(R.string.intro_steps, course.steps.size)} · ${stringResource(R.string.course_minutes, minutesOf(course.estimatedSeconds))}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.intro_park_notice), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(32.dp))
        BigButton(stringResource(R.string.intro_start), onStart, Modifier.fillMaxWidth(), primary = true)
        // 후속 조치가 정의된 코스에서만(AC-16-1). 정차 상태에서 쓰는 안내임을 함께 밝힌다(POL-F-13).
        if (course.followUps.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            BigButton(stringResource(R.string.followup_entry_intro), onFollowUp, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.followup_entry_notice), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * SCR-05 코스 완료. [attempt]는 방금 한 시도의 결과다. 건너뛴 단계가 있으면 완료로 보이지 않는다(AC-04-5).
 * [restore]는 아직 바뀐 채로 있는 것의 목록이다(FR-14). 앱은 되돌리지 않고 알리기만 한다. 되돌리면 바로 사라진다.
 * 후속 조치 진입(FR-16)은 후속 조치가 정의된 코스에서만 보인다(AC-16-1).
 */
@Composable
fun CourseDoneScreen(
    course: CourseDefinition,
    attempt: CourseResult?,
    next: CourseDefinition?,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onHome: () -> Unit,
    restore: Flow<RestoreReport?> = emptyFlow(),
    onFollowUp: () -> Unit = {},
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val report by produceState<RestoreReport?>(null, restore, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                restore.collect { value = it }
                awaitCancellation()
            } finally {
                // 복귀 때 이전 차량 상태를 새 확인 결과처럼 표시하지 않는다.
                value = null
            }
        }
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (attempt == CourseResult.HAS_SKIPPED) {
            Text(stringResource(R.string.done_has_skipped), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))
            BigButton(stringResource(R.string.done_retry), onRetry, primary = true)
        } else {
            Text("✓ ${stringResource(R.string.done_completed, course.name)}", style = MaterialTheme.typography.headlineMedium)
            if (next != null) {
                Spacer(Modifier.height(24.dp))
                BigButton("${stringResource(R.string.done_next_course)}: ${next.name}", onNext, primary = true)
            }
        }
        report?.takeIf { !it.isEmpty }?.let {
            Spacer(Modifier.height(24.dp))
            if (it.changed.isNotEmpty()) {
                Text(stringResource(R.string.restore_title), style = MaterialTheme.typography.titleMedium)
                it.changed.forEach { label -> Text("· $label", style = MaterialTheme.typography.bodyLarge) }
                Text(stringResource(R.string.restore_hint), style = MaterialTheme.typography.bodyMedium)
            }
            if (it.unverified.isNotEmpty()) {
                Text(stringResource(R.string.restore_unverified, it.unverified.joinToString(", ")), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (course.followUps.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            BigButton(stringResource(R.string.followup_entry_done), onFollowUp)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.followup_entry_notice), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(16.dp))
        BigButton(stringResource(R.string.done_home), onHome)
    }
}
