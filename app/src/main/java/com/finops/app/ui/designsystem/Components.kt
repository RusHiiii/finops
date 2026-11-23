package com.finops.app.ui.designsystem

import com.finops.app.ui.common.signedMoney
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import com.finops.app.ui.common.rememberLogo
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.finops.app.domain.model.CurrencyCode
import com.finops.app.ui.common.formatSignedPercent
import kotlin.math.abs

val CardShape = RoundedCornerShape(20.dp)
val ScreenPadding = PaddingValues(horizontal = 16.dp)

@Composable
fun gainLossColor(value: Double): Color =
    if (value >= 0) FinOpsTheme.finance.gain else FinOpsTheme.finance.loss

/** Pastille de variation en % (fond teinté vert/rouge, flèche de tendance). */
@Composable
fun ChangeBadge(percent: Double, modifier: Modifier = Modifier, prefix: String? = null) {
    val positive = percent >= 0
    val fg = if (positive) FinOpsTheme.finance.gain else FinOpsTheme.finance.loss
    val bg = if (positive) FinOpsTheme.finance.gainContainer else FinOpsTheme.finance.lossContainer
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            if (positive) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(14.dp),
        )
        Text(
            listOfNotNull(prefix, percent.formatSignedPercent()).joinToString(" "),
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Montant signé coloré, avec % optionnel : « +1 234,00 € (+5,20 %) ». */
@Composable
fun SignedAmount(
    amount: Double,
    currency: CurrencyCode,
    modifier: Modifier = Modifier,
    percent: Double? = null,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val pct = percent?.let { " (${it.formatSignedPercent()})" }.orEmpty()
    Text(amount.signedMoney(currency) + pct, modifier = modifier, color = gainLossColor(amount), style = style)
}

/** Carte de section : titre, action optionnelle à droite, contenu. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    action: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier.fillMaxWidth(), shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(contentPadding)) {
            if (title != null || action != null) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    action?.invoke()
                }
            }
            content()
        }
    }
}

@Composable
fun SectionAction(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(text) }
}

/**
 * Pastille ronde : logo ([logoUrl], actions) sur fond blanc s'il est disponible,
 * sinon initiales du ticker sur une couleur stable dérivée du ticker.
 */
@Composable
fun AssetAvatar(ticker: String, modifier: Modifier = Modifier, size: Dp = 40.dp, logoUrl: String? = null) {
    val logo = rememberLogo(logoUrl)
    if (logo != null) {
        Box(
            modifier.size(size).clip(CircleShape).background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(size * 0.68f))
        }
        return
    }
    val palette = FinOpsTheme.finance.chart
    val color = palette[abs(ticker.hashCode()) % palette.size]
    Box(
        modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            ticker.take(if (ticker.length <= 4) ticker.length else 3),
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.28f).sp,
            maxLines = 1,
        )
    }
}

/** Ligne d'instrument : avatar, nom + sous-titre, valeur + ligne secondaire à droite. */
@Composable
fun InstrumentRow(
    ticker: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailingTop: String? = null,
    trailingBottom: (@Composable () -> Unit)? = null,
    /** Signe de la performance : barre verte (≥ 0) ou rouge (< 0) à gauche ; null = pas de barre. */
    trend: Double? = null,
    onClick: (() -> Unit)? = null,
    /** Logo de l'action (voir [com.finops.app.ui.common.stockLogoUrl]). */
    logoUrl: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (trend != null) {
            TrendBar(trend)
            Spacer(Modifier.width(8.dp))
        }
        AssetAvatar(ticker, logoUrl = logoUrl)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailingTop != null || trailingBottom != null) {
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                trailingTop?.let { Text(it, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium) }
                trailingBottom?.invoke()
            }
        }
    }
}

/** Ligne libellé / valeur. */
@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = valueColor)
    }
}

/** Grille de statistiques en 2 colonnes ; les valeurs nulles sont masquées. */
@Composable
fun StatGrid(stats: List<Pair<String, String?>>, modifier: Modifier = Modifier) =
    StatGrid(stats.map { (label, value) -> Stat(label, value) }, modifier)

/** Statistique : libellé, valeur, et ligne secondaire optionnelle en petit (ex. conversion de devise). */
data class Stat(val label: String, val value: String?, val secondary: String? = null)

@Composable
@JvmName("StatGridOfStats")
fun StatGrid(stats: List<Stat>, modifier: Modifier = Modifier) {
    val visible = stats.filter { it.value != null }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        visible.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { stat ->
                    Column(Modifier.weight(1f)) {
                        Text(stat.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stat.value!!, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        stat.secondary?.let { Caption(it) }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier, height: Dp? = null) {
    Box(
        (if (height != null) modifier.fillMaxWidth().height(height) else modifier.fillMaxSize()),
        contentAlignment = Alignment.Center,
    ) { CircularProgressIndicator(strokeWidth = 3.dp) }
}

@Composable
fun MessageBox(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector = Icons.Default.ErrorOutline,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(4.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Libellé discret sous un montant (ex. « Mis à jour il y a 5 min »). */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodySmall, color = color)
}

/** Bandeau mis en avant (dégradé bleu) : patrimoine, valeur d'un portefeuille. */
@Composable
fun HeroCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    // Dégradé d'accent adouci (éclairci) pour un bandeau moins saturé ; le texte blanc reste lisible.
    val colors = FinOpsTheme.finance.hero.map { lerp(it, Color.White, HERO_SOFTENING) }
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(colors))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/**
 * Variation sur pastille légèrement teintée de vert (hausse) ou de rouge (baisse), pour le [HeroCard].
 * [text] remplace le % affiché ; le signe de [percent] fixe la couleur.
 */
@Composable
fun HeroChangeBadge(percent: Double, modifier: Modifier = Modifier, text: String? = null) {
    val fg = Color.White
    val tint = heroTrendColor(percent)
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.30f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            if (percent >= 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        Text(text ?: percent.formatSignedPercent(), color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

private const val HERO_SOFTENING = 0.14f

/** Vert / rouge pastel soutenus, lisibles sur le bandeau bleu sans être criards. */
fun heroTrendColor(value: Double): Color = if (value >= 0) Color(0xFF22C26B) else Color(0xFFF2505F)

/** Barre verticale verte (hausse) ou rouge (baisse). */
@Composable
fun TrendBar(value: Double, modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(4.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(gainLossColor(value))
            .semantics { contentDescription = if (value >= 0) "En hausse" else "En baisse" },
    )
}

/**
 * Sélecteur à pastilles défilant horizontalement : pastille choisie pleine (couleur d'accent),
 * les autres sur fond neutre contrasté. Remplace les FilterChip, peu lisibles sur fond transparent.
 */
@Composable
fun <T> PillSelector(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { o ->
            val isSelected = o == selected
            Surface(
                onClick = { onSelect(o) },
                shape = CircleShape,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Text(
                    label(o),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}
