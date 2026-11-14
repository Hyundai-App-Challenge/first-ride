package com.firstride.platform.vehicle

import com.firstride.engine.signal.FailReason
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * SDK 예외를 [FailReason]으로 바꾼다. SDK의 예외 클래스는 난독화되어 있어(버전마다 이름이 바뀔 수 있다)
 * 인자 없는 메서드 중 SDK의 `ErrorCode` enum을 돌려주는 것을 찾아 그 이름을 쓴다. 못 찾으면 UNKNOWN.
 * 주행 제한(ERROR_DRIVING_RESTRICTED)은 FailReason에 값이 없어 UNKNOWN이다. 주행 제한 자체는 DrivingGate가 다룬다.
 */
internal fun Throwable.toFailReason(): FailReason = if (this is AreaNotSupportedException) FailReason.UNSUPPORTED else when (errorCodeName()) {
    "ERROR_SECURITY_PERMISSION" -> FailReason.PERMISSION
    "ERROR_NOT_SUPPORTED_PROPERTY", "ERROR_NOT_SUPPORTED_AREA", "ERROR_NOT_SUPPORTED_VALUE_TYPE" -> FailReason.UNSUPPORTED
    "ERROR_SERVICE_CONNECTION_FAILURE" -> FailReason.CONNECTION
    "ERROR_EMPTY_RESULT" -> FailReason.EMPTY
    else -> if (this is SecurityException) FailReason.PERMISSION else FailReason.UNKNOWN
}

private val errorCodeGetters = ConcurrentHashMap<Class<*>, Method>()

internal fun Throwable.errorCodeName(): String? = runCatching {
    val getter = errorCodeGetters[javaClass] ?: javaClass.methods
        .firstOrNull { it.parameterCount == 0 && it.returnType.isEnum && it.returnType.simpleName == "ErrorCode" }
        ?.also { errorCodeGetters[javaClass] = it }
    (getter?.invoke(this) as? Enum<*>)?.name
}.getOrNull()
