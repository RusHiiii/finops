package com.finops.app.domain.calculator

import com.finops.app.domain.model.AllocationSlice

/**
 * Note de diversification de 1 (concentré) à 10 (très diversifié), fondée sur l'indice de
 * Herfindahl-Hirschman (HHI = Σ poids²) : note = 10 × (1 − HHI) / (1 − 1/[reference]),
 * où [reference] est le nombre de catégories d'un portefeuille parfaitement réparti.
 *
 * « Non classé » est ignoré (poids renormalisés). « Autre » agrège des catégories plus petites
 * que la plus petite catégorie nommée : sa contribution est estimée à poids × min(poids, plus petit nommé).
 */
object DiversificationCalculator {
    /** Nombre de catégories visé pour la note maximale. */
    const val COUNTRY_REFERENCE = 8
    const val SECTOR_REFERENCE = 11

    /** null si rien n'est classé. */
    fun score(slices: List<AllocationSlice>, reference: Int): Int? {
        val classified = slices.filter { it.key != Classification.UNCLASSIFIED && it.weight > 0 }
        val total = classified.sumOf { it.weight }
        if (total <= 0) return null
        val (other, named) = classified.partition { it.key == Classification.OTHER || it.key == "Autres" }
        val weights = named.map { it.weight / total }
        val smallest = weights.minOrNull() ?: 1.0
        val otherWeight = other.sumOf { it.weight } / total
        val hhi = weights.sumOf { it * it } + otherWeight * minOf(otherWeight, smallest)
        val raw = 10 * (1 - hhi) / (1 - 1.0 / reference)
        return raw.toInt().coerceIn(1, 10).let { if (raw - it >= 0.5 && it < 10) it + 1 else it }
    }

    /** Catégorie dominante (hors « Autre » / « Non classé ») et son poids parmi les positions classées. */
    fun dominant(slices: List<AllocationSlice>): AllocationSlice? {
        val classified = slices.filter { it.key != Classification.UNCLASSIFIED }
        val total = classified.sumOf { it.weight }.takeIf { it > 0 } ?: return null
        return classified.filter { it.key != Classification.OTHER }.maxByOrNull { it.weight }
            ?.let { it.copy(weight = it.weight / total) }
    }
}
