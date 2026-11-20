package com.finops.app.domain.usecase

import com.finops.app.common.runSuspendCatching
import com.finops.app.domain.calculator.Classification
import com.finops.app.domain.calculator.DiversificationCalculator
import com.finops.app.domain.calculator.PortfolioCalculator
import com.finops.app.domain.model.AllocationSlice
import com.finops.app.domain.model.AssetType
import com.finops.app.domain.model.Holding
import com.finops.app.domain.model.Weighting
import com.finops.app.domain.repository.FundRepository
import com.finops.app.domain.repository.IsinRepository
import com.finops.app.domain.repository.MarketRepository
import com.finops.app.domain.repository.PortfolioRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Exposition économique (par transparence pour les ETF), en EUR. */
data class Exposure(
    val bySector: List<AllocationSlice>,
    val byCountry: List<AllocationSlice>,
    /** Part de la valeur classée (hors « Non classé »), ∈ [0, 1]. */
    val coverage: Double,
) {
    /** Note de diversification géographique (1–10) ; null si rien n'est classé. */
    val geographicScore: Int? get() = DiversificationCalculator.score(byCountry, DiversificationCalculator.COUNTRY_REFERENCE)

    /** Note de diversification sectorielle (1–10) ; null si rien n'est classé. */
    val sectorScore: Int? get() = DiversificationCalculator.score(bySector, DiversificationCalculator.SECTOR_REFERENCE)
}

/**
 * Répartition sectorielle et géographique d'un portefeuille ([portfolioId] null = patrimoine).
 * Action : secteur et pays Google Finance. ETF : composition justETF (ISIN requis), le reste en « Autre ».
 * Ce qui ne peut pas être classé est regroupé en « Non classé ».
 */
class GetExposureUseCase @Inject constructor(
    private val portfolios: PortfolioRepository,
    private val market: MarketRepository,
    private val funds: FundRepository,
    private val isins: IsinRepository,
) {
    suspend operator fun invoke(portfolioId: Long?): Exposure = coroutineScope {
        val transactions = portfolios.observeTransactions().first().filter { portfolioId == null || it.portfolioId == portfolioId }
        val fx = portfolios.observeUsdToEur().first()
        val open = PortfolioCalculator.holdings(portfolios.getAssets(), transactions).filter { it.isOpen }
        val parts = open.map { h -> async { breakdown(h) to valueEur(h, fx) } }.awaitAll().filter { it.second > 0 }
        val total = parts.sumOf { it.second }
        if (total <= 0) return@coroutineScope Exposure(emptyList(), emptyList(), 0.0)

        fun aggregate(select: (Breakdown) -> List<Weighting>): List<AllocationSlice> =
            parts.flatMap { (b, value) -> select(b).map { it.name to value * it.weightPercent / 100 } }
                .groupBy({ it.first }, { it.second })
                .map { (k, v) -> AllocationSlice(k, v.sum(), v.sum() / total) }
                .sortedWith(compareBy<AllocationSlice> { it.key == Classification.UNCLASSIFIED || it.key == Classification.OTHER }.thenByDescending { it.valueEur })

        val bySector = aggregate { it.sectors }
        Exposure(
            bySector = bySector,
            byCountry = aggregate { it.countries },
            coverage = 1 - (bySector.firstOrNull { it.key == Classification.UNCLASSIFIED }?.weight ?: 0.0),
        )
    }

    private class Breakdown(val sectors: List<Weighting>, val countries: List<Weighting>)

    private fun valueEur(h: Holding, fx: Double?) = PortfolioCalculator.rateToEur(h.asset.currency, fx)?.let { h.marketValue * it } ?: 0.0

    private suspend fun breakdown(h: Holding): Breakdown {
        val unclassified = listOf(Weighting(Classification.UNCLASSIFIED, 100.0))
        if (h.asset.type == AssetType.STOCK) {
            val snapshot = runSuspendCatching { market.snapshot(h.asset.symbol) }.getOrNull() ?: return Breakdown(unclassified, unclassified)
            return Breakdown(
                listOf(Weighting(Classification.sector(snapshot.stats?.company?.sector), 100.0)),
                listOf(Weighting(Classification.country(snapshot.quote.country), 100.0)),
            )
        }
        val isin = isins.observeIsin(h.asset.symbol).first() ?: return Breakdown(unclassified, unclassified)
        val fund = runSuspendCatching { funds.profile(isin) }.getOrNull() ?: return Breakdown(unclassified, unclassified)
        return Breakdown(normalized(fund.sectors), normalized(fund.countries))
    }

    /** Complète à 100 % avec « Autre » ; vide = « Non classé ». */
    private fun normalized(weights: List<Weighting>): List<Weighting> {
        if (weights.isEmpty()) return listOf(Weighting(Classification.UNCLASSIFIED, 100.0))
        val sum = weights.sumOf { it.weightPercent }
        return if (sum < 99.5) weights + Weighting(Classification.OTHER, 100 - sum) else weights.map { it.copy(weightPercent = it.weightPercent * 100 / sum) }
    }
}
