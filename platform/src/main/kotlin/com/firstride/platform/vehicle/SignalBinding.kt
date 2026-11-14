package com.firstride.platform.vehicle

import com.firstride.engine.signal.SignalValue
import com.firstride.engine.signal.Support

/** SDK 콜백 한 쌍. 값이 null이면 SDK가 값을 주지 않은 것이다. */
internal typealias ValueCallback = (SignalValue?) -> Unit
internal typealias ErrorCallback = (Exception) -> Unit

/**
 * 신호 하나를 SDK 호출에 잇는다. 코루틴·Flow·스레드는 [VehicleSignalSource]가 한 곳에서 맡고,
 * 여기에는 어느 SDK 메서드를 어느 영역(A)으로 부르고 값을 어떻게 토큰·숫자로 바꾸는지만 둔다.
 * 모든 람다는 SDK 디스패처에서 불린다.
 */
internal class SignalBinding<A : Any>(
    val support: (onResult: (Support) -> Unit, onError: ErrorCallback) -> Unit,
    /** 이 신호를 읽을 SDK 영역을 고른다([AreaResolver]). */
    val area: (onResult: (A) -> Unit, onError: ErrorCallback) -> Unit,
    val read: (area: A, onValue: ValueCallback, onError: ErrorCallback) -> Unit,
    /** 구독을 걸고, 해제하는 함수를 돌려준다. 구독 메서드가 없는 신호는 null(폴링). */
    val subscribe: ((area: A, onValue: ValueCallback, onError: ErrorCallback) -> () -> Unit)? = null,
    val range: ((onResult: (IntRange?) -> Unit, onError: ErrorCallback) -> Unit)? = null,
)
