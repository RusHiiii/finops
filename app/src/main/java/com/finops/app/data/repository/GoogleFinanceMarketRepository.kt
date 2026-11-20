package com.finops.app.data.repository

import com.finops.app.data.remote.GoogleFinanceApi
import com.finops.app.data.remote.GoogleFinanceParser
import com.finops.app.domain.model.ChartRange
import com.finops.app.domain.model.CurrencyCode
import com.finops.app.domain.model.InstrumentQuote
import com.finops.app.domain.model.InstrumentSnapshot
import com.finops.app.domain.model.MarketDataException
import com.finops.app.domain.model.PricePoint
import com.finops.app.domain.model.SearchResult
import com.finops.app.domain.model.Symbol
import com.finops.app.domain.repository.MarketRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Données de marché Google Finance, avec cache mémoire des historiques. */
@Singleton
class GoogleFinanceMarketRepository @Inject constructor(
    private val api: GoogleFinanceApi,
    private val clock: Clock,
) : MarketRepository {

    private companion object {
        /** Fiche (cours + caractéristiques) : courte durée, le cours bouge. */
        const val SNAPSHOT_TTL = 60_000L
        val MAIN_EXCHANGES = listOf("EPA", "AMS", "ETR", "EBR", "BIT", "BME", "NASDAQ", "NYSE", "NYSEARCA")
    }

    private class Cached(val at: Long, val points: List<PricePoint>)

    private val historyCache = HashMap<Pair<Symbol, ChartRange>, Cached>()
    private val snapshotCache = HashMap<Symbol, Pair<Long, InstrumentSnapshot>>()
    private val cacheLock = Mutex()

    override suspend fun quote(symbol: Symbol): InstrumentQuote =
        GoogleFinanceParser.parseQuote(api.quotePage(symbol.toString()))
            ?: throw MarketDataException("Cours introuvable pour $symbol")

    override suspend fun snapshot(symbol: Symbol): InstrumentSnapshot? {
        val now = clock.millis()
        cacheLock.withLock { snapshotCache[symbol] }?.takeIf { now - it.first < SNAPSHOT_TTL }?.let { return it.second }
        val html = api.quotePage(symbol.toString())
        val parsed = GoogleFinanceParser.parseQuote(html) ?: return null
        val stats = GoogleFinanceParser.parseStats(html)
        // Le nom de cotation est parfois une abréviation illisible ; le nom usuel est préféré.
        val quote = stats?.displayName?.let { parsed.copy(name = it) } ?: parsed
        return InstrumentSnapshot(symbol, quote, stats)
            .also { s -> cacheLock.withLock { snapshotCache[symbol] = now to s } }
    }

    override suspend fun history(symbol: Symbol, range: ChartRange): List<PricePoint> {
        val key = symbol to range
        val now = clock.millis()
        cacheLock.withLock { historyCache[key] }?.takeIf { now - it.at < ttl(range) }?.let { return it.points }
        val payload = api.rpc(GoogleFinanceParser.RPC_CHART, GoogleFinanceParser.chartArgs(symbol, range.googleWindow))
        val points = GoogleFinanceParser.chartPoints(payload)
        if (points.isNotEmpty()) cacheLock.withLock { historyCache[key] = Cached(now, points) }
        return points
    }

    override suspend fun search(query: String): List<SearchResult> {
        val payload = api.rpc(GoogleFinanceParser.RPC_SEARCH, GoogleFinanceParser.searchArgs(query))
            ?: throw MarketDataException("Recherche indisponible : saisissez directement PLACE:TICKER (ex. EPA:CW8)")
        return GoogleFinanceParser.searchResults(payload).sortedBy(::rank)
    }

    override suspend fun fxRate(from: CurrencyCode, to: CurrencyCode): Double {
        if (from == to) return 1.0
        return GoogleFinanceParser.parseQuote(api.quotePage("${from.name}-${to.name}"))?.price
            ?: throw MarketDataException("Taux introuvable pour ${from.name}/${to.name}")
    }

    /**
     * Ordre d'affichage : devises gérées d'abord (EUR puis USD), places principales en tête,
     * sinon ordre de pertinence Google (tri stable).
     */
    private fun rank(r: SearchResult): Int {
        val currency = when (r.currency) {
            "EUR" -> 0
            "USD" -> 1
            else -> 2
        }
        val exchange = MAIN_EXCHANGES.indexOf(r.symbol.exchange).let { if (it < 0) MAIN_EXCHANGES.size else it }
        return currency * 100 + exchange
    }

    /** Historiques intrajournaliers rafraîchis souvent, les autres moins. */
    private fun ttl(range: ChartRange): Long = when (range) {
        ChartRange.ONE_DAY, ChartRange.FIVE_DAYS -> 2 * 60_000L
        else -> 30 * 60_000L
    }
}
