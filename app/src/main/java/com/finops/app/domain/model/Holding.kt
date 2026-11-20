package com.finops.app.domain.model

/** Position calculée à partir des transactions, dans la devise de l'actif. */
data class Holding(
    val asset: Asset,
    val quantity: Double,
    /** Coût d'acquisition restant (PRU × quantité, frais inclus). */
    val costBasis: Double,
    val realizedPnl: Double,
) {
    val isOpen: Boolean get() = quantity > 0
    val averagePrice: Double get() = if (quantity > 0) costBasis / quantity else 0.0
    val marketValue: Double get() = asset.lastPrice?.let { quantity * it } ?: costBasis
    val unrealizedPnl: Double get() = marketValue - costBasis
    val unrealizedPnlPercent: Double get() = if (costBasis == 0.0) 0.0 else unrealizedPnl / costBasis * 100

    /** Variation de valeur depuis la clôture précédente ; null si cours inconnus. */
    val dayChange: Double?
        get() {
            val last = asset.lastPrice ?: return null
            val prev = asset.previousClose ?: return null
            return quantity * (last - prev)
        }
}

/** Totaux convertis en EUR. [missingFx] = positions USD exclues faute de taux. */
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
