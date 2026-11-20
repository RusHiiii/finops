package com.finops.app.ui.feature.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finops.app.common.runSuspendCatching
import com.finops.app.domain.model.Symbol
import com.finops.app.domain.model.WatchlistItem
import com.finops.app.domain.usecase.ObserveWatchlistUseCase
import com.finops.app.domain.usecase.RefreshQuotesUseCase
import com.finops.app.domain.usecase.RemoveFromWatchlistUseCase
import com.finops.app.ui.common.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WatchlistUiState(
    val loading: Boolean = true,
    val items: List<WatchlistItem> = emptyList(),
    val refreshing: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    observeWatchlist: ObserveWatchlistUseCase,
    private val refreshQuotes: RefreshQuotesUseCase,
    private val removeFromWatchlist: RemoveFromWatchlistUseCase,
) : ViewModel() {
    private data class Local(val refreshing: Boolean = false, val message: String? = null)

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<WatchlistUiState> = combine(observeWatchlist(), local) { items, l ->
        WatchlistUiState(loading = false, items = items, refreshing = l.refreshing, message = l.message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchlistUiState())

    fun refresh() {
        if (local.value.refreshing) return
        viewModelScope.launch {
            local.update { it.copy(refreshing = true) }
            val result = refreshQuotes()
            local.update {
                it.copy(refreshing = false, message = if (result.isSuccess) null else "Cours indisponibles : ${result.failures.joinToString()}")
            }
        }
    }

    fun remove(symbol: Symbol) {
        viewModelScope.launch {
            runSuspendCatching { removeFromWatchlist(symbol) }.onFailure { e -> local.update { it.copy(message = e.userMessage()) } }
        }
    }

    fun onMessageShown() = local.update { it.copy(message = null) }
}
