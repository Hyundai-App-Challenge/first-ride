package com.firstride

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.firstride.app.AppViewModel
import com.firstride.app.FirstRideApp

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels {
        viewModelFactory { initializer { AppViewModel((application as FirstRideApp).container) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val screen by vm.screen.collectAsStateWithLifecycle()
            // 화면은 W-14(A)와 W-15(B)에서 채운다.
            Text("${getString(R.string.app_name)} — $screen")
        }
    }
}
