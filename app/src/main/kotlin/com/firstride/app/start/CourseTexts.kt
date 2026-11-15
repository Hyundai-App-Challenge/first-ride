package com.firstride.app.start

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.firstride.engine.course.CourseParser
import com.firstride.engine.course.ModelCourseText
import java.io.IOException

/**
 * 기본 예시 안내에 아이오닉5 실내 사진 팩의 코스 안내를 적용한다.
 */
object CourseTexts {
    /** 사진 팩에 없는 자산을 위한 기본 예시 폴더. */
    const val MODEL_DIR = "reference"

    /** 코스 ID → 차종별 문구. 형식이 틀린 파일은 숨기지 않고 예외로 드러낸다(POL-E-5). */
    fun load(assets: AssetManager): Map<String, ModelCourseText> =
        listOf("models/$MODEL_DIR", PhotoLocators.ASSET_DIR).flatMap { directory ->
            assets.list(directory).orEmpty()
                .filter { it.startsWith("CRS-") && it.endsWith(".json") }
                .map { name -> CourseParser.parseModelText(assets.open("$directory/$name").bufferedReader().use { it.readText() }) }
        }.associateBy { it.courseId }

    /** 위치 안내 그림. 파일이 없으면 null이고, 화면은 위치 문구만 보인다. */
    fun loadImage(assets: AssetManager, path: String): ImageBitmap? = try {
        PhotoLocators.load(assets, path)
            ?: assets.open("models/$MODEL_DIR/$path").use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
    } catch (e: IOException) {
        null
    }
}
