package com.firstride.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firstride.app.start.StartupResult
import com.firstride.app.start.checkCourses
import com.firstride.engine.course.CourseDefinition
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 앱 전체의 화면 상태. 화면 전환은 여기서만 한다(기술 설계 12절).
 * 창 크기·방향이 바뀌어도 ViewModel이 남아 진행 상태가 유지된다(POL-E-8).
 *
 * @param courses 앱에 든 코스 정의. 지원 여부를 확인해 [catalog]에 보일 것과 숨길 것을 나눈다.
 */
class AppViewModel(
    val container: AppContainer,
    private val courses: () -> List<CourseDefinition>,
) : ViewModel() {
    private val _screen = MutableStateFlow<Screen>(Screen.Start)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    /** 시작 확인의 결과. 홈과 설정이 쓴다. 확인 전에는 null. */
    private val _catalog = MutableStateFlow<StartupResult.Ready?>(null)
    val catalog: StateFlow<StartupResult.Ready?> = _catalog.asStateFlow()

    private var startup: Job? = null

    init { start() }

    fun navigate(to: Screen) {
        _screen.value = to
    }

    /**
     * SCR-01: 지원 여부를 확인하고 홈 또는 연결 오류로 간다(FR-01). 연결 오류에서는 사용자가 "다시 시도"를 누를 때만
     * 다시 부른다(POL-E-3). 확인하는 동안 음성 엔진도 미리 깨운다(첫 안내가 늦지 않게).
     */
    fun start() {
        if (startup?.isActive == true) return
        _screen.value = Screen.Start
        startup = viewModelScope.launch {
            container.tts
            when (val result = checkCourses(courses(), container.signals)) {
                is StartupResult.Ready -> {
                    _catalog.value = result
                    _screen.value = Screen.Home
                }
                StartupResult.ConnectionFailed -> _screen.value = Screen.ConnectionError
            }
        }
    }

    /** SCR-10 기록 지우기. 확인은 화면이 받는다(AC-11-3). */
    fun clearRecords(onDone: () -> Unit) {
        viewModelScope.launch {
            container.progress.clearAll()
            onDone()
        }
    }
}
