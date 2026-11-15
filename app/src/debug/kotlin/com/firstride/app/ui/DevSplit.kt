package com.firstride.app.ui

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeoSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * debug 전용: 내비게이션과 앱 창을 나눠 쓰는 상태를 흉내 낸다(Figma 05 보드).
 * 가로형 앱 창(너비 ≥ [Size.wideBreakpoint])을 반으로 나눠 왼쪽에 내비게이션 mock, 오른쪽에 앱을 그린다.
 * 앱은 841dp 칸만 받으므로 세로형 배치로 그려진다. 실제 내비게이션이 아니고 차량 신호와도 상관없다.
 *
 * 켜는 법: 개발자 패널의 "내비 mock" 또는 `adb shell am start -n com.firstride/.MainActivity --ez nav_mock true`.
 * release에는 이 파일이 없다(release의 [DevSplitFrame]은 내용을 그대로 그린다).
 */
object DevSplit {
    var on by mutableStateOf(false)
}

@Composable
fun DevSplitFrame(content: @Composable () -> Unit) {
    val activity = LocalContext.current as? Activity
    remember { if (activity?.intent?.getBooleanExtra("nav_mock", false) == true) DevSplit.on = true }
    if (!DevSplit.on) {
        content()
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth < Size.wideBreakpoint) {
            content()
        } else {
            Row(Modifier.fillMaxSize()) {
                NavMock(Modifier.weight(1f).fillMaxHeight())
                Box(Modifier.weight(1f).fillMaxHeight()) { content() }
            }
        }
    }
}

private class NavColors(
    val map: Color, val park: Color, val water: Color, val road: Color, val minor: Color,
    val route: Color, val bar: Color, val barText: Color, val barSub: Color,
)

private val LightNav = NavColors(
    map = Color(0xFFEDEFEA), park = Color(0xFFD9E6D2), water = Color(0xFFD3E2F0), road = Color.White, minor = Color(0xFFF7F8FA),
    route = Color(0xFF0064FF), bar = Color.White, barText = Color(0xFF131417), barSub = Color(0xFF686A72),
)
private val DarkNav = NavColors(
    map = Color(0xFF1F2226), park = Color(0xFF22302A), water = Color(0xFF1E2A36), road = Color(0xFF3A3D44), minor = Color(0xFF2C2F35),
    route = Color(0xFF0082FF), bar = Color(0xFF313236), barText = Color.White, barSub = Color(0xFFA4A8AE),
)

/** 내비게이션 자리를 채우는 그림. 좌표는 Figma NavMock(841×1210) 기준으로 그리고 칸 크기에 맞춰 늘린다. */
@Composable
private fun NavMock(modifier: Modifier) {
    val c = if (isSystemInDarkTheme()) DarkNav else LightNav
    Box(modifier.background(c.map)) {
        Canvas(Modifier.fillMaxSize()) {
            val sx = size.width / 841f
            val sy = size.height / 1210f
            fun p(x: Float, y: Float) = Offset(x * sx, y * sy)
            fun rect(color: Color, x: Float, y: Float, w: Float, h: Float, r: Float = 0f) =
                drawRoundRect(color, p(x, y), GeoSize(w * sx, h * sy), CornerRadius(r * sx))
            rect(c.park, 40f, 110f, 300f, 230f, 24f)
            rect(c.water, 560f, 880f, 281f, 330f)
            for (x in listOf(160f, 680f)) rect(c.minor, x - 12f, 0f, 24f, 1210f)
            for (y in listOf(560f, 1010f)) rect(c.minor, 0f, y - 12f, 841f, 24f)
            drawLine(c.road, p(-20f, 1150f), p(860f, 470f), strokeWidth = 44f * sx)
            rect(c.road, 0f, 352f, 841f, 56f)
            rect(c.road, 0f, 760f, 841f, 56f)
            rect(c.road, 420f, 0f, 56f, 1210f)
            val route = Path().apply { moveTo(p(448f, 1230f).x, p(448f, 1230f).y); lineTo(p(448f, 788f).x, p(448f, 788f).y); lineTo(p(860f, 788f).x, p(860f, 788f).y) }
            drawPath(route, c.route, style = Stroke(20f * sx, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val car = Path().apply {
                moveTo(p(448f, 940f).x, p(448f, 940f).y); lineTo(p(476f, 1010f).x, p(476f, 1010f).y)
                lineTo(p(448f, 994f).x, p(448f, 994f).y); lineTo(p(420f, 1010f).x, p(420f, 1010f).y); close()
            }
            drawPath(car, Color.White)
            drawPath(car, c.route, style = Stroke(6f * sx, join = StrokeJoin.Round))
        }
        // 위: 회전 안내 카드
        Row(
            Modifier.fillMaxWidth().padding(24.dp).background(Color(0xFF131417), RoundedCornerShape(24.dp)).padding(horizontal = 32.dp, vertical = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Canvas(Modifier.size(72.dp)) {
                val s = size.minDimension / 72f
                val arrow = Path().apply {
                    moveTo(18 * s, 66 * s); lineTo(18 * s, 30 * s); lineTo(56 * s, 30 * s)
                    moveTo(42 * s, 14 * s); lineTo(58 * s, 30 * s); lineTo(42 * s, 46 * s)
                }
                drawPath(arrow, Color.White, style = Stroke(9 * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            Column {
                Text("300 m", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                Text("테헤란로 방향 우회전", color = Color.White, fontSize = 28.sp)
            }
        }
        // 아래: 도착 정보
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp).background(c.bar, RoundedCornerShape(24.dp)).padding(horizontal = 32.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("14:32 도착 · 12분 · 4.8 km", color = c.barText, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            Text("내비게이션 (mock)", color = c.barSub, fontSize = 22.sp)
        }
    }
}
