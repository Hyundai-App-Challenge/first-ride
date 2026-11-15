package com.firstride.app.start

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.firstride.engine.course.CourseParser
import com.firstride.engine.course.ModelCourseText
import java.io.IOException

/**
 * 기본 예시 안내와 아이오닉5 공조 사진 팩을 조립한다. 다른 코스의 예시는 유지한다.
 */
object CourseTexts {
    /** 공조 사진 팩이 제공하지 않는 코스의 기본 예시 폴더. */
    const val MODEL_DIR = "reference"

    /** 코스 ID → 차종별 문구. 형식이 틀린 파일은 숨기지 않고 예외로 드러낸다(POL-E-5). */
    fun load(assets: AssetManager): Map<String, ModelCourseText> =
        // 이번 모델 팩은 공조 두 코스만 제공한다. 나머지 코스는 기존 예시를 유지한다.
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
