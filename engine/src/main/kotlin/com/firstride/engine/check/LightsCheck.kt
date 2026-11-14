package com.firstride.engine.check

import com.firstride.engine.signal.Reading
import com.firstride.engine.signal.SignalId
import com.firstride.engine.signal.SignalSource
import com.firstride.engine.signal.SignalValue
import com.firstride.engine.signal.Signals
import com.firstride.engine.signal.Support

/** 하차 전 소등 확인(FR-10)이 보는 등. 이 앱이 읽을 수 없는 등(주간주행등 등)은 화면의 고정 안내로 알린다. */
val LIGHTS_TO_CHECK: List<SignalId> = listOf(Signals.LIGHT_LOW_BEAM, Signals.LIGHT_TAIL_LAMP, Signals.LIGHT_CABIN)

enum class LampState { ON, OFF, UNKNOWN }

data class LampStatus(val signal: SignalId, val state: LampState)

/** SOME_ON: 켜진 등이 있다. ALL_READABLE_OFF: 읽은 등은 모두 꺼짐(읽지 못한 등은 따로 남을 수 있다). NONE_READABLE: 하나도 읽지 못했다. */
enum class LightsOutcome { SOME_ON, ALL_READABLE_OFF, NONE_READABLE }

data class LightsCheck(val lamps: List<LampStatus>, val outcome: LightsOutcome) {
    val on: List<SignalId> get() = lamps.filter { it.state == LampState.ON }.map { it.signal }
    val unknown: List<SignalId> get() = lamps.filter { it.state == LampState.UNKNOWN }.map { it.signal }
}

/** 읽지 못한 것, 값이 없는 것, ON·OFF가 아닌 값(`DAYTIME_RUNNING` 등)은 꺼짐이 아니라 확인 불가다(AC-10-5, POL-P-4). */
fun lampState(reading: Reading?): LampState = when ((reading as? Reading.Value)?.value) {
    SignalValue.Token("ON") -> LampState.ON
    SignalValue.Token("OFF") -> LampState.OFF
    else -> LampState.UNKNOWN
}

/** [readings]에는 지원되는 등만 넣는다(AC-10-6). 값이 아직 오지 않은 등은 null로 넣으면 확인 불가다. */
fun checkLights(readings: Map<SignalId, Reading?>): LightsCheck {
    val lamps = readings.map { (id, r) -> LampStatus(id, lampState(r)) }
    val outcome = when {
        lamps.any { it.state == LampState.ON } -> LightsOutcome.SOME_ON
        lamps.any { it.state == LampState.OFF } -> LightsOutcome.ALL_READABLE_OFF
        else -> LightsOutcome.NONE_READABLE
    }
    return LightsCheck(lamps, outcome)
}

/** 소등 확인이 다룰 수 있는 등. 비어 있으면 홈의 진입 버튼을 숨긴다(AC-10-6). */
suspend fun supportedLights(source: SignalSource): List<SignalId> =
    LIGHTS_TO_CHECK.filter { source.support(it) == Support.SUPPORTED }
