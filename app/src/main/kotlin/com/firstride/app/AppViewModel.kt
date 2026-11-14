package com.firstride.app

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 앱 전체의 화면 상태. 화면 전환은 여기서만 한다(기술 설계 12절).
 * 창 크기·방향이 바뀌어도 ViewModel이 남아 진행 상태가 유지된다(POL-E-8).
 */
class AppViewModel(val container: AppContainer) : ViewModel() {
    private val _screen = MutableStateFlow<Screen>(Screen.Start)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    fun navigate(to: Screen) {
        _screen.value = to
    }
}
