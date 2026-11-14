package com.firstride.engine.signal

import com.firstride.engine.signal.SignalValue.Token

/** 신호 ID와 값 토큰 목록(기술 설계 4-3절). 값 이름은 실측 T6으로 확정하며, 달라지면 어댑터가 흡수한다. */
object Signals {
    val HVAC_FRONT_DEFROSTER = SignalId("hvac.frontDefroster")
    val HVAC_REAR_DEFROSTER = SignalId("hvac.rearDefroster")
    val HVAC_AC = SignalId("hvac.ac")
    val HVAC_RECIRCULATION = SignalId("hvac.recirculation")
    val HVAC_FAN_SPEED = SignalId("hvac.fanSpeed")            // 숫자
    val HVAC_FAN_DIRECTION = SignalId("hvac.fanDirection")    // 폴링
    val HVAC_MAX_DEFROST = SignalId("hvac.maxDefrost")
    val HVAC_TEMPERATURE = SignalId("hvac.temperature")       // 숫자, 0.1℃ 단위 (22.5℃ → 225)
    val LIGHT_LOW_BEAM = SignalId("light.lowBeam")
    val LIGHT_TAIL_LAMP = SignalId("light.tailLamp")
    val LIGHT_CABIN = SignalId("light.cabin")
    val WIPER_STATE = SignalId("wiper.state")
    val TURN_SIGNAL_STATE = SignalId("turnSignal.state")
    val WINDOW_DRIVER_POSITION = SignalId("window.driverPosition")  // 숫자
    val WINDOW_LOCK = SignalId("window.lock")
    val DOOR_LOCK = SignalId("door.lock")

    private val onOff = setOf("ON", "OFF")

    /** 토큰 신호가 가질 수 있는 값. 숫자 신호는 키가 없다. `hvac.fanDirection`의 값은 실측 T5 전까지 열어 둔다(null). */
    val tokens: Map<SignalId, Set<String>?> = mapOf(
        HVAC_FRONT_DEFROSTER to onOff,
        HVAC_REAR_DEFROSTER to onOff,
        HVAC_AC to onOff,
        HVAC_RECIRCULATION to setOf("FRESH", "RECIRCULATE"),
        HVAC_FAN_DIRECTION to null,
        HVAC_MAX_DEFROST to onOff,
        LIGHT_LOW_BEAM to onOff,
        LIGHT_TAIL_LAMP to onOff,
        LIGHT_CABIN to onOff,
        WIPER_STATE to setOf("OFF", "AUTO", "LOW", "HIGH"),
        TURN_SIGNAL_STATE to setOf("OFF", "LEFT", "RIGHT"),
        WINDOW_LOCK to onOff,
        DOOR_LOCK to setOf("LOCKED", "UNLOCKED"),
    )

    val numeric: Set<SignalId> = setOf(HVAC_FAN_SPEED, HVAC_TEMPERATURE, WINDOW_DRIVER_POSITION)

    val all: Set<SignalId> = tokens.keys + numeric

    fun token(name: String): SignalValue = Token(name)
}
