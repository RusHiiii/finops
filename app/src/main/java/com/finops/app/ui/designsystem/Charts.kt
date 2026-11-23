package com.finops.app.ui.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.finops.app.domain.model.ChartRange
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer

@Immutable
data class ChartPoint(val x: Long, val y: Double)

/**
 * Courbe avec remplissage en dégradé et lecture au doigt : appui ou glissement → [onScrub] reçoit
 * l'index du point sous le doigt, puis null au relâchement. Abscisses réparties par index
 * (pas de trous la nuit et le week-end).
 *
 * @param secondary seconde courbe en pointillés (ex. capital investi), mêmes abscisses que [points].
 * @param baseline ligne horizontale en pointillés (ex. clôture précédente, PRU).
 * @param baselineText petit libellé affiché au bout de la ligne [baseline] (ex. « Clôture préc. 688,91 € »).
 * @param secondaryText petit libellé affiché au bout de la courbe [secondary] (ex. « Investi 704,24 € »).
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    secondary: List<ChartPoint>? = null,
    baseline: Double? = null,
    baselineText: String? = null,
    secondaryText: String? = null,
    scrubIndex: Int? = null,
    onScrub: (Int?) -> Unit = {},
    contentDescription: String = "Graphique",
    /** Libellé des graduations de l'axe des ordonnées. */
    formatAxis: (Double) -> String = { String.format(java.util.Locale.FRANCE, "%.2f", it) },
) {
    val guide = MaterialTheme.colorScheme.outline
    val grid = MaterialTheme.colorScheme.outlineVariant
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val background = MaterialTheme.colorScheme.surfaceContainer
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val baselineStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
    val measurer = rememberTextMeasurer()
    if (points.size < 2) {
        Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            Caption("Pas assez de données pour cette période")
        }
        return
    }
    val n = points.size
    val values = remember(points, secondary, baseline) { points.map { it.y } + secondary.orEmpty().map { it.y } + listOfNotNull(baseline) }
    val ticks = remember(values) { niceTicks(values.min(), values.max()) }
    val labels = remember(ticks) { ticks.map { formatAxis(it) } }
    val axisWidthPx = remember(labels) { labels.maxOf { measurer.measure(it, axisStyle).size.width } }
    val gapPx = with(LocalDensity.current) { 8.dp.toPx() }
    // Axe des abscisses : quelques dates réparties par index, format selon la durée couverte.
    val xTicks = remember(points) { xAxisTicks(points) }
    val xAxisHeightPx = remember(xTicks) { (xTicks.maxOfOrNull { measurer.measure(it.second, axisStyle).size.height } ?: 0) }
    val xAxisGapPx = with(LocalDensity.current) { 6.dp.toPx() }
    fun plotWidth(total: Float) = (total - axisWidthPx - gapPx).coerceAtLeast(1f)
    /** Zone de tracé à droite de l'axe des ordonnées. */
    val plotLeft = axisWidthPx + gapPx
    fun indexAt(x: Float, width: Float) = ((x - plotLeft) / plotWidth(width) * (n - 1)).roundToInt().coerceIn(0, n - 1)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { this.contentDescription = contentDescription }
            .pointerInput(points) {
                detectTapGestures(onPress = { o ->
                    onScrub(indexAt(o.x, size.width.toFloat()))
                    tryAwaitRelease()
                    onScrub(null)
                })
            }
            .pointerInput(points) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> onScrub(indexAt(o.x, size.width.toFloat())) },
                    onDragEnd = { onScrub(null) },
                    onDragCancel = { onScrub(null) },
                ) { change, _ ->
                    change.consume()
                    onScrub(indexAt(change.position.x, size.width.toFloat()))
                }
            },
    ) {
        val lo = ticks.first()
        val span = (ticks.last() - lo).takeIf { it > 0 } ?: 1.0
        val top = 8.dp.toPx()
        val h = size.height - top - xAxisHeightPx - xAxisGapPx - 4.dp.toPx()
        val w = plotWidth(size.width)
        val right = plotLeft + w
        fun x(i: Int) = plotLeft + i.toFloat() / (n - 1) * w
        fun y(v: Double) = top + ((1 - (v - lo) / span) * h).toFloat()

        // Axe des ordonnées : lignes de repère + libellés à gauche.
        ticks.forEachIndexed { i, t ->
            val ty = y(t)
            drawLine(grid, Offset(plotLeft, ty), Offset(right, ty), strokeWidth = 1.dp.toPx())
            val layout = measurer.measure(labels[i], axisStyle)
            // Libellés alignés à droite, contre la zone de tracé.
            drawText(layout, topLeft = Offset((axisWidthPx - layout.size.width).toFloat(), ty - layout.size.height / 2f))
        }

        // Axe des abscisses : trait, graduations et dates centrées (bornées à la zone de tracé).
        val axisY = top + h
        drawLine(guide.copy(alpha = 0.6f), Offset(plotLeft, axisY), Offset(right, axisY), strokeWidth = 1.dp.toPx())
        xTicks.forEach { (i, text) ->
            val tx = x(i)
            drawLine(guide.copy(alpha = 0.6f), Offset(tx, axisY), Offset(tx, axisY + 3.dp.toPx()), strokeWidth = 1.dp.toPx())
            val layout = measurer.measure(text, axisStyle)
            val lx = (tx - layout.size.width / 2f).coerceIn(plotLeft, right - layout.size.width)
            drawText(layout, topLeft = Offset(lx, axisY + xAxisGapPx))
        }

        baseline?.let {
            drawLine(
                guide, Offset(plotLeft, y(it)), Offset(right, y(it)),
                strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)),
            )
        }

        val line = Path().apply {
            points.forEachIndexed { i, p -> if (i == 0) moveTo(x(i), y(p.y)) else lineTo(x(i), y(p.y)) }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(right, top + h)
            lineTo(plotLeft, top + h)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f))))

        secondary?.takeIf { it.size == n }?.let { s ->
            val path = Path().apply { s.forEachIndexed { i, p -> if (i == 0) moveTo(x(i), y(p.y)) else lineTo(x(i), y(p.y)) } }
            drawPath(
                path, secondaryColor.copy(alpha = 0.7f),
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))),
            )
        }

        drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        /** Petit libellé à droite, collé à la hauteur [ly] : au-dessus ([above]) ou en dessous, selon la place disponible. */
        fun tag(text: String, ly: Float, above: Boolean) {
            val layout = measurer.measure(text, baselineStyle)
            val pad = 3.dp.toPx()
            val fitsAbove = ly - layout.size.height - 2 * pad > 0
            val fitsBelow = ly + layout.size.height + 2 * pad < size.height
            val ty = if (above && fitsAbove || !fitsBelow) ly - layout.size.height - pad else ly + pad
            val tx = right - layout.size.width - pad
            drawRoundRect(
                background.copy(alpha = 0.85f),
                topLeft = Offset(tx - pad, ty - pad / 2),
                size = Size(layout.size.width + 2 * pad, layout.size.height + pad),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
            drawText(layout, topLeft = Offset(tx, ty))
        }

        // Libellés de la ligne de référence et de la courbe secondaire, du côté opposé à la fin de la courbe principale.
        val lastY = y(points.last().y)
        if (baseline != null && baselineText != null) tag(baselineText, y(baseline), above = y(baseline) <= lastY + 1)
        val lastSecondary = secondary?.takeIf { it.size == n }?.last()?.y
        if (lastSecondary != null && secondaryText != null) tag(secondaryText, y(lastSecondary), above = y(lastSecondary) <= lastY + 1)

        scrubIndex?.takeIf { it in points.indices }?.let { i ->
            val cx = x(i)
            val cy = y(points[i].y)
            drawLine(guide, Offset(cx, 0f), Offset(cx, axisY), strokeWidth = 1.dp.toPx())
            drawCircle(background, radius = 7.dp.toPx(), center = Offset(cx, cy))
            drawCircle(color, radius = 5.dp.toPx(), center = Offset(cx, cy))
        }
    }
}

