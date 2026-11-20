package com.finops.app.domain.calculator

import com.finops.app.domain.model.AssetType
import com.finops.app.domain.model.FundProfile
import com.finops.app.domain.model.InstrumentSnapshot

/** Labels courts (max [MAX]) pour identifier un instrument d'un coup d'œil, et une description d'une phrase. */
object InstrumentLabels {
    const val MAX = 3


    /**
     * Trois labels : le type (Action / ETF), le pays — siège pour une action, domiciliation du fonds
     * pour un ETF (à défaut, pays de cotation) — et le secteur d'activité.
     */
    fun labels(snapshot: InstrumentSnapshot, fund: FundProfile?): List<String> {
        val type = when (snapshot.quote.type) {
            AssetType.STOCK -> "Action"
            AssetType.ETF -> "ETF"
            null -> null
        }
        val country = fund?.domicile?.takeIf { snapshot.quote.type != AssetType.STOCK }
            ?: snapshot.quote.country?.let(Classification::country)
        return listOfNotNull(type, country, sector(snapshot, fund)).take(MAX)
    }

    /** Libellés courts pour les labels (les noms complets restent utilisés dans l'exposition). */
    private val SHORT_SECTORS = mapOf(
        "Matériaux non énergétiques" to "Matériaux",
        "Biens de consommation cycliques" to "Conso. cyclique",
        "Biens de consommation non cycliques" to "Conso. de base",
        "Soins de santé" to "Santé",
        "Télécommunication" to "Télécoms",
        "Services aux collectivités" to "Services publics",
        "Fournisseur" to "Services publics",
        "Services aux entreprises" to "Services",
        "Services aux consommateurs" to "Services",
    )

    private fun short(sector: String) = SHORT_SECTORS[sector] ?: sector

    /** Seuil au-delà duquel un ETF est considéré comme sectoriel. */
    private const val SECTOR_FUND_THRESHOLD = 50.0

    /**
     * Action : secteur Google. ETF : secteur dominant s'il pèse plus de 50 % du fonds
     * (ETF sectoriel), sinon « Multi-secteurs » ; inconnu sans fiche justETF.
     */
    private fun sector(snapshot: InstrumentSnapshot, fund: FundProfile?): String? =
        if (snapshot.quote.type == AssetType.STOCK) {
            snapshot.stats?.company?.sector?.let { short(Classification.sector(it)) }
        } else {
            fund?.sectors?.filter { it.name != Classification.OTHER }?.maxByOrNull { it.weightPercent }?.let {
                if (it.weightPercent > SECTOR_FUND_THRESHOLD) short(it.name) else "Multi-secteurs"
            }
        }

    /** Description courte : indice et univers pour un ETF, secteur et activité pour une action. */
    fun summary(snapshot: InstrumentSnapshot, fund: FundProfile?): String? {
        val company = snapshot.stats?.company
        return when {
            fund?.indexName != null ->
                "ETF qui réplique l'indice ${fund.indexName}" + (fund.investmentFocus?.let { " (${it.lowercase()})" } ?: "") +
                    (fund.terPercent?.let { ", frais de ${"%.2f".format(java.util.Locale.FRANCE, it)} % par an." } ?: ".")
            snapshot.quote.type == AssetType.STOCK && company?.industry != null ->
                "Entreprise du secteur ${Classification.sector(company.sector).lowercase()}, activité : ${company.industry.lowercase()}."
            else -> snapshot.stats?.description?.substringBefore(". ")?.takeIf { it.isNotBlank() }?.let { "$it." }
        }
    }
}
