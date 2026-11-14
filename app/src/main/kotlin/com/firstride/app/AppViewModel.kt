package com.firstride.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firstride.app.practice.Interruption
import com.firstride.app.start.StartupResult
import com.firstride.app.start.checkCourses
import com.firstride.engine.course.CourseDefinition
import com.firstride.engine.course.ModelCourseText
import com.firstride.engine.course.StepDefinition
import com.firstride.engine.course.StepText
import com.firstride.engine.drive.DriveState
import com.firstride.engine.judge.CourseRunner
import com.firstride.engine.judge.FollowUpRunner
import com.firstride.engine.judge.RestoreReport
import com.firstride.engine.judge.StepRunner
import com.firstride.engine.judge.observeRestore
import com.firstride.engine.progress.CourseResult
import com.firstride.engine.judge.Phase
import com.firstride.engine.judge.stepView
import com.firstride.engine.voice.SttPort
import com.firstride.engine.voice.VoiceAction
import com.firstride.engine.voice.VoiceCoordinator
import com.firstride.engine.voice.applyVoiceAction
import com.firstride.engine.voice.narrateCourse
import com.firstride.engine.voice.narrateStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    /** 코스 ID별 차종 문구. 단계 상태를 읽어 줄 때 쓴다(FR-05). */
    private val texts: Map<String, ModelCourseText>,
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

    /** 방금 끝낸 시도의 결과. 완료 화면(SCR-05)이 건너뜀 여부를 보여 준다. 저장된 최고 기록과 다를 수 있다(POL-R-7). */
    private val _lastAttempt = MutableStateFlow<CourseResult?>(null)
    val lastAttempt: StateFlow<CourseResult?> = _lastAttempt.asStateFlow()

    /** 끊긴 실습. 제한이 풀리거나 앞으로 돌아왔을 때 이어서 하기를 물을지 정한다(POL-D-6, POL-R-10~12). */
    private val _interruption = MutableStateFlow<Interruption?>(null)
    val interruption: StateFlow<Interruption?> = _interruption.asStateFlow()

    /** 지금 하고 있는 후속 조치 안내(FR-16). 하고 있지 않으면 null. 코스 실행과 같은 실습 범위에서 돈다. */
    private val _followUp = MutableStateFlow<FollowUpRunner?>(null)
    val followUp: StateFlow<FollowUpRunner?> = _followUp.asStateFlow()

    /** 원상 복귀 기준을 소유한 방금 시도(FR-14). 메모리에만 두고 저장하지 않는다. */
    private var restoreAttempt: Pair<String, CourseRunner>? = null

    /** 음성 설정(SCR-10). 저장된 값을 읽기 전에는 켜짐으로 본다. */
    val voiceEnabled: StateFlow<Boolean> = container.settings.voiceEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** SCR-04의 음성 진행 표시. [listening]은 "듣는 중"(POL-V-13), [off]는 진행 불가(AC-06-6). */
    data class VoiceStatus(val listening: Boolean = false, val off: Boolean = true)
    private val _voiceStatus = MutableStateFlow(VoiceStatus())
    val voiceStatus: StateFlow<VoiceStatus> = _voiceStatus.asStateFlow()

    /** 실제 발화 정확도 게이트(T9) 통과 전까지 연결하지 않는다. null이면 TTS 안내와 터치만 쓴다. */
    private val stt: SttPort? = null

    private var inForeground = false
    private var startup: Job? = null
    private var practice: Job? = null
    private val practiceMutex = Mutex()
    private var pausedRunner: CourseRunner? = null

    /** 자식 음성·구독의 정리까지 직렬화해 이전 stop이 다음 실습을 멈추지 않게 한다. */
    private fun launchPractice(block: suspend CoroutineScope.() -> Unit): Job = viewModelScope.launch {
        practiceMutex.withLock { coroutineScope(block) }
    }

    /** 지금 실습의 음성 조율기. 실습이 아니면 null. */
    private var voice: VoiceCoordinator? = null

    init {
        viewModelScope.launch { _screen.collect { Variant.trace("screen $it") } }
        start()
        viewModelScope.launch {
            driveState.collect {
                Variant.trace("drive $it foreground=$inForeground")
                if (it != DriveState.RESTRICTED) {
                    // 정차 복귀는 화면 복귀가 아니다. 숨은 시작 조회는 켜지 않는다.
                    shouldAskResume()
                    return@collect
                }
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
        if (!inForeground) return
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
        clearInterruption()
        val runner = CourseRunner(course, container.signals, container.progress)
        restoreAttempt = course.id to runner
        launchCourse(course, runner)
    }

    private fun launchCourse(course: CourseDefinition, runner: CourseRunner) {
        _runner.value = runner
        _screen.value = Screen.Step(course.id)
        practice = launchPractice {
            val voice = VoiceCoordinator(
                container.tts,
                stt = stt,
                voiceEnabled = { voiceEnabled.value },
                isStepDone = { runner.step.value?.runner?.state?.value?.phase == Phase.DONE },
                onAction = { onVoiceAction(it, course, runner) },
            ).also { this@AppViewModel.voice = it }
            val speaking = launch { voice.run() }
            val status = launch {
                combine(voice.listening, voice.available, voice.sttAvailable, voiceEnabled) { listening, speakOk, listenOk, on ->
                    VoiceStatus(listening, off = !(speakOk && listenOk && on))
                }.collect { _voiceStatus.value = it }
            }
            val narrating = launch { narrateCourse(runner, course, texts[course.id], voice) }
            val tracer = launch {
                runner.step.collectLatest { p -> p?.runner?.state?.collect { Variant.trace("step ${p.index + 1}/${p.total} ${it.phase}") } }
            }
            _lastAttempt.value = runner.run()
            tracer.cancel()
            narrating.cancel()
            speaking.cancel()
            status.cancel()
            _voiceStatus.value = VoiceStatus()
            this@AppViewModel.voice = null
            _runner.value = null
            _screen.value = Screen.CourseDone(course.id)
        }
    }

    /**
     * SCR-03·SCR-05의 후속 조치 진입(FR-16). 후속 조치가 정의된 코스에서만 시작한다(AC-16-1). 실습 범위 안에서 돌아서
     * 주행 제한이나 백그라운드 전환이 오면 코스와 함께 취소되고, 이어서 하지 않는다(AC-16-13, POL-F-10).
     * 진행 기록 저장소는 쓰지 않는다(POL-F-8).
     */
    fun startFollowUp(course: CourseDefinition) {
        if (driveState.value == DriveState.RESTRICTED || course.followUps.isEmpty()) return
        cancelPractice()
        clearInterruption()
        val runner = FollowUpRunner(course, container.signals)
        _followUp.value = runner
        _screen.value = Screen.FollowUp(course.id)
        practice = launchPractice {
            val voice = VoiceCoordinator(container.tts, voiceEnabled = { voiceEnabled.value }).also { this@AppViewModel.voice = it }
            launch { voice.run() }      // 끝난 뒤에도 마지막 문구를 읽을 수 있게 취소될 때까지 둔다
            runner.run()
        }
    }

    /** 후속 조치 단계의 안내를 읽는다. 화면이 단계를 그리는 동안만 돈다. */
    suspend fun narrateFollowUp(runner: StepRunner, step: StepDefinition, text: StepText?) {
        voice?.let { narrateStep(runner, step, text, it) }
    }

    /** "그만하기" 또는 끝난 뒤 홈으로. 아무것도 기록하지 않는다(POL-F-8). */
    fun quitFollowUp() {
        cancelPractice()
        _screen.value = Screen.Home
    }

    /** SCR-05의 원상 복귀 목록(FR-14). 방금 한 코스가 아니면 null 하나만 낸다. 수집을 멈추면 구독이 풀린다. */
    fun restoreReport(course: CourseDefinition): Flow<RestoreReport?> {
        val (id, attempt) = restoreAttempt ?: return flowOf(null)
        if (id != course.id) return flowOf(null)
        return observeRestore(course, attempt.initialValues, container.signals)
    }

    /** "다시 듣기"와 "어디 있어요". 3초 안의 같은 문구도 사용자가 요청한 것은 읽는다(POL-V-3의 예외). */
    fun say(text: String) {
        voice?.announce(text, force = true)
    }

    /** 음성 동작을 터치 버튼과 같은 일로 옮긴다(POL-V-15). 지금 단계의 화면 문구를 기준으로 읽는다. */
    private fun onVoiceAction(action: VoiceAction, course: CourseDefinition, runner: CourseRunner) {
        val p = runner.step.value ?: return
        val def = course.steps[p.index]
        applyVoiceAction(action, p.runner, stepView(p.runner.state.value, def, texts[course.id]?.steps?.get(def.id)), ::say)
    }

    /** SCR-10 음성 켜기/끄기. 기록 지우기로 지워지지 않는다(POL-S-5). */
    fun setVoiceEnabled(enabled: Boolean) {
        viewModelScope.launch { container.settings.setVoiceEnabled(enabled) }
    }

    /** "그만하기": 기록하지 않고 홈으로(POL-R-6). 이어서 하기도 묻지 않는다. */
    fun quitCourse() {
        cancelPractice()
        clearInterruption()
        _screen.value = Screen.Home
    }

    /** 앱이 백그라운드로 갔다(Activity onStop). 창 크기 변경에는 오지 않는다(실측 T14). */
    fun onBackground() {
        inForeground = false
        startup?.cancel()
        startup = null
        interrupt()
    }

    /** 화면 복귀 때만 미완료 시작 확인을 이어 간다. 기한은 재개 버튼에서도 다시 확인한다. */
    fun onForeground() {
        inForeground = true
        if (_screen.value == Screen.Start) start()
        if (_interruption.value != null) shouldAskResume()
    }

    fun resumeInterruptedCourse() {
        if (driveState.value != DriveState.PARKED || !shouldAskResume()) return
        val cut = _interruption.value ?: return
        val course = _catalog.value?.visible?.firstOrNull { it.id == cut.courseId }
        val runner = pausedRunner
        if (course == null || runner == null) { dismissInterruption(); return }
        clearInterruption()
        launchCourse(course, runner)
    }

    private fun clearInterruption() {
        pausedRunner = null
        _interruption.value = null
    }

    /** 이어서 하기를 묻지 않고 홈으로(POL-D-6의 "홈으로", 30분 경과 POL-R-12). */
    fun dismissInterruption() {
        clearInterruption()
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
        if (_followUp.value != null) {      // 후속 조치는 끊기면 이어서 하지 않는다(POL-F-10)
            cancelPractice()
            _screen.value = Screen.Home
            return
        }
        val runner = _runner.value ?: return
        val courseId = (_screen.value as? Screen.Step)?.courseId ?: return
        // 마지막 단계가 끝나 결과를 기록하는 중이면 끝까지 두고 음성만 멈춘다.
        val total = _catalog.value?.visible?.firstOrNull { it.id == courseId }?.steps?.size ?: return
        val stepIndex = runner.nextStepIndex
        if (stepIndex >= total) { container.tts.stop(); return }
        cancelPractice()
        pausedRunner = runner
        _interruption.value = Interruption(courseId, stepIndex, container.clock())
    }

    private fun cancelPractice() {
        val wasRunning = practice != null
        practice?.cancel()
        practice = null
        voice = null
        _voiceStatus.value = VoiceStatus()
        _runner.value = null
        _followUp.value = null
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
