package com.firstride.app.ui

import com.firstride.engine.course.StepDefinition
import com.firstride.engine.course.StepText

/** 복구할 조작의 그림을 먼저 쓰고, 유지 조건이 다시 어긋나도 원래 그림을 찾을 수 있게 한다. */
internal fun restoreStepText(step: StepDefinition, original: StepDefinition?, text: StepText?, instruction: String): StepText? {
    if (text == null) return null
    val locators = original?.action.orEmpty().associate { it.signal to text.locatorAsset } + text.conditionLocators
    return text.copy(
        instruction = instruction,
        locatorAsset = step.action.firstOrNull()?.signal?.let(locators::get) ?: text.locatorAsset,
        conditionLocators = locators,
    )
}
