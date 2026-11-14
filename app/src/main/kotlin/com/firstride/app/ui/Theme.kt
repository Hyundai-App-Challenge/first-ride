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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * 디자인 값. 색은 DESIGN.md, 글자·간격·크기·모서리는 docs/08-design-tokens.md 가 기준이고
 * Figma 변수(color · type · space · radius)와 같다. 값을 바꿀 때는 세 곳을 함께 고친다.
 */

/** 간격과 크기(dp). 문서 3절. */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val sm = 12.dp
    val m = 16.dp
    val ml = 20.dp
    val l = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
    val screenH = 32.dp
    val screenV = 24.dp
}

object Size {
    val touchMin = 76.dp
    val buttonH = 88.dp
    val topBarH = 96.dp
    val mark = 44.dp
    val stateIcon = 40.dp
    val icon = 36.dp
    /** 세로형에서 위치 안내 판의 높이. */
    val locatorPortrait = 340.dp
    /** 이 너비 이상이면 가로형(문서 5-1절). 실측 창은 1682(가로형)와 841(세로형). */
    val wideBreakpoint = 1200.dp
}

object Radius {
    val sm = 12.dp
    val md = 16.dp
    val button = 20.dp
    val card = 24.dp
}

/**
 * Material 3 [ColorScheme]에 없는 앱 색. 이름과 값은 DESIGN.md 2절, Figma 컬렉션 `color`의 변수와 같다.
 * 글자에는 색을 넣지 않는다. 상태색(confirmed·attention·error)은 채운 도형에만 쓰고, 그 위 글자·기호는 [onStatus]다.
 */
@Immutable
data class AppColors(
    val card: Color,                // bg/card
    val blockedBg: Color,           // bg/blocked
    val textTertiary: Color,        // text/tertiary (64%)
    val textDisabled: Color,        // text/disabled (32%)
    val primaryPressed: Color,      // action/primary-pressed
    val secondary: Color,           // action/secondary
    val secondaryPressed: Color,    // action/secondary-pressed
    val disabledContainer: Color,   // action/disabled
    val statusConfirmed: Color,     // status/confirmed
    val statusAttention: Color,     // status/attention
    val onStatus: Color,            // status/on
    val locatorPlate: Color,        // locator/plate
    val locatorInk: Color,          // locator/ink
)

private val Ink = Color(0xFF131417)       // Pleos basic_static_dark_100
private val White = Color(0xFFFFFFFF)
private val LocatorPlate = Color(0xFFF6F7F9)   // 위치 안내 그림의 바탕과 같은 색. 야간에도 바꾸지 않는다(DESIGN.md 2-5절)
private val LocatorInk = Color(0xFF2C3038)

private val LightApp = AppColors(
    card = White,
    blockedBg = Color(0xFFEDEEF2),
    textTertiary = Ink.copy(alpha = 0.64f),
    textDisabled = Ink.copy(alpha = 0.32f),
    primaryPressed = Color(0xFF313236),
    secondary = Color(0xFFE0E1E6),
    secondaryPressed = Color(0xFFC7C9CE),
    disabledContainer = Color(0xFFEDEEF2),
    statusConfirmed = Color(0xFF02C265),
    statusAttention = Color(0xFFFFC224),
    onStatus = Ink,
    locatorPlate = LocatorPlate,
    locatorInk = LocatorInk,
)

private val DarkApp = AppColors(
    card = Color(0xFF313236),
    blockedBg = Color(0xFF313236),
    textTertiary = White.copy(alpha = 0.64f),
    textDisabled = White.copy(alpha = 0.32f),
    primaryPressed = Color(0xFFC7C9CE),
    secondary = Color(0xFF313236),
    secondaryPressed = Color(0xFF44464E),
    disabledContainer = Color(0xFF313236),
    statusConfirmed = Color(0xFF00E074),
    statusAttention = Color(0xFFFFC224),
    onStatus = Ink,
    locatorPlate = LocatorPlate,
    locatorInk = LocatorInk,
)

