package com.firstride.platform.vehicle

import com.firstride.engine.signal.FailReason
import kotlin.test.Test
import kotlin.test.assertEquals

class VehicleErrorsTest {
    @Suppress("unused") enum class ErrorCode { ERROR_SECURITY_PERMISSION, ERROR_NOT_SUPPORTED_AREA, ERROR_SERVICE_CONNECTION_FAILURE, ERROR_DRIVING_RESTRICTED, ERROR_EMPTY_RESULT }

    /** SDK의 난독화된 예외처럼 이름 모를 메서드가 ErrorCode를 돌려준다. */
    class ObfuscatedException(private val code: ErrorCode) : Exception() { fun a(): ErrorCode = code }

    @Test
    fun `SDK 오류 코드를 FailReason으로 바꾼다`() {
        assertEquals(FailReason.PERMISSION, ObfuscatedException(ErrorCode.ERROR_SECURITY_PERMISSION).toFailReason())
        assertEquals(FailReason.UNSUPPORTED, ObfuscatedException(ErrorCode.ERROR_NOT_SUPPORTED_AREA).toFailReason())
        assertEquals(FailReason.CONNECTION, ObfuscatedException(ErrorCode.ERROR_SERVICE_CONNECTION_FAILURE).toFailReason())
        assertEquals(FailReason.EMPTY, ObfuscatedException(ErrorCode.ERROR_EMPTY_RESULT).toFailReason())
    }

    @Test
    fun `주행 제한 오류는 FailReason에 값이 없어 UNKNOWN이다`() {
        assertEquals(FailReason.UNKNOWN, ObfuscatedException(ErrorCode.ERROR_DRIVING_RESTRICTED).toFailReason())
    }

    @Test
    fun `퍼미션 없이 SDK가 던지는 SecurityException은 PERMISSION이다`() {
        assertEquals(FailReason.PERMISSION, SecurityException("getHvacAcState need Permission").toFailReason())
        assertEquals(FailReason.UNSUPPORTED, AreaNotSupportedException("hvac.ac").toFailReason())
        assertEquals(FailReason.UNKNOWN, Exception("x").toFailReason())
    }
}
