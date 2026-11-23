package com.finops.app.data.remote

import com.finops.app.domain.model.EarningsEvent
import com.finops.app.domain.model.EarningsTiming
import com.finops.app.domain.model.MarketDataException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import javax.inject.Inject

/** API interne du calendrier des résultats de nasdaq.com (non officielle : peut changer). */
class NasdaqEarningsApi @Inject constructor(private val http: HttpClient) {

    /** JSON des publications du jour [date]. */
    suspend fun day(date: LocalDate): String {
        val response = try {
            http.get("$BASE?date=$date", HEADERS)
        } catch (e: java.io.IOException) {
            throw MarketDataException("Connexion impossible à Nasdaq", e)
        }
        if (response.code != 200) throw MarketDataException("Nasdaq : HTTP ${response.code}")
        return response.body
    }

    private companion object {
        const val BASE = "https://api.nasdaq.com/api/calendar/earnings"
        val HEADERS = mapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36",
            "Accept" to "application/json, text/plain, */*",
        )
    }
}

object NasdaqEarningsParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String, date: LocalDate): List<EarningsEvent> {
        val root = try {
            json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            throw MarketDataException("Format du calendrier des résultats inattendu", e)
        }
        val rows = (root["data"] as? JsonObject)?.get("rows") ?: return emptyList()
        if (rows !is JsonArray) return emptyList()
        return rows.mapNotNull { row ->
            val o = row as? JsonObject ?: return@mapNotNull null
            fun str(key: String) = (o[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "N/A" }
            val ticker = str("symbol") ?: return@mapNotNull null
            EarningsEvent(
                ticker = ticker,
                companyName = str("name") ?: ticker,
                date = date,
                timing = when (str("time")) {
                    "time-pre-market" -> EarningsTiming.BEFORE_OPEN
                    "time-after-hours" -> EarningsTiming.AFTER_CLOSE
                    else -> EarningsTiming.UNKNOWN
                },
                fiscalQuarter = str("fiscalQuarterEnding"),
                epsForecast = str("epsForecast")?.let(::amount),
                eps = str("eps")?.let(::amount),
                surprisePercent = str("surprise")?.toDoubleOrNull(),
            )
        }
    }

    /** « $1.16 », « ($0.12) » (négatif). */
    private fun amount(text: String): Double? {
        val negative = text.startsWith("(") || text.startsWith("-")
        val v = text.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: return null
        return if (negative) -v else v
    }
}
