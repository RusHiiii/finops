package com.finops.app.domain.model

/** Position calculée à partir des transactions, dans la devise de l'actif. */
data class Holding(
    val asset: Asset,
    val quantity: Double,
    /** Coût d'acquisition restant (PRU × quantité, frais inclus). */
    val costBasis: Double,
    val realizedPnl: Double,
    /** Part de [costBasis] convertie en EUR au taux de chaque transaction. */
    val costBasisFixedEur: Double,
    /** Part de [costBasis] sans taux saisi, en devise de l'actif. */
    val costBasisPending: Double,
    /** Part de [realizedPnl] convertie en EUR au taux de chaque transaction. */
    val realizedPnlFixedEur: Double,
    /** Part de [realizedPnl] sans taux saisi, en devise de l'actif. */
    val realizedPnlPending: Double,
) {
    val isOpen: Boolean get() = quantity > 0
    val averagePrice: Double get() = if (quantity > 0) costBasis / quantity else 0.0
    val marketValue: Double get() = asset.lastPrice?.let { quantity * it } ?: costBasis
    val unrealizedPnl: Double get() = marketValue - costBasis
    val unrealizedPnlPercent: Double get() = if (costBasis == 0.0) 0.0 else unrealizedPnl / costBasis * 100

    /** Coût restant en EUR au taux de chaque transaction (taux courant [usdToEur] à défaut) ; null si un taux manque. */
    fun costBasisEur(usdToEur: Double?): Double? = toEur(costBasisFixedEur, costBasisPending, usdToEur)

    /** PRU en EUR au taux de chaque transaction ; null si un taux manque. */
    fun averagePriceEur(usdToEur: Double?): Double? = if (quantity > 0) costBasisEur(usdToEur)?.let { it / quantity } else 0.0

    /** Plus-value réalisée en EUR, effet de change inclus ; null si un taux manque. */
    fun realizedPnlEur(usdToEur: Double?): Double? = toEur(realizedPnlFixedEur, realizedPnlPending, usdToEur)

    /** Plus-value latente en EUR (valeur au taux courant − coût aux taux historiques) ; null si un taux manque. */
    fun unrealizedPnlEur(usdToEur: Double?): Double? {
        val rate = currentRateToEur(usdToEur) ?: return null
        val cost = costBasisEur(usdToEur) ?: return null
        return marketValue * rate - cost
    }

    /** Plus-value latente en % du coût en EUR (effet de change inclus) ; null si un taux manque. */
    fun unrealizedPnlPercentEur(usdToEur: Double?): Double? {
        val cost = costBasisEur(usdToEur) ?: return null
        val pnl = unrealizedPnlEur(usdToEur) ?: return null
        return if (cost == 0.0) 0.0 else pnl / cost * 100
    }

    private fun currentRateToEur(usdToEur: Double?): Double? = when (asset.currency) {
        CurrencyCode.EUR -> 1.0
        CurrencyCode.USD -> usdToEur
    }

    private fun toEur(fixed: Double, pending: Double, usdToEur: Double?): Double? =
        if (pending == 0.0) fixed else currentRateToEur(usdToEur)?.let { fixed + pending * it }

    /** Variation de valeur depuis la clôture précédente ; null si cours inconnus. */
    val dayChange: Double?
        get() {
            val last = asset.lastPrice ?: return null
            val prev = asset.previousClose ?: return null
            return quantity * (last - prev)
        }
}

/** Totaux en EUR : valeur au taux courant, coût et réalisé aux taux des transactions. [missingFx] = positions USD exclues faute de taux. */
data class PortfolioSummary(
    val totalValueEur: Double,
    val totalCostEur: Double,
    val realizedPnlEur: Double,
    val dayChangeEur: Double,
    val missingFx: Boolean,
) {
    val unrealizedPnlEur: Double get() = totalValueEur - totalCostEur
    val unrealizedPnlPercent: Double get() = if (totalCostEur == 0.0) 0.0 else unrealizedPnlEur / totalCostEur * 100
    val dayChangePercent: Double
        get() {
            val previous = totalValueEur - dayChangeEur
            return if (previous == 0.0) 0.0 else dayChangeEur / previous * 100
        }

    operator fun plus(other: PortfolioSummary) = PortfolioSummary(
        totalValueEur + other.totalValueEur,
        totalCostEur + other.totalCostEur,
        realizedPnlEur + other.realizedPnlEur,
        dayChangeEur + other.dayChangeEur,
        missingFx || other.missingFx,
    )

    companion object {
        val EMPTY = PortfolioSummary(0.0, 0.0, 0.0, 0.0, false)
    }
}

data class PortfolioOverview(
    val portfolio: Portfolio,
    val summary: PortfolioSummary,
    /** Nombre de positions ouvertes. */
    val openPositions: Int = 0,
    /** Part du portefeuille dans le patrimoine total, ∈ [0, 1]. */
    val weight: Double = 0.0,
    /** Plus ancienne mise à jour de cours parmi ses positions ouvertes. */
    val quotesUpdatedAt: Long? = null,
)

/** Part d'une catégorie dans le patrimoine ; [weight] ∈ [0, 1]. */
data class AllocationSlice(val key: String, val valueEur: Double, val weight: Double)

data class Allocation(
    val byType: List<AllocationSlice>,
    val byCurrency: List<AllocationSlice>,
    val byAsset: List<AllocationSlice>,
) {
    companion object {
        val EMPTY = Allocation(emptyList(), emptyList(), emptyList())
    }
}

/** Point de la courbe d'un portefeuille : valeur de marché et capital investi, en EUR. */
data class ValuePoint(val epochMillis: Long, val valueEur: Double, val investedEur: Double)
