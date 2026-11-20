package com.finops.app.domain.model

import java.time.LocalDate

/** Code ISIN (12 caractères, clé de contrôle valide). */
@JvmInline
value class Isin private constructor(val value: String) {
    override fun toString() = value

    companion object {
        private val PATTERN = Regex("^[A-Z]{2}[A-Z0-9]{9}[0-9]$")

        /** null si [text] n'est pas un ISIN valide (format + clé de Luhn). */
        fun parse(text: String): Isin? {
            val s = text.trim().uppercase()
            if (!PATTERN.matches(s)) return null
            // Lettres → nombres (A=10…Z=35), puis Luhn sur la chaîne de chiffres.
            val digits = s.map { if (it.isDigit()) it.toString() else (it - 'A' + 10).toString() }.joinToString("")
            var sum = 0
            digits.reversed().forEachIndexed { i, c ->
                var d = c - '0'
                if (i % 2 == 1) {
                    d *= 2
                    if (d > 9) d -= 9
                }
                sum += d
            }
            return if (sum % 10 == 0) Isin(s) else null
        }
    }
}

/** Fiche descriptive d'un ETF (source : justETF). Champs null = non publiés. */
data class FundProfile(
    val isin: Isin,
    val indexName: String?,
    /** Ex. « Actions, Monde ». */
    val investmentFocus: String?,
    val fundSize: String?,
    /** Frais totaux sur encours, en % par an. */
    val terPercent: Double?,
    val replication: String?,
    val replicationDetail: String?,
    val sustainable: Boolean?,
    val fundCurrency: String?,
    val currencyHedged: Boolean?,
    val launchDate: String?,
    /** « Capitalisation » ou « Distribution ». */
    val distributionPolicy: String?,
    val distributionInterval: String?,
    val domicile: String?,
    val issuer: String?,
    val peaEligible: Boolean?,
    val risk: RiskOverview?,
    /** 10 principales positions. */
    val topHoldings: List<Weighting> = emptyList(),
    /** Nombre total de positions du fonds. */
    val holdingsCount: Int? = null,
    /** Principaux secteurs (+ « Autre »), poids en %. */
    val sectors: List<Weighting> = emptyList(),
    /** Principaux pays (+ « Autre »), poids en %. */
    val countries: List<Weighting> = emptyList(),
)

/** Ligne de composition : nom et poids en %. */
data class Weighting(val name: String, val weightPercent: Double)

/** Données d'entreprise (actions) fournies par Google Finance. */
data class CompanyInfo(
    val sector: String?,
    val industry: String?,
    val country: String?,
    val founded: LocalDate?,
    val website: String?,
)

/** Aperçu des risques sur 1, 3 et 5 ans ; valeurs en %. */
data class RiskOverview(
    /** Indicateur 1 (faible) à 7 (élevé). */
    val indicator: Int?,
    /** true si l'indicateur est estimé (volatilité 5 ans) et non issu du DIC. */
    val indicatorEstimated: Boolean,
    val volatility: Map<Int, Double>,
    /** Pertes maximales, négatives. */
    val maxDrawdown: Map<Int, Double>,
) {
    companion object {
        val YEARS = listOf(1, 3, 5)
    }
}
