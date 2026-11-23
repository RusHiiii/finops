package com.finops.app.domain.usecase

import com.finops.app.common.runSuspendCatching
import com.finops.app.domain.calculator.InstrumentLabels
import com.finops.app.domain.calculator.RiskCalculator
import com.finops.app.domain.model.AssetType
import com.finops.app.domain.model.ChartRange
import com.finops.app.domain.model.FundProfile
import com.finops.app.domain.model.InstrumentSnapshot
import com.finops.app.domain.model.Isin
import com.finops.app.domain.model.RiskOverview
import com.finops.app.domain.model.Symbol
import com.finops.app.domain.model.ValidationException
import com.finops.app.domain.repository.FundRepository
import com.finops.app.domain.repository.IsinRepository
import com.finops.app.domain.repository.MarketRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Informations dérivées d'une fiche : labels, fiche fonds, risques. */
data class InstrumentInsights(
    val labels: List<String>,
    val isin: Isin?,
    /** null si ISIN inconnu, action, ou fiche indisponible (voir [fundError]). */
    val fund: FundProfile?,
    val fundError: String?,
    val risk: RiskOverview?,
)

class ObserveIsinUseCase @Inject constructor(private val repository: IsinRepository) {
    operator fun invoke(symbol: Symbol): Flow<Isin?> = repository.observeIsin(symbol)
}

class LinkIsinUseCase @Inject constructor(private val repository: IsinRepository) {
    /** @throws ValidationException si [text] n'est pas un ISIN valide. */
    suspend operator fun invoke(symbol: Symbol, text: String): Isin {
        val isin = Isin.parse(text) ?: throw ValidationException("ISIN invalide (12 caractères, ex. FR0010315770)")
        repository.link(symbol, isin)
        return isin
    }
}

/**
 * Assemble les informations complémentaires. ETF avec ISIN : fiche et risques justETF ;
 * sinon (ou à défaut), risques calculés sur l'historique Google Finance et indicateur estimé.
 */
class GetInstrumentInsightsUseCase @Inject constructor(
    private val funds: FundRepository,
    private val market: MarketRepository,
) {
    suspend operator fun invoke(snapshot: InstrumentSnapshot, isin: Isin?): InstrumentInsights = coroutineScope {
        val isFund = snapshot.quote.type != AssetType.STOCK
        val fundResult = if (isFund && isin != null) runSuspendCatching { funds.profile(isin) } else Result.success(null)
        val fund = fundResult.getOrNull()
        val risk = fund?.risk?.takeIf { it.volatility.isNotEmpty() } ?: computedRisk(snapshot.symbol)
        InstrumentInsights(
            labels = InstrumentLabels.labels(snapshot, fund),
            isin = isin,
            fund = fund,
            fundError = fundResult.exceptionOrNull()?.message
                ?: if (isFund && isin != null && fund == null) "Fonds introuvable sur justETF pour $isin" else null,
            risk = risk,
        )
    }

    private suspend fun computedRisk(symbol: Symbol): RiskOverview? = coroutineScope {
        val oneYear = async { runSuspendCatching { market.history(symbol, ChartRange.ONE_YEAR) }.getOrDefault(emptyList()) }
        val fiveYears = async { runSuspendCatching { market.history(symbol, ChartRange.FIVE_YEARS) }.getOrDefault(emptyList()) }
        val y1 = oneYear.await()
        val y5 = fiveYears.await()
        fun pts(years: Int) = if (years == 1 && y1.size > 2) y1 else y5
        val vol = RiskOverview.YEARS.mapNotNull { y -> RiskCalculator.volatility(pts(y), y)?.let { y to it } }.toMap()
        val dd = RiskOverview.YEARS.mapNotNull { y -> RiskCalculator.maxDrawdown(pts(y), y)?.let { y to it } }.toMap()
        if (vol.isEmpty() && dd.isEmpty()) return@coroutineScope null
        val reference = vol[5] ?: vol[3] ?: vol[1]
        RiskOverview(reference?.let(RiskCalculator::riskClass), indicatorEstimated = true, volatility = vol, maxDrawdown = dd)
    }
}
