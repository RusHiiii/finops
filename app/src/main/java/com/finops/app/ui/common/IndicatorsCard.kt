package com.finops.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finops.app.domain.calculator.DiversificationCalculator
import com.finops.app.domain.model.AllocationSlice
import com.finops.app.ui.designsystem.Caption
import com.finops.app.ui.designsystem.FinOpsTheme
import com.finops.app.ui.designsystem.LoadingBox
import com.finops.app.ui.designsystem.SectionCard

private val Warning = Color(0xFFF59E0B)

/** Couleur d'une note : vert (7–10), orange (4–6), rouge (1–3). */
@Composable
fun scoreColor(score: Int): Color = when {
    score >= 7 -> FinOpsTheme.finance.gain
    score >= 4 -> Warning
    else -> FinOpsTheme.finance.loss
}

private fun verdict(score: Int) = when {
    score >= 7 -> "Bien diversifié"
    score >= 4 -> "Diversification moyenne"
    else -> "Très concentré"
}

/** Indicateurs de diversification géographique et sectorielle (notes de 1 à 10). */
@Composable
fun IndicatorsCard(exposure: ExposureState) {
    SectionCard(title = "Indicateurs") {
        val e = exposure.exposure
        when {
            exposure.loading -> LoadingBox(height = 120.dp)
            e == null -> Caption("Indicateurs indisponibles pour le moment.")
            else -> Column {
                ScoreRow("Diversité géographique", e.geographicScore, DiversificationCalculator.dominant(e.byCountry))
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                ScoreRow("Diversité sectorielle", e.sectorScore, DiversificationCalculator.dominant(e.bySector))
                Spacer(Modifier.height(12.dp))
                Caption(
                    "Note de 1 (concentré) à 10 (très diversifié), calculée sur l'exposition par transparence " +
                        "(composition des ETF)." +
                        if (e.coverage < 0.999) " Les lignes non classées sont ignorées." else ""
                )
            }
        }
    }
}

@Composable
private fun ScoreRow(label: String, score: Int?, dominant: AllocationSlice?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Caption(
                    if (score == null) "Non évaluable : aucune ligne classée"
                    else verdict(score) + (dominant?.let { " · ${it.key} ${(it.weight * 100).formatPercent(0)}" } ?: "")
                )
            }
            if (score != null) {
                Text(
                    "$score/10",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(scoreColor(score))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        if (score != null) ScoreGauge(score)
    }
}

/** Jauge de 10 segments ; les segments atteints prennent la couleur de la note. */
@Composable
private fun ScoreGauge(score: Int) {
    val color = scoreColor(score)
    Row(
        Modifier.fillMaxWidth().semantics { contentDescription = "Note $score sur 10" },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        (1..10).forEach { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (i <= score) color else MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
    }
}
