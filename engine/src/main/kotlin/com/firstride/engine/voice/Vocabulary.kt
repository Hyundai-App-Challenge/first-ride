package com.firstride.engine.voice

/** 음성으로 말할 수 있는 어휘는 이 셋뿐이다(FR-06). 그만하기·기록 지우기는 없다(POL-V-16). */
enum class Vocab { NEXT, REPEAT, WHERE }

private val WORDS = listOf(Vocab.NEXT to "다음", Vocab.REPEAT to "다시", Vocab.WHERE to "어디야")

/**
 * 인식 결과에서 어휘를 찾는다. 공백과 문장부호를 지우고 어휘가 들어 있는지 본다.
 * 어휘가 없거나 둘 이상 들어 있으면 null이다(POL-V-9, POL-V-10).
 */
fun matchVocabulary(text: String): Vocab? {
    val t = text.filter { it.isLetterOrDigit() }
    return WORDS.filter { (_, w) -> w in t }.singleOrNull()?.first
}
