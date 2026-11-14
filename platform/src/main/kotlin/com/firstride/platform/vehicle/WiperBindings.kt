package com.firstride.platform.vehicle

import ai.pleos.playground.vehicle.api.Wiper
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.WiperSwitch
import com.firstride.engine.signal.SignalId
import com.firstride.engine.signal.SignalValue
import com.firstride.engine.signal.SignalValue.Token
import com.firstride.engine.signal.Signals
import com.firstride.engine.signal.Support

/**
 * 앞유리 와이퍼의 단(`wiper.state`). 단은 스위치 값(`WiperSwitch`)에만 있고 구독 메서드가 없어 폴링한다.
 * 구독할 수 있는 `WiperState`는 켜짐/꺼짐뿐이라 Low ↔ High ↔ Auto 변화를 알 수 없다(실측 T6, 11-14).
 *
 * VC가 내는 네 값만 코스의 토큰으로 바꾼다: OFF, AUTO, CONTINUOUS_LEVEL_1 → LOW, CONTINUOUS_LEVEL_3 → HIGH.
 * 그 밖의 값(간헐, 미스트, 다른 연속 단)은 SDK 이름 그대로 낸다. 어떤 목표와도 맞지 않아 완료로도 꺼짐으로도 읽히지 않는다.
 * 차종마다 단 이름이 다를 수 있어 짐작해 LOW·HIGH로 바꾸지 않는다(POL-P-2).
 */
private fun WiperSwitch?.token(): SignalValue? = when (this) {
    null -> null
    WiperSwitch.OFF -> Token("OFF")
    WiperSwitch.AUTO -> Token("AUTO")
    WiperSwitch.CONTINUOUS_LEVEL_1 -> Token("LOW")
    WiperSwitch.CONTINUOUS_LEVEL_3 -> Token("HIGH")
    else -> Token(name)
}

internal fun wiperBindings(wiper: Wiper): Map<SignalId, SignalBinding<*>> {
    val matches: (WindowArea) -> Boolean = { WindowValue.FRONT_WINDSHIELD in it.values }
    val area = AreaResolver("wiper.state", { ok, err -> wiper.checkWiperSwitchCapabilities({ _, m -> ok(m.keys) }, err) }, matches)
    return mapOf(
        Signals.WIPER_STATE to SignalBinding(
            support = { ok, err ->
                wiper.checkWiperSwitchCapabilities({ c, m ->
                    ok(if (c.accessType in setOf(AccessType.READ, AccessType.READ_WRITE) && m.keys.any(matches)) Support.SUPPORTED else Support.UNSUPPORTED)
                }, err)
            },
            area = area::resolve,
            read = { a, ok, err -> wiper.getCurrentWiperSwitch(a, { ok(it.token()) }, err) },
        ),
    )
}
