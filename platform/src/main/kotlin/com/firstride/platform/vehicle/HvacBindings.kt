package com.firstride.platform.vehicle

import ai.pleos.playground.vehicle.api.HVAC
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.FanDirectionType
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.HvacAcStateListener
import ai.pleos.playground.vehicle.listener.HvacDefrosterStateListener
import ai.pleos.playground.vehicle.listener.HvacFanSpeedListener
import ai.pleos.playground.vehicle.listener.HvacMaxDefrostStateListener
import ai.pleos.playground.vehicle.listener.HvacRecirculationStateListener
import ai.pleos.playground.vehicle.listener.HvacTemperatureListener
import com.firstride.engine.signal.SignalId
import com.firstride.engine.signal.SignalValue
import com.firstride.engine.signal.SignalValue.Number
import com.firstride.engine.signal.SignalValue.Token
import com.firstride.engine.signal.Signals
import com.firstride.engine.signal.Support
import kotlin.math.roundToInt

private val ON = Token("ON")
private val OFF = Token("OFF")
private fun Boolean?.onOff(): SignalValue? = this?.let { if (it) ON else OFF }

/** 내기 순환이 켜져 있으면(true) RECIRCULATE, 꺼져 있으면 외기 유입(FRESH). VC의 Air Recirculation과 같은 뜻. */
private fun Boolean?.recirculation(): SignalValue? = this?.let { if (it) Token("RECIRCULATE") else Token("FRESH") }

/** 0.1℃ 단위 정수(22.5℃ → 225). 기술 설계 4-3절. 숫자가 아니면(NaN) 값 없음. */
private fun Float?.tenths(): Int? = this?.takeIf { it.isFinite() }?.let { (it * 10).roundToInt() }

private fun FanDirectionType?.token(): SignalValue? = this?.takeIf { it != FanDirectionType.UNKNOWN }?.let { Token(it.name) }

private val readable = setOf(AccessType.READ, AccessType.READ_WRITE)

private fun SeatArea.hasDriver() = SeatValue.ROW_1_LEFT in values

/** 영역을 쓰는 신호 하나의 바인딩. 지원 여부는 읽을 수 있고 운전석(또는 해당 유리)을 포함하는 영역이 있을 때만 SUPPORTED. */
private fun <A : ai.pleos.playground.vehicle.api.model.area.Area> areaBinding(
    area: AreaResolver<A>,
    capability: (onResult: (CapabilityConfig, Collection<A>) -> Unit, onError: ErrorCallback) -> Unit,
    read: (A, ValueCallback, ErrorCallback) -> Unit,
    register: ((A, ValueCallback, ErrorCallback) -> () -> Unit)?,
    range: ((onResult: (IntRange?) -> Unit, onError: ErrorCallback) -> Unit)? = null,
    matches: (A) -> Boolean,
) = SignalBinding(
    support = { ok, err ->
        capability({ c, areas -> ok(if (c.accessType in readable && areas.any(matches)) Support.SUPPORTED else Support.UNSUPPORTED) }, err)
    },
    area = area::resolve,
    read = read,
    subscribe = register,
    range = range,
)

