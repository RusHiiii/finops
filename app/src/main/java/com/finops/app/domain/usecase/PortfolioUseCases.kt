package com.finops.app.domain.usecase

import com.finops.app.domain.calculator.AllocationCalculator
import com.finops.app.domain.calculator.PortfolioCalculator
import com.finops.app.domain.model.Allocation
import com.finops.app.domain.model.Holding
import com.finops.app.domain.model.Portfolio
import com.finops.app.domain.model.PortfolioOverview
import com.finops.app.domain.model.PortfolioSummary
import com.finops.app.domain.model.Transaction
import com.finops.app.domain.model.TransactionEntry
import com.finops.app.domain.model.ValidationException
import com.finops.app.domain.repository.PortfolioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Vue consolidée de tous les portefeuilles. */
data class Dashboard(
    val total: PortfolioSummary,
    val portfolios: List<PortfolioOverview>,
    /** Positions ouvertes consolidées par actif, triées par valeur décroissante. */
    val holdings: List<Holding>,
    val allocation: Allocation,
    val usdToEur: Double?,
    /** Date du dernier rafraîchissement des cours (le plus ancien des actifs détenus). */
    val quotesUpdatedAt: Long?,
) {
    val isEmpty: Boolean get() = holdings.isEmpty() && total.realizedPnlEur == 0.0
}

class ObserveDashboardUseCase @Inject constructor(private val repository: PortfolioRepository) {
    operator fun invoke(): Flow<Dashboard> = combine(
        repository.observePortfolios(),
        repository.observeAssets(),
        repository.observeTransactions(),
        repository.observeUsdToEur(),
    ) { portfolios, assets, transactions, fx ->
        val byPortfolio = transactions.groupBy { it.portfolioId }
        val consolidated = PortfolioCalculator.holdings(assets, transactions)
        val open = consolidated.filter { it.isOpen }
        val total = PortfolioCalculator.summary(consolidated, fx)
        val overviews = portfolios.map { p ->
            val holdings = PortfolioCalculator.holdings(assets, byPortfolio[p.id].orEmpty())
            val summary = PortfolioCalculator.summary(holdings, fx)
            val openHoldings = holdings.filter { it.isOpen }
            PortfolioOverview(
                portfolio = p,
                summary = summary,
                openPositions = openHoldings.size,
                weight = if (total.totalValueEur > 0) summary.totalValueEur / total.totalValueEur else 0.0,
                quotesUpdatedAt = openHoldings.mapNotNull { it.asset.lastPriceAt }.minOrNull(),
            )
        }
        Dashboard(
            total = total,
            portfolios = overviews,
            holdings = open.sortedByDescending { h -> h.marketValue * (PortfolioCalculator.rateToEur(h.asset.currency, fx) ?: 0.0) },
            allocation = AllocationCalculator.allocation(open, fx),
            usdToEur = fx,
            quotesUpdatedAt = open.mapNotNull { it.asset.lastPriceAt }.minOrNull(),
        )
    }
}

data class PortfolioDetail(
    val portfolio: Portfolio,
    val summary: PortfolioSummary,
    /** Positions ouvertes triées par valeur décroissante. */
    val holdings: List<Holding>,
    /** Positions soldées (plus-value réalisée uniquement). */
    val closedHoldings: List<Holding>,
    val allocation: Allocation,
    val usdToEur: Double?,
    /** Plus ancienne mise à jour de cours parmi les positions ouvertes. */
    val quotesUpdatedAt: Long?,
)

class ObservePortfolioDetailUseCase @Inject constructor(private val repository: PortfolioRepository) {
    /** Émet null si le portefeuille n'existe pas (ou plus). */
    operator fun invoke(portfolioId: Long): Flow<PortfolioDetail?> = combine(
        repository.observePortfolio(portfolioId),
        repository.observeAssets(),
        repository.observeTransactions().map { txs -> txs.filter { it.portfolioId == portfolioId } },
        repository.observeUsdToEur(),
    ) { portfolio, assets, transactions, fx ->
        portfolio ?: return@combine null
        val holdings = PortfolioCalculator.holdings(assets, transactions)
        val (open, closed) = holdings.partition { it.isOpen }
        PortfolioDetail(
            portfolio = portfolio,
            summary = PortfolioCalculator.summary(holdings, fx),
            holdings = open.sortedByDescending { h -> h.marketValue * (PortfolioCalculator.rateToEur(h.asset.currency, fx) ?: 0.0) },
            closedHoldings = closed,
            allocation = AllocationCalculator.allocation(open, fx),
            usdToEur = fx,
            quotesUpdatedAt = open.mapNotNull { it.asset.lastPriceAt }.minOrNull(),
        )
    }
}

