package com.finops.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.finops.app.common.IoDispatcher
import com.finops.app.domain.model.AccentColor
import com.finops.app.domain.model.AppSettings
import com.finops.app.domain.model.ChartRange
import com.finops.app.domain.model.CurrencyCode
import com.finops.app.domain.model.ThemeMode
import com.finops.app.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Préférences stockées dans des SharedPreferences, exposées en flux. */
@Singleton
class PreferencesSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : SettingsRepository {
    private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun observeSettings(): Flow<AppSettings> = callbackFlow {
        trySend(read())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate().distinctUntilChanged().flowOn(io)

    override suspend fun update(transform: (AppSettings) -> AppSettings) = withContext(io) {
        val s = transform(read())
        prefs.edit()
            .putString(KEY_THEME, s.theme.name)
            .putString(KEY_ACCENT, s.accent.name)
            .putString(KEY_RANGE, s.defaultRange.name)
            .putBoolean(KEY_REFRESH, s.refreshOnLaunch)
            .putString(KEY_CURRENCIES, s.currencies.joinToString(",") { it.name })
            .putBoolean(KEY_NOTIFY_EARNINGS, s.notifyEarnings)
            .putString(KEY_DISPLAY_CURRENCY, s.displayCurrency.name)
            .putBoolean(KEY_BIOMETRIC_LOCK, s.biometricLock)
            .apply()
    }

    private fun read(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            theme = prefs.getString(KEY_THEME, null)?.let { v -> ThemeMode.entries.firstOrNull { it.name == v } } ?: defaults.theme,
            accent = prefs.getString(KEY_ACCENT, null)?.let { v -> AccentColor.entries.firstOrNull { it.name == v } } ?: defaults.accent,
            defaultRange = prefs.getString(KEY_RANGE, null)?.let { v -> ChartRange.entries.firstOrNull { it.name == v } } ?: defaults.defaultRange,
            refreshOnLaunch = prefs.getBoolean(KEY_REFRESH, defaults.refreshOnLaunch),
            currencies = prefs.getString(KEY_CURRENCIES, null)
                ?.split(',')?.mapNotNull { v -> CurrencyCode.entries.firstOrNull { it.name == v } }?.toSet()
                ?.takeIf { it.isNotEmpty() } ?: defaults.currencies,
            notifyEarnings = prefs.getBoolean(KEY_NOTIFY_EARNINGS, defaults.notifyEarnings),
            displayCurrency = prefs.getString(KEY_DISPLAY_CURRENCY, null)?.let { v -> CurrencyCode.entries.firstOrNull { it.name == v } }
                ?: defaults.displayCurrency,
            biometricLock = prefs.getBoolean(KEY_BIOMETRIC_LOCK, defaults.biometricLock),
        )
    }

    private companion object {
        const val FILE = "settings"
        const val KEY_THEME = "theme"
        const val KEY_ACCENT = "accent"
        const val KEY_RANGE = "default_range"
        const val KEY_REFRESH = "refresh_on_launch"
        const val KEY_CURRENCIES = "currencies"
        const val KEY_NOTIFY_EARNINGS = "notify_earnings"
        const val KEY_DISPLAY_CURRENCY = "display_currency"
        const val KEY_BIOMETRIC_LOCK = "biometric_lock"
    }
}
