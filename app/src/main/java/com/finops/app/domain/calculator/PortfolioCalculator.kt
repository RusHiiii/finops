package com.finops.app.domain.calculator

import com.finops.app.domain.model.Asset
import com.finops.app.domain.model.CurrencyCode
import com.finops.app.domain.model.Holding
import com.finops.app.domain.model.PortfolioSummary
import com.finops.app.domain.model.Transaction
import com.finops.app.domain.model.TransactionSide

/** Méthode du prix moyen pondéré (PRU), frais d'achat inclus dans le coût. */
object PortfolioCalculator {
    internal const val EPS = 1e-9

    fun holding(asset: Asset, transactions: List<Transaction>): Holding {
        var position = Position.EMPTY
        for (t in sorted(transactions)) position = position.apply(t)
        return Holding(asset, position.quantity, position.cost, position.realized)
    }

    /** Une position par actif ayant au moins une transaction, triées par type puis ticker. */
    fun holdings(assets: List<Asset>, transactions: List<Transaction>): List<Holding> {
        val assetsById = assets.associateBy { it.id }
        return transactions.groupBy { it.assetId }
            .mapNotNull { (assetId, txs) -> assetsById[assetId]?.let { holding(it, txs) } }
            .sortedWith(compareBy({ it.asset.type }, { it.asset.symbol.ticker }))
    }

    /** false si une vente dépasse la quantité détenue à sa date. */
    fun isConsistent(transactions: List<Transaction>): Boolean {
        var qty = 0.0
        for (t in sorted(transactions)) {
            qty += if (t.side == TransactionSide.BUY) t.quantity else -t.quantity
            if (qty < -EPS) return false
        }
        return true
    }

    fun summary(holdings: List<Holding>, usdToEur: Double?): PortfolioSummary {
        var value = 0.0
        var cost = 0.0
        var realized = 0.0
        var day = 0.0
        var missingFx = false
        for (h in holdings) {
            val rate = rateToEur(h.asset.currency, usdToEur)
            if (rate == null) {
                missingFx = true
                continue
            }
            value += h.marketValue * rate
            cost += h.costBasis * rate
            realized += h.realizedPnl * rate
            day += (h.dayChange ?: 0.0) * rate
        }
        return PortfolioSummary(value, cost, realized, day, missingFx)
    }

    /** null si la devise n'est pas convertible faute de taux. */
    fun rateToEur(currency: CurrencyCode, usdToEur: Double?): Double? = when (currency) {
        CurrencyCode.EUR -> 1.0
        CurrencyCode.USD -> usdToEur
    }

    internal fun sorted(transactions: List<Transaction>) =
        transactions.sortedWith(compareBy({ it.date }, { it.id }))

    /** État d'une position après application successive des transactions. */
    internal data class Position(val quantity: Double, val cost: Double, val realized: Double) {
        fun apply(t: Transaction): Position = when (t.side) {
            TransactionSide.BUY -> copy(quantity = quantity + t.quantity, cost = cost + t.quantity * t.price + t.fees)
            TransactionSide.SELL -> {
                val avg = if (quantity > EPS) cost / quantity else 0.0
                val removed = avg * t.quantity
                val qty = quantity - t.quantity
                if (qty < EPS) {
                    Position(0.0, 0.0, realized + t.quantity * t.price - t.fees - removed)
                } else {
                    Position(qty, cost - removed, realized + t.quantity * t.price - t.fees - removed)
                }
            }
        }

        companion object {
            val EMPTY = Position(0.0, 0.0, 0.0)
        }
    }
}
