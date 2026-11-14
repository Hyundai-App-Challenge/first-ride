package com.firstride.engine.course

import com.firstride.engine.signal.SignalValue

enum class Direction { UP, DOWN }

sealed interface Target {
    /** [max]는 AtMax에만 쓴다. SignalSource.range의 상한이며, 모르면 null(→ 충족 안 됨). */
    fun matches(current: SignalValue, initial: SignalValue?, max: Int? = null): Boolean

    data class Equals(val value: SignalValue) : Target {
        override fun matches(current: SignalValue, initial: SignalValue?, max: Int?) = current == value
    }

    /** 시작 값보다 커짐/작아짐 (POL-J-10). 시작 값을 모르면 충족 안 됨. */
    data class Relative(val direction: Direction) : Target {
        override fun matches(current: SignalValue, initial: SignalValue?, max: Int?): Boolean {
            val c = (current as? SignalValue.Number)?.value ?: return false
            val i = (initial as? SignalValue.Number)?.value ?: return false
            return if (direction == Direction.UP) c > i else c < i
        }
    }

    /** 지원 범위의 최댓값 (후속 조치용). */
    data object AtMax : Target {
        override fun matches(current: SignalValue, initial: SignalValue?, max: Int?) =
            max != null && (current as? SignalValue.Number)?.value == max
    }
}
