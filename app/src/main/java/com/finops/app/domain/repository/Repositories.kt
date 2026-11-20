package com.finops.app.domain.repository

import com.finops.app.domain.model.AppSettings
import com.finops.app.domain.model.Asset
import com.finops.app.domain.model.BackupSummary
import com.finops.app.domain.model.ChartRange
import com.finops.app.domain.model.CurrencyCode
import com.finops.app.domain.model.FundProfile
import com.finops.app.domain.model.InstrumentQuote
import com.finops.app.domain.model.Isin
import com.finops.app.domain.model.InstrumentSnapshot
import com.finops.app.domain.model.Portfolio
import com.finops.app.domain.model.PricePoint
import com.finops.app.domain.model.SearchResult
import com.finops.app.domain.model.Symbol
import com.finops.app.domain.model.Transaction
import com.finops.app.domain.model.TransactionDraft
import com.finops.app.domain.model.WatchlistItem
import kotlinx.coroutines.flow.Flow

/** Données locales de l'utilisateur (source de vérité, hors ligne). */
interface PortfolioRepository {
    fun observePortfolios(): Flow<List<Portfolio>>
    fun observePortfolio(id: Long): Flow<Portfolio?>
    fun observeAssets(): Flow<List<Asset>>
    fun observeTransactions(): Flow<List<Transaction>>

    /** Taux USD → EUR mémorisé lors du dernier rafraîchissement. */
    fun observeUsdToEur(): Flow<Double?>

    suspend fun getAssets(): List<Asset>
    suspend fun getAsset(id: Long): Asset?
    suspend fun findAsset(symbol: Symbol): Asset?

    suspend fun createPortfolio(name: String): Long
    suspend fun renamePortfolio(id: Long, name: String)

    /** Supprime le portefeuille, ses transactions et les actifs devenus orphelins. */
    suspend fun deletePortfolio(id: Long)

    /**
     * Enregistre la transaction (et crée l'actif si besoin) de façon atomique.
     * @throws com.finops.app.domain.model.ValidationException si une vente dépasse la quantité détenue.
     */
    suspend fun addTransaction(draft: TransactionDraft): Long

    /** @throws com.finops.app.domain.model.ValidationException si des ventes deviennent incohérentes. */
    suspend fun deleteTransaction(transaction: Transaction)

    suspend fun updateQuote(assetId: Long, price: Double, previousClose: Double?, at: Long)
    suspend fun updateUsdToEur(rate: Double, at: Long)
}

/** Données de marché distantes (Google Finance). Toutes les méthodes lèvent [com.finops.app.domain.model.MarketDataException]. */
interface MarketRepository {
    suspend fun quote(symbol: Symbol): InstrumentQuote

    /** null si le symbole n'existe pas. */
    suspend fun snapshot(symbol: Symbol): InstrumentSnapshot?

    suspend fun history(symbol: Symbol, range: ChartRange): List<PricePoint>

    suspend fun search(query: String): List<SearchResult>

    /** 1 [from] = x [to]. */
    suspend fun fxRate(from: CurrencyCode, to: CurrencyCode): Double
}

/** Instruments suivis par l'utilisateur (stockage local). */
interface WatchlistRepository {
    /** Plus récents en premier. */
    fun observeWatchlist(): Flow<List<WatchlistItem>>
    fun observeIsWatched(symbol: Symbol): Flow<Boolean>
    suspend fun getWatchlist(): List<WatchlistItem>
    suspend fun add(item: WatchlistItem)
    suspend fun remove(symbol: Symbol)
    suspend fun updateQuote(symbol: Symbol, price: Double, previousClose: Double?, at: Long)
}

/** Fiches descriptives des ETF (justETF). Lève [com.finops.app.domain.model.MarketDataException]. */
interface FundRepository {
    /** null si l'ISIN est inconnu de la source (ex. action). */
    suspend fun profile(isin: Isin): FundProfile?
}

/** Correspondance symbole → ISIN mémorisée localement (recherche par ISIN ou saisie). */
interface IsinRepository {
    fun observeIsin(symbol: Symbol): Flow<Isin?>
    suspend fun link(symbol: Symbol, isin: Isin)
}

/** Préférences persistées localement. */
interface SettingsRepository {
    fun observeSettings(): Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

/** Sauvegarde complète des données utilisateur sous forme de document texte. */
interface BackupRepository {
    suspend fun export(): String

    /**
     * Remplace toutes les données par celles de [document], de façon atomique.
     * @throws com.finops.app.domain.model.ValidationException si le document est invalide.
     */
    suspend fun restore(document: String): BackupSummary

    /** Supprime toutes les données utilisateur et recrée le portefeuille par défaut (état d'une nouvelle installation). */
    suspend fun clearAll()
}

/** Lecture / écriture d'un document désigné par l'utilisateur (URI). */
interface DocumentStorage {
    suspend fun write(uri: String, content: String)
    suspend fun read(uri: String): String
}
