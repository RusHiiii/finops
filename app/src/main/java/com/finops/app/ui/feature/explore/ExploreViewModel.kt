package com.finops.app.ui.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finops.app.common.runSuspendCatching
import com.finops.app.domain.model.SearchResult
import com.finops.app.domain.model.Symbol
import com.finops.app.domain.model.CurrencyCode
import com.finops.app.domain.usecase.ObserveSettingsUseCase
import com.finops.app.domain.usecase.SearchInstrumentsUseCase
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.finops.app.ui.common.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExploreUiState(
    val query: String = "",
    /** Suggestions limitées aux devises suivies. */
    val suggestions: List<Suggestion> = SUGGESTIONS,
    val results: List<SearchResult> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
) {
    /** Saisie trop courte : afficher les suggestions. */
    val showSuggestions: Boolean get() = query.trim().length < SearchInstrumentsUseCase.MIN_QUERY_LENGTH
}

/** Instruments populaires proposés avant toute recherche. */
data class Suggestion(val symbol: Symbol, val name: String, val currency: CurrencyCode)

val SUGGESTIONS = listOf(
    Suggestion(Symbol("CW8", "EPA"), "Amundi MSCI World", CurrencyCode.EUR),
    Suggestion(Symbol("EWLD", "EPA"), "Amundi PEA MSCI World", CurrencyCode.EUR),
    Suggestion(Symbol("ESE", "EPA"), "BNP Paribas Easy S&P 500", CurrencyCode.EUR),
    Suggestion(Symbol("PAEEM", "EPA"), "Amundi PEA Emergents", CurrencyCode.EUR),
    Suggestion(Symbol("MC", "EPA"), "LVMH", CurrencyCode.EUR),
    Suggestion(Symbol("AI", "EPA"), "Air Liquide", CurrencyCode.EUR),
    Suggestion(Symbol("AAPL", "NASDAQ"), "Apple", CurrencyCode.USD),
    Suggestion(Symbol("NVDA", "NASDAQ"), "NVIDIA", CurrencyCode.USD),
)

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val search: SearchInstrumentsUseCase,
    observeSettings: ObserveSettingsUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(ExploreUiState())
    val state: StateFlow<ExploreUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        // Devises modifiées : suggestions filtrées et recherche en cours relancée.
        viewModelScope.launch {
            observeSettings().map { it.currencies }.distinctUntilChanged().collect { allowed ->
                _state.update { it.copy(suggestions = SUGGESTIONS.filter { s -> s.currency in allowed }) }
                if (!_state.value.showSuggestions) onQueryChange(_state.value.query)
            }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query, error = null) }
        job?.cancel()
        if (_state.value.showSuggestions) {
            _state.update { it.copy(results = emptyList(), loading = false) }
            return
        }
        job = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            _state.update { it.copy(loading = true) }
            runSuspendCatching { search(query) }
                .onSuccess { r ->
                    _state.update { it.copy(results = r, loading = false, error = if (r.isEmpty()) "Aucun résultat pour « ${query.trim()} » dans les devises suivies" else null) }
                }
                .onFailure { e -> _state.update { it.copy(results = emptyList(), loading = false, error = e.userMessage()) } }
        }
    }

    companion object {
        const val DEBOUNCE_MS = 350L
    }
}
