package com.firstride.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firstride.R
import com.firstride.app.AppViewModel
import com.firstride.engine.course.CourseDefinition
import com.firstride.engine.course.ModelCourseText
import com.firstride.engine.judge.CourseRunner
import com.firstride.engine.judge.Phase
import com.firstride.engine.judge.StepProgress
import com.firstride.engine.judge.StepView
import com.firstride.engine.judge.stepView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SCR-04 단계 진행. 판정 상태가 바뀌면 안내 문구, 위치 안내, 조건 표시가 바뀐다(사용자 플로우 3절).
 * "완료"나 "했어요" 버튼은 없다. 완료는 신호로만 만들어진다(AC-02-9).
 * 상태는 ViewModel의 실행기가 들고 있어서 창 방향이 바뀌어도 이어진다(AC-04-8).
 */
@Composable
fun StepScreen(
    runner: CourseRunner?,
    course: CourseDefinition?,
    texts: ModelCourseText?,
    loadImage: (String) -> ImageBitmap?,
    onSpeak: (String) -> Unit,
    onQuit: () -> Unit,
    voice: AppViewModel.VoiceStatus = AppViewModel.VoiceStatus(),
) {
    if (runner == null || course == null) return Centered { CircularProgressIndicator() }
    val progress by runner.step.collectAsStateWithLifecycle()
    val p = progress ?: return Centered { CircularProgressIndicator() }
    StepContent(p, course, texts, loadImage, onSpeak, onQuit, voice)
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { content() }
}

@Composable
internal fun StepContent(
    p: StepProgress,
    course: CourseDefinition,
    texts: ModelCourseText?,
    loadImage: (String) -> ImageBitmap?,
    onSpeak: (String) -> Unit,
    onQuit: () -> Unit,
    voice: AppViewModel.VoiceStatus,
) {
    val state by p.runner.state.collectAsStateWithLifecycle()
    val def = course.steps[p.index]
    val view = stepView(state, def, texts?.steps?.get(def.id))

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = course.name,
            left = { TextButton(stringResource(R.string.step_quit), onQuit) },
            right = { TopBarInfo(stringResource(R.string.step_progress, p.index + 1, p.total)) },
        )
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = Space.screenH, vertical = Space.screenV)) {
            // 가로형은 두 칸(POL-U-1). 왼쪽은 문구·조건·버튼(운전석에 가깝다), 오른쪽은 위치 안내(문서 5-2절).
            // 늘어나는 것은 위치 안내의 크기다(POL-U-2).
            if (maxWidth >= Size.wideBreakpoint) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
                    Column(Modifier.weight(5f).fillMaxHeight()) {
                        // 판이 길어지면 이 열만 스크롤되고 버튼 줄(Controls)은 자리를 지킨다.
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.ml)) {
                            MessageBlock(view)
                            StatePanel(view)
                        }
                        Spacer(Modifier.height(Space.ml))
                        Controls(view, p, onSpeak, voice)
                    }
                    Locator(view, loadImage, Modifier.weight(6f).fillMaxHeight(), wide = true)
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.ml)) {
                        MessageBlock(view)
                        Locator(view, loadImage, Modifier, wide = false)
                        ConditionList(view)
                    }
                    Spacer(Modifier.height(Space.ml))
                    Controls(view, p, onSpeak, voice)
                }
            }
        }
    }
}

/**
 * 상태 문구. 기다리는 중에는 안내 문구 한 문장을 크게, 그 밖의 상태는 [StateBanner]로 기호·이름표·색을 함께 보인다.
 * 색만으로 구분하지 않는다(POL-U-4).
 */
@Composable
private fun MessageBlock(view: StepView) {
    if (view.phase == Phase.WAITING) {
        Text(view.message, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
    } else {
        StateBanner(view.phase, view.message)
    }
}

/** 조합 판정 단계에서 조건마다 충족 여부를 따로 보여 준다(AC-04-3). 조건이 하나뿐이면 문구가 이미 말해 준다. */
@Composable
private fun ConditionList(view: StepView) {
    if (view.conditions.size < 2 || view.phase == Phase.PRECHECK) return
    Column {
        view.conditions.forEach { ConditionRow(it) }
    }
}

/**
 * 가로형 왼쪽 칸의 "지금 차 상태" 판. 조건이 하나여도 그린다. "완료" 버튼이 없으니(AC-02-9)
 * 차가 알아서 확인한다는 것을 도움말로 알린다. 누를 수 없다(clickable 없음).
 * 차량 상태를 처음 확인하는 동안(PRECHECK)과 조건이 없을 때는 그리지 않는다.
 */
@Composable
private fun StatePanel(view: StepView) {
    if (view.conditions.isEmpty() || view.phase == Phase.PRECHECK) return
    Column(
        Modifier.fillMaxWidth().background(appColors.card, RoundedCornerShape(Radius.card)).padding(Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Text(stringResource(R.string.step_state_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        Column { view.conditions.forEach { ConditionRow(it) } }
        Text(stringResource(R.string.step_state_help), style = MaterialTheme.typography.bodyMedium, color = appColors.textTertiary)
    }
}

/** 위치 안내 그림과 위치 문구. 그림이 없거나 읽지 못해도 문구는 나온다. */
@Composable
private fun Locator(view: StepView, loadImage: (String) -> ImageBitmap?, modifier: Modifier, wide: Boolean) {
    // 새 조작부를 안내하는 동안 이전 그림이 남지 않도록 상태 자체를 경로에 묶는다.
    val image by key(view.locatorAsset) {
        produceState<ImageBitmap?>(null) {
            value = view.locatorAsset?.let { path -> withContext(Dispatchers.IO) { loadImage(path) } }
        }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        // 가로형은 칸 높이를 다 쓰고, 세로형은 340dp로 묶는다(문서 5-2절).
        if (view.locatorAsset != null) LocatorPlate(image, view.locatorText, if (wide) Modifier.weight(1f) else Modifier.height(Size.locatorPortrait))
        view.locatorText?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/**
 * 음성 진행의 세 어휘와 같은 동작을 하는 터치 버튼이 항상 있다(AC-04-6). 확인 불가에서는 "다시 확인"이 더해진다.
 * 버튼 줄은 화면 아래에 고정한다.
 */
@Composable
private fun Controls(view: StepView, p: StepProgress, onSpeak: (String) -> Unit, voice: AppViewModel.VoiceStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        VoiceBar(voice)
        if (view.phase == Phase.UNVERIFIABLE) {
            PrimaryButton(stringResource(R.string.step_recheck), p.runner::retry, Modifier.fillMaxWidth())
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            SecondaryButton(stringResource(R.string.step_replay), { (view.spoken ?: view.message).takeIf { it.isNotBlank() }?.let(onSpeak) }, Modifier.weight(1f))
            SecondaryButton(stringResource(R.string.step_where), { view.locatorText?.let(onSpeak) }, Modifier.weight(1f))
            TextButton(stringResource(R.string.step_skip), p.runner::skip, Modifier.weight(1f))
        }
    }
}
