package com.finops.app.ui.feature.instrument

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.finops.app.domain.model.FundProfile
import com.finops.app.domain.model.RiskOverview
import com.finops.app.domain.usecase.InstrumentInsights
import com.finops.app.ui.common.formatDecimal
import com.finops.app.ui.designsystem.Caption
import com.finops.app.ui.designsystem.KeyValueRow
import com.finops.app.ui.designsystem.LoadingBox
import com.finops.app.ui.designsystem.SectionCard

/**
 * Labels d'identification (max 3) : pastilles pleines bien contrastées.
 * [onHero] = version claire sur fond de bandeau bleu.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LabelChips(labels: List<String>, modifier: Modifier = Modifier, onHero: Boolean = false) {
    val bg = if (onHero) Color.White else MaterialTheme.colorScheme.primary
    val fg = if (onHero) com.finops.app.ui.designsystem.FinOpsTheme.finance.hero.last() else MaterialTheme.colorScheme.onPrimary
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEach { l ->
            Text(
                l,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = fg,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 180.dp)
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

/** Informations complémentaires d'un ETF (fiche justETF), ou invitation à associer un ISIN. */
@Composable
fun FundInfoCard(insights: InstrumentInsights?, loading: Boolean, onLinkIsin: (String) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    SectionCard(title = "Informations complémentaires") {
        val fund = insights?.fund
        when {
            loading -> LoadingBox(height = 96.dp)
            fund != null -> {
                FundRows(fund)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Caption("ISIN ${fund.isin} · Source : justETF", Modifier.weight(1f))
                    TextButton(onClick = { editing = true }) { Text("Modifier") }
                }
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    insights?.fundError?.takeIf { insights.isin != null }
                        ?: "Associez l'ISIN de cet ETF pour afficher sa fiche : taille, frais, réplication, domiciliation…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                insights?.isin?.let { Caption("ISIN associé : $it") }
                Button(onClick = { editing = true }) {
                    Text(if (insights?.isin == null) "Associer un ISIN" else "Modifier l'ISIN")
                }
            }
        }
    }
    if (editing) {
        IsinDialog(
            initial = insights?.isin?.value.orEmpty(),
            onDismiss = { editing = false },
            onConfirm = { onLinkIsin(it); editing = false },
        )
    }
}

@Composable
private fun FundRows(f: FundProfile) {
    val rows = listOf(
        "Taille du fonds" to f.fundSize,
        "Frais courants (TER)" to f.terPercent?.let { "${it.formatDecimal()} % par an" },
        "Méthode de réplication" to listOfNotNull(f.replication, f.replicationDetail?.let { "($it)" }).joinToString(" ").ifEmpty { null },
        "Durable (ESG)" to f.sustainable?.let(::yesNo),
        "Couverture de change" to f.currencyHedged?.let(::yesNo),
        "Date de création" to f.launchDate,
        "Politique de distribution" to listOfNotNull(f.distributionPolicy, f.distributionInterval?.lowercase()?.let { "($it)" })
            .joinToString(" ").ifEmpty { null },
        "Domiciliation du fonds" to f.domicile,
        "Émetteur" to f.issuer,
        "Indice répliqué" to f.indexName,
        "Éligible au PEA" to f.peaEligible?.let(::yesNo),
    )
    rows.forEachIndexed { i, (label, value) ->
        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        KeyValueRow(label, value ?: "Non disponible")
    }
}

private fun yesNo(b: Boolean) = if (b) "Oui" else "Non"

@Composable
private fun IsinDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ISIN de l'ETF") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Caption("12 caractères, visible sur le site de l'émetteur ou de votre courtier.")
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.uppercase().trim() },
                    placeholder = { Text("FR0010315770") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.length == 12) { Text("Valider") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Aperçu des risques : indicateur 1–7, volatilité et perte maximale sur 1, 3 et 5 ans. */
@Composable
fun RiskCard(risk: RiskOverview?, loading: Boolean) {
    SectionCard(title = "Aperçu des risques") {
        when {
            loading -> LoadingBox(height = 96.dp)
            risk == null -> Caption("Historique insuffisant pour évaluer le risque.")
            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                risk.indicator?.let { RiskScale(it) }
                Caption(
                    if (risk.indicatorEstimated) "Indicateur estimé à partir de la volatilité (barème européen SRRI), mesures calculées sur l'historique Google Finance."
                    else "Indicateur synthétique de risque (SRI) du document d'informations clés. Source : justETF."
                )
                RiskTable(risk)
            }
        }
    }
}

