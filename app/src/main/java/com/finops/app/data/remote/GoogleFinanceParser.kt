package com.finops.app.data.remote

import com.finops.app.domain.model.AssetType
import com.finops.app.domain.model.CompanyInfo
import com.finops.app.domain.model.InstrumentQuote
import com.finops.app.domain.model.InstrumentStats
import com.finops.app.domain.model.PricePoint
import com.finops.app.domain.model.SearchResult
import com.finops.app.domain.model.Symbol
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Décodage des formats (non documentés) de Google Finance :
 * - pages de cotation : blocs `AF_initDataCallback` identifiés par leur id RPC ;
 * - réponses `batchexecute` : lignes JSON `["wrb.fr", rpcId, "<payload JSON>", …]`.
 */
internal object GoogleFinanceParser {
    const val RPC_QUOTE = "gCvqoe"
    const val RPC_STATS = "JL8oKc"
    const val RPC_CHART = "c2u4wc"
    const val RPC_SEARCH = "mKsvE"

    private val DATA = Regex("""AF_initDataCallback\(\{key: '(ds:\d+)'.*?data:(.*?), sideChannel""", RegexOption.DOT_MATCHES_ALL)
    private val REQUEST = Regex("""'(ds:\d+)' : \{id:'(\w+)',request:(\[.*?\])\}""")

    // --- Requêtes RPC -------------------------------------------------------------------------

    /** Valeur du champ `f.req` d'un appel batchexecute. */
    fun batchRequest(rpcId: String, args: String): String =
        buildJsonArray {
            addJsonArray {
                addJsonArray {
                    add(rpcId)
                    add(args)
                    add(JsonNull)
                    add("generic")
                }
            }
        }.toString()

    /** Arguments du RPC d'historique : `[[[null,[ticker,place]]], fenêtre]`. */
    fun chartArgs(symbol: Symbol, window: Int): String =
        buildJsonArray {
            addJsonArray { add(symbolRef(symbol)) }
            add(window)
        }.toString()

    /** Arguments du RPC de recherche : `[requête]` (nom, ticker ou ISIN). */
    fun searchArgs(query: String): String = buildJsonArray { add(query) }.toString()

    private fun symbolRef(symbol: Symbol) = buildJsonArray {
        add(JsonNull)
        addJsonArray {
            add(symbol.ticker)
            add(symbol.exchange)
        }
    }

    // --- Réponses batchexecute ----------------------------------------------------------------

