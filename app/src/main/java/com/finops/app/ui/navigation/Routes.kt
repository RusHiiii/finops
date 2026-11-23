package com.finops.app.ui.navigation

import kotlinx.serialization.Serializable

// Les ViewModels lisent les arguments dans leur SavedStateHandle via les noms de propriétés
// ci-dessous (constantes [RouteArgs]) : garder les noms synchronisés.

@Serializable
data object DashboardRoute

@Serializable
data object WatchlistRoute

@Serializable
data object TransactionsRoute

@Serializable
data object ExploreRoute

@Serializable
data object SettingsRoute

@Serializable
data object NewsRoute

@Serializable
data class PortfolioRoute(val portfolioId: Long)

/** [portfolioId] = [RouteArgs.ALL] : position consolidée tous portefeuilles. */
@Serializable
data class HoldingRoute(val assetId: Long, val portfolioId: Long = RouteArgs.ALL)

@Serializable
data class InstrumentRoute(val ticker: String, val exchange: String)

/**
 * Saisie d'une transaction. Pré-remplissage optionnel depuis un portefeuille ([portfolioId]),
 * une position ([assetId]) ou une fiche instrument (ticker, exchange, name, type, currency, price).
 * [type], [currency] et [side] = noms d'enum. [transactionId] ≠ [RouteArgs.ALL] : modification de cette transaction.
 */
@Serializable
data class AddTransactionRoute(
    val portfolioId: Long = RouteArgs.ALL,
    val assetId: Long = RouteArgs.ALL,
    val ticker: String? = null,
    val exchange: String? = null,
    val name: String? = null,
    val type: String? = null,
    val currency: String? = null,
    val price: String? = null,
    val side: String? = null,
    val transactionId: Long = RouteArgs.ALL,
)

object RouteArgs {
    const val ALL = -1L
    const val PORTFOLIO_ID = "portfolioId"
    const val ASSET_ID = "assetId"
    const val TICKER = "ticker"
    const val EXCHANGE = "exchange"
    const val NAME = "name"
    const val TYPE = "type"
    const val CURRENCY = "currency"
    const val PRICE = "price"
    const val SIDE = "side"
    const val TRANSACTION_ID = "transactionId"
}
