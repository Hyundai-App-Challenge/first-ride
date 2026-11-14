package com.firstride.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
 * 묶음 구분과 배지는 3단계(FR-09)에서 더한다. 가로형은 왼쪽 목록, 오른쪽 640dp 칸(문서 5-2절).
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
    val results by produceState<Map<String, CourseResult>?>(null, courses) {
        value = courses.associate { it.id to progress.result(it.id) }
    }
    // 소등 확인은 세 등이 모두 미지원이면 버튼을 숨긴다(AC-10-6).
    val lightsAvailable by produceState(false, signals) { value = supportedLights(signals).isNotEmpty() }
    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = stringResource(R.string.app_name),
            right = { TextButton(stringResource(R.string.settings_title), onSettings) },
        )
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = Space.screenH, vertical = Space.screenV)) {
            val wide = maxWidth >= Size.wideBreakpoint
            val lights: @Composable (Modifier) -> Unit = { m ->
                if (lightsAvailable) SecondaryButton(stringResource(R.string.home_lights_check), onLightsCheck, m.fillMaxWidth())
            }
            val list: @Composable (Modifier) -> Unit = { m ->
                Column(m.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(Space.s))
                    val loaded = results
                    when {
                        courses.isEmpty() -> Text(stringResource(R.string.no_supported_courses), style = MaterialTheme.typography.bodyLarge)
                        loaded == null -> Spinner()
                        else -> {
                            val firstRun = loaded.values.all { it == CourseResult.NOT_STARTED }
                            courses.forEach { c ->
                                CourseCard(c, loaded.getValue(c.id), highlight = firstRun && c.id == REPRESENTATIVE_COURSE, onClick = { onOpen(c) })
                            }
                        }
                    }
                }
            }
            if (wide) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    list(Modifier.weight(1f).fillMaxHeight())
                    Column(Modifier.width(640.dp).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) { lights(Modifier) }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    list(Modifier.weight(1f))
                    if (lightsAvailable) Spacer(Modifier.height(Space.ml))
                    lights(Modifier)
                }
            }
        }
    }
}

/** 코스 카드. 카드 전체가 터치 대상이다. 첫 실행의 대표 코스는 채운 이름표("여기서 시작")로 알린다. 테두리로 강조하지 않는다. */
@Composable
private fun CourseCard(course: CourseDefinition, result: CourseResult, highlight: Boolean, onClick: () -> Unit) {
    CardButton(onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.ml)) {
            Column(Modifier.weight(1f)) {
                Text(course.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    stringResource(R.string.course_minutes, minutesOf(course.estimatedSeconds)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColors.textTertiary,
                )
            }
            if (highlight) {
                Pill(stringResource(R.string.home_start_here), background = MaterialTheme.colorScheme.primary, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Pill(
                    stringResource(
                        when (result) {
                            CourseResult.NOT_STARTED -> R.string.course_status_not_started
                            CourseResult.HAS_SKIPPED -> R.string.course_status_has_skipped
                            CourseResult.COMPLETED -> R.string.course_status_completed
                        },
                    ),
                )
            }
        }
    }
}
