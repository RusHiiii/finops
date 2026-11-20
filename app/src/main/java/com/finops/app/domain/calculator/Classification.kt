package com.finops.app.domain.calculator

/**
 * Libellés communs de secteurs et de pays, alignés sur ceux de justETF (français) pour pouvoir
 * additionner l'exposition des actions (Google, en anglais / ISO) et celle des ETF (justETF).
 */
object Classification {
    const val OTHER = "Autre"
    const val UNCLASSIFIED = "Non classé"

    private val SECTORS = mapOf(
        "Information Technology" to "Technologie",
        "Technology" to "Technologie",
        "Financials" to "Finance",
        "Industrials" to "Industrie",
        "Health Care" to "Soins de santé",
        "Healthcare" to "Soins de santé",
        "Consumer Discretionary" to "Biens de consommation cycliques",
        "Consumer Staples" to "Biens de consommation non cycliques",
        "Communication Services" to "Télécommunication",
        "Energy" to "Énergie",
        "Materials" to "Matériaux non énergétiques",
        "Utilities" to "Fournisseur",
        "Real Estate" to "Immobilier",
    )

    private val COUNTRIES = mapOf(
        "US" to "États-Unis", "FR" to "France", "DE" to "Allemagne", "NL" to "Pays-Bas", "GB" to "Grande-Bretagne",
        "IT" to "Italie", "ES" to "Espagne", "BE" to "Belgique", "CH" to "Suisse", "IE" to "Irlande",
        "LU" to "Luxembourg", "JP" to "Japon", "CA" to "Canada", "CN" to "Chine", "TW" to "Taïwan",
        "KR" to "Corée du Sud", "IN" to "Inde", "AU" to "Australie", "SE" to "Suède", "DK" to "Danemark",
        "FI" to "Finlande", "NO" to "Norvège", "PT" to "Portugal", "AT" to "Autriche", "BR" to "Brésil",
    )

    /** Secteur Google (anglais) → libellé commun ; inconnu = tel quel. */
    fun sector(google: String?): String = google?.let { SECTORS[it] ?: it } ?: UNCLASSIFIED

    /** Code pays ISO → libellé commun ; inconnu = code tel quel. */
    fun country(iso: String?): String = iso?.let { COUNTRIES[it.uppercase()] ?: it } ?: UNCLASSIFIED
}
