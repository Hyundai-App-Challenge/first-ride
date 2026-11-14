package com.firstride.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 색은 차갑고 낮은 채도의 회색 계열 하나와 포인트색 하나(코발트)뿐이다. 순백과 순흑을 쓰지 않는다.
 * 글자와 배경의 대비는 낮 5.8:1, 밤 7.1:1 이상이다(WCAG AA 4.5:1 충족. 차량 화면은 멀리서 훑어본다).
 */
private val LightColors: ColorScheme = lightColorScheme(
    background = Color(0xFFECEEF1),
    onBackground = Color(0xFF14171B),
    surface = Color(0xFFF7F8FA),
    onSurface = Color(0xFF14171B),
    surfaceVariant = Color(0xFFE0E3E8),
    onSurfaceVariant = Color(0xFF4A5159),
    outline = Color(0xFFB9BFC7),
    outlineVariant = Color(0xFFD3D7DD),
    primary = Color(0xFF2B4FCC),
    onPrimary = Color(0xFFF8FAFF),
    primaryContainer = Color(0xFFDCE4FF),
    onPrimaryContainer = Color(0xFF0F2370),
)

private val DarkColors: ColorScheme = darkColorScheme(
    background = Color(0xFF0E1114),
    onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF171B20),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF1F252B),
    onSurfaceVariant = Color(0xFFA6ADB5),
    outline = Color(0xFF38414A),
    outlineVariant = Color(0xFF2A3139),
    primary = Color(0xFF8FA8FF),
    onPrimary = Color(0xFF0C1A55),
    primaryContainer = Color(0xFF1B2A66),
    onPrimaryContainer = Color(0xFFDCE4FF),
)

/** 모서리는 한 체계로 고정한다. 버튼과 선택 알약은 완전한 알약(Material 기본), 면은 아래 값. */
private val CarShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(38.dp),
)

/** 차량 화면은 멀리서 훑어본다. 기본보다 큰 글자를 쓰고, 큰 제목은 자간을 조여 단단하게 보이게 한다. */
private val CarTypography = Typography(
    headlineMedium = TextStyle(fontSize = 38.sp, fontWeight = FontWeight.Bold, lineHeight = 48.sp, letterSpacing = (-0.8).sp),
    headlineSmall = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 42.sp, letterSpacing = (-0.6).sp),
    titleLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp, letterSpacing = (-0.4).sp),
    titleMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp, letterSpacing = (-0.2).sp),
    bodyLarge = TextStyle(fontSize = 24.sp, lineHeight = 34.sp),
    bodyMedium = TextStyle(fontSize = 21.sp, lineHeight = 30.sp),
    labelLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
)

/** 주간·야간은 시스템을 따른다. 앱에 테마 설정을 두지 않는다(POL-U-3). */
@Composable
fun FirstRideTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = CarShapes,
        typography = CarTypography,
        content = content,
    )
}
