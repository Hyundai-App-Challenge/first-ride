package com.firstride

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.firstride.app.AppViewModel
import com.firstride.app.FirstRideApp
import com.firstride.app.Screen
import com.firstride.app.start.CourseCatalog
import com.firstride.app.ui.ConnectionErrorScreen
import com.firstride.app.ui.SettingsScreen
import com.firstride.app.ui.StartScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels {
        viewModelFactory {
            initializer { AppViewModel((application as FirstRideApp).container) { CourseCatalog.load(assets) } }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    val screen by vm.screen.collectAsStateWithLifecycle()
                    val catalog by vm.catalog.collectAsStateWithLifecycle()
                    when (val s = screen) {
                        Screen.Start -> StartScreen()
                        Screen.ConnectionError -> ConnectionErrorScreen(onRetry = vm::start)
                        Screen.Settings -> SettingsScreen(catalog, onClear = vm::clearRecords, onBack = { vm.navigate(Screen.Home) })
                        // 홈과 나머지 화면은 W-14(A)에서 채운다. 아래는 흐름을 확인하기 위한 자리 표시다.
                        Screen.Home -> Column(Modifier.padding(32.dp)) {
                            val visible = catalog?.visible.orEmpty()
                            if (visible.isEmpty()) Text(stringResource(R.string.no_supported_courses))
                            visible.forEach { Text(it.name) }
                            TextButton(onClick = { vm.navigate(Screen.Settings) }) { Text(stringResource(R.string.settings_title)) }
                        }
                        else -> Text("$s")
                    }
                }
            }
        }
    }
}
