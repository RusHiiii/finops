package com.finops.app.ui.common

import com.finops.app.ui.designsystem.ChartPoint
import com.finops.app.ui.designsystem.heroTrendColor
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.saveable.rememberSaveable
import com.finops.app.domain.model.AllocationSlice
import com.finops.app.domain.usecase.Exposure
import com.finops.app.ui.designsystem.WeightBar
import androidx.compose.foundation.layout.Arrangement
import com.finops.app.common.roundedPercents
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finops.app.domain.model.Allocation
import com.finops.app.domain.model.ChartRange
import com.finops.app.domain.model.CurrencyCode
import com.finops.app.domain.model.Holding
import com.finops.app.domain.model.PortfolioSummary
import com.finops.app.domain.calculator.Classification
import com.finops.app.domain.calculator.InstrumentLabels
import com.finops.app.ui.designsystem.WorldMap
import com.finops.app.domain.calculator.PortfolioCalculator
import com.finops.app.ui.designsystem.Caption
import com.finops.app.ui.designsystem.DonutChart
import com.finops.app.ui.designsystem.DonutSlice
import com.finops.app.ui.designsystem.FinOpsTheme
import com.finops.app.ui.designsystem.HeroAmountStyle
import com.finops.app.ui.designsystem.HeroCard
import com.finops.app.ui.designsystem.HeroChangeBadge
import com.finops.app.ui.designsystem.InstrumentRow
import com.finops.app.ui.designsystem.LineChart
import com.finops.app.ui.designsystem.LoadingBox
import com.finops.app.ui.designsystem.RangeSelector
import com.finops.app.ui.designsystem.SectionCard
import com.finops.app.ui.designsystem.SignedAmount
import com.finops.app.ui.designsystem.StatGrid
import com.finops.app.ui.designsystem.gainLossColor

/**
 * En-tête d'un patrimoine / portefeuille : valeur totale et variation du jour. Pendant la lecture
 * du graphique au doigt, affiche la valeur et la date du point pointé.
 */
