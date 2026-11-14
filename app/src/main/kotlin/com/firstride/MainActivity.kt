package com.firstride

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.firstride.app.AppViewModel
import com.firstride.app.FirstRideApp
import com.firstride.app.Screen
import com.firstride.app.start.CourseCatalog
import com.firstride.app.start.CourseTexts
import com.firstride.app.ui.ConnectionErrorScreen
import com.firstride.app.ui.CourseDoneScreen
import com.firstride.app.ui.CourseIntroScreen
import com.firstride.app.ui.DevPanel
import com.firstride.app.ui.DrivingBlockScreen
import com.firstride.app.ui.FirstRideTheme
import com.firstride.app.ui.HomeScreen
import com.firstride.app.ui.SettingsScreen
import com.firstride.app.ui.StartScreen
import com.firstride.app.ui.StepScreen
import com.firstride.engine.course.CourseDefinition
import com.firstride.engine.drive.DriveState

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels {
        viewModelFactory {
            initializer { AppViewModel((application as FirstRideApp).container, CourseTexts.load(assets)) { CourseCatalog.load(assets) } }
        }
    }

    /** 백그라운드 전환. 창 크기·방향·주야간 변경에는 오지 않는다(configChanges, 실측 T14·T15). */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) vm.onBackground()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FirstRideTheme {
                Box {
                    Surface {
                        val screen by vm.screen.collectAsStateWithLifecycle()
                        val catalog by vm.catalog.collectAsStateWithLifecycle()
                        val drive by vm.driveState.collectAsStateWithLifecycle()
                        val runner by vm.runner.collectAsStateWithLifecycle()
                        val lastAttempt by vm.lastAttempt.collectAsStateWithLifecycle()
                        val texts = remember { CourseTexts.load(assets) }
                        val visible = catalog?.visible.orEmpty()

                        // 주행 제한은 화면 전환보다 위에 있다(POL-D-2, 기술 설계 12절).
                        if (drive == DriveState.RESTRICTED) DrivingBlockScreen() else when (val s = screen) {
                            Screen.Start -> StartScreen()
                            Screen.ConnectionError -> ConnectionErrorScreen(onRetry = vm::start)
                            Screen.Settings -> SettingsScreen(catalog, onClear = vm::clearRecords, onBack = { vm.navigate(Screen.Home) })
                            Screen.Home -> HomeScreen(
                                courses = visible,
                                progress = vm.container.progress,
                                onOpen = { vm.navigate(Screen.CourseIntro(it.id)) },
                                onSettings = { vm.navigate(Screen.Settings) },
                            )
                            is Screen.CourseIntro -> WithCourse(visible, s.courseId, onMissing = { vm.navigate(Screen.Home) }) { course ->
                                CourseIntroScreen(course, onStart = { vm.startCourse(course) }, onBack = { vm.navigate(Screen.Home) })
                            }
                            is Screen.Step -> {
                                val course = visible.firstOrNull { it.id == s.courseId }
                                StepScreen(
                                    runner = runner,
                                    course = course,
                                    texts = texts[s.courseId],
                                    loadImage = { path -> CourseTexts.loadImage(assets, path) },
                                    onSpeak = vm::say,
                                    onQuit = vm::quitCourse,
                                )
                            }
                            is Screen.CourseDone -> WithCourse(visible, s.courseId, onMissing = { vm.navigate(Screen.Home) }) { course ->
                                CourseDoneScreen(
                                    course = course,
                                    attempt = lastAttempt,
                                    next = visible.getOrNull(visible.indexOf(course) + 1),
                                    onNext = { visible.getOrNull(visible.indexOf(course) + 1)?.let { vm.navigate(Screen.CourseIntro(it.id)) } },
                                    onRetry = { vm.startCourse(course) },
                                    onHome = { vm.navigate(Screen.Home) },
                                )
                            }
                            else -> Text("$s")
                        }
                    }
                    DevPanel()
                }
            }
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
