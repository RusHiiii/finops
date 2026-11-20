package com.finops.app.domain.model

/** Identifiant Google Finance : `TICKER:PLACE` (ex. CW8:EPA, AAPL:NASDAQ). */
data class Symbol(val ticker: String, val exchange: String) {
    /** Format technique Google Finance (`TICKER:PLACE`), utilisé pour les appels et les clés. */
    override fun toString() = "$ticker:$exchange"

    /** Format d'affichage, place puis ticker : `NASDAQ:NVDA`. */
    val display: String get() = "$exchange:$ticker"

    companion object {
        private val PATTERN = Regex("""^([A-Z0-9.\-]{1,15}):([A-Z0-9]{2,15})$""")

        /** null si [text] n'est pas de la forme `TICKER:PLACE`. */
        fun parse(text: String): Symbol? =
            PATTERN.matchEntire(text.trim().uppercase())?.destructured?.let { (t, e) -> Symbol(t, e) }
    }
}

/** Périodes proposées par Google Finance ; [googleWindow] = code de fenêtre de l'API. */
enum class ChartRange(val label: String, val googleWindow: Int) {
    ONE_DAY("1J", 1),
    FIVE_DAYS("5J", 2),
    ONE_MONTH("1M", 3),
    SIX_MONTHS("6M", 4),
    YEAR_TO_DATE("YTD", 5),
    ONE_YEAR("1A", 6),
    FIVE_YEARS("5A", 7),
    MAX("MAX", 8),
}

data class PricePoint(val epochMillis: Long, val price: Double)

data class SearchResult(
    val symbol: Symbol,
    val name: String,
    val type: AssetType?,
    /** Code ISO brut : peut être hors [CurrencyCode] (GBP, CHF…). */
    val currency: String?,
    val price: Double?,
    val changePercent: Double?,
)

data class InstrumentQuote(
    val name: String,
    val type: AssetType?,
    val currency: String?,
    val price: Double,
    val change: Double?,
    val changePercent: Double?,
    val previousClose: Double?,
    /** Pays de cotation / siège (code ISO 2 lettres). */
    val country: String? = null,
) {
    /** null si la devise n'est pas gérée par l'application. */
    val currencyCode: CurrencyCode? get() = CurrencyCode.entries.firstOrNull { it.name == currency }
}

data class InstrumentStats(
    val open: Double?,
    val dayHigh: Double?,
    val dayLow: Double?,
    val yearHigh: Double?,
    val yearLow: Double?,
    val volume: Double?,
    val marketCap: Double?,
    val peRatio: Double?,
    val dividendYieldPercent: Double?,
    val description: String?,
    val company: CompanyInfo? = null,
    /** Nom usuel (ex. « Air Liquide ») ; le nom de cotation peut être abrégé de façon illisible. */
    val displayName: String? = null,
)

data class InstrumentSnapshot(
    val symbol: Symbol,
    val quote: InstrumentQuote,
    val stats: InstrumentStats?,
)

/** Instrument suivi (watchlist), avec le dernier cours mémorisé. */
data class WatchlistItem(
    val symbol: Symbol,
    val name: String,
    val type: AssetType?,
    val currency: String?,
    val lastPrice: Double?,
    val previousClose: Double?,
    val lastPriceAt: Long?,
    val addedAt: Long,
) {
    /** Variation du jour en % ; null si cours inconnus. */
    val dayChangePercent: Double?
        get() {
            val last = lastPrice ?: return null
            val prev = previousClose?.takeIf { it != 0.0 } ?: return null
            return (last - prev) / prev * 100
        }
}
