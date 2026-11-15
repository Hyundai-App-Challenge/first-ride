package com.firstride.app

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firstride.app.ui.ConnectionErrorScreen
import com.firstride.app.ui.CourseDoneScreen
import com.firstride.app.ui.CourseIntroScreen
import com.firstride.app.ui.DrivingBlockScreen
import com.firstride.app.ui.FollowUpScreen
import com.firstride.app.ui.HomeScreen
import com.firstride.app.ui.LightsCheckScreen
import com.firstride.app.ui.SettingsScreen
import com.firstride.app.ui.StartScreen
import com.firstride.app.ui.StepScreen
import com.firstride.engine.course.CourseDefinition
import com.firstride.engine.course.ModelCourseText
import com.firstride.engine.drive.DriveState

/**
 * 화면 분기(기술 설계 12절). 화면마다 한 줄이고, 화면의 내용은 각자의 파일에 있다.
 * 새 화면은 [Screen]에 값을 더하고 여기에 한 줄을 더한다. 화면 파일끼리는 서로 고치지 않는다.
 */
@Composable
fun AppNavigation(vm: AppViewModel, texts: Map<String, ModelCourseText>, loadImage: (String) -> ImageBitmap?) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val drive by vm.driveState.collectAsStateWithLifecycle()
    val runner by vm.runner.collectAsStateWithLifecycle()
    val lastAttempt by vm.lastAttempt.collectAsStateWithLifecycle()
    val voiceStatus by vm.voiceStatus.collectAsStateWithLifecycle()
    val voiceEnabled by vm.voiceEnabled.collectAsStateWithLifecycle()
    val visible = catalog?.visible.orEmpty()

    // 주행 제한은 화면 전환보다 위에 있다(POL-D-2).
    if (drive == DriveState.RESTRICTED) {
        DrivingBlockScreen()
        return
    }
    when (val s = screen) {
        Screen.Start -> StartScreen()
        Screen.ConnectionError -> ConnectionErrorScreen(onRetry = vm::start)
        Screen.Settings -> SettingsScreen(catalog, voiceEnabled, onVoiceEnabled = vm::setVoiceEnabled, onClear = vm::clearRecords, onBack = { vm.navigate(Screen.Home) })
        Screen.Home -> HomeScreen(
            courses = visible,
            progress = vm.container.progress,
            onOpen = { vm.navigate(Screen.CourseIntro(it.id)) },
            onSettings = { vm.navigate(Screen.Settings) },
            signals = vm.container.signals,
            onLightsCheck = { vm.navigate(Screen.LightsCheck) },
            texts = texts,
            loadImage = loadImage,
        )
        is Screen.CourseIntro -> WithCourse(visible, s.courseId, onMissing = { vm.navigate(Screen.Home) }) { course ->
            CourseIntroScreen(
                course,
                onStart = { vm.startCourse(course) },
                onBack = { vm.navigate(Screen.Home) },
                onFollowUp = { vm.startFollowUp(course) },
            )
        }
        is Screen.Step -> StepScreen(
            runner = runner,
            course = visible.firstOrNull { it.id == s.courseId },
            texts = texts[s.courseId],
            loadImage = loadImage,
            onSpeak = vm::say,
            onQuit = vm::quitCourse,
            voice = voiceStatus,
        )
        is Screen.CourseDone -> WithCourse(visible, s.courseId, onMissing = { vm.navigate(Screen.Home) }) { course ->
            CourseDoneScreen(
                course = course,
                attempt = lastAttempt,
                next = visible.getOrNull(visible.indexOf(course) + 1),
                onNext = { visible.getOrNull(visible.indexOf(course) + 1)?.let { vm.navigate(Screen.CourseIntro(it.id)) } },
                onRetry = { vm.startCourse(course) },
                onHome = { vm.navigate(Screen.Home) },
                restore = remember(course.id) { vm.restoreReport(course) },
                onFollowUp = { vm.startFollowUp(course) },
            )
        }
        Screen.LightsCheck -> LightsCheckScreen(vm)
        is Screen.FollowUp -> WithCourse(visible, s.courseId, onMissing = { vm.navigate(Screen.Home) }) { course ->
            FollowUpScreen(vm, s, course, texts[s.courseId], loadImage)
        }
    }
}

/** 코스를 찾아 그린다. 숨겨졌거나 없으면 홈으로 보낸다(그리는 도중이 아니라 효과로). */
@Composable
private fun WithCourse(
    courses: List<CourseDefinition>,
    id: String,
    onMissing: () -> Unit,
    content: @Composable (CourseDefinition) -> Unit,
) {
    val course = courses.firstOrNull { it.id == id }
    if (course != null) content(course) else LaunchedEffect(id) { onMissing() }
}