@Composable
fun ValueHeader(
    label: String,
    summary: PortfolioSummary,
    chart: ChartUiState,
    scrubIndex: Int?,
    range: ChartRange,
    updatedAt: String?,
) {
    val scrubbed = scrubIndex?.let { chart.points.getOrNull(it) }
    val onHero = FinOpsTheme.finance.onHero
    val soft = onHero.copy(alpha = 0.8f)
    HeroCard {
        Text(label, style = MaterialTheme.typography.labelLarge, color = soft)
        Text(
            (scrubbed?.y ?: summary.totalValueEur).money(CurrencyCode.EUR),
            style = HeroAmountStyle,
            color = onHero,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (scrubbed != null) {
                val first = chart.points.first().y
                HeroChangeBadge(if (first == 0.0) 0.0 else (scrubbed.y - first) / first * 100)
                Text(scrubbed.x.formatInstant(intraday = range.isIntraday), style = MaterialTheme.typography.bodySmall, color = soft)
            } else {
                HeroChangeBadge(summary.dayChangePercent)
                Text(
                    summary.dayChangeEur.signedMoney(CurrencyCode.EUR) + " aujourd'hui",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onHero,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            buildAnnotatedString {
                append("Plus-value latente ")
                withStyle(SpanStyle(color = heroTrendColor(summary.unrealizedPnlEur), fontWeight = FontWeight.SemiBold)) {
                    append(summary.unrealizedPnlEur.signedMoney(CurrencyCode.EUR) + " (" + summary.unrealizedPnlPercent.formatSignedPercent() + ")")
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = soft,
        )
        when {
            summary.missingFx -> Text("Taux USD/EUR indisponible : positions en USD exclues", style = MaterialTheme.typography.bodySmall, color = onHero)
            updatedAt != null -> Text(updatedAt, style = MaterialTheme.typography.bodySmall, color = soft)
        }
    }
}

/** Graphique d'historique + sélecteur de période + performance de la période. */
@Composable
fun HistoryChartCard(
    chart: ChartUiState,
    range: ChartRange,
    scrubIndex: Int?,
    onScrub: (Int?) -> Unit,
    onRangeSelected: (ChartRange) -> Unit,
) {
    val perf = chart.performance
    val color = if (perf == null || perf.isPositive) FinOpsTheme.finance.gain else FinOpsTheme.finance.loss
    // Valeurs en EUR, tracées dans la devise d'affichage.
    val eurFactor = LocalMoney.current.factor(CurrencyCode.EUR.name) ?: 1.0
    SectionCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp, horizontal = 12.dp)) {
        when {
            chart.loading -> LoadingBox(height = 220.dp)
            chart.error != null -> Column(Modifier.fillMaxWidth().height(220.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Caption(chart.error)
            }
            else -> LineChart(
                points = chart.points.scaled(eurFactor),
                color = color,
                secondary = chart.secondary?.scaled(eurFactor),
                secondaryText = chart.secondary?.lastOrNull()?.let { "Investi ${it.y.money(CurrencyCode.EUR)}" },
                scrubIndex = scrubIndex,
                onScrub = onScrub,
                contentDescription = "Évolution sur ${range.longLabel}",
                formatAxis = { it.formatAxis() },
            )
        }
        Spacer(Modifier.height(12.dp))
        RangeSelector(range, onRangeSelected)
        if (perf != null && !chart.loading) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Caption("Performance ${range.longLabel.lowercase()}", Modifier.weight(1f))
                SignedAmount(perf.absolute, CurrencyCode.EUR, percent = perf.percent, style = MaterialTheme.typography.labelLarge)
            }
            if (chart.secondary != null) {
                Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    LegendDot(color)
                    Caption(" Valeur    ")
                    LegendDot(MaterialTheme.colorScheme.onSurfaceVariant)
                    Caption(" Montant investi")
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color) {
    androidx.compose.foundation.layout.Box(Modifier.size(8.dp).clip(CircleShape).background(color))
}

/** Synthèse chiffrée : valeur, montant investi, plus-values latente et réalisée. */
@Composable
fun SummaryCard(summary: PortfolioSummary) {
    SectionCard(title = "Synthèse") {
        StatGrid(
            listOf(
                "Valeur actuelle" to summary.totalValueEur.money(CurrencyCode.EUR),
                "Montant investi" to summary.totalCostEur.money(CurrencyCode.EUR),
                "Plus-value latente" to summary.unrealizedPnlEur.signedMoney(CurrencyCode.EUR) +
                    " (" + summary.unrealizedPnlPercent.formatSignedPercent() + ")",
                "Plus-value réalisée" to summary.realizedPnlEur.signedMoney(CurrencyCode.EUR),
            )
        )
    }
}

enum class AllocationView(val label: String) { ASSET("Ligne"), TYPE("Type"), CURRENCY("Devise") }

enum class ExposureView(val label: String) { SECTOR("Secteur"), COUNTRY("Pays"), MAP("Carte") }

/** Exposition sectorielle / géographique, chargée en arrière-plan (réseau). */
data class ExposureState(val loading: Boolean = true, val exposure: Exposure? = null)

/** Répartition du patrimoine ou d'un portefeuille : par ligne, type ou devise. */
@Composable
fun AllocationCard(allocation: Allocation, holdings: List<Holding>) {
    var view by rememberSaveable { mutableStateOf(AllocationView.ASSET) }
    val names = holdings.associate { it.asset.symbol.toString() to it.asset.name }
    val slices = when (view) {
        AllocationView.ASSET -> allocation.byAsset
        AllocationView.TYPE -> allocation.byType
        AllocationView.CURRENCY -> allocation.byCurrency
    }
    SectionCard(title = "Répartition") {
        ViewChips(AllocationView.entries, view, { it.label }) { view = it }
        Spacer(Modifier.height(12.dp))
        when {
            slices.isEmpty() -> Caption("Aucune position ouverte.")
            view == AllocationView.ASSET -> AllocationBars(grouped(slices, MAX_ASSET_ROWS).map { (names[it.key] ?: it.key) to it })
            else -> SliceDonut(grouped(slices)) { names[it] ?: allocationLabel(it) }
        }
    }
}

/**
 * Exposition économique par secteur ou par pays, calculée par transparence
 * (composition des ETF via justETF, secteur / pays Google pour les actions).
 */
@Composable
fun ExposureCard(exposure: ExposureState) {
    var view by rememberSaveable { mutableStateOf(ExposureView.SECTOR) }
    val e = exposure.exposure
    val slices = when (view) {
        ExposureView.SECTOR -> shortSectors(e?.bySector.orEmpty())
        ExposureView.COUNTRY, ExposureView.MAP -> e?.byCountry.orEmpty()
    }
    SectionCard(title = "Exposition") {
        ViewChips(ExposureView.entries, view, { it.label }) { view = it }
        Spacer(Modifier.height(12.dp))
        when {
            exposure.loading -> LoadingBox(height = 132.dp)
            slices.isEmpty() -> Caption("Exposition indisponible pour le moment.")
            else -> {
                if (view == ExposureView.MAP) {
                    WorldMap(remember(slices) { slices.associate { it.key to it.weight } })
                } else {
                    SliceDonut(grouped(slices)) { it }
                }
                if (e != null && e.coverage < 0.999) {
                    Spacer(Modifier.height(8.dp))
                    Caption(
                        "${((1 - e.coverage) * 100).formatPercent(0)} non classé : associez l'ISIN de vos ETF " +
                            "(fiche de l'ETF) pour les décomposer."
                    )
                }
            }
        }
    }
}

/** Libellés courts des secteurs ; fusionne ceux qui partagent un libellé (ex. « Services »). */
private fun shortSectors(raw: List<AllocationSlice>): List<AllocationSlice> =
    raw.groupBy { InstrumentLabels.short(it.key) }
        .map { (key, s) -> AllocationSlice(key, s.sumOf { it.valueEur }, s.sumOf { it.weight }) }
        .sortedWith(compareBy<AllocationSlice> { it.key == Classification.UNCLASSIFIED || it.key == Classification.OTHER }.thenByDescending { it.weight })

@Composable
private fun <T> ViewChips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o -> FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) }) }
    }
}

