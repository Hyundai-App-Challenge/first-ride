package com.firstride.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 차량 화면은 멀리서 훑어본다. 기본보다 큰 글자를 쓴다. */
private val CarTypography = Typography(
    headlineMedium = TextStyle(fontSize = 38.sp, fontWeight = FontWeight.Bold, lineHeight = 48.sp),
    headlineSmall = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 42.sp),
    titleLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp),
    titleMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp),
    bodyLarge = TextStyle(fontSize = 24.sp, lineHeight = 34.sp),
    bodyMedium = TextStyle(fontSize = 21.sp, lineHeight = 30.sp),
    labelLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
)

/** 주간·야간은 시스템을 따른다. 앱에 테마 설정을 두지 않는다(POL-U-3). */
@Composable
fun FirstRideTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        typography = CarTypography,
        content = content,
    )
}
