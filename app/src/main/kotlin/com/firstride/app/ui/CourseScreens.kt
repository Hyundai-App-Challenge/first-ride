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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.firstride.R
import com.firstride.engine.course.CourseDefinition
import com.firstride.engine.progress.CourseResult

/** SCR-03 코스 소개. 무엇을 익히는지, 몇 단계인지 알리고 시작한다. */
@Composable
fun CourseIntroScreen(course: CourseDefinition, onStart: () -> Unit, onBack: () -> Unit) {
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
    }
}

/**
 * SCR-05 코스 완료. [attempt]는 방금 한 시도의 결과다. 건너뛴 단계가 있으면 완료로 보이지 않는다(AC-04-5).
 * 켜 둔 것 안내(FR-14)와 후속 조치 진입(FR-16)은 3단계에서 더한다.
 */
@Composable
fun CourseDoneScreen(
    course: CourseDefinition,
    attempt: CourseResult?,
    next: CourseDefinition?,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onHome: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
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
        Spacer(Modifier.height(16.dp))
        BigButton(stringResource(R.string.done_home), onHome)
    }
}
