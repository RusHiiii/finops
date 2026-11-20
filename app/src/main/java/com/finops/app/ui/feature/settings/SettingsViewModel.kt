package com.finops.app.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finops.app.domain.model.AccentColor
import com.finops.app.domain.model.AppSettings
import com.finops.app.domain.model.ChartRange
import com.finops.app.domain.model.CurrencyCode
import com.finops.app.domain.model.ThemeMode
import com.finops.app.common.runSuspendCatching
import com.finops.app.domain.usecase.DeleteAllDataUseCase
import com.finops.app.domain.usecase.ExportDataUseCase
import com.finops.app.domain.usecase.ImportDataUseCase
import com.finops.app.domain.usecase.ObserveSettingsUseCase
import com.finops.app.ui.common.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.LocalDate
import com.finops.app.domain.usecase.UpdateSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
    private val exportData: ExportDataUseCase,
    private val importData: ImportDataUseCase,
    private val deleteAllData: DeleteAllDataUseCase,
    private val clock: Clock,
) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        observeSettings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    /** Opération d'export / import en cours. */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Message ponctuel (snackbar). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Nom proposé pour le fichier de sauvegarde. */
    fun backupFileName(): String = "finops-sauvegarde-${LocalDate.now(clock)}.json"

    fun export(uri: String) = run("Données exportées") { exportData(uri) }

    fun import(uri: String) = run(null) {
        val r = importData(uri)
        _message.value = "Import terminé : ${r.portfolios} portefeuille(s), ${r.transactions} transaction(s), ${r.watchlist} suivi(s)"
    }

    fun deleteAll() = run("Toutes les données ont été supprimées") { deleteAllData() }

    fun onMessageShown() {
        _message.value = null
    }

    private fun run(success: String?, block: suspend () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            runSuspendCatching { block() }
                .onSuccess { success?.let { _message.value = it } }
                .onFailure { e -> _message.value = e.userMessage() }
            _busy.value = false
        }
    }

    fun setTheme(theme: ThemeMode) = update { it.copy(theme = theme) }
    fun setAccent(accent: AccentColor) = update { it.copy(accent = accent) }
    fun setDefaultRange(range: ChartRange) = update { it.copy(defaultRange = range) }
    fun setRefreshOnLaunch(enabled: Boolean) = update { it.copy(refreshOnLaunch = enabled) }

    /** Active / désactive une devise ; au moins une devise reste toujours suivie. */
    fun setCurrency(currency: CurrencyCode, enabled: Boolean) = update {
        val next = if (enabled) it.currencies + currency else it.currencies - currency
        if (next.isEmpty()) it else it.copy(currencies = next)
    }

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { updateSettings(transform) }
    }
}
