package com.firstride.engine.signal

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update

/** 테스트와 debug 개발자 패널용 신호원. SignalSource의 계약 5개를 지킨다. */
class FakeSignalSource : SignalSource {
    private val readings = MutableStateFlow<Map<SignalId, Reading>>(emptyMap())
    private val supports = HashMap<SignalId, Support>()
    private val ranges = HashMap<SignalId, IntRange?>()
    private val polled = HashSet<SignalId>()
    private val active = AtomicInteger()

    /** 테스트에서 구독 누수를 확인한다. */
    val activeSubscriptions: Int get() = active.get()

    fun set(id: SignalId, value: SignalValue) = readings.update { it + (id to Reading.Value(value)) }
    fun fail(id: SignalId, reason: FailReason) = readings.update { it + (id to Reading.Failed(reason)) }
    fun setSupport(id: SignalId, support: Support) { supports[id] = support }
    fun setRange(id: SignalId, range: IntRange?) { ranges[id] = range }
    fun setPolled(id: SignalId, isPolled: Boolean) { if (isPolled) polled += id else polled -= id }

    private fun current(id: SignalId): Reading = readings.value[id] ?: Reading.Failed(FailReason.EMPTY)

    override suspend fun support(id: SignalId): Support = supports[id] ?: Support.SUPPORTED
    override suspend fun read(id: SignalId): Reading = current(id)
    override fun isPolled(id: SignalId): Boolean = id in polled
    override suspend fun range(id: SignalId): IntRange? = ranges[id]

    override fun observe(id: SignalId): Flow<Reading> =
        readings.map { it[id] ?: Reading.Failed(FailReason.EMPTY) }
            .distinctUntilChanged()
            .onStart { active.incrementAndGet() }
            .onCompletion { active.decrementAndGet() }
}
