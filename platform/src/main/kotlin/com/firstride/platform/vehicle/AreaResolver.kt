package com.firstride.platform.vehicle

import ai.pleos.playground.vehicle.api.model.area.Area

/** 우리가 읽으려는 자리가 이 신호의 지원 영역 목록에 없다. */
internal class AreaNotSupportedException(signal: String) : Exception("no supported area for $signal")

/**
 * 신호를 읽을 SDK 영역을 지원 여부 응답에서 고른다. 차량마다 영역을 묶는 방식이 다르다.
 * 에뮬레이터는 에어컨·순환을 1·2열 좌석 전체를 묶은 영역 하나로, 풍량을 1열 좌·우를 묶은 영역으로,
 * 온도는 좌석마다 따로 준다(실측 T6, 11-14). 그래서 영역을 고정하지 않고 운전석을 포함하는 영역을 찾는다.
 * 찾으면 기억하고, 실패하면 기억하지 않아 다음 호출에서 다시 묻는다.
 */
internal class AreaResolver<A : Area>(
    private val signal: String,
    private val capabilityAreas: (onResult: (Collection<A>) -> Unit, onError: ErrorCallback) -> Unit,
    private val matches: (A) -> Boolean,
) {
    @Volatile private var resolved: A? = null

    fun resolve(onArea: (A) -> Unit, onError: ErrorCallback) {
        resolved?.let { onArea(it); return }
        capabilityAreas({ areas ->
            val area = areas.firstOrNull(matches)
            if (area == null) onError(AreaNotSupportedException(signal))
            else { resolved = area; onArea(area) }
        }, onError)
    }
}
