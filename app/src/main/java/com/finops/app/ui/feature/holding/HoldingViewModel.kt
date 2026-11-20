package com.finops.app.ui.feature.holding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finops.app.common.runSuspendCatching
import com.finops.app.domain.model.ChartRange
import com.finops.app.domain.model.Transaction
import com.finops.app.domain.usecase.DeleteTransactionUseCase
import com.finops.app.domain.usecase.GetPriceHistoryUseCase
import com.finops.app.domain.usecase.HoldingDetail
import com.finops.app.domain.usecase.ObserveHoldingDetailUseCase
import com.finops.app.ui.common.ChartUiState
import com.finops.app.ui.common.priceChartFlow
import com.finops.app.ui.common.userMessage
import com.finops.app.ui.navigation.RouteArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HoldingUiState(
    val loading: Boolean = true,
    /** null une fois chargé = plus aucune transaction (dernière supprimée). */
    val detail: HoldingDetail? = null,
    val range: ChartRange = ChartRange.ONE_YEAR,
    val chart: ChartUiState = ChartUiState(),
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HoldingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeHolding: ObserveHoldingDetailUseCase,
    private val getHistory: GetPriceHistoryUseCase,
    private val deleteTransaction: DeleteTransactionUseCase,
) : ViewModel() {
    val assetId: Long = checkNotNull(savedStateHandle[RouteArgs.ASSET_ID])

    /** null = position consolidée tous portefeuilles. */
    val portfolioId: Long? = savedStateHandle.get<Long>(RouteArgs.PORTFOLIO_ID)?.takeIf { it != RouteArgs.ALL }

    private val range = MutableStateFlow(ChartRange.ONE_YEAR)
    private val message = MutableStateFlow<String?>(null)

    private val detail = observeHolding(portfolioId, assetId).shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val chart = detail.filterNotNull()
        .map { it.holding.asset }
        .distinctUntilChanged { a, b -> a.symbol == b.symbol }
        .flatMapLatest { asset ->
            priceChartFlow(range, previousClose = { asset.previousClose }) { r -> getHistory(asset.symbol, r) }
        }
        .onStart { emit(ChartUiState()) }

    val uiState: StateFlow<HoldingUiState> = combine(detail, range, chart, message) { d, r, c, m ->
        HoldingUiState(loading = false, detail = d, range = r, chart = c, message = m)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HoldingUiState())

    fun selectRange(r: ChartRange) {
        range.value = r
    }

    fun delete(transaction: Transaction) {
        viewModelScope.launch {
            runSuspendCatching { deleteTransaction(transaction) }.onFailure { e -> message.value = e.userMessage() }
        }
    }

    fun onMessageShown() = message.update { null }
}
