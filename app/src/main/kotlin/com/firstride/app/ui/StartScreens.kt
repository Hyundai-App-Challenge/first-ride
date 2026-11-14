package com.firstride.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.firstride.R
import kotlinx.coroutines.delay

/** SCR-01 시작. 2초를 넘기면 진행 표시를 보인다(AC-01-1). */
@Composable
fun StartScreen() {
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(2_000)
        slow = true
    }
    CenteredBody {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.l))
        if (slow) {
            Spinner()
            Spacer(Modifier.height(Space.m))
            Text(stringResource(R.string.start_checking), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * SCR-08 주행 중 차단. 문구 하나만 있다. 버튼·목록·움직이는 요소가 없다(POL-D-4, AC-07-1).
 * 플랫폼이 주행 중에 띄우는 회색 화면과 같은 톤이다(문서 5-2절).
 */
@Composable
fun DrivingBlockScreen() {
    BoxWithConstraints(Modifier.fillMaxSize().background(appColors.blockedBg)) {
        val style = if (maxWidth >= Size.wideBreakpoint) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium
        Column(
            Modifier.fillMaxSize().padding(horizontal = Space.screenH),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.driving_block), style = style, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        }
    }
}

/** SCR-09 연결 오류. 무슨 일이 있었는지와 할 수 있는 행동, 다시 시도(FR-12, POL-E-2). */
@Composable
fun ConnectionErrorScreen(onRetry: () -> Unit) {
    CenteredBody {
        Text(stringResource(R.string.connection_error_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.m))
        Text(stringResource(R.string.connection_error_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.l))
        PrimaryButton(stringResource(R.string.retry), onRetry)
    }
}
