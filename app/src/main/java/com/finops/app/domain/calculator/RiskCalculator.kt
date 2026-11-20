package com.finops.app.domain.calculator

import com.finops.app.domain.model.PricePoint
import java.time.Duration
import kotlin.math.ln
import kotlin.math.sqrt

/** Mesures de risque à partir d'un historique de cours. */
object RiskCalculator {
    private const val DAYS_PER_YEAR = 365.25

    /**
     * Volatilité annualisée (écart-type des rendements logarithmiques), en %, sur les [years] dernières
     * années de [points]. La fréquence (jour, semaine) est déduite de l'espacement médian des points.
     * null si moins de 10 rendements ou si l'historique ne couvre pas ~90 % de la période.
     */
    fun volatility(points: List<PricePoint>, years: Int): Double? {
        val window = window(points, years) ?: return null
        val returns = window.zipWithNext { a, b -> ln(b.price / a.price) }
        if (returns.size < 10) return null
        val mean = returns.average()
        val variance = returns.sumOf { (it - mean) * (it - mean) } / (returns.size - 1)
        val spacingDays = window.zipWithNext { a, b -> (b.epochMillis - a.epochMillis) / 86_400_000.0 }.sorted()
            .let { it[it.size / 2] }.coerceAtLeast(1.0)
        // Jours de bourse : ~252 par an pour un pas quotidien.
        val periodsPerYear = if (spacingDays <= 1.5) 252.0 else DAYS_PER_YEAR / spacingDays
        return sqrt(variance * periodsPerYear) * 100
    }

    /** Perte maximale (pic → creux) en %, négative ou nulle, sur les [years] dernières années. */
    fun maxDrawdown(points: List<PricePoint>, years: Int): Double? {
        val window = window(points, years) ?: return null
        var peak = window.first().price
        var worst = 0.0
        for (p in window) {
            if (p.price > peak) peak = p.price
            worst = minOf(worst, (p.price - peak) / peak)
        }
        return worst * 100
    }

    /**
     * Classe de risque 1 à 7 selon les seuils de volatilité annualisée du barème européen
     * (SRRI, règlement 583/2010) : < 0,5 / 2 / 5 / 10 / 15 / 25 %.
     */
    fun riskClass(volatilityPercent: Double): Int = when {
        volatilityPercent < 0.5 -> 1
        volatilityPercent < 2 -> 2
        volatilityPercent < 5 -> 3
        volatilityPercent < 10 -> 4
        volatilityPercent < 15 -> 5
        volatilityPercent < 25 -> 6
        else -> 7
    }

    private fun window(points: List<PricePoint>, years: Int): List<PricePoint>? {
        if (points.size < 2) return null
        val end = points.last().epochMillis
        val start = end - Duration.ofDays((years * DAYS_PER_YEAR).toLong()).toMillis()
        // Couverture minimale de 90 % de la période demandée.
        if (points.first().epochMillis > start + (end - start) / 10) return null
        return points.filter { it.epochMillis >= start }.filter { it.price > 0 }.takeIf { it.size >= 2 }
    }
}
