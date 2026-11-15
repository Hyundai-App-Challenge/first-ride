package com.firstride.measure

import com.firstride.engine.voice.ListenResult
import kotlin.test.Test
import kotlin.test.assertEquals

class SttMeasurementTest {
    @Test fun `POL-S-2 임의 인식 문장과 줄바꿈을 측정 출력에 포함하지 않는다`() {
        for (text in listOf("다음", "임의의 비공개 문장\n추가 로그처럼 보이는 내용", "", "  ")) {
            assertEquals("Text", ListenResult.Text(text).measurementLabel())
        }
    }

    @Test fun `무음과 오류는 원문 없이 종류만 구분한다`() {
        assertEquals("Silence", ListenResult.Silence.measurementLabel())
        assertEquals("Error", ListenResult.Error.measurementLabel())
    }
}