@Composable
private fun SliceDonut(slices: List<AllocationSlice>, label: (String) -> String) {
    DonutChart(
        slices.map { s ->
            DonutSlice(label = label(s.key), weight = s.weight, value = s.valueEur.money(CurrencyCode.EUR, decimals = 0))
        },
    )
}

/**
 * Au plus [max] lignes nommées ; le reste (et une éventuelle ligne « Autre » existante)
 * est fusionné dans une seule ligne « Autre », placée en dernier.
 */
private fun grouped(raw: List<AllocationSlice>, max: Int = MAX_ROWS): List<AllocationSlice> {
    val (other, named) = raw.partition { it.key == OTHER || it.key == "Autres" }
    if (named.size <= max && other.size <= 1) return named + other
    val rest = named.drop(max) + other
    val merged = AllocationSlice(OTHER, rest.sumOf { it.valueEur }, rest.sumOf { it.weight })
    return named.take(max) + listOfNotNull(merged.takeIf { rest.isNotEmpty() })
}

private const val MAX_ROWS = 4
private const val MAX_ASSET_ROWS = 6
private const val OTHER = "Autre"

/** Répartition par ligne : barres horizontales avec poids et montant. */
@Composable
private fun AllocationBars(rows: List<Pair<String, AllocationSlice>>) {
    val palette = FinOpsTheme.finance.chart
    val percents = remember(rows) { roundedPercents(rows.map { it.second.weight }) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEachIndexed { i, (label, s) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f))
                    Caption(s.valueEur.money(CurrencyCode.EUR, decimals = 0) + "  ")
                    Text(percents[i].formatPercent(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                WeightBar(s.weight, color = palette[i % palette.size])
            }
        }
    }
}

