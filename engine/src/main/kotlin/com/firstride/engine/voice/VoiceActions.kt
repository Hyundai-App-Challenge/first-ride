package com.firstride.engine.voice

import com.firstride.engine.judge.StepRunner
import com.firstride.engine.judge.StepView

/**
 * 조율기가 낸 동작을 단계 실행기와 음성에 잇는다. 화면의 터치 버튼과 같은 일을 한다(POL-V-15).
 * [say]는 3초 안의 같은 문구도 읽는 요청이다.
 */
fun applyVoiceAction(action: VoiceAction, runner: StepRunner, view: StepView, say: (String) -> Unit) {
    when (action) {
        VoiceAction.Next -> runner.advance()
        VoiceAction.ConfirmSkip -> runner.skip()
        VoiceAction.Repeat -> (view.spoken ?: view.message).takeIf { it.isNotBlank() }?.let(say)
        VoiceAction.Where -> view.locatorText?.let(say)
    }
}
