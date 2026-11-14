package com.firstride.app.start

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.IOException

/**
 * 홈의 360도 차 화면에 쓸 차종. [id]는 `assets/cars/<id>/` 폴더 이름이고 화면 이름은 밑줄을 공백으로 바꾼 것이다.
 * 폴더 안에는 `ext_000.webp`부터 번호순 연속 이미지(한 바퀴)와 선택으로 `interior.webp`(가로로 긴 실내 파노라마)를 둔다.
 * 차종을 더하는 일은 폴더 하나를 더하는 일이다. 위치 안내 문구와 그림은 `assets/models/<id>/`에 있으면 그것을 쓰고,
 * 없으면 기준 차종의 것을 쓴다([CourseTexts]).
 */
data class CarModel(val id: String, val frames: List<String>, val interior: String?) {
    val name: String get() = id.replace('_', ' ')
}

object CarCatalog {
    private const val DIR = "cars"
    private val EXT = Regex("ext_(\\d+)\\.(webp|png|jpg)")

    /** 연속 이미지가 4장 이상인 폴더만 차종으로 본다. 그보다 적으면 돌려 볼 수 없다. */
    fun list(assets: AssetManager): List<CarModel> =
        assets.list(DIR).orEmpty().sorted().mapNotNull { id ->
            val files = assets.list("$DIR/$id").orEmpty()
            val frames = files.filter { EXT.matches(it) }.sortedBy { EXT.matchEntire(it)!!.groupValues[1].toInt() }
            if (frames.size < 4) null else CarModel(id, frames, files.firstOrNull { it.startsWith("interior.") })
        }

    fun frame(assets: AssetManager, model: CarModel, index: Int): ImageBitmap? =
        decode(assets, "$DIR/${model.id}/${model.frames[index.mod(model.frames.size)]}")

    fun interior(assets: AssetManager, model: CarModel): ImageBitmap? =
        model.interior?.let { decode(assets, "$DIR/${model.id}/$it") }

    private fun decode(assets: AssetManager, path: String): ImageBitmap? = try {
        assets.open(path).use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
    } catch (e: IOException) {
        null
    }
}
