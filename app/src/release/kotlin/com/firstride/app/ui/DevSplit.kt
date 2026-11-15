package com.firstride.app.ui

import androidx.compose.runtime.Composable

/** release 빌드에는 내비게이션 mock이 없다. 내용을 그대로 그린다. */
@Composable
fun DevSplitFrame(content: @Composable () -> Unit) = content()
