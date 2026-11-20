package com.finops.app.data.remote

import com.finops.app.domain.model.Isin
import com.finops.app.domain.model.MarketDataException
import javax.inject.Inject

/**
 * Pages publiques de profil ETF de justETF (pas d'API officielle : peut casser si le site change).
 * @see JustEtfParser
 */
class JustEtfApi @Inject constructor(private val http: HttpClient) {

    /** HTML du profil ; null si justETF ne connaît pas l'ISIN (pas de page de profil). */
    suspend fun profilePage(isin: Isin): String? {
        val response = call { http.get("$BASE/fr/etf-profile.html?isin=${isin.value}", HEADERS) }
        return when (response.code) {
            200 -> response.body.takeIf { JustEtfParser.isProfile(it) }
            404 -> null
            else -> throw MarketDataException("justETF : HTTP ${response.code} pour $isin")
        }
    }

    private inline fun call(block: () -> HttpResponse): HttpResponse =
        try {
            block()
        } catch (e: java.io.IOException) {
            throw MarketDataException("Connexion impossible à justETF", e)
        }

    private companion object {
        const val BASE = "https://www.justetf.com"
        val HEADERS = mapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36",
            "Accept-Language" to "fr-FR,fr;q=0.9",
        )
    }
}
