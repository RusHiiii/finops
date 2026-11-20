package com.finops.app.ui.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finops.app.common.runSuspendCatching
import com.finops.app.domain.model.ChartRange
import com.finops.app.domain.usecase.CreatePortfolioUseCase
import com.finops.app.domain.usecase.Dashboard
import com.finops.app.domain.usecase.GetPortfolioHistoryUseCase
import com.finops.app.domain.usecase.ObserveDashboardUseCase
import com.finops.app.domain.usecase.RefreshQuotesUseCase
import com.finops.app.ui.common.ChartUiState
import com.finops.app.ui.common.ExposureState
import com.finops.app.ui.common.exposureFlow
import com.finops.app.domain.usecase.GetExposureUseCase
import com.finops.app.domain.usecase.ObserveSettingsUseCase
import kotlinx.coroutines.flow.first
import com.finops.app.ui.common.historyChartFlow
import com.finops.app.ui.common.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val loading: Boolean = true,
    val dashboard: Dashboard? = null,
    val range: ChartRange = DEFAULT_RANGE,
    val chart: ChartUiState = ChartUiState(),
    val refreshing: Boolean = false,
    val exposure: ExposureState = ExposureState(),
    /** Message ponctuel à afficher (snackbar), acquitté par [DashboardViewModel.onMessageShown]. */
    val message: String? = null,
)

val DEFAULT_RANGE = ChartRange.ONE_YEAR

@HiltViewModel
class DashboardViewModel @Inject constructor(
    observeDashboard: ObserveDashboardUseCase,
    private val getHistory: GetPortfolioHistoryUseCase,
    private val refreshQuotes: RefreshQuotesUseCase,
    private val createPortfolio: CreatePortfolioUseCase,
    private val getExposure: GetExposureUseCase,
    private val observeSettings: ObserveSettingsUseCase,
) : ViewModel() {

    private val range = MutableStateFlow(DEFAULT_RANGE)
    private val local = MutableStateFlow(LocalState())

    private data class LocalState(val refreshing: Boolean = false, val message: String? = null)

    private val dashboard = observeDashboard().shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val positionsKey = dashboard.map { d -> d.holdings.map { it.asset.id to it.quantity } }

    private val chart = historyChartFlow(positionsKey, range) { r -> getHistory(portfolioId = null, range = r) }

    private val exposure = exposureFlow(positionsKey) { getExposure(portfolioId = null) }

    val uiState: StateFlow<DashboardUiState> =
        combine(dashboard, range, chart, local, exposure) { d, r, c, l, e ->
            DashboardUiState(
                loading = false,
                dashboard = d,
                range = r,
                chart = c,
                refreshing = l.refreshing,
                exposure = e,
                message = l.message,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    init {
        viewModelScope.launch {
            val settings = observeSettings().first()
            range.value = settings.defaultRange
            if (settings.refreshOnLaunch) refresh()
        }
    }

    fun selectRange(r: ChartRange) {
        range.value = r
    }

    fun refresh() {
        if (local.value.refreshing) return
        viewModelScope.launch {
            local.update { it.copy(refreshing = true) }
            val result = refreshQuotes()
            local.update {
                it.copy(
                    refreshing = false,
                    message = if (result.isSuccess) null else "Cours indisponibles : ${result.failures.joinToString()}",
                )
            }
        }
    }

    fun createPortfolio(name: String) {
        viewModelScope.launch {
            runSuspendCatching { createPortfolio.invoke(name) }
                .onFailure { e -> local.update { it.copy(message = e.userMessage()) } }
        }
    }

    fun onMessageShown() = local.update { it.copy(message = null) }
}