private val X_TIME = java.time.format.DateTimeFormatter.ofPattern("HH:mm", java.util.Locale.FRANCE)
private val X_DAY = java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.FRANCE)
private val X_MONTH = java.time.format.DateTimeFormatter.ofPattern("MMM yy", java.util.Locale.FRANCE)
private val X_YEAR = java.time.format.DateTimeFormatter.ofPattern("yyyy", java.util.Locale.FRANCE)

/**
 * Graduations de l'axe des abscisses : (index du point, date) pour [count] points répartis.
 * Format selon la durée : heure (≤ 2 j), jour (≤ 45 j), mois (≤ 2 ans), année.
 */
internal fun xAxisTicks(points: List<ChartPoint>, count: Int = 4): List<Pair<Int, String>> {
    if (points.size < 2) return emptyList()
    val spanDays = (points.last().x - points.first().x) / 86_400_000.0
    val fmt = when {
        spanDays <= 2 -> X_TIME
        spanDays <= 45 -> X_DAY
        spanDays <= 730 -> X_MONTH
        else -> X_YEAR
    }
    val zone = java.time.ZoneId.systemDefault()
    val last = points.lastIndex
    return (0 until count).map { k -> (k * last.toDouble() / (count - 1)).roundToInt() }
        .distinct()
        .map { i -> i to java.time.Instant.ofEpochMilli(points[i].x).atZone(zone).format(fmt) }
        .distinctBy { it.second }
}

