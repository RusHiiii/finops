package com.finops.app.data.remote

import com.finops.app.domain.model.FundProfile
import com.finops.app.domain.model.Isin
import com.finops.app.domain.model.RiskOverview
import com.finops.app.domain.model.Weighting

/** Extraction des données d'une page de profil justETF (version française), via les attributs `data-testid`. */
internal object JustEtfParser {
    private const val PROFILE_MARKER = "etf-basics_row_ter"

    fun isProfile(html: String) = PROFILE_MARKER in html

    fun parse(html: String, isin: Isin): FundProfile? {
        if (!isProfile(html)) return null
        return FundProfile(
            isin = isin,
            indexName = value(html, "index-name"),
            investmentFocus = value(html, "investment-focus"),
            fundSize = row(html, "fund-size", label = "Taille du fonds"),
            terPercent = value(html, "ter")?.let(::percent),
            replication = value(html, "replication"),
            replicationDetail = value(html, "replication-method"),
            sustainable = value(html, "sustainable")?.let(::yesNo),
            fundCurrency = value(html, "fund-currency"),
            currencyHedged = value(html, "currency-hedge")?.let(::yesNo),
            launchDate = value(html, "launch-date"),
            distributionPolicy = value(html, "distribution-policy"),
            distributionInterval = value(html, "distribution-interval"),
            domicile = value(html, "domicile-country"),
            issuer = value(html, "fund-provider"),
            peaEligible = Regex("""Éligible au PEA</span>\s*</td>\s*<td[^>]*>\s*<span>([^<]+)</span>""")
                .find(html)?.groupValues?.get(1)?.let(::yesNo),
            risk = risk(html),
            topHoldings = composition(html, "top-holdings"),
            holdingsCount = Regex("""data-testid="tl_etf-holdings_top-holdings_count">[^<\d]*([\d\s\u00A0\u202F.]+)""")
                .find(html)?.groupValues?.get(1)?.filter { it.isDigit() }?.toIntOrNull(),
            sectors = composition(html, "sectors"),
            countries = composition(html, "countries"),
        )
    }

    /** Lignes d'un tableau de composition (`top-holdings`, `sectors`, `countries`). */
    private fun composition(html: String, kind: String): List<Weighting> =
        Regex("""data-testid="etf-holdings_${kind}_row">(.*?)</tr>""", RegexOption.DOT_MATCHES_ALL).findAll(html).mapNotNull { m ->
            val row = m.groupValues[1]
            val name = Regex("""data-testid="tl_etf-holdings_${kind}_(?:link_name|value_name)"[^>]*>(?:\s*<span>)?([^<]+)""")
                .find(row)?.groupValues?.get(1)?.let(::clean)?.takeIf { it.isNotEmpty() }
                ?.replaceFirstChar { it.uppercase() } ?: return@mapNotNull null
            val weight = Regex("""data-testid="tl_etf-holdings_${kind}_value_percentage">([^<]+)""")
                .find(row)?.groupValues?.get(1)?.let(::percent) ?: return@mapNotNull null
            Weighting(name, weight)
        }.toList()

    private fun risk(html: String): RiskOverview? {
        val indicator = Regex("""risk-class-indicator__active"[^>]*>\s*<span>(\d)</span>""").find(html)?.groupValues?.get(1)?.toInt()
        fun byId(prefix: String) = Regex("""id="$prefix[0-9a-f]*">([^<]+)<""").find(html)?.groupValues?.get(1)?.let(::percent)
        val vol = mapOf(1 to byId("yearVolatility"), 3 to byId("threeYearVolatility"), 5 to byId("fiveYearVolatility"))
            .filterValues { it != null }.mapValues { it.value!! }
        val dd = mapOf(1 to byId("yearMaxDrawdown"), 3 to byId("threeYearMaxDrawdown"), 5 to byId("fiveYearMaxDrawdown"))
            .filterValues { it != null }.mapValues { it.value!! }
        if (indicator == null && vol.isEmpty() && dd.isEmpty()) return null
        return RiskOverview(indicator, indicatorEstimated = false, volatility = vol, maxDrawdown = dd)
    }

    /** Premier texte non vide d'un élément `tl_etf-basics_value_<key>`. */
    private fun value(html: String, key: String): String? =
        Regex("""data-testid="tl_etf-basics_value_$key"[^>]*>([^<]*)""").findAll(html)
            .map { clean(it.groupValues[1]) }
            .firstOrNull { it.isNotEmpty() }

    /** Texte d'une ligne sans valeur balisée (ex. taille du fonds), libellé retiré. */
    private fun row(html: String, key: String, label: String): String? {
        val start = html.indexOf("""data-testid="etf-basics_row_$key"""").takeIf { it >= 0 } ?: return null
        val end = html.indexOf("</tr>", start).takeIf { it > 0 } ?: return null
        val text = clean(html.substring(start, end).substringAfter('>').replace(Regex("<[^>]+>"), " "))
        return text.removePrefix(label).trim().ifEmpty { null }
    }

    private fun clean(s: String) = s
        .replace("&nbsp;", " ").replace("&#039;", "'").replace("&amp;", "&").replace("&quot;", "\"")
        .replace(Regex("\\s+"), " ").trim()

    /** « 0,30% p.a. » → 0.30 ; « -6,58% » → -6.58. */
    private fun percent(s: String): Double? =
        Regex("""-?\d+(?:[.,]\d+)?""").find(s.replace('−', '-'))?.value?.replace(',', '.')?.toDoubleOrNull()

    private fun yesNo(s: String): Boolean? = when {
        s.startsWith("Oui", ignoreCase = true) -> true
        s.startsWith("Non", ignoreCase = true) -> false
        else -> null
    }
}