private fun scheme(dark: Boolean): ColorScheme {
    val primary = if (dark) Color(0xFFE0E1E6) else Color(0xFF44464E)          // action/primary (회색 버튼 바탕. 글자에 쓰지 않는다)
    val onPrimary = if (dark) Ink else White                                   // text/on-primary
    val background = if (dark) Color(0xFF1D1E21) else Color(0xFFF7F8FA)       // bg/app
    val surface = if (dark) Ink else White                                     // bg/surface
    val surfaceVariant = if (dark) Color(0xFF313236) else Color(0xFFEDEEF2)   // bg/surface-variant
    val onSurface = if (dark) White else Ink                                   // text/primary
    val onSurfaceVariant = onSurface.copy(alpha = 0.84f)                       // text/secondary
    val outlineVariant = if (dark) Color(0xFF44464E) else Color(0xFFE0E1E6)   // border/subtle
    val error = if (dark) Color(0xFFFF4C28) else Color(0xFFFE3D16)            // error
    val base = if (dark) darkColorScheme() else lightColorScheme()
    // 쓰지 않는 칸도 Material 기본 보라색이 새어 나오지 않게 같은 계열로 채운다.
    return base.copy(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = surfaceVariant, onPrimaryContainer = onSurface,
        secondary = primary, onSecondary = onPrimary,
        secondaryContainer = surfaceVariant, onSecondaryContainer = onSurface,
        tertiary = primary, onTertiary = onPrimary,
        tertiaryContainer = surfaceVariant, onTertiaryContainer = onSurface,
        background = background, onBackground = onSurface,
        surface = surface, onSurface = onSurface,
        surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
        surfaceTint = Color.Transparent,
        surfaceBright = surface, surfaceDim = background,
        surfaceContainerLowest = surface, surfaceContainerLow = surface, surfaceContainer = surface,
        surfaceContainerHigh = if (dark) Color(0xFF313236) else White,   // 대화 상자 바탕(Pleos background_popup)
        surfaceContainerHighest = surface,
        inverseSurface = onSurface, inverseOnSurface = surface, inversePrimary = primary,
        outline = outlineVariant, outlineVariant = outlineVariant,
        error = error, onError = Ink,
        scrim = Color(0x99000000),
    )
}

/** 글자. 차에서 멀리 짧게 본다. 22sp 미만은 쓰지 않는다(문서 2절). 글꼴은 시스템 글꼴. */
private fun style(size: Int, line: Int, weight: FontWeight) =
    TextStyle(fontFamily = FontFamily.Default, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

private val CarTypography = Typography(
    displaySmall = style(48, 60, FontWeight.Bold),        // display: 차단 화면(가로형), 앱 이름
    headlineMedium = style(38, 50, FontWeight.Bold),      // headline: 화면의 큰 제목
    headlineSmall = style(32, 42, FontWeight.Bold),       // instruction: 단계 안내 문구
    titleLarge = style(28, 38, FontWeight.SemiBold),      // title: TopBar·카드·배너 제목
    titleMedium = style(26, 34, FontWeight.Medium),       // row: 조건 이름, 목록 항목
    bodyLarge = style(24, 34, FontWeight.Normal),         // body
    bodyMedium = style(22, 30, FontWeight.Normal),        // caption: 보조 문구
    labelLarge = style(26, 32, FontWeight.SemiBold),      // label: 버튼
    labelMedium = style(22, 28, FontWeight.SemiBold),     // label-sm: 상태 이름표, 조건 상태 문구
)

private val CarShapes = Shapes(
    small = RoundedCornerShape(Radius.sm),
    medium = RoundedCornerShape(Radius.button),
    large = RoundedCornerShape(Radius.card),
)

private val LocalAppColors = staticCompositionLocalOf { LightApp }

/** 앱 색. `MaterialTheme.colorScheme`에 없는 것(상태색, 누름 색 등). */
val appColors: AppColors
    @Composable @ReadOnlyComposable get() = LocalAppColors.current

/** 주간·야간은 시스템을 따른다. 앱에 테마 설정을 두지 않는다(POL-U-3). */
@Composable
fun FirstRideTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalAppColors provides if (dark) DarkApp else LightApp) {
        MaterialTheme(colorScheme = scheme(dark), typography = CarTypography, shapes = CarShapes, content = content)
    }
}
