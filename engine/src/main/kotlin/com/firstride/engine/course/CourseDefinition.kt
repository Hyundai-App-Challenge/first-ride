package com.firstride.engine.course

import com.firstride.engine.signal.SignalId

enum class Kind { ACTION, HOLD }

data class ConditionDef(val signal: SignalId, val target: Target, val label: String, val kind: Kind)

data class MisoperationDef(val id: String, val signal: SignalId, val target: Target, val correction: String)

data class StepDefinition(
    val id: String,
    val action: List<ConditionDef>,
    val hold: List<ConditionDef>,
    val misoperations: List<MisoperationDef>,
    val doneText: String,
)

/** 후속 조치(FR-16). 조건 하나가 곧 조치의 목표다. */
data class FollowUpDef(val id: String, val condition: ConditionDef)

/** 차종 공통 정의 (courses/CRS-xx.json). */
data class CourseDefinition(
    val id: String,
    val name: String,
    val estimatedSeconds: Int,
    val steps: List<StepDefinition>,
    /** 코스 소개(SCR-03)의 한 줄 설명. 왜 알아야 하는지. */
    val summary: String = "",
    val followUps: List<FollowUpDef> = emptyList(),
    val exhaustedText: String? = null,
)

/** 차종별 문구와 위치 안내 자산 (models/<차종>/CRS-xx.json). */
data class StepText(
    val instruction: String,
    val instructionAtLimit: String? = null,
    val locatorText: String,
    val locatorAsset: String,
    val conditionLocators: Map<SignalId, String> = emptyMap(),
    val misoperationLocators: Map<String, String> = emptyMap(),
)

data class ModelCourseText(val courseId: String, val steps: Map<String, StepText>)
