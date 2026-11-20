package com.finops.app.common

import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Convertit des poids (proportions quelconques) en pourcentages arrondis à [decimals] décimales
 * dont la somme vaut exactement 100 (méthode du plus fort reste) : arrondir chaque valeur
 * séparément peut sinon donner 99,9 % ou 100,1 % au total.
 */
fun roundedPercents(weights: List<Double>, decimals: Int = 1): List<Double> {
    val sum = weights.sum()
    if (weights.isEmpty() || sum <= 0) return weights.map { 0.0 }
    val factor = 10.0.pow(decimals)
    val scaled = weights.map { it / sum * 100 * factor }
    val units = scaled.map { floor(it).toLong() }.toMutableList()
    var missing = (100 * factor).roundToLong() - units.sum()
    // L'écart restant va aux plus grandes parties décimales.
    scaled.indices.sortedByDescending { scaled[it] - floor(scaled[it]) }.forEach { i ->
        if (missing > 0) {
            units[i]++
            missing--
        }
    }
    return units.map { it / factor }
}
