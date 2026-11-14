package com.firstride.app.start

import android.content.res.AssetManager
import com.firstride.engine.course.CourseDefinition
import com.firstride.engine.course.CourseParser

/**
 * `assets/courses/`의 코스 정의(차종 공통 파일)를 읽는다. 순서는 파일 이름(코스 번호) 순.
 * 형식이 틀린 파일은 숨기지 않고 예외로 드러낸다(POL-E-5). 정의 검증은 단위 테스트에서 먼저 걸린다(W-07).
 */
object CourseCatalog {
    private const val DIR = "courses"

    fun load(assets: AssetManager): List<CourseDefinition> =
        assets.list(DIR).orEmpty()
            .filter { it.startsWith("CRS-") && it.endsWith(".json") }
            .sorted()
            .map { name -> CourseParser.parseCourse(assets.open("$DIR/$name").bufferedReader().use { it.readText() }) }
}
