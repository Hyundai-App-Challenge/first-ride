package com.firstride.engine.course

import com.firstride.engine.signal.SignalId
import com.firstride.engine.signal.SignalValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** 정의 파일이 형식에 맞지 않을 때. 조용히 무시하지 않는다(AC-13-2). */
class CourseFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

object CourseParser {
    private val json = Json { ignoreUnknownKeys = false }

    fun parseCourse(text: String): CourseDefinition = parse { json.decodeFromString<CourseDto>(text).toDomain() }

    fun parseModelText(text: String): ModelCourseText = parse { json.decodeFromString<ModelDto>(text).toDomain() }

    private fun <T> parse(block: () -> T): T = try {
        block()
    } catch (e: SerializationException) {
        throw CourseFormatException(e.message ?: "형식 오류", e)
    } catch (e: IllegalArgumentException) {
        throw CourseFormatException(e.message ?: "형식 오류", e)
    }

    @Serializable private class CondDto(
        val signal: String,
        val equals: JsonPrimitive? = null,
        val atMax: Boolean? = null,
        val relative: String? = null,
        val label: String = "",
    )

    @Serializable private class MisoDto(val id: String, val `when`: CondDto, val correction: String)

    @Serializable private class StepDto(
        val id: String,
        val action: List<CondDto>,
        val hold: List<CondDto> = emptyList(),
        val misoperations: List<MisoDto> = emptyList(),
        val doneText: String,
    )

    @Serializable private class FollowUpDto(val id: String, val condition: CondDto)

    @Serializable private class CourseDto(
        val id: String,
        val name: String,
        val estimatedSeconds: Int,
        val steps: List<StepDto>,
        val followUps: List<FollowUpDto> = emptyList(),
        val exhaustedText: String? = null,
    )

    @Serializable private class StepTextDto(
        val instruction: String,
        val instructionAtLimit: String? = null,
        val locatorText: String,
        val locatorAsset: String,
        val conditionLocators: Map<String, String> = emptyMap(),
        val misoperationLocators: Map<String, String> = emptyMap(),
    )

    @Serializable private class ModelDto(val courseId: String, val steps: Map<String, StepTextDto>)

    private fun CondDto.target(): Target {
        val given = listOfNotNull(
            equals?.let { "equals" }, atMax?.let { "atMax" }, relative?.let { "relative" },
        )
        require(given.size == 1) { "'$signal' 조건에는 equals, atMax, relative 중 하나만 있어야 한다: $given" }
        return when {
            equals != null -> Target.Equals(
                if (equals.isString) SignalValue.Token(equals.content)
                else SignalValue.Number(requireNotNull(equals.intOrNull) { "'$signal'의 equals가 정수가 아니다" }),
            )
            atMax != null -> {
                require(atMax) { "'$signal'의 atMax는 true만 쓴다" }
                Target.AtMax
            }
            else -> Target.Relative(Direction.valueOf(relative!!.uppercase()))
        }
    }

    private fun CondDto.toDef(kind: Kind) = ConditionDef(SignalId(signal), target(), label, kind)

    private fun CourseDto.toDomain() = CourseDefinition(
        id, name, estimatedSeconds,
        steps = steps.map { s ->
            StepDefinition(
                s.id,
                s.action.map { it.toDef(Kind.ACTION) },
                s.hold.map { it.toDef(Kind.HOLD) },
                s.misoperations.map { MisoperationDef(it.id, SignalId(it.`when`.signal), it.`when`.target(), it.correction) },
                s.doneText,
            )
        },
        followUps = followUps.map { FollowUpDef(it.id, it.condition.toDef(Kind.ACTION)) },
        exhaustedText = exhaustedText,
    )

    private fun ModelDto.toDomain() = ModelCourseText(
        courseId,
        steps.mapValues { (_, t) ->
            StepText(
                t.instruction, t.instructionAtLimit, t.locatorText, t.locatorAsset,
                t.conditionLocators.mapKeys { SignalId(it.key) }, t.misoperationLocators,
            )
        },
    )
}
