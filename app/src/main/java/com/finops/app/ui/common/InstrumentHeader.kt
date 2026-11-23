package com.finops.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.finops.app.ui.designsystem.AssetAvatar
import com.finops.app.ui.designsystem.FinOpsTheme
import com.finops.app.ui.designsystem.HeroAmountStyle
import com.finops.app.ui.designsystem.HeroCard
import com.finops.app.ui.designsystem.HeroChangeBadge

/**
 * En-tête commun des fiches instrument et position : bandeau bleu avec logo, nom, sous-titre,
 * labels, montant principal, variation et ligne d'information.
 */
@Composable
fun InstrumentHeroHeader(
    ticker: String,
    name: String,
    subtitle: String,
    amount: String,
    modifier: Modifier = Modifier,
    /** Contre-valeur dans la devise par défaut, affichée en petit à côté du montant (devise ≠ devise par défaut). */
    convertedAmount: String? = null,
    labels: List<String> = emptyList(),
    changePercent: Double? = null,
    changeText: String? = null,
    footer: String? = null,
    logoUrl: String? = null,
) {
    val onHero = FinOpsTheme.finance.onHero
    val soft = onHero.copy(alpha = 0.85f)
    HeroCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                AssetAvatar(ticker, size = 44.dp, logoUrl = logoUrl)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, color = onHero, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = soft)
            }
        }
        if (labels.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            HeroLabels(labels)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(amount, style = HeroAmountStyle, color = onHero, modifier = Modifier.alignByBaseline())
            convertedAmount?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = soft, modifier = Modifier.alignByBaseline())
            }
        }
        if (changePercent != null || changeText != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                changePercent?.let { HeroChangeBadge(it) }
                changeText?.let { Text(it, color = onHero, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        footer?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = soft) }
    }
}

/** Labels blancs à texte bleu, lisibles sur le bandeau. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroLabels(labels: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEach { l ->
            Text(
                l,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = FinOpsTheme.finance.hero.last(),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 180.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}
