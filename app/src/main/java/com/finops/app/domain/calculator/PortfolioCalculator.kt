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
        for (t in sorted(transactions)) position = position.apply(t, asset.currency)
        return Holding(
            asset, position.quantity, position.cost, position.realized,
            position.costEur, position.costPending, position.realizedEur, position.realizedPending,
        )
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
            cost += h.costBasisEur(usdToEur) ?: 0.0
            realized += h.realizedPnlEur(usdToEur) ?: 0.0
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

    /** Taux devise de l'actif → EUR à la date de [t] ; null si non saisi (USD sans taux). */
    private fun transactionRateToEur(t: Transaction, currency: CurrencyCode): Double? = when (currency) {
        CurrencyCode.EUR -> 1.0
        CurrencyCode.USD -> t.usdToEur
    }

    /**
     * État d'une position après application successive des transactions.
     * [cost] et [realized] en devise de l'actif ; leurs contre-valeurs EUR sont scindées en
     * part convertie au taux de chaque transaction ([costEur], [realizedEur]) et part sans taux saisi,
     * restée en devise de l'actif et convertie au taux courant ([costPending], [realizedPending]).
     */
    internal data class Position(
        val quantity: Double,
        val cost: Double,
        val realized: Double,
        val costEur: Double = 0.0,
        val costPending: Double = 0.0,
        val realizedEur: Double = 0.0,
        val realizedPending: Double = 0.0,
    ) {
        fun apply(t: Transaction, currency: CurrencyCode): Position {
            val rate = transactionRateToEur(t, currency)
            return when (t.side) {
                TransactionSide.BUY -> {
                    val amount = t.quantity * t.price + t.fees
                    copy(
                        quantity = quantity + t.quantity,
                        cost = cost + amount,
                        costEur = costEur + (rate?.let { amount * it } ?: 0.0),
                        costPending = costPending + (if (rate == null) amount else 0.0),
                    )
                }
                TransactionSide.SELL -> {
                    // Part du coût sortie au PRU, proportionnelle à la quantité vendue.
                    val share = if (quantity > EPS) t.quantity / quantity else 0.0
                    val removed = cost * share
                    val removedEur = costEur * share
                    val removedPending = costPending * share
                    val proceeds = t.quantity * t.price - t.fees
                    val qty = quantity - t.quantity
                    val closed = qty < EPS
                    Position(
                        quantity = if (closed) 0.0 else qty,
                        cost = if (closed) 0.0 else cost - removed,
                        realized = realized + proceeds - removed,
                        costEur = if (closed) 0.0 else costEur - removedEur,
                        costPending = if (closed) 0.0 else costPending - removedPending,
                        realizedEur = realizedEur + (rate?.let { proceeds * it } ?: 0.0) - removedEur,
                        realizedPending = realizedPending + (if (rate == null) proceeds else 0.0) - removedPending,
                    )
                }
            }
        }

        companion object {
            val EMPTY = Position(0.0, 0.0, 0.0)
        }
    }
}
