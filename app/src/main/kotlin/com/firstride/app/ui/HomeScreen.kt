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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import com.firstride.engine.progress.CourseResult
import com.firstride.engine.check.supportedLights
import com.firstride.engine.progress.ProgressStore
import com.firstride.engine.signal.SignalSource

/** 대표 코스. 기록이 하나도 없을 때만 "여기서 시작"으로 강조한다(POL-C-6). */
const val REPRESENTATIVE_COURSE = "CRS-01"

/** 예상 시간을 분 단위로 올림해 보여 준다. */
fun minutesOf(seconds: Int): Int = maxOf(1, (seconds + 59) / 60)

/**
 * SCR-02 홈. 지원되는 코스를 코스 번호 순으로 보여 준다(POL-C-5). 점수·등급·순위가 없다(AC-03-4).
 * 묶음 구분과 배지는 3단계(FR-09)에서 더한다.
 */
@Composable
fun HomeScreen(
    courses: List<CourseDefinition>,
    progress: ProgressStore,
    onOpen: (CourseDefinition) -> Unit,
    onSettings: () -> Unit,
    signals: SignalSource,
    onLightsCheck: () -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // 완료한 조회는 같은 화면에서 유지하고, 도중에 멈춘 조회만 복귀 후 다시 한다.
    val results by produceState<Map<String, CourseResult>?>(null, courses, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (value == null) value = courses.associate { it.id to progress.result(it.id) }
        }
    }
    // 소등 확인은 세 등이 모두 미지원이면 버튼을 숨긴다(AC-10-6).
    val lightsAvailable by produceState(false, signals, lifecycle) {
        var checked = false
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (!checked) {
                value = supportedLights(signals).isNotEmpty()
                checked = true
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(32.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onSettings) { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.labelLarge) }
        }
        Spacer(Modifier.height(24.dp))
        val loaded = results
        when {
            courses.isEmpty() -> {
                Text(stringResource(R.string.no_supported_courses), style = MaterialTheme.typography.bodyLarge)
                if (lightsAvailable) {
                    Spacer(Modifier.height(16.dp))
                    BigButton(stringResource(R.string.home_lights_check), onLightsCheck, Modifier.fillMaxWidth())
                }
            }
            loaded == null -> CircularProgressIndicator()
            else -> {
                val firstRun = loaded.values.all { it == CourseResult.NOT_STARTED }
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    courses.forEach { c ->
                        CourseCard(c, loaded.getValue(c.id), highlight = firstRun && c.id == REPRESENTATIVE_COURSE, onClick = { onOpen(c) })
                    }
                    if (lightsAvailable) BigButton(stringResource(R.string.home_lights_check), onLightsCheck, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun CourseCard(course: CourseDefinition, result: CourseResult, highlight: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            if (highlight) {
                Text(stringResource(R.string.home_start_here), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(course.name, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Text(stringResource(R.string.course_minutes, minutesOf(course.estimatedSeconds)), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(
                        when (result) {
                            CourseResult.NOT_STARTED -> R.string.course_status_not_started
                            CourseResult.HAS_SKIPPED -> R.string.course_status_has_skipped
                            CourseResult.COMPLETED -> R.string.course_status_completed
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
