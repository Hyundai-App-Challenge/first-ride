package com.firstride.engine.judge

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** 판정의 시간 값(정책서 12절). 모두 실측 뒤 조정한다. */
data class JudgeConfig(
    val precheckTimeout: Duration = 3.seconds,   // POL-J-19
    val hintDelay: Duration = 15.seconds,        // POL-V-5
    val autoAdvance: Duration = 2.seconds,       // AC-04-4
)
