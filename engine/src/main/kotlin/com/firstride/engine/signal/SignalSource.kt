package com.firstride.engine.signal

import kotlinx.coroutines.flow.Flow

@JvmInline value class SignalId(val key: String)

sealed interface SignalValue {
    data class Token(val name: String) : SignalValue
    data class Number(val value: Int) : SignalValue
}

enum class FailReason { PERMISSION, UNSUPPORTED, CONNECTION, EMPTY, TIMEOUT, UNKNOWN }

sealed interface Reading {
    data class Value(val value: SignalValue) : Reading
    data class Failed(val reason: FailReason) : Reading
}

enum class Support { SUPPORTED, UNSUPPORTED, DENIED, UNKNOWN }

interface SignalSource {
    /** 지원 여부. checkXxxCapabilities의 AccessType이 READ 또는 READ_WRITE면 SUPPORTED. */
    suspend fun support(id: SignalId): Support

    /** 현재 값을 한 번 읽는다. 시간 제한은 호출하는 쪽이 건다. */
    suspend fun read(id: SignalId): Reading

    /**
     * 값의 변화를 흘려보낸다. 계약:
     *  1. 수집을 시작하면 현재 값을 먼저 한 번 내보낸다.
     *  2. 같은 값은 연달아 내보내지 않는다.
     *  3. 수집을 취소하면 구독을 해제한다(unregister). 남는 리스너가 없다.
     *  4. 구독이 없는 신호는 내부에서 폴링한다. 호출하는 쪽은 차이를 모른다.
     *  5. 실패는 예외가 아니라 Reading.Failed로 내보낸다.
     */
    fun observe(id: SignalId): Flow<Reading>

    /** 폴링으로 읽는 신호인지. 화면의 "확인 중" 표시에만 쓴다. */
    fun isPolled(id: SignalId): Boolean

    /** 숫자 신호의 지원 범위(checkXxxCapabilities의 AreaBounds). 범위를 모르면 null. */
    suspend fun range(id: SignalId): IntRange?
}
