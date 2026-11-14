package com.firstride.platform.vehicle

import ai.pleos.playground.vehicle.api.Light
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.listener.LightStateListener
import com.firstride.engine.signal.SignalId
import com.firstride.engine.signal.SignalValue
import com.firstride.engine.signal.SignalValue.Token
import com.firstride.engine.signal.Signals
import com.firstride.engine.signal.Support

/**
 * 등화 상태. SDK 값은 ON/OFF/DAYTIME_RUNNING이다. 주간주행등은 켜짐도 꺼짐도 아니므로 그대로 `DAYTIME_RUNNING` 토큰으로 낸다.
 * 등화 신호는 영역 인자가 없다.
 */
private fun LightState?.token(): SignalValue? = this?.let { Token(it.name) }

private fun lightBinding(
    capability: (onResult: (AccessType) -> Unit, onError: ErrorCallback) -> Unit,
    read: (onValue: (LightState?) -> Unit, onError: ErrorCallback) -> Unit,
    register: (LightStateListener) -> Unit,
    unregister: (LightStateListener) -> Unit,
) = SignalBinding<Unit>(
    support = { ok, err -> capability({ ok(if (it in setOf(AccessType.READ, AccessType.READ_WRITE)) Support.SUPPORTED else Support.UNSUPPORTED) }, err) },
    area = { ok, _ -> ok(Unit) },
    read = { _, ok, err -> read({ ok(it.token()) }, err) },
    subscribe = { _, ok, err ->
        val l = object : LightStateListener {
            override fun onLightStateUpdated(p0: LightState?) = ok(p0.token())
            override fun onFailed(p0: Exception) = err(p0)
        }
        register(l)
        ({ unregister(l) })
    },
)

internal fun lightBindings(light: Light): Map<SignalId, SignalBinding<*>> = mapOf(
    Signals.LIGHT_LOW_BEAM to lightBinding(
        capability = { ok, err -> light.checkLowBeamLightStateCapabilities({ c, _ -> ok(c.accessType) }, err) },
        read = { ok, err -> light.getCurrentLowBeamLightState(ok, err) },
        register = light::registerLowBeamLightState,
        unregister = light::unregisterLowBeamLightState,
    ),
    Signals.LIGHT_TAIL_LAMP to lightBinding(
        capability = { ok, err -> light.checkTailLampLightStateCapabilities({ c, _ -> ok(c.accessType) }, err) },
        read = { ok, err -> light.getCurrentTailLampLightState(ok, err) },
        register = light::registerTailLampLightState,
        unregister = light::unregisterTailLampLightState,
    ),
    Signals.LIGHT_CABIN to lightBinding(
        capability = { ok, err -> light.checkCabinLightStateCapabilities({ c, _ -> ok(c.accessType) }, err) },
        read = { ok, err -> light.getCurrentCabinLightState(ok, err) },
        register = light::registerCabinLightState,
        unregister = light::unregisterCabinLightState,
    ),
)
