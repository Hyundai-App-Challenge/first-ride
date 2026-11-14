package com.firstride.engine.check

import com.firstride.engine.signal.FailReason
import com.firstride.engine.signal.FakeSignalSource
import com.firstride.engine.signal.Reading
import com.firstride.engine.signal.SignalValue.Token
import com.firstride.engine.signal.Signals
import com.firstride.engine.signal.Support
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class LightsCheckTest {
    private fun v(name: String) = Reading.Value(Token(name))

    @Test fun `AC-10-1 켜진 등이 있으면 SOME_ON이고 어느 등인지 알 수 있다`() {
        val c = checkLights(mapOf(Signals.LIGHT_LOW_BEAM to v("OFF"), Signals.LIGHT_TAIL_LAMP to v("OFF"), Signals.LIGHT_CABIN to v("ON")))
        assertEquals(LightsOutcome.SOME_ON, c.outcome)
        assertEquals(listOf(Signals.LIGHT_CABIN), c.on)
    }

    @Test fun `AC-10-2 읽은 등이 모두 꺼져 있으면 ALL_READABLE_OFF다`() {
        val c = checkLights(mapOf(Signals.LIGHT_LOW_BEAM to v("OFF"), Signals.LIGHT_TAIL_LAMP to v("OFF"), Signals.LIGHT_CABIN to v("OFF")))
        assertEquals(LightsOutcome.ALL_READABLE_OFF, c.outcome)
        assertEquals(emptyList(), c.unknown)
    }

    @Test fun `AC-10-5 POL-W-7 읽지 못한 등은 꺼짐이 아니라 확인 불가이고 모든 등이 꺼졌다고 하지 않는다`() {
        val c = checkLights(mapOf(
            Signals.LIGHT_LOW_BEAM to v("OFF"),
            Signals.LIGHT_TAIL_LAMP to Reading.Failed(FailReason.TIMEOUT),
            Signals.LIGHT_CABIN to null,
        ))
        assertEquals(LightsOutcome.ALL_READABLE_OFF, c.outcome)   // 화면은 확인 불가인 등을 따로 보이므로 "모두 꺼짐"으로 단정하지 않는다
        assertEquals(listOf(Signals.LIGHT_TAIL_LAMP, Signals.LIGHT_CABIN), c.unknown)
    }

    @Test fun `AC-10-5 하나도 읽지 못하면 NONE_READABLE이다`() {
        val c = checkLights(mapOf(Signals.LIGHT_LOW_BEAM to Reading.Failed(FailReason.CONNECTION), Signals.LIGHT_CABIN to null))
        assertEquals(LightsOutcome.NONE_READABLE, c.outcome)
    }

    @Test fun `AC-10-5 켜진 등이 있으면 다른 등을 읽지 못해도 SOME_ON이다`() {
        val c = checkLights(mapOf(Signals.LIGHT_LOW_BEAM to v("ON"), Signals.LIGHT_CABIN to Reading.Failed(FailReason.EMPTY)))
        assertEquals(LightsOutcome.SOME_ON, c.outcome)
        assertEquals(listOf(Signals.LIGHT_CABIN), c.unknown)
    }

    @Test fun `주간주행등 같은 ON OFF가 아닌 값은 꺼짐이 아니라 확인 불가다`() {
        assertEquals(LampState.UNKNOWN, lampState(v("DAYTIME_RUNNING")))
        assertEquals(LampState.UNKNOWN, lampState(null))
        assertEquals(LampState.ON, lampState(v("ON")))
        assertEquals(LampState.OFF, lampState(v("OFF")))
    }

    @Test fun `AC-10-6 미지원이거나 권한이 없는 등은 대상에서 빠진다`() = runTest {
        val src = FakeSignalSource().apply {
            setSupport(Signals.LIGHT_LOW_BEAM, Support.UNSUPPORTED)
            setSupport(Signals.LIGHT_TAIL_LAMP, Support.DENIED)
        }
        assertEquals(listOf(Signals.LIGHT_CABIN), supportedLights(src))
        src.setSupport(Signals.LIGHT_CABIN, Support.UNSUPPORTED)
        assertEquals(emptyList(), supportedLights(src))   // 홈의 진입 버튼을 숨긴다
    }
}