internal fun hvacBindings(hvac: HVAC): Map<SignalId, SignalBinding<*>> {
    fun seatResolver(name: String, capability: (onResult: (Collection<SeatArea>) -> Unit, onError: ErrorCallback) -> Unit) =
        AreaResolver(name, capability) { it.hasDriver() }

    val acCap: (onResult: (CapabilityConfig, Collection<SeatArea>) -> Unit, onError: ErrorCallback) -> Unit =
        { ok, err -> hvac.checkHvacAcCapabilities({ c, a -> ok(c, a) }, err) }
    val recircCap: (onResult: (CapabilityConfig, Collection<SeatArea>) -> Unit, onError: ErrorCallback) -> Unit =
        { ok, err -> hvac.checkHvacRecirculationCapabilities({ c, a -> ok(c, a) }, err) }
    val maxDefrostCap: (onResult: (CapabilityConfig, Collection<SeatArea>) -> Unit, onError: ErrorCallback) -> Unit =
        { ok, err -> hvac.checkHvacMaxDefrostCapabilities({ c, a -> ok(c, a) }, err) }
    val fanSpeedCap: (onResult: (CapabilityConfig, Collection<SeatArea>) -> Unit, onError: ErrorCallback) -> Unit =
        { ok, err -> hvac.checkHvacFanSpeedCapabilities({ c, m -> ok(c, m.keys) }, err) }
    val fanDirectionCap: (onResult: (CapabilityConfig, Collection<SeatArea>) -> Unit, onError: ErrorCallback) -> Unit =
        { ok, err -> hvac.checkHvacFanDirectionCapabilities({ c, m -> ok(c, m.keys) }, err) }
    val temperatureCap: (onResult: (CapabilityConfig, Collection<SeatArea>) -> Unit, onError: ErrorCallback) -> Unit =
        { ok, err -> hvac.checkHvacTemperatureCapabilities({ c, m -> ok(c, m.keys) }, err) }

    return mapOf(
        Signals.HVAC_FRONT_DEFROSTER to defroster(hvac, WindowValue.FRONT_WINDSHIELD),
        Signals.HVAC_REAR_DEFROSTER to defroster(hvac, WindowValue.REAR_WINDSHIELD),
        Signals.HVAC_AC to areaBinding(
            area = seatResolver("hvac.ac") { ok, err -> acCap({ _, a -> ok(a) }, err) },
            capability = acCap,
            read = { a, ok, err -> hvac.getHvacAcState(a, { ok(it.onOff()) }, err) },
            register = { a, ok, err ->
                val l = object : HvacAcStateListener {
                    override fun onHvacAcStateUpdated(p0: Boolean?) = ok(p0.onOff())
                    override fun onFailed(p0: Exception) = err(p0)
                }
                hvac.registerHvacAcState(a, l)
                ({ hvac.unregisterHvacAcState(a, l) })
            },
            matches = { it.hasDriver() },
        ),
        Signals.HVAC_RECIRCULATION to areaBinding(
            area = seatResolver("hvac.recirculation") { ok, err -> recircCap({ _, a -> ok(a) }, err) },
            capability = recircCap,
            read = { a, ok, err -> hvac.getHvacRecirculationState(a, { ok(it.recirculation()) }, err) },
            register = { a, ok, err ->
                // SDK는 내기 순환 구독에도 자동 내기 순환과 같은 이름의 리스너 메서드를 쓴다.
                val l = object : HvacRecirculationStateListener {
                    override fun onHvacAutoRecirculationStateUpdated(p0: Boolean?) = ok(p0.recirculation())
                    override fun onFailed(p0: Exception) = err(p0)
                }
                hvac.registerHvacRecirculationState(a, l)
                ({ hvac.unregisterHvacRecirculationState(a, l) })
            },
            matches = { it.hasDriver() },
        ),
        Signals.HVAC_MAX_DEFROST to areaBinding(
            area = seatResolver("hvac.maxDefrost") { ok, err -> maxDefrostCap({ _, a -> ok(a) }, err) },
            capability = maxDefrostCap,
            read = { a, ok, err -> hvac.getHvacMaxDefrostState(a, { ok(it.onOff()) }, err) },
            register = { a, ok, err ->
                val l = object : HvacMaxDefrostStateListener {
                    override fun onHvacMaxDefrostStateUpdated(p0: Boolean?) = ok(p0.onOff())
                    override fun onFailed(p0: Exception) = err(p0)
                }
                hvac.registerHvacMaxDefrostState(a, l)
                ({ hvac.unregisterHvacMaxDefrostState(a, l) })
            },
            matches = { it.hasDriver() },
        ),
        Signals.HVAC_FAN_SPEED to areaBinding(
            area = seatResolver("hvac.fanSpeed") { ok, err -> fanSpeedCap({ _, a -> ok(a) }, err) },
            capability = fanSpeedCap,
            read = { a, ok, err -> hvac.getHvacFanSpeed(a, { ok(it?.let(::Number)) }, err) },
            register = { a, ok, err ->
                val l = object : HvacFanSpeedListener {
                    override fun onHvacFanSpeedUpdated(p0: Int?) = ok(p0?.let(::Number))
                    override fun onFailed(p0: Exception) = err(p0)
                }
                hvac.registerHvacFanSpeed(a, l)
                ({ hvac.unregisterHvacFanSpeed(a, l) })
            },
            range = { ok, err ->
                hvac.checkHvacFanSpeedCapabilities({ _, m ->
                    ok(m.entries.firstOrNull { it.key.hasDriver() }?.value?.let { it.minValue..it.maxValue })
                }, err)
            },
            matches = { it.hasDriver() },
        ),
        // 풍향은 구독 메서드가 없어 폴링한다(기술 설계 10-2절).
        Signals.HVAC_FAN_DIRECTION to areaBinding(
            area = seatResolver("hvac.fanDirection") { ok, err -> fanDirectionCap({ _, a -> ok(a) }, err) },
            capability = fanDirectionCap,
            read = { a, ok, err -> hvac.getHvacFanDirection(a, { ok(it.token()) }, err) },
            register = null,
            matches = { it.hasDriver() },
        ),
        Signals.HVAC_TEMPERATURE to areaBinding(
            area = seatResolver("hvac.temperature") { ok, err -> temperatureCap({ _, a -> ok(a) }, err) },
            capability = temperatureCap,
            read = { a, ok, err -> hvac.getHvacTemperature(a, TemperatureUnit.CELSIUS, { ok(it.tenths()?.let(::Number)) }, err) },
            register = { a, ok, err ->
                val l = object : HvacTemperatureListener {
                    override fun onHvacTemperatureUpdated(p0: Float?) = ok(p0.tenths()?.let(::Number))
                    override fun onFailed(p0: Exception) = err(p0)
                }
                hvac.registerHvacTemperature(a, l)
                ({ hvac.unregisterHvacTemperature(a, l) })
            },
            range = { ok, err ->
                hvac.checkHvacTemperatureCapabilities({ _, m ->
                    ok(m.entries.firstOrNull { it.key.hasDriver() }?.value?.let { b -> val lo = b.minValue.tenths(); val hi = b.maxValue.tenths(); if (lo != null && hi != null) lo..hi else null })
                }, err)
            },
            matches = { it.hasDriver() },
        ),
    )
}

private fun defroster(hvac: HVAC, glass: WindowValue): SignalBinding<WindowArea> {
    val cap: (onResult: (CapabilityConfig, Collection<WindowArea>) -> Unit, onError: ErrorCallback) -> Unit =
        { ok, err -> hvac.checkHvacDefrosterCapabilities({ c, a -> ok(c, a) }, err) }
    val matches: (WindowArea) -> Boolean = { glass in it.values }
    return areaBinding(
        area = AreaResolver("hvac.defroster.$glass", { ok, err -> cap({ _, a -> ok(a) }, err) }, matches),
        capability = cap,
        read = { a, ok, err -> hvac.getHvacDefrosterState(a, { ok(it.onOff()) }, err) },
        register = { a, ok, err ->
            val l = object : HvacDefrosterStateListener {
                override fun onHvacDefrosterStateUpdated(p0: Boolean?) = ok(p0.onOff())
                override fun onFailed(p0: Exception) = err(p0)
            }
            hvac.registerHvacDefrosterState(a, l)
            ({ hvac.unregisterHvacDefrosterState(a, l) })
        },
        matches = matches,
    )
}
