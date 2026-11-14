package com.firstride.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firstride.app.practice.Interruption
import com.firstride.app.start.StartupResult
import com.firstride.app.start.checkCourses
import com.firstride.engine.course.CourseDefinition
import com.firstride.engine.drive.DriveState
import com.firstride.engine.judge.CourseRunner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 앱 전체의 화면 상태와 실습 범위. 화면 전환은 여기서만 한다(기술 설계 12절).
 * 창 크기·방향이 바뀌어도 ViewModel이 남아 진행 상태가 유지된다(POL-E-8).
 *
 * 실습 범위(기술 설계 8-2절): 코스 실행(구독, 타이머)과 음성은 [practice] 코루틴 하나 아래에서 돈다.
 * 주행 제한, 백그라운드 전환, 그만하기 가운데 무엇이든 이 하나를 취소한다. 각 기능이 따로 멈출지 판단하지 않는다.
 *
 * @param courses 앱에 든 코스 정의. 지원 여부를 확인해 [catalog]에 보일 것과 숨길 것을 나눈다.
 */
class AppViewModel(
    val container: AppContainer,
    private val courses: () -> List<CourseDefinition>,
) : ViewModel() {
    private val _screen = MutableStateFlow<Screen>(Screen.Start)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    /** 주행 제한. RESTRICTED면 화면은 지금 화면과 무관하게 차단 화면(SCR-08)을 그린다(POL-D-2). */
    val driveState: StateFlow<DriveState> = container.drivingGate.state

    /** 시작 확인의 결과. 홈과 설정이 쓴다. 확인 전에는 null. */
    private val _catalog = MutableStateFlow<StartupResult.Ready?>(null)
    val catalog: StateFlow<StartupResult.Ready?> = _catalog.asStateFlow()

    /** 지금 실행 중인 코스. 단계 진행 화면(SCR-04)이 `runner.step`을 그린다. 실습 중이 아니면 null. */
    private val _runner = MutableStateFlow<CourseRunner?>(null)
    val runner: StateFlow<CourseRunner?> = _runner.asStateFlow()

    /** 끊긴 실습. 제한이 풀리거나 앞으로 돌아왔을 때 이어서 하기를 물을지 정한다(POL-D-6, POL-R-10~12). */
    private val _interruption = MutableStateFlow<Interruption?>(null)
    val interruption: StateFlow<Interruption?> = _interruption.asStateFlow()

    private var startup: Job? = null
    private var practice: Job? = null

    init {
        viewModelScope.launch { _screen.collect { Variant.trace("screen $it") } }
        start()
        viewModelScope.launch {
            driveState.collect {
                if (it != DriveState.RESTRICTED) return@collect
                interrupt()
                // 시작 확인 중에 제한이 걸리면 읽기를 멈추고 풀릴 때까지 다시 기다린다(POL-P-1).
                if (startup?.isActive == true) {
                    startup?.cancel()
                    startup = null
                    start()
                }
            }
        }
    }

    fun navigate(to: Screen) {
        _screen.value = to
    }

    /**
     * SCR-01: 지원 여부를 확인하고 홈 또는 연결 오류로 간다(FR-01). 주행 제한 중이면 풀릴 때까지 아무것도 하지 않는다
     * (POL-D-8, POL-P-1). 연결 오류에서는 사용자가 "다시 시도"를 누를 때만 다시 부른다(POL-E-3).
     * 확인하는 동안 음성 엔진도 미리 깨운다(첫 안내가 늦지 않게).
     */
    fun start() {
        if (startup?.isActive == true) return
        _screen.value = Screen.Start
        startup = viewModelScope.launch {
            driveState.first { it == DriveState.PARKED }
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

    /** SCR-03의 "시작". 코스를 실습 범위 안에서 돌리고, 끝까지 가면 결과가 기록된 뒤 완료 화면으로 간다. */
    fun startCourse(course: CourseDefinition) {
        if (driveState.value == DriveState.RESTRICTED) return
        cancelPractice()
        _interruption.value = null
        val runner = CourseRunner(course, container.signals, container.progress)
        _runner.value = runner
        _screen.value = Screen.Step(course.id)
        practice = viewModelScope.launch {
            val tracer = launch {
                runner.step.collectLatest { p -> p?.runner?.state?.collect { Variant.trace("step ${p.index + 1}/${p.total} ${it.phase}") } }
            }
            runner.run()
            tracer.cancel()
            _runner.value = null
            _screen.value = Screen.CourseDone(course.id)
        }
    }

    /** "그만하기": 기록하지 않고 홈으로(POL-R-6). 이어서 하기도 묻지 않는다. */
    fun quitCourse() {
        cancelPractice()
        _interruption.value = null
        _screen.value = Screen.Home
    }

    /** 앱이 백그라운드로 갔다(Activity onStop). 창 크기 변경에는 오지 않는다(실측 T14). */
    fun onBackground() = interrupt()

    /** 이어서 하기를 묻지 않고 홈으로(POL-D-6의 "홈으로", 30분 경과 POL-R-12). */
    fun dismissInterruption() {
        _interruption.value = null
        if (_screen.value is Screen.Step) _screen.value = Screen.Home
    }

    /** 이어서 하기를 물어야 하는가. 끊긴 실습이 있고 30분이 지나지 않았을 때만. 지났으면 홈으로 보낸다. */
    fun shouldAskResume(): Boolean {
        val cut = _interruption.value ?: return false
        if (cut.canResume(container.clock())) return true
        dismissInterruption()
        return false
    }

    /** 실습 중이었다면 범위를 취소하고 위치를 기억한다(POL-R-10). 구독·타이머·음성이 함께 멈춘다(POL-D-3, POL-E-6). */
    private fun interrupt() {
        val runner = _runner.value ?: return
        val courseId = (_screen.value as? Screen.Step)?.courseId ?: return
        // 마지막 단계가 끝나 결과를 기록하는 중이면(step이 이미 비었다) 끝까지 두고 음성만 멈춘다.
        val stepIndex = runner.step.value?.index ?: run { container.tts.stop(); return }
        cancelPractice()
        _interruption.value = Interruption(courseId, stepIndex, container.clock())
    }

    private fun cancelPractice() {
        val wasRunning = practice != null
        practice?.cancel()
        practice = null
        _runner.value = null
        if (wasRunning) container.tts.stop()
    }

    /** SCR-10 기록 지우기. 확인은 화면이 받는다(AC-11-3). */
    fun clearRecords(onDone: () -> Unit) {
        viewModelScope.launch {
            container.progress.clearAll()
            onDone()
        }
    }
}
