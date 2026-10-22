package com.obpiub.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.obpiub.myapplication.presentation.AppNavigation
import com.obpiub.myapplication.presentation.theme.MyApplicationTheme
import com.obpiub.myapplication.presentation.SettingsViewModel
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as NotesApplication).container
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(factory = viewModelFactory {
                initializer { SettingsViewModel(container.settingsRepository, container.balanceRepository) }
            })
            val state by settingsViewModel.state.collectAsStateWithLifecycle()
            MyApplicationTheme(state.settings) {
                val lightBackground = MaterialTheme.colorScheme.background.luminance() > 0.5f
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = lightBackground
                        isAppearanceLightNavigationBars = lightBackground
                    }
                }
                if (!state.hasLoadedSettings) {
                    Surface {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    AppNavigation(container, settingsViewModel)
                }
            }
        }
    }
}