/** Ligne de position : valeur, quantité × PRU, plus-value latente. */
@Composable
fun HoldingRow(holding: Holding, weightPercent: Double? = null, onClick: () -> Unit) {
    val a = holding.asset
    // PRU converti au taux de chaque transaction (pas au taux courant).
    val display = LocalMoney.current
    val pru = holding.averagePriceEur(display.usdToEur)
        ?.takeIf { display.converts(a.currency.name) }
        ?.let { display.format(it, CurrencyCode.EUR.name) }
        ?: holding.averagePrice.money(a.currency)
    // Plus-value en EUR (effet de change inclus) si les montants sont convertis, sinon en devise de l'actif.
    val eurPnl = holding.unrealizedPnlEur(display.usdToEur)?.takeIf { display.converts(a.currency.name) }
    val pnl = eurPnl ?: holding.unrealizedPnl
    val pnlPercent = eurPnl?.let { holding.unrealizedPnlPercentEur(display.usdToEur) } ?: holding.unrealizedPnlPercent
    InstrumentRow(
        ticker = a.symbol.ticker,
        title = a.name,
        subtitle = "${holding.quantity.formatQuantity()} × $pru" +
            (weightPercent?.let { " · " + it.formatPercent() } ?: ""),
        trailingTop = holding.marketValue.money(a.currency),
        trailingBottom = {
            Text(
                pnlPercent.formatSignedPercent(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = gainLossColor(pnl),
            )
        },
        trend = pnl,
        onClick = onClick,
        logoUrl = stockLogoUrl(a.symbol, a.type),
    )
}

@Composable
fun SectionSpacer() = Spacer(Modifier.height(12.dp).width(0.dp))

/** Graphique de cours d'un instrument, avec ligne de référence optionnelle (ex. PRU). */
@Composable
fun PriceChartCard(
    chart: ChartUiState,
    range: ChartRange,
    scrubIndex: Int?,
    onScrub: (Int?) -> Unit,
    onRangeSelected: (ChartRange) -> Unit,
    currency: String?,
    baseline: Double? = null,
    baselineLabel: String? = null,
) {
    val perf = chart.performance
    val color = if (perf == null || perf.isPositive) FinOpsTheme.finance.gain else FinOpsTheme.finance.loss
    // Cours dans la devise de cotation, tracés dans la devise d'affichage.
    val factor = LocalMoney.current.factor(currency) ?: 1.0
    SectionCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp, horizontal = 12.dp)) {
        val scrubbed = scrubIndex?.let { chart.points.getOrNull(it) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (scrubbed != null) {
                Text(scrubbed.y.money(currency), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Caption(scrubbed.x.formatInstant(intraday = range.isIntraday))
            } else if (perf != null && !chart.loading) {
                Caption(range.longLabel, Modifier.weight(1f))
                Text(
                    perf.absolute.signedMoney(currency) + "  " + perf.percent.formatSignedPercent(),
                    style = MaterialTheme.typography.labelLarge,
                    color = gainLossColor(perf.absolute),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        when {
            chart.loading -> LoadingBox(height = 220.dp)
            chart.error != null -> Column(Modifier.fillMaxWidth().height(220.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Caption(chart.error)
            }
            else -> LineChart(
                points = chart.points.scaled(factor),
                color = color,
                baseline = baseline?.let { it * factor },
                baselineText = if (baseline != null && baselineLabel != null) "$baselineLabel ${baseline.money(currency)}" else null,
                scrubIndex = scrubIndex,
                onScrub = onScrub,
                contentDescription = "Cours sur ${range.longLabel}",
                formatAxis = { it.formatAxis() },
            )
        }
        Spacer(Modifier.height(12.dp))
        RangeSelector(range, onRangeSelected)
    }
}

/** Positions ouvertes regroupées par type (ETF, Actions), avec sous-total en EUR et poids. */
@Composable
fun PositionsCard(holdings: List<Holding>, usdToEur: Double?, onOpen: (Holding) -> Unit) {
    fun eur(h: Holding) = PortfolioCalculator.rateToEur(h.asset.currency, usdToEur)?.let { h.marketValue * it } ?: 0.0
    val groups = holdings.groupBy { it.asset.type }.toSortedMap()
    val ordered = groups.values.flatten()
    // Poids arrondis sur l'ensemble des lignes : leur somme (et celle des sous-totaux) fait 100 %.
    val percents = remember(ordered, usdToEur) { ordered.zip(roundedPercents(ordered.map(::eur))).toMap() }
    SectionCard(title = "Positions", contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
        groups.forEach { (type, group) ->
            val subtotal = group.sumOf(::eur)
            val subtotalPercent = group.sumOf { percents[it] ?: 0.0 }
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(type.pluralLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Caption(subtotal.money(CurrencyCode.EUR, decimals = 0) + " · " + subtotalPercent.formatPercent())
            }
            group.forEachIndexed { i, h ->
                if (i > 0) androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                HoldingRow(h, weightPercent = percents[h]) { onOpen(h) }
            }
        }
    }
}

/** Ordonnées multipliées par [factor] (conversion de devise). */
private fun List<ChartPoint>.scaled(factor: Double): List<ChartPoint> =
    if (factor == 1.0) this else map { it.copy(y = it.y * factor) }
