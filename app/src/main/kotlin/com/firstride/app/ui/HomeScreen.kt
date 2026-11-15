package com.firstride.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.firstride.R
import com.firstride.app.start.REPRESENTATIVE_COURSE
import com.firstride.app.start.isFirstRun
import com.firstride.app.start.nextCourse
import com.firstride.engine.course.CourseDefinition
import com.firstride.engine.course.ModelCourseText
import com.firstride.engine.progress.CourseResult
import com.firstride.engine.check.supportedLights
import com.firstride.engine.progress.ProgressStore
import com.firstride.engine.signal.SignalSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    texts: Map<String, ModelCourseText> = emptyMap(),
    loadImage: (String) -> ImageBitmap? = { null },
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
                            val firstRun = isFirstRun(courses, loaded)
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
                    // 오른쪽 칸: 진행 현황과 다음 코스(스크롤), 맨 아래 소등 확인(문서 5-2절).
                    // 결과를 아직 읽지 못했으면 소등 확인만 그린다.
                    Column(Modifier.width(640.dp).fillMaxHeight()) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.ml)) {
                            val loaded = results
                            if (loaded != null && courses.isNotEmpty()) {
                                ProgressPanel(courses, loaded)
                                val next = nextCourse(courses, loaded)
                                if (next != null) {
                                    NextCourseCard(next, texts[next.id], loadImage, firstRun = isFirstRun(courses, loaded), onClick = { onOpen(next) })
                                } else {
                                    Text(stringResource(R.string.home_all_done), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (lightsAvailable) Spacer(Modifier.height(Space.ml))
                        lights(Modifier)
                    }
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
                Pill(stringResource(statusText(result)))
            }
        }
    }
}

/**
 * 진행 현황("익힌 조작"). 코스마다 상태 기호 하나를 홈 목록 순서로 놓는다(POL-C-5).
 * 숫자·백분율·막대를 쓰지 않는다(AC-03-4). 누를 수 없다. 기호마다 "코스 이름, 상태"를 읽어 준다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgressPanel(courses: List<CourseDefinition>, results: Map<String, CourseResult>) {
    Column(
        Modifier.fillMaxWidth().background(appColors.card, RoundedCornerShape(Radius.card)).padding(Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Text(stringResource(R.string.home_progress_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.m), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            courses.forEach { c ->
                val result = results[c.id] ?: CourseResult.NOT_STARTED
                // "✓ 완료"의 기호는 화면용이라 읽어 주는 문구에서는 뺀다.
                val status = stringResource(statusText(result)).removePrefix(stringResource(R.string.step_done_mark)).trim()
                val description = "${c.name}, $status"
                Box(Modifier.semantics { contentDescription = description }) {
                    StatusMark(
                        when (result) {
                            CourseResult.COMPLETED -> Mark.CONFIRMED
                            CourseResult.HAS_SKIPPED -> Mark.SKIPPED
                            CourseResult.NOT_STARTED -> Mark.EMPTY
                        },
                    )
                }
            }
        }
    }
}

/**
 * "다음에 익힐 코스" 카드. 카드 전체가 터치 대상이고 목록의 코스 카드처럼 코스 소개(SCR-03)로 간다.
 * 이름표 → 첫 단계의 위치 안내 그림 → 코스 이름 → 한 줄 설명 → "N단계 · 약 M분".
 * 그림은 경로가 없거나 읽지 못하면 그 줄만 뺀다. 기록이 하나도 없으면 이름표가 "여기서 시작"이다(POL-C-6).
 */
@Composable
private fun NextCourseCard(
    course: CourseDefinition,
    texts: ModelCourseText?,
    loadImage: (String) -> ImageBitmap?,
    firstRun: Boolean,
    onClick: () -> Unit,
) {
    val stepText = course.steps.firstOrNull()?.let { texts?.steps?.get(it.id) }
    val path = stepText?.locatorAsset
    val image by produceState<ImageBitmap?>(null, path) {
        value = path?.let { withContext(Dispatchers.IO) { loadImage(it) } }
    }
    CardButton(onClick) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            if (firstRun) {
                Pill(stringResource(R.string.home_start_here), background = MaterialTheme.colorScheme.primary, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Pill(stringResource(R.string.home_next_title))
            }
            image?.let { LocatorPlate(it, stepText?.locatorText, Modifier.aspectRatio(LOCATOR_ASPECT)) }
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(course.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                if (course.summary.isNotBlank()) {
                    Text(course.summary, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "${stringResource(R.string.intro_steps, course.steps.size)} · ${stringResource(R.string.course_minutes, minutesOf(course.estimatedSeconds))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColors.textTertiary,
                )
            }
        }
    }
}

/** 위치 안내 그림의 비율(1200×760). */
private const val LOCATOR_ASPECT = 1200f / 760f

private fun statusText(result: CourseResult) = when (result) {
    CourseResult.NOT_STARTED -> R.string.course_status_not_started
    CourseResult.HAS_SKIPPED -> R.string.course_status_has_skipped
    CourseResult.COMPLETED -> R.string.course_status_completed
}
