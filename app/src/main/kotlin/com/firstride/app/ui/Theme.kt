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
import com.firstride.engine.judge.Phase

/*
 * 디자인 값. 출처는 docs/08-design-tokens.md 와 Figma 변수(color · type · space · radius).
 * 값을 바꿀 때는 문서와 Figma를 함께 고친다.
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

/** 판정 상태 하나의 색: 바탕, 글자, 기호(테두리도 이 색). 문서 1-2절. */
@Immutable
data class StatusColor(val bg: Color, val fg: Color, val icon: Color)

/** Material 3 [ColorScheme]에 없는 앱 색. 문서 1-1절의 "(앱 색)". */
@Immutable
data class AppColors(
    val blockedBg: Color,
    val textDisabled: Color,
    val primaryPressed: Color,
    val secondaryPressed: Color,
    val disabledContainer: Color,
    val locatorPlate: Color,
    val locatorInk: Color,
    val markMet: Color,
    val markAlready: Color,
    val markUnknown: Color,
    val markWrong: Color,
    val precheck: StatusColor,
    val waiting: StatusColor,
    val already: StatusColor,
    val partial: StatusColor,
    val correction: StatusColor,
    val done: StatusColor,
    val unverifiable: StatusColor,
    val skipped: StatusColor,
) {
    fun status(phase: Phase): StatusColor = when (phase) {
        Phase.PRECHECK -> precheck
        Phase.WAITING -> waiting
        Phase.ALREADY_AT_TARGET -> already
        Phase.PARTIAL -> partial
        Phase.CORRECTION -> correction
        Phase.DONE -> done
        Phase.UNVERIFIABLE -> unverifiable
        Phase.SKIPPED -> skipped
    }
}

private val LocatorPlate = Color(0xFFF6F7F9)   // 위치 안내 그림의 바탕과 같은 색. 야간에도 바꾸지 않는다(문서 1-4절)
private val LocatorInk = Color(0xFF2C3038)

private val LightApp = AppColors(
    blockedBg = Color(0xFFE3E6EB),
    textDisabled = Color(0xFF8A919C),
    primaryPressed = Color(0xFF1A3EA6),
    secondaryPressed = Color(0xFFE8ECF2),
    disabledContainer = Color(0xFFE3E6EB),
    locatorPlate = LocatorPlate,
    locatorInk = LocatorInk,
    markMet = Color(0xFF1E7B43),
    markAlready = Color(0xFF0B7C88),
    markUnknown = Color(0xFF6A4FC4),
    markWrong = Color(0xFF8F6B00),
    precheck = StatusColor(Color(0xFFEEF0F3), Color(0xFF3E4450), Color(0xFF3E4450)),
    waiting = StatusColor(Color(0xFFE7EEFF), Color(0xFF1D3F99), Color(0xFF2350C8)),
    already = StatusColor(Color(0xFFDFF3F4), Color(0xFF0B5F69), Color(0xFF0B7C88)),
    partial = StatusColor(Color(0xFFE3F4E8), Color(0xFF1E6B3A), Color(0xFF1E7B43)),
    correction = StatusColor(Color(0xFFFFF3C4), Color(0xFF6B4E00), Color(0xFF8F6B00)),
    done = StatusColor(Color(0xFF1E7B43), Color(0xFFFFFFFF), Color(0xFFFFFFFF)),
    unverifiable = StatusColor(Color(0xFFEFEAFB), Color(0xFF4B2E99), Color(0xFF6A4FC4)),
    skipped = StatusColor(Color(0xFFEEF0F3), Color(0xFF4A505A), Color(0xFF6B7380)),
)

private val DarkApp = AppColors(
    blockedBg = Color(0xFF2A2E35),
    textDisabled = Color(0xFF6E7580),
    primaryPressed = Color(0xFFB5CBFF),
    secondaryPressed = Color(0xFF2A2E35),
    disabledContainer = Color(0xFF2A2E35),
    locatorPlate = LocatorPlate,
    locatorInk = LocatorInk,
    markMet = Color(0xFF5CCB86),
    markAlready = Color(0xFF5CC8D1),
    markUnknown = Color(0xFF9C86F0),
    markWrong = Color(0xFFD9A800),
    precheck = StatusColor(Color(0xFF2A2E35), Color(0xFFD3D8E0), Color(0xFFD3D8E0)),
    waiting = StatusColor(Color(0xFF1B2A4D), Color(0xFFB9CCFF), Color(0xFF8FB0FF)),
    already = StatusColor(Color(0xFF123638), Color(0xFF8FDCE2), Color(0xFF5CC8D1)),
    partial = StatusColor(Color(0xFF173322), Color(0xFF9FE0B4), Color(0xFF5CCB86)),
    correction = StatusColor(Color(0xFF3D3000), Color(0xFFFFE08A), Color(0xFFD9A800)),
    done = StatusColor(Color(0xFF7FD9A0), Color(0xFF0B2E18), Color(0xFF0B2E18)),
    unverifiable = StatusColor(Color(0xFF2B2147), Color(0xFFCDBDFF), Color(0xFF9C86F0)),
    skipped = StatusColor(Color(0xFF2A2E35), Color(0xFFB4BAC4), Color(0xFF8A929E)),
)

private fun scheme(dark: Boolean): ColorScheme {
    val primary = if (dark) Color(0xFF8FB0FF) else Color(0xFF2350C8)
    val onPrimary = if (dark) Color(0xFF0A1F52) else Color(0xFFFFFFFF)
    val background = if (dark) Color(0xFF121417) else Color(0xFFF1F3F6)
    val surface = if (dark) Color(0xFF1C1F24) else Color(0xFFFFFFFF)
    val surfaceVariant = if (dark) Color(0xFF262A31) else Color(0xFFE8EBF0)
    val onSurface = if (dark) Color(0xFFF1F3F6) else Color(0xFF16181C)
    val onSurfaceVariant = if (dark) Color(0xFFB4BAC4) else Color(0xFF4A505A)
    val outline = if (dark) Color(0xFF8A929E) else Color(0xFF6B7380)
    val outlineVariant = if (dark) Color(0xFF343942) else Color(0xFFDDE1E7)
    val error = if (dark) Color(0xFFFF8A80) else Color(0xFFC62828)
    val onError = if (dark) Color(0xFF3B0909) else Color(0xFFFFFFFF)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    // 쓰지 않는 칸도 기본 보라색이 새어 나오지 않게 같은 계열로 채운다(문서 1-1절).
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
        surfaceContainerHigh = surface, surfaceContainerHighest = surface,
        inverseSurface = onSurface, inverseOnSurface = surface, inversePrimary = primary,
        outline = outline, outlineVariant = outlineVariant,
        error = error, onError = onError,
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

/** 앱 색. `MaterialTheme.colorScheme`에 없는 것(상태 색, 누름 색 등). */
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
