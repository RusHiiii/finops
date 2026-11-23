package com.finops.app.ui.feature.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finops.app.common.runSuspendCatching
import com.finops.app.domain.model.NewsArticle
import com.finops.app.domain.model.WatchedEarnings
import com.finops.app.domain.usecase.GetMarketNewsUseCase
import com.finops.app.domain.usecase.GetWatchlistEarningsUseCase
import com.finops.app.domain.usecase.ObserveWatchlistUseCase
import com.finops.app.ui.common.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Contenu chargé à la demande : [items] null tant que rien n'a été chargé. */
data class Loadable<T>(
    val items: T? = null,
    val refreshing: Boolean = false,
    val error: String? = null,
)

data class NewsUiState(
    val news: Loadable<List<NewsArticle>> = Loadable(),
    val earnings: Loadable<List<WatchedEarnings>> = Loadable(),
    /** La watchlist contient au moins une action américaine. */
    val hasUsStocks: Boolean = true,
)

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val getMarketNews: GetMarketNewsUseCase,
    private val getWatchlistEarnings: GetWatchlistEarningsUseCase,
    observeWatchlist: ObserveWatchlistUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(NewsUiState())
    val uiState: StateFlow<NewsUiState> = state.asStateFlow()

    private var newsJob: Job? = null
    private var earningsJob: Job? = null

    init {
        refreshNews()
        // Recharge le calendrier quand les actions suivies changent.
        viewModelScope.launch {
            observeWatchlist()
                .map { items -> items.map { it.symbol }.toSet() }
                .distinctUntilChanged()
                .collect { refreshEarnings() }
        }
    }

    fun refreshNews() {
        newsJob?.cancel()
        newsJob = viewModelScope.launch {
            state.update { it.copy(news = it.news.copy(refreshing = true, error = null)) }
            runSuspendCatching { getMarketNews() }
                .onSuccess { list -> state.update { it.copy(news = Loadable(list)) } }
                .onFailure { e -> state.update { it.copy(news = it.news.copy(refreshing = false, error = e.userMessage())) } }
        }
    }

    fun refreshEarnings() {
        earningsJob?.cancel()
        earningsJob = viewModelScope.launch {
            state.update { it.copy(earnings = it.earnings.copy(refreshing = true, error = null)) }
            runSuspendCatching { getWatchlistEarnings() }
                .onSuccess { r -> state.update { it.copy(earnings = Loadable(r.events), hasUsStocks = r.trackedCount > 0) } }
                .onFailure { e -> state.update { it.copy(earnings = it.earnings.copy(refreshing = false, error = e.userMessage())) } }
        }
    }
}
