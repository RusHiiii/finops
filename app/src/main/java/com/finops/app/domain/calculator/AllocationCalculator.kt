package com.finops.app.domain.calculator

import com.finops.app.domain.model.Allocation
import com.finops.app.domain.model.AllocationSlice
import com.finops.app.domain.model.Holding

/** Répartition des positions ouvertes, en EUR, triée par poids décroissant. */
object AllocationCalculator {

    fun allocation(holdings: List<Holding>, usdToEur: Double?): Allocation {
        val valued = holdings.filter { it.isOpen }.mapNotNull { h ->
            PortfolioCalculator.rateToEur(h.asset.currency, usdToEur)?.let { h to h.marketValue * it }
        }
        val total = valued.sumOf { it.second }
        if (total <= 0.0) return Allocation.EMPTY

        fun slices(key: (Holding) -> String) = valued
            .groupBy({ key(it.first) }, { it.second })
            .map { (k, values) -> AllocationSlice(k, values.sum(), values.sum() / total) }
            .sortedByDescending { it.valueEur }

        return Allocation(
            byType = slices { it.asset.type.name },
            byCurrency = slices { it.asset.currency.name },
            byAsset = slices { it.asset.symbol.toString() },
        )
    }
}