/** Échelle 1 à 7, case active colorée du vert (faible) au rouge (élevé). */
@Composable
private fun RiskScale(level: Int) {
    val colors = listOf(
        Color(0xFF22C55E), Color(0xFF84CC16), Color(0xFFEAB308), Color(0xFFF59E0B),
        Color(0xFFF97316), Color(0xFFEF4444), Color(0xFFDC2626),
    )
    Column {
        Row(
            Modifier.fillMaxWidth().semantics { contentDescription = "Indicateur de risque $level sur 7" },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            (1..7).forEach { i ->
                val active = i == level
                Box(
                    Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) colors[i - 1] else MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$i",
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Caption("Risque plus faible", Modifier.weight(1f))
            Caption("Risque plus élevé")
        }
    }
}

@Composable
private fun RiskTable(risk: RiskOverview) {
    fun cell(v: Double?) = v?.let { "${it.formatDecimal()} %" } ?: "–"
    Column {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1.4f))
            RiskOverview.YEARS.forEach { y ->
                Caption(if (y == 1) "1 an" else "$y ans", Modifier.weight(1f).let { it }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        listOf("Volatilité" to risk.volatility, "Perte maximale" to risk.maxDrawdown).forEach { (label, values) ->
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.4f))
                RiskOverview.YEARS.forEach { y ->
                    Text(
                        cell(values[y]),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.weight(1f),
                        color = if (label.startsWith("Perte") && values[y] != null) MaterialTheme.colorScheme.error else Color.Unspecified,
                    )
                }
            }
        }
    }
}

private enum class CompositionView(val label: String) { HOLDINGS("Positions"), SECTORS("Secteurs"), COUNTRIES("Pays") }

/** Composition d'un ETF : 10 principales positions, secteurs et pays, avec leur poids. */
@Composable
fun CompositionCard(fund: FundProfile) {
    var view by rememberSaveable { mutableStateOf(CompositionView.HOLDINGS) }
    val available = CompositionView.entries.filter {
        when (it) {
            CompositionView.HOLDINGS -> fund.topHoldings.isNotEmpty()
            CompositionView.SECTORS -> fund.sectors.isNotEmpty()
            CompositionView.COUNTRIES -> fund.countries.isNotEmpty()
        }
    }
    if (available.isEmpty()) return
    if (view !in available) view = available.first()
    val rows = when (view) {
        CompositionView.HOLDINGS -> fund.topHoldings
        CompositionView.SECTORS -> toHundred(fund.sectors)
        CompositionView.COUNTRIES -> toHundred(fund.countries)
    }
    val palette = com.finops.app.ui.designsystem.FinOpsTheme.finance.chart
    val maxWeight = rows.maxOf { it.weightPercent }.coerceAtLeast(1.0)
    SectionCard(title = "Composition") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            available.forEach { v ->
                androidx.compose.material3.FilterChip(selected = v == view, onClick = { view = v }, label = { Text(v.label) })
            }
        }
        Spacer(Modifier.height(8.dp))
        if (view == CompositionView.HOLDINGS) {
            val top = fund.topHoldings.sumOf { it.weightPercent }
            Caption(
                "Les ${fund.topHoldings.size} principales positions pèsent ${top.formatDecimal()} % du fonds" +
                    (fund.holdingsCount?.let { " (sur $it au total)" } ?: "") + "."
            )
            Spacer(Modifier.height(8.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEachIndexed { i, w ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(w.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f))
                        Text("${w.weightPercent.formatDecimal()} %", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    // Barres relatives au plus gros poids pour rester lisibles.
                    com.finops.app.ui.designsystem.WeightBar(
                        w.weightPercent / maxWeight,
                        color = if (view == CompositionView.HOLDINGS) MaterialTheme.colorScheme.primary else palette[i % palette.size],
                    )
                }
            }
        }
        if (view != CompositionView.HOLDINGS) {
            Spacer(Modifier.height(8.dp))
            Caption("Principales catégories publiées par justETF ; le reste est regroupé en « Autre ».")
        }
    }
}


/** Poids arrondis à 2 décimales dont la somme fait exactement 100 %. */
private fun toHundred(rows: List<com.finops.app.domain.model.Weighting>) =
    rows.zip(com.finops.app.common.roundedPercents(rows.map { it.weightPercent }, decimals = 2)) { w, p -> w.copy(weightPercent = p) }
