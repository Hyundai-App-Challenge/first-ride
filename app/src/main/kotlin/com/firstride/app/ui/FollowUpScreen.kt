package com.firstride.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firstride.R
import com.firstride.app.AppViewModel
import com.firstride.app.Screen
import com.firstride.engine.course.CourseDefinition
import com.firstride.engine.course.ModelCourseText
import com.firstride.engine.judge.FollowUpAnswer
import com.firstride.engine.judge.FollowUpState

/**
 * SCR-11 후속 조치(FR-16). 조치 안내는 SCR-04의 [StepContent]를 그대로 쓰고, 조치가 끝나면 "김이 걷혔나요?"를 묻는다.
 * 답은 터치로만 받고(POL-F-12) 갈 길만 고른다. 저장하지 않는다(POL-F-8). 앱이 차를 조작하는 제안은 없다(POL-F-9).
 */
@Composable
fun FollowUpScreen(
    vm: AppViewModel,
    screen: Screen.FollowUp,
    course: CourseDefinition,
    texts: ModelCourseText?,
    loadImage: (String) -> ImageBitmap?,
) {
    val runner by vm.followUp.collectAsStateWithLifecycle()
    val r = runner
    if (r == null) {
        // 끊겼거나 끝난 뒤에 이 화면이 남았다. 이어서 하지 않고 홈으로 간다(POL-F-10).
        LaunchedEffect(screen.courseId) { vm.navigate(Screen.Home) }
        return
    }
    val state by r.state.collectAsStateWithLifecycle()
    when (val s = state) {
        FollowUpState.Checking -> Centered {
            CircularProgressIndicator(Modifier.width(32.dp).height(32.dp))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.followup_checking), style = MaterialTheme.typography.headlineSmall)
        }
        is FollowUpState.Doing -> {
            val baseStep = course.steps.lastOrNull()
            val base = baseStep?.id?.let { texts?.steps?.get(it) }
            val restoreText = stringResource(R.string.followup_restore_instruction, s.step.action.joinToString(", ") { it.label.ifBlank { it.signal.key } })
            val text = if (s.restore) restoreStepText(s.step, baseStep, base, restoreText) else texts?.steps?.get(s.step.id)
            // StepContent는 코스 정의와 단계 번호로 단계를 찾는다. 조치 하나짜리 코스로 감싸서 넘긴다.
            val single = remember(s.step) { CourseDefinition(course.id, course.name, 0, listOf(s.step)) }
            val singleTexts = remember(s.step, text) { ModelCourseText(course.id, listOfNotNull(text?.let { s.step.id to it }).toMap()) }
            LaunchedEffect(s.step.id) { vm.narrateFollowUp(s.progress.runner, s.step, text) }
            StepContent(s.progress, single, singleTexts, loadImage, onSpeak = vm::say, onQuit = vm::quitFollowUp, voice = AppViewModel.VoiceStatus())
        }
        FollowUpState.Asking -> {
            val ask = stringResource(R.string.followup_ask)
            LaunchedEffect(Unit) { vm.say(ask) }
            Centered {
                Text(ask, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(32.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigButton(stringResource(R.string.followup_yes), { r.answer(FollowUpAnswer.RESOLVED) }, Modifier.weight(1f), primary = true)
                    BigButton(stringResource(R.string.followup_no), { r.answer(FollowUpAnswer.NOT_YET) }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                BigButton(stringResource(R.string.followup_quit), vm::quitFollowUp, Modifier.fillMaxWidth())
            }
        }
        FollowUpState.Exhausted -> {
            val text = course.exhaustedText?.takeIf { it.isNotBlank() } ?: stringResource(R.string.followup_exhausted_default)
            LaunchedEffect(Unit) { vm.say(text) }
            Ending(text, vm)
        }
        FollowUpState.Resolved -> Ending(stringResource(R.string.followup_resolved), vm)
    }
}

@Composable
private fun Ending(text: String, vm: AppViewModel) = Centered {
    Text(text, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(32.dp))
    BigButton(stringResource(R.string.followup_home), vm::quitFollowUp, Modifier.fillMaxWidth(), primary = true)
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}
