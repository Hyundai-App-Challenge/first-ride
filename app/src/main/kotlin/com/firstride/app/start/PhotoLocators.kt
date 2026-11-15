package com.firstride.app.start

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONArray
import org.json.JSONObject

/** 웹과 같은 장면·정규화 좌표로 조작 위치를 그린다. 원본 사진은 수정하지 않는다. */
internal object PhotoLocators {
    const val ASSET_DIR = "panels/ioniq5-2025"
    private var panel: JSONObject? = null
    private val photos = mutableMapOf<String, Bitmap>()

    @Synchronized
    fun load(assets: AssetManager, path: String): ImageBitmap? {
        val data = panel ?: JSONObject(assets.open("$ASSET_DIR/panel.json").bufferedReader().use { it.readText() }).also { panel = it }
        val locators = data.getJSONObject("locators")
        if (!locators.has(path)) return null
        val selected = locators.getJSONArray(path).let { ids -> (0 until ids.length()).map { ids.getString(it) }.toSet() }
        val controls = data.getJSONArray("controls").let { items -> (0 until items.length()).map { items.getJSONObject(it) } }
        val viewId = controls.firstOrNull { it.getString("id") in selected }?.getString("view") ?: "climate"
        val view = data.getJSONArray("views").let { items -> (0 until items.length()).map { items.getJSONObject(it) } }
            .single { it.getString("id") == viewId }
        val photoPath = view.getString("photo")
        val source = photos.getOrPut(photoPath) {
            assets.open("$ASSET_DIR/$photoPath").use { requireNotNull(BitmapFactory.decodeStream(it)) { "실내 사진을 읽을 수 없습니다" } }
        }
        val crop = view.getJSONArray("crop").rect(source.width.toFloat(), source.height.toFloat())
        val image = Bitmap.createBitmap(crop.width().toInt(), crop.height().toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        canvas.drawBitmap(source, Rect(crop.left.toInt(), crop.top.toInt(), crop.right.toInt(), crop.bottom.toInt()),
            Rect(0, 0, image.width, image.height), null)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (control in controls) {
            if (control.getString("id") !in selected) continue
            val bounds = control.getJSONArray("rect").rect(source.width.toFloat(), source.height.toFloat())
            bounds.offset(-crop.left, -crop.top)
            bounds.inset(-5f, -5f)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(48, 255, 204, 51)
            canvas.drawRoundRect(bounds, 10f, 10f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f
            paint.color = Color.rgb(255, 204, 51)
            canvas.drawRoundRect(bounds, 10f, 10f, paint)
        }
        return image.asImageBitmap()
    }

    private fun JSONArray.rect(width: Float, height: Float): RectF {
        val x = getDouble(0).toFloat() * width
        val y = getDouble(1).toFloat() * height
        return RectF(x, y, x + getDouble(2).toFloat() * width, y + getDouble(3).toFloat() * height)
    }
}
