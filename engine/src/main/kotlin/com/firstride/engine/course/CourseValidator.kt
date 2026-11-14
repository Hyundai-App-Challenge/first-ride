package com.firstride.engine.course

import com.firstride.engine.signal.SignalId
import com.firstride.engine.signal.SignalValue
import com.firstride.engine.signal.Signals

/**
 * 코스 정의가 앱이 읽을 수 있는 형태인지 검사한다(기술 설계 6-3절). 하나라도 어긋나면 오류이고,
 * 조용히 숨기지 않는다(POL-E-5). 단위 테스트가 assets의 모든 정의를 검사하고(빌드 시점), 앱 시작 때 한 번 더 부른다.
 */
object CourseValidator {
    enum class Kind { UNKNOWN_SIGNAL, BAD_VALUE, BAD_TARGET, NO_ACTION, DUPLICATE_ID, MISSING_TEXT, MISSING_ASSET, MISMATCH, TOO_LONG }

    data class Error(val kind: Kind, val where: String, val message: String) {
        override fun toString() = "[$kind] $where: $message"
    }

    /** shortcut: 3줄(POL-W-4)을 글자 수로 어림한다(한 줄 30자). 화면 폭을 실측(G1)한 뒤 맞춘다. */
    const val MAX_TEXT_LENGTH = 90

    /** [text]가 null이면 차종별 파일이 없는 것이다. [assetExists]는 차종 폴더 기준의 자산 경로를 받는다. */
    fun validate(course: CourseDefinition, text: ModelCourseText?, assetExists: (String) -> Boolean): List<Error> {
        val errors = mutableListOf<Error>()
        fun err(kind: Kind, where: String, message: String) { errors += Error(kind, where, message) }

        // ── 차종 공통 ──
        val stepIds = course.steps.map { it.id }
        val followUpIds = course.followUps.map { it.id }
        (stepIds + followUpIds).groupBy { it }.filterValues { it.size > 1 }.keys
            .forEach { err(Kind.DUPLICATE_ID, it, "단계·후속 조치 ID가 겹친다") }
        if (course.followUps.isNotEmpty() && course.exhaustedText.isNullOrBlank()) {
            err(Kind.MISSING_TEXT, course.id, "후속 조치가 있는데 exhaustedText가 없다")
        }

        for (s in course.steps) {
            if (s.action.isEmpty()) err(Kind.NO_ACTION, s.id, "조작 조건이 없다")
            (s.action + s.hold).forEach { checkTarget(it.signal, it.target, s.id, ::err) }
            s.misoperations.forEach { checkTarget(it.signal, it.target, "${s.id}/${it.id}", ::err) }
            s.misoperations.groupBy { it.id }.filterValues { it.size > 1 }.keys
                .forEach { err(Kind.DUPLICATE_ID, s.id, "오조작 ID '$it'가 겹친다") }
            checkLength(s.doneText, "${s.id}.doneText", ::err)
            s.misoperations.forEach { checkLength(it.correction, "${s.id}/${it.id}.correction", ::err) }
        }
        checkLength(course.summary, "${course.id}.summary", ::err)
        course.followUps.forEach { checkTarget(it.condition.signal, it.condition.target, it.id, ::err) }
        course.exhaustedText?.let { checkLength(it, "${course.id}.exhaustedText", ::err) }

        // ── 차종별 ──
        if (text == null) {
            err(Kind.MISSING_TEXT, course.id, "차종별 파일이 없다(POL-C-7)")
            return errors
        }
        if (text.courseId != course.id) err(Kind.MISMATCH, course.id, "차종별 파일의 courseId가 '${text.courseId}'다")

        val expected = stepIds + followUpIds
        (expected - text.steps.keys).forEach { err(Kind.MISSING_TEXT, it, "차종별 파일에 안내 문구와 위치 안내가 없다") }
        (text.steps.keys - expected.toSet()).forEach { err(Kind.MISMATCH, it, "코스 정의에 없는 단계·조치다") }

        fun checkText(id: String, signals: Set<SignalId>, misoIds: Set<String>) {
            val t = text.steps[id] ?: return
            if (t.instruction.isBlank()) err(Kind.MISSING_TEXT, id, "instruction이 비었다")
            if (t.locatorText.isBlank()) err(Kind.MISSING_TEXT, id, "locatorText가 비었다")
            checkAsset(t.locatorAsset, id, assetExists, ::err)
            checkLength(t.instruction, "$id.instruction", ::err)
            checkLength(t.locatorText, "$id.locatorText", ::err)
            t.instructionAtLimit?.let { checkLength(it, "$id.instructionAtLimit", ::err) }
            t.conditionLocators.forEach { (sig, path) ->
                if (sig !in signals) err(Kind.MISMATCH, id, "conditionLocators의 '${sig.key}'는 이 단계의 조건이 아니다")
                checkAsset(path, id, assetExists, ::err)
            }
            t.misoperationLocators.forEach { (mid, path) ->
                if (mid !in misoIds) err(Kind.MISMATCH, id, "misoperationLocators의 '$mid'는 이 단계의 오조작이 아니다")
                checkAsset(path, id, assetExists, ::err)
            }
        }
        course.steps.forEach { s ->
            checkText(s.id, (s.action + s.hold).map { it.signal }.toSet(), s.misoperations.map { it.id }.toSet())
        }
        course.followUps.forEach { f -> checkText(f.id, setOf(f.condition.signal), emptySet()) }
        return errors
    }

    private fun checkAsset(path: String, where: String, exists: (String) -> Boolean, err: (Kind, String, String) -> Unit) {
        if (path.isBlank() || !exists(path)) err(Kind.MISSING_ASSET, where, "위치 안내 자산이 없다: '$path'")
    }

    private fun checkLength(text: String, where: String, err: (Kind, String, String) -> Unit) {
        if (text.length > MAX_TEXT_LENGTH) err(Kind.TOO_LONG, where, "${text.length}자. ${MAX_TEXT_LENGTH}자(3줄)를 넘는다")
    }

    private fun checkTarget(signal: SignalId, target: Target, where: String, err: (Kind, String, String) -> Unit) {
        if (signal !in Signals.all) {
            err(Kind.UNKNOWN_SIGNAL, where, "신호 '${signal.key}'가 신호 목록에 없다")
            return
        }
        val numeric = signal in Signals.numeric
        when (target) {
            is Target.Equals -> when (val v = target.value) {
                is SignalValue.Number -> if (!numeric) err(Kind.BAD_VALUE, where, "'${signal.key}'는 숫자 신호가 아니다")
                is SignalValue.Token -> {
                    val allowed = Signals.tokens[signal]
                    if (numeric) err(Kind.BAD_VALUE, where, "'${signal.key}'는 숫자 신호인데 값이 '${v.name}'다")
                    else if (allowed != null && v.name !in allowed) {
                        err(Kind.BAD_VALUE, where, "'${signal.key}'에 '${v.name}'는 쓸 수 없다. 가능한 값: ${allowed.sorted()}")
                    }
                }
            }
            is Target.Relative, Target.AtMax ->
                if (!numeric) err(Kind.BAD_TARGET, where, "상대 목표와 atMax는 숫자 신호에만 쓴다: '${signal.key}'")
        }
    }
}
