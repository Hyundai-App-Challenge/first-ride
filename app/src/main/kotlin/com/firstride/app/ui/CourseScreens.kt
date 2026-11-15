package com.firstride.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import com.firstride.engine.course.CourseDefinition
import com.firstride.engine.judge.Phase
import com.firstride.engine.judge.RestoreReport
import com.firstride.engine.progress.CourseResult
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** 가로형에서 글이 너무 길게 늘어나지 않게 내용 너비를 묶는다(세로형 앱 창 너비). */
private val ContentMaxWidth = 841.dp

/**
 * TopBar 아래 본문. 내용이 넘치면 위쪽만 스크롤하고 [bottom]의 버튼은 화면 아래에 고정한다.
 * 가로형에서는 내용 너비를 [ContentMaxWidth]로 묶는다.
 */
@Composable
internal fun ScreenBody(
    modifier: Modifier = Modifier,
    bottom: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize().padding(horizontal = Space.screenH, vertical = Space.screenV)) {
        Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.ml)) { content() }
            Column(Modifier.fillMaxWidth().padding(top = Space.ml), verticalArrangement = Arrangement.spacedBy(Space.m)) { bottom() }
        }
    }
}

/** SCR-03 코스 소개. 무엇을 익히는지, 몇 단계인지 알리고 시작한다. */
@Composable
fun CourseIntroScreen(course: CourseDefinition, onStart: () -> Unit, onBack: () -> Unit, onFollowUp: () -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        TopBar(title = stringResource(R.string.intro_title), left = { TextButton(stringResource(R.string.back), onBack) })
        ScreenBody(
            bottom = {
                PrimaryButton(stringResource(R.string.intro_start), onStart, Modifier.fillMaxWidth())
                // 후속 조치가 정의된 코스에서만(AC-16-1). 정차 상태에서 쓰는 안내임을 함께 밝힌다(POL-F-13).
                if (course.followUps.isNotEmpty()) {
                    TextButton(stringResource(R.string.followup_entry_intro), onFollowUp, Modifier.fillMaxWidth())
                    Text(stringResource(R.string.followup_entry_notice), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Text(course.name, style = MaterialTheme.typography.headlineMedium, color = cs.onSurface)
                if (course.summary.isNotBlank()) Text(course.summary, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                Text(
                    "${stringResource(R.string.intro_steps, course.steps.size)} · ${stringResource(R.string.course_minutes, minutesOf(course.estimatedSeconds))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            }
            NoticeBox(stringResource(R.string.intro_park_notice))
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
    val cs = MaterialTheme.colorScheme
    val skipped = attempt == CourseResult.HAS_SKIPPED
    Column(Modifier.fillMaxSize()) {
        TopBar(title = course.name)
        ScreenBody(
            bottom = {
                if (skipped) {
                    PrimaryButton(stringResource(R.string.done_retry), onRetry, Modifier.fillMaxWidth())
                } else if (next != null) {
                    PrimaryButton("${stringResource(R.string.done_next_course)} · ${next.name}", onNext, Modifier.fillMaxWidth())
                }
                SecondaryButton(stringResource(R.string.done_home), onHome, Modifier.fillMaxWidth())
                if (course.followUps.isNotEmpty()) {
                    TextButton(stringResource(R.string.followup_entry_done), onFollowUp, Modifier.fillMaxWidth())
                    Text(stringResource(R.string.followup_entry_notice), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
            },
        ) {
            // 결과는 상태 배너와 같은 기호·색을 쓴다(완료 ✓ / 건너뜀 »). 색만으로 구분하지 않는다(POL-U-4).
            if (skipped) StateBanner(Phase.SKIPPED, stringResource(R.string.done_has_skipped))
            else StateBanner(Phase.DONE, stringResource(R.string.done_completed, course.name))
            report?.takeIf { !it.isEmpty }?.let {
                if (it.changed.isNotEmpty()) {
                    Column {
                        Text(stringResource(R.string.restore_title), style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                        it.changed.forEach { label -> ListRow(label) }
                        Spacer(Modifier.height(Space.s))
                        Text(stringResource(R.string.restore_hint), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                }
                if (it.unverified.isNotEmpty()) {
                    Text(stringResource(R.string.restore_unverified, it.unverified.joinToString(", ")), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

/** 가운데 정렬 화면(시작·오류·후속 조치 질문). */
@Composable
internal fun CenteredBody(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Space.screenH, vertical = Space.screenV),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = ContentMaxWidth), horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}
