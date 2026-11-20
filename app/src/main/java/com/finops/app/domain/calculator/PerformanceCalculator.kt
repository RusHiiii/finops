package com.finops.app.domain.calculator

import com.finops.app.domain.model.PricePoint
import com.finops.app.domain.model.ValuePoint

data class Performance(val absolute: Double, val percent: Double) {
    val isPositive: Boolean get() = absolute >= 0
}

object PerformanceCalculator {

    /** Variation entre [reference] (par défaut le premier point) et le dernier point ; null si moins de 2 points. */
    fun ofPrices(points: List<PricePoint>, reference: Double? = null): Performance? {
        if (points.isEmpty() || (reference == null && points.size < 2)) return null
        return between(reference ?: points.first().price, points.last().price)
    }

    /**
     * Performance d'un portefeuille sur la courbe : gain de valeur hors apports
     * (variation de valeur − variation du capital investi), rapportée à la valeur de départ
     * augmentée des apports nets de la période.
     */
    fun ofPortfolio(points: List<ValuePoint>): Performance? {
        if (points.size < 2) return null
        val first = points.first()
        val last = points.last()
        val netDeposits = last.investedEur - first.investedEur
        val gain = (last.valueEur - first.valueEur) - netDeposits
        val base = first.valueEur + maxOf(netDeposits, 0.0)
        return Performance(gain, if (base == 0.0) 0.0 else gain / base * 100)
    }

    fun between(from: Double, to: Double): Performance =
        Performance(to - from, if (from == 0.0) 0.0 else (to - from) / from * 100)
}