/** 3 à 6 graduations « rondes » (pas de 1, 2, 2,5 ou 5 × 10ⁿ) encadrant [min, max]. */
internal fun niceTicks(min: Double, max: Double, target: Int = 4): List<Double> {
    val range = (max - min).takeIf { it > 0 } ?: (kotlin.math.abs(max) * 0.02).coerceAtLeast(1e-6)
    val raw = range / (target - 1)
    val magnitude = 10.0.pow(floor(log10(raw)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { it * magnitude >= raw } * magnitude
    val start = floor(min / step) * step
    val end = ceil(max / step) * step
    return generateSequence(start) { it + step }.takeWhile { it <= end + step / 2 }.toList()
}

/** Sélecteur de période : pastilles de même largeur. */
@Composable
fun RangeSelector(
    selected: ChartRange,
    onSelect: (ChartRange) -> Unit,
    modifier: Modifier = Modifier,
    ranges: List<ChartRange> = ChartRange.entries,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ranges.forEach { r ->
            val isSelected = r == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable { onSelect(r) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    r.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Immutable
data class DonutSlice(val label: String, val weight: Double, val value: String)

/** Anneau de répartition + légende (pourcentage et montant). */
@Composable
fun DonutChart(slices: List<DonutSlice>, modifier: Modifier = Modifier, center: String? = null) {
    val palette = FinOpsTheme.finance.chart
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val percents = remember(slices) { com.finops.app.common.roundedPercents(slices.map { it.weight }) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(132.dp)) {
                val stroke = 18.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                val gap = if (slices.size > 1) 2f else 0f
                var start = -90f
                slices.forEachIndexed { i, s ->
                    val sweep = (s.weight * 360).toFloat()
                    if (sweep > gap) {
                        drawArc(
                            palette[i % palette.size], start + gap / 2, sweep - gap, false,
                            Offset(inset, inset), arcSize, style = Stroke(stroke),
                        )
                    }
                    start += sweep
                }
            }
            center?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
        }
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            slices.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(palette[i % palette.size]))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        Caption(s.value)
                    }
                    Text(
                        String.format(java.util.Locale.FRANCE, "%.1f %%", percents[i]),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** Barre de poids horizontale (ex. part d'une position). */
@Composable
fun WeightBar(weight: Double, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Box(Modifier.fillMaxWidth(weight.toFloat().coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
    }
}
