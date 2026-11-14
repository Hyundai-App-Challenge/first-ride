package com.firstride.platform.vehicle

import ai.pleos.playground.vehicle.api.Door
import ai.pleos.playground.vehicle.api.TurnSignal
import ai.pleos.playground.vehicle.api.Window
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.listener.DoorLockStateListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WindowLockStateListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import com.firstride.engine.signal.SignalId
import com.firstride.engine.signal.SignalValue
import com.firstride.engine.signal.SignalValue.Number
import com.firstride.engine.signal.SignalValue.Token
import com.firstride.engine.signal.Signals
import com.firstride.engine.signal.Support

private val readable = setOf(AccessType.READ, AccessType.READ_WRITE)
private fun CapabilityConfig.support(hasArea: Boolean = true) =
    if (accessType in readable && hasArea) Support.SUPPORTED else Support.UNSUPPORTED

private fun Boolean?.onOff(): SignalValue? = this?.let { Token(if (it) "ON" else "OFF") }
private fun Boolean?.locked(): SignalValue? = this?.let { Token(if (it) "LOCKED" else "UNLOCKED") }
private fun TurnSignalValue?.token(): SignalValue? = this?.let { Token(it.name) }

/** 방향지시등(OFF/LEFT/RIGHT). 영역 인자 없음. */
internal fun turnSignalBindings(turn: TurnSignal): Map<SignalId, SignalBinding<*>> = mapOf(
    Signals.TURN_SIGNAL_STATE to SignalBinding<Unit>(
        support = { ok, err -> turn.checkTurnSignalStateCapabilities({ ok(it.support()) }, err) },
        area = { ok, _ -> ok(Unit) },
        read = { _, ok, err -> turn.getCurrentTurnSignalState({ ok(it.token()) }, err) },
        subscribe = { _, ok, err ->
            val l = object : TurnSignalStateListener {
                override fun onTurnSignalStateUpdated(p0: TurnSignalValue?) = ok(p0.token())
                override fun onFailed(p0: Exception) = err(p0)
            }
            turn.registerTurnSignalState(l)
            ({ turn.unregisterTurnSignalState(l) })
        },
    ),
)

/** 운전석 창문 위치(숫자, 범위는 지원 여부 응답)와 창문 잠금. 영역은 운전석 창문(1열 왼쪽)을 포함하는 것. */
internal fun windowBindings(window: Window): Map<SignalId, SignalBinding<*>> {
    val driver: (WindowArea) -> Boolean = { WindowValue.ROW_1_LEFT in it.values }
    val positionArea = AreaResolver("window.driverPosition", { ok, err -> window.checkWindowPositionCapabilities({ _, m -> ok(m.keys) }, err) }, driver)
    val lockArea = AreaResolver("window.lock", { ok, err -> window.checkWindowLockCapabilities({ _, a -> ok(a) }, err) }, driver)
    return mapOf(
        Signals.WINDOW_DRIVER_POSITION to SignalBinding(
            support = { ok, err -> window.checkWindowPositionCapabilities({ c, m -> ok(c.support(m.keys.any(driver))) }, err) },
            area = positionArea::resolve,
            read = { a, ok, err -> window.getCurrentWindowPosition(a, { ok(it?.let(::Number)) }, err) },
            subscribe = { a, ok, err ->
                val l = object : WindowPositionListener {
                    override fun onWindowPositionUpdated(p0: Int?) = ok(p0?.let(::Number))
                    override fun onFailed(p0: Exception) = err(p0)
                }
                window.registerWindowPosition(a, l)
                ({ window.unregisterWindowPosition(a, l) })
            },
            range = { ok, err ->
                window.checkWindowPositionCapabilities({ _, m ->
                    ok(m.entries.firstOrNull { driver(it.key) }?.value?.let { it.minValue..it.maxValue })
                }, err)
            },
        ),
        Signals.WINDOW_LOCK to SignalBinding(
            support = { ok, err -> window.checkWindowLockCapabilities({ c, a -> ok(c.support(a.any(driver))) }, err) },
            area = lockArea::resolve,
            read = { a, ok, err -> window.getCurrentWindowLockState(a, { ok(it.onOff()) }, err) },
            subscribe = { a, ok, err ->
                val l = object : WindowLockStateListener {
                    override fun onWindowLockStateUpdated(p0: Boolean?) = ok(p0.onOff())
                    override fun onFailed(p0: Exception) = err(p0)
                }
                window.registerWindowLockState(a, l)
                ({ window.unregisterWindowLockState(a, l) })
            },
        ),
    )
}

/** 운전석 문 잠금(LOCKED/UNLOCKED). */
internal fun doorBindings(door: Door): Map<SignalId, SignalBinding<*>> {
    val driver: (DoorArea) -> Boolean = { DoorValue.ROW_1_LEFT in it.values }
    val area = AreaResolver("door.lock", { ok, err -> door.checkDoorLockCapabilities({ _, a -> ok(a) }, err) }, driver)
    return mapOf(
        Signals.DOOR_LOCK to SignalBinding(
            support = { ok, err -> door.checkDoorLockCapabilities({ c, a -> ok(c.support(a.any(driver))) }, err) },
            area = area::resolve,
            read = { a, ok, err -> door.getDoorLockState(a, { ok(it.locked()) }, err) },
            subscribe = { a, ok, err ->
                val l = object : DoorLockStateListener {
                    override fun onDoorLockStateUpdated(p0: Boolean?) = ok(p0.locked())
                    override fun onFailed(p0: Exception) = err(p0)
                }
                door.registerDoorLockState(a, l)
                ({ door.unregisterDoorLockState(a, l) })
            },
        ),
    )
}