data class HoldingDetail(
    val holding: Holding,
    /** Plus récentes en premier. */
    val transactions: List<TransactionEntry>,
    /** Poids de la position dans le périmètre (portefeuille ou patrimoine), ∈ [0, 1]. */
    val weight: Double?,
)

class ObserveHoldingDetailUseCase @Inject constructor(private val repository: PortfolioRepository) {
    /** [portfolioId] null = toutes les positions sur l'actif, tous portefeuilles confondus. Émet null si plus aucune transaction. */
    operator fun invoke(portfolioId: Long?, assetId: Long): Flow<HoldingDetail?> = combine(
        repository.observePortfolios(),
        repository.observeAssets(),
        repository.observeTransactions(),
        repository.observeUsdToEur(),
    ) { portfolios, assets, all, fx ->
        val asset = assets.firstOrNull { it.id == assetId } ?: return@combine null
        val scope = if (portfolioId == null) all else all.filter { it.portfolioId == portfolioId }
        val txs = scope.filter { it.assetId == assetId }
        if (txs.isEmpty()) return@combine null
        val holding = PortfolioCalculator.holding(asset, txs)
        val scopeValue = PortfolioCalculator.summary(PortfolioCalculator.holdings(assets, scope), fx).totalValueEur
        val holdingValue = PortfolioCalculator.rateToEur(asset.currency, fx)?.let { holding.marketValue * it }
        val portfoliosById = portfolios.associateBy { it.id }
        HoldingDetail(
            holding = holding,
            transactions = txs.sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.id })
                .mapNotNull { t -> portfoliosById[t.portfolioId]?.let { TransactionEntry(t, asset, it) } },
            weight = if (holding.isOpen && holdingValue != null && scopeValue > 0) holdingValue / scopeValue else null,
        )
    }
}

class ObserveTransactionHistoryUseCase @Inject constructor(private val repository: PortfolioRepository) {
    /** Toutes les transactions, plus récentes en premier. */
    operator fun invoke(): Flow<List<TransactionEntry>> = combine(
        repository.observePortfolios(),
        repository.observeAssets(),
        repository.observeTransactions(),
    ) { portfolios, assets, transactions ->
        val p = portfolios.associateBy { it.id }
        val a = assets.associateBy { it.id }
        transactions
            .sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.id })
            .mapNotNull { t -> TransactionEntry(t, a[t.assetId] ?: return@mapNotNull null, p[t.portfolioId] ?: return@mapNotNull null) }
    }
}

class ObservePortfoliosUseCase @Inject constructor(private val repository: PortfolioRepository) {
    operator fun invoke(): Flow<List<Portfolio>> = repository.observePortfolios()
}

class CreatePortfolioUseCase @Inject constructor(private val repository: PortfolioRepository) {
    suspend operator fun invoke(name: String): Long = repository.createPortfolio(validName(name))
}

class RenamePortfolioUseCase @Inject constructor(private val repository: PortfolioRepository) {
    suspend operator fun invoke(id: Long, name: String) = repository.renamePortfolio(id, validName(name))
}

class DeletePortfolioUseCase @Inject constructor(private val repository: PortfolioRepository) {
    suspend operator fun invoke(id: Long) = repository.deletePortfolio(id)
}

internal const val MAX_PORTFOLIO_NAME = 40

internal fun validName(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) throw ValidationException("Le nom est obligatoire")
    if (trimmed.length > MAX_PORTFOLIO_NAME) throw ValidationException("Nom trop long ($MAX_PORTFOLIO_NAME caractères max.)")
    return trimmed
}
