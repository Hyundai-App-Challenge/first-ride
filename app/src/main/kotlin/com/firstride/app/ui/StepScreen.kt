package com.firstride.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
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

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight       // 가로형은 두 칸(POL-U-1). 늘어나는 것은 위치 안내의 크기다(POL-U-2)
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.step_progress, p.index + 1, p.total), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onQuit) { Text(stringResource(R.string.step_quit), style = MaterialTheme.typography.labelLarge) }
            }
            VoiceIndicator(voice)
            Spacer(Modifier.height(12.dp))
            if (wide) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        MessageBlock(view)
                        Spacer(Modifier.height(16.dp))
                        ConditionList(view)
                        Spacer(Modifier.height(16.dp))
                        Controls(view, p, onSpeak)
                    }
                    Column(Modifier.weight(1f)) { Locator(view, loadImage, Modifier.weight(1f)) }
                }
            } else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    MessageBlock(view)
                    Spacer(Modifier.height(16.dp))
                    Locator(view, loadImage, Modifier.heightIn(max = 360.dp))
                    Spacer(Modifier.height(16.dp))
                    ConditionList(view)
                }
                Spacer(Modifier.height(12.dp))
                Controls(view, p, onSpeak)
            }
        }
    }
}

/** 듣는 중(POL-V-13)과 음성 꺼짐(AC-06-6) 표시. 글자로 알려 준다(POL-U-4). 둘 다 아니면 자리를 차지하지 않는다. */
@Composable
private fun VoiceIndicator(voice: AppViewModel.VoiceStatus) {
    if (voice.listening) Text(stringResource(R.string.voice_listening), style = MaterialTheme.typography.titleMedium)
    if (voice.off) Text(stringResource(R.string.voice_off), style = MaterialTheme.typography.titleMedium)
}

/** 상태 문구. 확인 중에는 진행 표시, 완료에는 표시(✓)를 붙인다. 색만으로 구분하지 않는다(POL-U-4). */
@Composable
private fun MessageBlock(view: StepView) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (view.phase == Phase.PRECHECK) {
            CircularProgressIndicator(Modifier.width(32.dp).height(32.dp))
            Spacer(Modifier.width(16.dp))
        }
        val mark = if (view.phase == Phase.DONE) "${stringResource(R.string.step_done_mark)} " else ""
        Text(mark + view.message, style = MaterialTheme.typography.headlineSmall)
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

/** 위치 안내 그림과 위치 문구. 그림이 없거나 읽지 못해도 문구는 나온다. */
@Composable
private fun Locator(view: StepView, loadImage: (String) -> ImageBitmap?, modifier: Modifier) {
    // 새 조작부를 안내하는 동안 이전 그림이 남지 않도록 상태 자체를 경로에 묶는다.
    val image by key(view.locatorAsset) {
        produceState<ImageBitmap?>(null) {
            value = view.locatorAsset?.let { path -> withContext(Dispatchers.IO) { loadImage(path) } }
        }
    }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        image?.let {
            Image(it, contentDescription = view.locatorText, contentScale = ContentScale.Fit, modifier = Modifier.weight(1f, fill = false).fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        view.locatorText?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
    }
}

/** 음성 진행의 세 어휘와 같은 동작을 하는 터치 버튼이 항상 있다(AC-04-6). 확인 불가에서는 "다시 확인"이 더해진다. */
@Composable
private fun Controls(view: StepView, p: StepProgress, onSpeak: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (view.phase == Phase.UNVERIFIABLE) {
            BigButton(stringResource(R.string.step_recheck), p.runner::retry, Modifier.fillMaxWidth(), primary = true)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton(stringResource(R.string.step_replay), { (view.spoken ?: view.message).takeIf { it.isNotBlank() }?.let(onSpeak) }, Modifier.weight(1f))
            BigButton(stringResource(R.string.step_where), { view.locatorText?.let(onSpeak) }, Modifier.weight(1f))
            BigButton(stringResource(R.string.step_skip), p.runner::skip, Modifier.weight(1f))
        }
    }
}
