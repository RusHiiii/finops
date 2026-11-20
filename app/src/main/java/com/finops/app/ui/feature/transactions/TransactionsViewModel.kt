package com.finops.app.ui.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finops.app.common.runSuspendCatching
import com.finops.app.domain.model.Portfolio
import com.finops.app.domain.model.Transaction
import com.finops.app.domain.model.TransactionEntry
import com.finops.app.domain.usecase.DeleteTransactionUseCase
import com.finops.app.domain.usecase.ObserveTransactionHistoryUseCase
import com.finops.app.ui.common.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class MonthGroup(val month: YearMonth, val entries: List<TransactionEntry>)

data class TransactionsUiState(
    val loading: Boolean = true,
    val months: List<MonthGroup> = emptyList(),
    /** Portefeuilles ayant au moins une transaction (filtres). */
    val portfolios: List<Portfolio> = emptyList(),
    /** null = tous. */
    val filter: Long? = null,
    val message: String? = null,
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    observeHistory: ObserveTransactionHistoryUseCase,
    private val deleteTransaction: DeleteTransactionUseCase,
) : ViewModel() {
    private val filter = MutableStateFlow<Long?>(null)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TransactionsUiState> = combine(observeHistory(), filter, message) { all, f, m ->
        val visible = if (f == null) all else all.filter { it.portfolio.id == f }
        TransactionsUiState(
            loading = false,
            months = visible.groupBy { YearMonth.from(it.transaction.date) }.map { (k, v) -> MonthGroup(k, v) },
            portfolios = all.map { it.portfolio }.distinctBy { it.id }.sortedBy { it.id },
            filter = f,
            message = m,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    fun setFilter(portfolioId: Long?) {
        filter.value = portfolioId
    }

    fun delete(transaction: Transaction) {
        viewModelScope.launch {
            runSuspendCatching { deleteTransaction(transaction) }.onFailure { e -> message.value = e.userMessage() }
        }
    }

    fun onMessageShown() = message.update { null }
}