    /** Payload décodé du RPC [rpcId] dans une réponse batchexecute ; null si absent ou vide. */
    fun batchPayload(body: String, rpcId: String): JsonElement? =
        body.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("[") }
            .mapNotNull(::parseOrNull)
            .flatMap { (it as? JsonArray).orEmpty() }
            .firstOrNull { it.at(0).str == "wrb.fr" && it.at(1).str == rpcId }
            ?.at(2)?.str
            ?.let(::parseOrNull)

    /** Points d'un historique (payload du RPC [RPC_CHART]). */
    fun chartPoints(payload: JsonElement?): List<PricePoint> =
        (payload.at(0, 0, 3, 0, 1) as? JsonArray)?.mapNotNull(::pointFrom).orEmpty()

    /**
     * Résultats de recherche (payload du RPC [RPC_SEARCH]). Le format exact n'étant pas documenté,
     * on retient toute entrée ayant la forme d'une cotation : `[_, [ticker, place], nom, type?, devise?, [prix, var, var%]?]`.
     */
    fun searchResults(payload: JsonElement?): List<SearchResult> {
        val found = mutableListOf<SearchResult>()
        fun walk(e: JsonElement?) {
            if (e !is JsonArray) return
            quoteLike(e)?.let { found += it; return }
            e.forEach(::walk)
        }
        walk(payload)
        return found.distinctBy { it.symbol }
    }

    private fun quoteLike(a: JsonArray): SearchResult? {
        val ticker = a.at(1, 0).str ?: return null
        val exchange = a.at(1, 1).str ?: return null
        val name = a.at(2).str ?: return null
        if ((a.at(1) as? JsonArray)?.size != 2) return null
        return SearchResult(
            symbol = Symbol(ticker, exchange),
            name = name,
            type = typeOf(a.at(3).int),
            currency = a.at(4).str,
            price = a.at(5, 0).dbl,
            changePercent = a.at(5, 2).dbl,
        )
    }

    // --- Pages de cotation --------------------------------------------------------------------

    private class Block(val rpcId: String, val request: JsonElement?, val data: JsonElement?)

    private fun blocks(html: String): List<Block> {
        val data = DATA.findAll(html).associate { it.groupValues[1] to it.groupValues[2] }
        return REQUEST.findAll(html).map { m ->
            val (key, rpcId, request) = m.destructured
            Block(rpcId, parseOrNull(request), data[key]?.let(::parseOrNull))
        }.toList()
    }

    fun parseQuote(html: String): InstrumentQuote? =
        blocks(html).filter { it.rpcId == RPC_QUOTE }.firstNotNullOfOrNull { quoteFrom(it.data) }

    fun parseStats(html: String): InstrumentStats? =
        blocks(html).firstOrNull { it.rpcId == RPC_STATS }?.data.at(0, 0)?.let { s ->
            InstrumentStats(
                open = s.at(9).dbl,
                dayHigh = s.at(10).dbl,
                dayLow = s.at(11).dbl,
                yearHigh = s.at(12).dbl,
                yearLow = s.at(13).dbl,
                volume = s.at(14).dbl,
                marketCap = s.at(7).dbl,
                peRatio = s.at(16).dbl,
                dividendYieldPercent = s.at(17).dbl,
                description = s.at(2).str,
                displayName = s.at(1).str?.takeIf { it.isNotBlank() },
                company = CompanyInfo(
                    sector = s.at(71).str,
                    industry = s.at(78).str,
                    country = null,
                    founded = s.at(4)?.let { d ->
                        runCatching { LocalDate.of(d.at(0).int!!, d.at(1).int!!, d.at(2).int!!) }.getOrNull()
                    },
                    website = s.at(22).str,
                ),
            )
        }

    private fun quoteFrom(data: JsonElement?): InstrumentQuote? {
        val q = data.at(0, 0, 0) ?: return null
        val price = q.at(5, 0).dbl ?: return null
        return InstrumentQuote(
            name = q.at(2).str.orEmpty(),
            type = typeOf(q.at(3).int),
            currency = q.at(4).str,
            price = price,
            change = q.at(5, 1).dbl,
            changePercent = q.at(5, 2).dbl,
            previousClose = q.at(7).dbl,
            country = q.at(9).str,
        )
    }

    private fun typeOf(code: Int?): AssetType? = when (code) {
        0 -> AssetType.STOCK
        5 -> AssetType.ETF
        else -> null
    }

    /** Point : [[année, mois, jour, heure?, minute?, seconde?, null, [décalage UTC en s]], [prix, …], volume?] */
    private fun pointFrom(p: JsonElement): PricePoint? {
        val d = p.at(0) ?: return null
        val price = p.at(1, 0).dbl ?: return null
        val dateTime = runCatching {
            LocalDateTime.of(
                d.at(0).int ?: return null,
                d.at(1).int ?: return null,
                d.at(2).int ?: return null,
                d.at(3).int ?: 0,
                d.at(4).int ?: 0,
                d.at(5).int ?: 0,
            )
        }.getOrNull() ?: return null
        val offset = ZoneOffset.ofTotalSeconds(d.at(7, 0).int ?: 0)
        return PricePoint(dateTime.toInstant(offset).toEpochMilli(), price)
    }

    // --- JSON ---------------------------------------------------------------------------------

    private fun parseOrNull(text: String): JsonElement? = runCatching { Json.parseToJsonElement(text) }.getOrNull()

    private fun JsonElement?.at(vararg path: Int): JsonElement? {
        var cur = this
        for (i in path) cur = (cur as? JsonArray)?.getOrNull(i) ?: return null
        return cur.takeUnless { it is JsonNull }
    }

    private val JsonElement?.dbl: Double? get() = (this as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
    private val JsonElement?.int: Int? get() = (this as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
    private val JsonElement?.str: String? get() = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
}
