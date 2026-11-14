package com.firstride.app.start

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.firstride.engine.course.CourseParser
import com.firstride.engine.course.ModelCourseText
import java.io.IOException

/**
 * 기준 차종의 안내 문구와 위치 안내 자산(`assets/models/<차종>/`, 기술 설계 6-1절).
 * 차종을 더하는 일은 폴더 하나를 더하는 일이다. 지금은 한 종류뿐이다.
 */
object CourseTexts {
    /** [가정] 기준 차종(D2)이 정해지기 전의 폴더 이름. */
    const val MODEL_DIR = "reference"

    /** 코스 ID → 차종별 문구. 형식이 틀린 파일은 숨기지 않고 예외로 드러낸다(POL-E-5). */
    fun load(assets: AssetManager): Map<String, ModelCourseText> =
        assets.list("models/$MODEL_DIR").orEmpty()
            .filter { it.startsWith("CRS-") && it.endsWith(".json") }
            .map { name ->
                CourseParser.parseModelText(assets.open("models/$MODEL_DIR/$name").bufferedReader().use { it.readText() })
            }
            .associateBy { it.courseId }

    /** 위치 안내 그림. 파일이 없으면 null이고, 화면은 위치 문구만 보인다. */
    fun loadImage(assets: AssetManager, path: String): ImageBitmap? = try {
        assets.open("models/$MODEL_DIR/$path").use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
    } catch (e: IOException) {
        null
    }
}
