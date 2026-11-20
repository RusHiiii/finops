package com.finops.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.toArgb
import com.finops.app.ui.designsystem.FinOpsTheme
import com.finops.app.ui.designsystem.windowBackground
import com.finops.app.ui.navigation.FinOpsApp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finops.app.domain.model.AppSettings
import com.finops.app.domain.model.ThemeMode
import com.finops.app.domain.usecase.ObserveSettingsUseCase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var observeSettings: ObserveSettingsUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by observeSettings().collectAsStateWithLifecycle(initialValue = AppSettings())
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }
            DisposableEffect(dark, settings.accent) {
                window.decorView.setBackgroundColor(windowBackground(settings.accent, dark).toArgb())
                onDispose {}
            }
            FinOpsTheme(darkTheme = dark, accent = settings.accent) {
                FinOpsApp()
            }
        }
    }

    private companion object {
        // Valeurs par défaut d'androidx.activity pour le voile de la barre de navigation.
        val LIGHT_SCRIM = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
