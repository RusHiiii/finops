package com.finops.app.domain.model

import java.time.LocalDate

enum class AssetType { ETF, STOCK }

enum class CurrencyCode { EUR, USD }

enum class TransactionSide { BUY, SELL }

data class Portfolio(val id: Long = 0, val name: String)

/** Instrument coté. [symbol] = couple ticker / place Google Finance (EPA, NASDAQ, NYSEARCA…). */
data class Asset(
    val id: Long = 0,
    val symbol: Symbol,
    val name: String,
    val type: AssetType,
    val currency: CurrencyCode,
    val lastPrice: Double? = null,
    val previousClose: Double? = null,
    val lastPriceAt: Long? = null,
)

/** [price] et [fees] exprimés dans la devise de l'actif. */
data class Transaction(
    val id: Long = 0,
    val portfolioId: Long,
    val assetId: Long,
    val side: TransactionSide,
    val quantity: Double,
    val price: Double,
    val fees: Double,
    val date: LocalDate,
    /** Taux USD/EUR (1 USD = x EUR) à la date de la transaction ; null pour un actif en devise d'affichage ou non saisi. */
    val usdToEur: Double? = null,
) {
    /** Montant décaissé (achat) ou encaissé (vente), frais inclus. */
    val netAmount: Double get() = when (side) {
        TransactionSide.BUY -> quantity * price + fees
        TransactionSide.SELL -> quantity * price - fees
    }
}

/** Saisie utilisateur : crée l'actif s'il n'existe pas encore. */
data class TransactionDraft(
    val portfolioId: Long,
    val symbol: Symbol,
    val name: String,
    val type: AssetType,
    val currency: CurrencyCode,
    val side: TransactionSide,
    val quantity: Double,
    val price: Double,
    val fees: Double,
    val date: LocalDate,
    val usdToEur: Double? = null,
)

/** Transaction enrichie pour l'affichage d'un historique. */
data class TransactionEntry(
    val transaction: Transaction,
    val asset: Asset,
    val portfolio: Portfolio,
)
