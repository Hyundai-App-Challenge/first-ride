package com.firstride

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.firstride.app.AppNavigation
import com.firstride.app.AppViewModel
import com.firstride.app.FirstRideApp
import com.firstride.app.start.CourseCatalog
import com.firstride.app.start.CourseTexts
import com.firstride.app.ui.DevPanel
import com.firstride.app.ui.FirstRideTheme

/** 앱의 유일한 Activity. 화면 분기는 [AppNavigation]에 있다. */
class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels {
        viewModelFactory {
            initializer { AppViewModel((application as FirstRideApp).container, CourseTexts.load(assets)) { CourseCatalog.load(assets) } }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }

    /** 백그라운드 전환. 창 크기·방향·주야간 변경에는 오지 않는다(configChanges, 실측 T14·T15). */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) vm.onBackground()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FirstRideTheme {
                Box {
                    Surface {
                        val texts = remember { CourseTexts.load(assets) }
                        AppNavigation(vm, texts, loadImage = { path -> CourseTexts.loadImage(assets, path) })
                    }
                    DevPanel()
                }
            }
        }
    }
}
