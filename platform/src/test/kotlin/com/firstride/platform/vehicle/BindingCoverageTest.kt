package com.firstride.platform.vehicle

import ai.pleos.playground.vehicle.api.Door
import ai.pleos.playground.vehicle.api.HVAC
import ai.pleos.playground.vehicle.api.Light
import ai.pleos.playground.vehicle.api.TurnSignal
import ai.pleos.playground.vehicle.api.Window
import ai.pleos.playground.vehicle.api.Wiper
import com.firstride.engine.signal.Signals
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

/** 4-3절의 신호마다 어댑터가 하나씩 있다. 신호를 더하고 어댑터를 빠뜨리면 여기서 걸린다. */
class BindingCoverageTest {
    private inline fun <reified T> unused(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, _, _ -> error("not called") } as T

    @Test
    fun `모든 신호에 바인딩이 있고 겹치지 않는다`() {
        val parts = listOf(
            hvacBindings(unused<HVAC>()),
            lightBindings(unused<Light>()),
            wiperBindings(unused<Wiper>()),
            turnSignalBindings(unused<TurnSignal>()),
            windowBindings(unused<Window>()),
            doorBindings(unused<Door>()),
        )
        assertEquals(Signals.all, parts.flatMap { it.keys }.toSet())
        assertEquals(parts.sumOf { it.size }, Signals.all.size)
    }
}
