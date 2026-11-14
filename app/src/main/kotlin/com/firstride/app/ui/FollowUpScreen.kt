package com.firstride.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.firstride.app.AppViewModel
import com.firstride.app.Screen

/**
 * SCR-11 후속 조치 확인(FR-16). 자리만 있다. 이 파일은 후속 조치 묶음이 채운다.
 * 연결은 [AppNavigation]에 이미 되어 있어서 이 함수의 본문만 바꾸면 된다.
 */
@Composable
fun FollowUpScreen(vm: AppViewModel, screen: Screen.FollowUp) {
    Column(Modifier.fillMaxSize().padding(32.dp)) {
        Text("후속 조치 (${screen.courseId}, 준비 중)")
        TextButton(onClick = { vm.navigate(Screen.Home) }) { Text("홈으로") }
    }
}
