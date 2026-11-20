package com.finops.app.ui.feature.portfolio

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finops.app.common.runSuspendCatching
import com.finops.app.domain.model.ChartRange
import com.finops.app.domain.usecase.DeletePortfolioUseCase
import com.finops.app.domain.usecase.GetPortfolioHistoryUseCase
import com.finops.app.domain.usecase.ObservePortfolioDetailUseCase
import com.finops.app.domain.usecase.PortfolioDetail
import com.finops.app.domain.usecase.RenamePortfolioUseCase
import com.finops.app.ui.common.ChartUiState
import com.finops.app.ui.common.ExposureState
import com.finops.app.ui.common.exposureFlow
import com.finops.app.domain.usecase.GetExposureUseCase
import com.finops.app.domain.usecase.ObserveSettingsUseCase
import kotlinx.coroutines.flow.first
import com.finops.app.ui.common.historyChartFlow
import com.finops.app.ui.common.userMessage
import com.finops.app.ui.feature.dashboard.DEFAULT_RANGE
import com.finops.app.ui.navigation.RouteArgs
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

data class PortfolioUiState(
    val loading: Boolean = true,
    /** null une fois chargé = portefeuille supprimé. */
    val detail: PortfolioDetail? = null,
    val range: ChartRange = DEFAULT_RANGE,
    val chart: ChartUiState = ChartUiState(),
    val exposure: ExposureState = ExposureState(),
    val deleted: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeDetail: ObservePortfolioDetailUseCase,
    private val getHistory: GetPortfolioHistoryUseCase,
    private val renamePortfolio: RenamePortfolioUseCase,
    private val deletePortfolio: DeletePortfolioUseCase,
    private val getExposure: GetExposureUseCase,
    observeSettings: ObserveSettingsUseCase,
) : ViewModel() {
    val portfolioId: Long = checkNotNull(savedStateHandle[RouteArgs.PORTFOLIO_ID])

    private val range = MutableStateFlow(DEFAULT_RANGE)
    private val local = MutableStateFlow(Local())

    private data class Local(val deleted: Boolean = false, val message: String? = null)

    private val detail = observeDetail(portfolioId).shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val positionsKey = detail.map { d -> d?.holdings?.map { it.asset.id to it.quantity }.orEmpty() }

    private val chart = historyChartFlow(positionsKey, range) { r -> getHistory(portfolioId, r) }

    private val exposure = exposureFlow(positionsKey) { getExposure(portfolioId) }

    val uiState: StateFlow<PortfolioUiState> = combine(detail, range, chart, local, exposure) { d, r, c, l, e ->
        PortfolioUiState(loading = false, detail = d, range = r, chart = c, exposure = e, deleted = l.deleted, message = l.message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PortfolioUiState())

    init {
        viewModelScope.launch { range.value = observeSettings().first().defaultRange }
    }

    fun selectRange(r: ChartRange) {
        range.value = r
    }

    fun rename(name: String) = launch { renamePortfolio(portfolioId, name) }

    fun delete() = launch {
        deletePortfolio(portfolioId)
        local.update { it.copy(deleted = true) }
    }

    fun onMessageShown() = local.update { it.copy(message = null) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            runSuspendCatching { block() }.onFailure { e -> local.update { it.copy(message = e.userMessage()) } }
        }
    }
}
