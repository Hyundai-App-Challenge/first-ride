package com.firstride.app.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.firstride.R
import com.firstride.app.start.CarModel
import com.firstride.engine.check.supportedLights
import com.firstride.engine.course.CourseDefinition
import com.firstride.engine.progress.CourseResult
import com.firstride.engine.progress.ProgressStore
import com.firstride.engine.signal.SignalSource

/** 대표 코스. 기록이 하나도 없을 때만 "여기서 시작"으로 강조한다(POL-C-6). */
const val REPRESENTATIVE_COURSE = "CRS-01"

/** 예상 시간을 분 단위로 올림해 보여 준다. */
fun minutesOf(seconds: Int): Int = maxOf(1, (seconds + 59) / 60)

/** 이 폭부터 차 무대를 왼쪽, 코스를 오른쪽에 둔다. 그보다 좁으면 위아래로 쌓는다(세로 화면, POL-U-1). */
private val SPLIT_FROM = 900.dp

/** 차 무대에 쓸 수 있는 차종과 이미지. [models]가 비어 있고 [emptyHint]도 없으면 무대를 그리지 않는다. */
class CarStageState(
    val models: List<CarModel>,
    val selectedId: String,
    val onSelect: (String) -> Unit,
    val images: CarImages,
    val emptyHint: String?,
) {
    val visible: Boolean get() = models.isNotEmpty() || emptyHint != null
}

/**
 * SCR-02 홈. 지원되는 코스를 코스 번호 순으로 보여 준다(POL-C-5). 점수·등급·순위가 없다(AC-03-4).
 * 차 이미지가 있으면 한쪽에 360도 차 무대를 둔다. 묶음 구분과 배지는 3단계(FR-09)에서 더한다.
 */
@Composable
fun HomeScreen(
    courses: List<CourseDefinition>,
    progress: ProgressStore,
    onOpen: (CourseDefinition) -> Unit,
    onSettings: () -> Unit,
    signals: SignalSource,
    onLightsCheck: () -> Unit,
    car: CarStageState,
) {
    val selected = car.models.firstOrNull { it.id == car.selectedId } ?: car.models.firstOrNull()
    // 고른 차종을 안내 문구에도 반영한다. 아직 고르지 않았으면 무대에 처음 보이는 차종으로 맞춘다.
    LaunchedEffect(selected?.id) { selected?.let { if (it.id != car.selectedId) car.onSelect(it.id) } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val split = maxWidth >= SPLIT_FROM
        Column(Modifier.fillMaxSize().padding(32.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = onSettings) { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.labelLarge) }
            }
            Spacer(Modifier.height(24.dp))
            val list: @Composable (Modifier) -> Unit = { m -> CourseList(courses, progress, onOpen, signals, onLightsCheck, m) }
            val stage: @Composable (Modifier) -> Unit = { m ->
                CarStage(car.models, selected, { car.onSelect(it.id) }, car.images, m, empty = car.emptyHint)
            }
            when {
                !car.visible -> list(Modifier.fillMaxSize())
                split -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    stage(Modifier.weight(1.3f).fillMaxHeight())
                    list(Modifier.weight(1f).fillMaxHeight())
                }
                else -> Column(Modifier.fillMaxSize()) {
                    stage(Modifier.fillMaxWidth().height(340.dp))
                    Spacer(Modifier.height(24.dp))
                    list(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun CourseList(
    courses: List<CourseDefinition>,
    progress: ProgressStore,
    onOpen: (CourseDefinition) -> Unit,
    signals: SignalSource,
    onLightsCheck: () -> Unit,
    modifier: Modifier,
) {
    val results by produceState<Map<String, CourseResult>?>(null, courses) {
        value = courses.associate { it.id to progress.result(it.id) }
    }
    // 소등 확인은 세 등이 모두 미지원이면 버튼을 숨긴다(AC-10-6).
    val lightsAvailable by produceState(false, signals) { value = supportedLights(signals).isNotEmpty() }
    val loaded = results
    Column(modifier) {
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
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    courses.forEachIndexed { i, c ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        CourseRow(c, loaded.getValue(c.id), highlight = firstRun && c.id == REPRESENTATIVE_COURSE, onClick = { onOpen(c) })
                    }
                    if (lightsAvailable) {
                        Spacer(Modifier.height(24.dp))
                        BigButton(stringResource(R.string.home_lights_check), onLightsCheck, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** 카드 대신 선으로 나눈 한 줄. 누르는 면은 줄 전체다(최소 높이는 안의 글자와 여백으로 64dp를 넘는다). */
@Composable
private fun CourseRow(course: CourseDefinition, result: CourseResult, highlight: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 22.dp),
    ) {
        if (highlight) {
            Text(stringResource(R.string.home_start_here), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Text(course.name, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            val quiet = MaterialTheme.colorScheme.onSurfaceVariant
            Text(stringResource(R.string.course_minutes, minutesOf(course.estimatedSeconds)), style = MaterialTheme.typography.bodyLarge, color = quiet)
            Text(
                stringResource(
                    when (result) {
                        CourseResult.NOT_STARTED -> R.string.course_status_not_started
                        CourseResult.HAS_SKIPPED -> R.string.course_status_has_skipped
                        CourseResult.COMPLETED -> R.string.course_status_completed
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = quiet,
            )
        }
    }
}
