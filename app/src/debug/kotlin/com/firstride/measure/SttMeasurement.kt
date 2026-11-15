package com.firstride.measure

import com.firstride.engine.voice.ListenResult

/** 개발용 측정에서도 인식 문장을 로그·화면 기록에 남기지 않는다(POL-V-14, POL-S-2). */
internal fun ListenResult.measurementLabel(): String = when (this) {
    is ListenResult.Text -> "Text"
    ListenResult.Silence -> "Silence"
    ListenResult.Error -> "Error"
}
