package com.finops.app.data.remote

import com.finops.app.domain.model.MarketDataException
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject

/**
 * Accès bas niveau à Google Finance (pas d'API officielle : peut casser si Google modifie son site).
 * - pages `google.com/finance/quote/…` : cotation et caractéristiques intégrées au HTML ;
 * - endpoint `batchexecute` : appels RPC internes (historiques, recherche).
 */
class GoogleFinanceApi @Inject constructor(private val http: HttpClient) {

    /** HTML de la page de cotation de [symbol] (`TICKER:PLACE` ou paire `USD-EUR`). */
    suspend fun quotePage(symbol: String): String {
        val response = call { http.get("$BASE/quote/$symbol?hl=en", HEADERS) }
        if (response.code != 200) throw MarketDataException("Google Finance : HTTP ${response.code} pour $symbol")
        return response.body
    }

    /**
     * Appelle le RPC [rpcId] avec les arguments JSON [args] ; renvoie sa réponse décodée,
     * ou null si Google n'a renvoyé aucune donnée.
     */
    suspend fun rpc(rpcId: String, args: String): JsonElement? {
        val fReq = GoogleFinanceParser.batchRequest(rpcId, args)
        val response = call {
            http.postForm("$BASE/_/GoogleFinanceUi/data/batchexecute?rpcids=$rpcId&hl=en&rt=c", mapOf("f.req" to fReq), HEADERS)
        }
        if (response.code != 200) throw MarketDataException("Google Finance : HTTP ${response.code} ($rpcId)")
        return GoogleFinanceParser.batchPayload(response.body, rpcId)
    }

    private inline fun call(block: () -> HttpResponse): HttpResponse =
        try {
            block()
        } catch (e: java.io.IOException) {
            throw MarketDataException("Connexion impossible à Google Finance", e)
        }

    private companion object {
        const val BASE = "https://www.google.com/finance"
        val HEADERS = mapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36",
            "Accept-Language" to "en-US,en;q=0.9",
            // Évite la page de consentement cookies (UE).
            "Cookie" to "CONSENT=YES+; SOCS=CAI",
        )
    }
}
