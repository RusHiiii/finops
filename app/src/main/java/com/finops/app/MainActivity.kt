package com.finops.app

import android.os.Bundle
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import com.finops.app.ui.security.AppLockViewModel
import com.finops.app.ui.security.LockScreen
import com.finops.app.ui.security.LockState
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
import com.finops.app.domain.usecase.ObserveDisplayCurrencyUseCase
import com.finops.app.ui.common.LocalMoney
import com.finops.app.ui.common.MoneyDisplay
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
/** FragmentActivity : requise par BiometricPrompt. */
class MainActivity : FragmentActivity() {
    @Inject
    lateinit var observeSettings: ObserveSettingsUseCase

    @Inject
    lateinit var observeDisplayCurrency: ObserveDisplayCurrencyUseCase

    private val appLock: AppLockViewModel by viewModels()

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
            val lock by appLock.state.collectAsStateWithLifecycle()
            val display by observeDisplayCurrency().collectAsStateWithLifecycle(initialValue = null)
            val money = remember(display) { display?.let { MoneyDisplay(it.currency, it.usdToEur) } ?: MoneyDisplay() }
            FinOpsTheme(darkTheme = dark, accent = settings.accent) {
                when (lock) {
                    LockState.UNLOCKED -> CompositionLocalProvider(LocalMoney provides money) { FinOpsApp() }
                    LockState.LOCKED -> LockScreen(onUnlocked = appLock::onUnlocked)
                    null -> Unit // Préférences en cours de lecture : rien n'est affiché.
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appLock.onForeground()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) appLock.onBackground()
    }

    private companion object {
        // Valeurs par défaut d'androidx.activity pour le voile de la barre de navigation.
        val LIGHT_SCRIM = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
