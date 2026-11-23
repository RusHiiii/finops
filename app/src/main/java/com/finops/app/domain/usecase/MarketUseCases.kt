package com.finops.app.domain.usecase

import com.finops.app.common.DefaultDispatcher
import com.finops.app.domain.calculator.Performance
import com.finops.app.domain.calculator.PerformanceCalculator
import com.finops.app.domain.calculator.PortfolioHistoryCalculator
import com.finops.app.domain.model.ChartRange
import com.finops.app.domain.model.CurrencyCode
import com.finops.app.domain.model.InstrumentSnapshot
import com.finops.app.domain.model.Isin
import com.finops.app.domain.model.PricePoint
import com.finops.app.domain.model.SearchResult
import com.finops.app.domain.model.Symbol
import com.finops.app.domain.model.ValuePoint
import com.finops.app.domain.repository.MarketRepository
import com.finops.app.domain.repository.IsinRepository
import com.finops.app.domain.repository.PortfolioRepository
import com.finops.app.domain.repository.SettingsRepository
import com.finops.app.domain.repository.WatchlistRepository
import com.finops.app.common.runSuspendCatching
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject

data class RefreshResult(val failures: List<String>) {
    val isSuccess: Boolean get() = failures.isEmpty()
}

/**
 * Met à jour le cours des actifs détenus et des instruments suivis (une requête par symbole),
 * ainsi que le taux USD/EUR. Ne lève pas : les échecs sont listés.
 */
class RefreshQuotesUseCase @Inject constructor(
    private val portfolios: PortfolioRepository,
    private val watchlist: WatchlistRepository,
    private val market: MarketRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(): RefreshResult = coroutineScope {
        val now = clock.millis()
        val assets = portfolios.getAssets()
        val watched = watchlist.getWatchlist()
        val symbols = (assets.map { it.symbol } + watched.map { it.symbol }).distinct()
        val quotes = symbols.map { s -> async { s to runSuspendCatching { market.quote(s) } } }
        val fx = async { runSuspendCatching { market.fxRate(CurrencyCode.USD, CurrencyCode.EUR) } }
        val bySymbol = quotes.awaitAll().toMap()
        val failures = bySymbol.filterValues { it.isFailure }.keys.map { it.toString() }.toMutableList()
        for (a in assets) bySymbol[a.symbol]?.getOrNull()?.let { portfolios.updateQuote(a.id, it.price, it.previousClose, now) }
        for (w in watched) bySymbol[w.symbol]?.getOrNull()?.let { watchlist.updateQuote(w.symbol, it.price, it.previousClose, now) }
        fx.await().onSuccess { portfolios.updateUsdToEur(it, now) }.onFailure { failures += "USD/EUR" }
        RefreshResult(failures)
    }
}

class SearchInstrumentsUseCase @Inject constructor(
    private val market: MarketRepository,
    private val isins: IsinRepository,
    private val settings: SettingsRepository,
) {
    /**
     * Recherche par nom, ticker ou ISIN, limitée aux devises suivies (paramètres).
     * Moins de [MIN_QUERY_LENGTH] caractères : aucun résultat. `TICKER:PLACE` ou `PLACE:TICKER` est résolu directement.
     */
    suspend operator fun invoke(query: String): List<SearchResult> {
        val q = query.trim()
        if (q.length < MIN_QUERY_LENGTH) return emptyList()
        val allowed = settings.observeSettings().first().currencies.map { it.name }.toSet()
        Symbol.parse(q)?.let { parsed ->
            // Accepte TICKER:PLACE (CW8:EPA) et PLACE:TICKER (EPA:CW8), le format affiché par l'app.
            val snapshot = market.snapshot(parsed) ?: market.snapshot(Symbol(parsed.exchange, parsed.ticker)) ?: return emptyList()
            val quote = snapshot.quote
            return listOf(SearchResult(snapshot.symbol, quote.name, quote.type, quote.currency, quote.price, quote.changePercent))
                .filter { it.currency in allowed }
        }
        val results = market.search(q)
        // Recherche par ISIN : toutes les cotations trouvées sont celles de ce titre, on mémorise le lien.
        Isin.parse(q)?.let { isin -> results.forEach { isins.link(it.symbol, isin) } }
        return results.filter { it.currency in allowed }
    }

    companion object {
        const val MIN_QUERY_LENGTH = 2
    }
}

class GetInstrumentUseCase @Inject constructor(private val market: MarketRepository) {
    suspend operator fun invoke(symbol: Symbol): InstrumentSnapshot? = market.snapshot(symbol)
}

/** Taux de conversion d'une devise de cotation vers l'euro ; null si EUR, devise inconnue ou taux indisponible. */
class GetRateToEurUseCase @Inject constructor(
    private val market: MarketRepository,
    private val portfolios: PortfolioRepository,
) {
    suspend operator fun invoke(currency: String?): Double? {
        val code = currency?.uppercase() ?: return null
        if (code == "EUR") return null
        // Londres cote en pence (GBX) : 1 GBX = 0,01 GBP.
        val (iso, factor) = if (code == "GBX" || currency == "GBp") "GBP" to 0.01 else code to 1.0
        if (iso == "USD") portfolios.observeUsdToEur().first()?.let { return it }
        return runSuspendCatching { market.fxRate(iso, "EUR") * factor }.getOrNull()
    }
}

class GetPriceHistoryUseCase @Inject constructor(private val market: MarketRepository) {
    suspend operator fun invoke(symbol: Symbol, range: ChartRange): List<PricePoint> = market.history(symbol, range)
}

/** Performance de l'instrument sur chaque période ; période absente si l'historique est indisponible. */
class GetPerformancesUseCase @Inject constructor(private val market: MarketRepository) {
    suspend operator fun invoke(symbol: Symbol, previousClose: Double?): Map<ChartRange, Performance> = coroutineScope {
        ChartRange.entries.map { range ->
            async {
                val points = runSuspendCatching { market.history(symbol, range) }.getOrNull().orEmpty()
                val reference = if (range == ChartRange.ONE_DAY) previousClose else null
                PerformanceCalculator.ofPrices(points, reference)?.let { range to it }
            }
        }.awaitAll().filterNotNull().toMap()
    }
}

/** Courbe de valeur d'un portefeuille ([portfolioId] null = patrimoine complet) sur une période. */
class GetPortfolioHistoryUseCase @Inject constructor(
    private val portfolios: PortfolioRepository,
    private val market: MarketRepository,
    private val clock: Clock,
    @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(portfolioId: Long?, range: ChartRange): List<ValuePoint> = coroutineScope {
        val transactions = portfolios.observeTransactions().first()
            .filter { portfolioId == null || it.portfolioId == portfolioId }
        val heldIds = transactions.map { it.assetId }.toSet()
        val assets = portfolios.getAssets().filter { it.id in heldIds }
        val fx = portfolios.observeUsdToEur().first()
        val histories = assets.map { a ->
            async { a.id to runSuspendCatching { market.history(a.symbol, range) }.getOrDefault(emptyList()) }
        }.awaitAll().toMap()
        withContext(dispatcher) {
            PortfolioHistoryCalculator.compute(assets, transactions, histories, fx, clock.zone)
        }
    }
}
