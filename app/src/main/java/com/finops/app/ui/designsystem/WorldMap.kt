package com.finops.app.ui.designsystem

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import kotlin.math.sqrt

/**
 * Carte du monde (Natural Earth 110m, domaine public) colorée selon [weights] :
 * libellé de pays (français, ex. « États-Unis ») → poids ∈ [0, 1].
 * Les pays trop petits pour l'échelle 110m (Singapour, Hong Kong…) n'apparaissent pas.
 */
@Composable
fun WorldMap(weights: Map<String, Double>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val countries by produceState<List<MapCountry>>(emptyList()) { value = WorldShapes.load(context) }
    val byIso = remember(weights, countries) { WorldShapes.weightsByIso(weights, countries) }
    val max = byIso.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0

    val empty = MaterialTheme.colorScheme.surfaceVariant
    val low = lerp(empty, MaterialTheme.colorScheme.primary, 0.2f)
    val high = MaterialTheme.colorScheme.primary
    val border = MaterialTheme.colorScheme.surface

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(WorldShapes.WIDTH / WorldShapes.HEIGHT)
                .semantics { contentDescription = "Carte de l'exposition par pays" },
        ) {
            val k = size.width / WorldShapes.WIDTH
            scale(k, k, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                countries.forEach { c ->
                    val w = byIso[c.iso]
                    // Racine carrée : les petits poids restent visibles face à un pays dominant.
                    val color = if (w == null || w <= 0) empty else lerp(low, high, sqrt(w / max).toFloat())
                    drawPath(c.path, color)
                    drawPath(c.path, border, style = Stroke(width = 0.6f / k))
                }
            }
        }
        if (byIso.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Caption("0 %")
                Box(Modifier.width(96.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Brush.horizontalGradient(listOf(low, high))))
                Caption(String.format(java.util.Locale.FRANCE, "%.0f %%", max * 100))
            }
        }
    }
}

@Composable
private fun Caption(text: String) =
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

class MapCountry(val iso: String, val name: String, val path: Path)

private object WorldShapes {
    /**
     * Projection de Miller, en degrés : x = longitude + 180, y = M(84°) − M(latitude),
     * M(φ) = 1,25 · ln(tan(π/4 + 0,4 φ)) ; latitudes bornées à [−58°, 84°] (Antarctique exclu).
     */
    const val WIDTH = 360f
    const val HEIGHT = 180.3f

    /** Libellés utilisés par justETF / [com.finops.app.domain.calculator.Classification] absents de Natural Earth. */
    private val ALIASES = mapOf(
        "grande-bretagne" to "GB", "royaume-uni" to "GB", "angleterre" to "GB",
        "etats-unis d'amerique" to "US", "usa" to "US",
        "coree" to "KR", "republique de coree" to "KR",
        "republique tcheque" to "CZ", "tchequie" to "CZ",
        "chine" to "CN", "russie" to "RU", "emirats arabes unis" to "AE",
    )

    @Volatile private var cache: List<MapCountry>? = null

    suspend fun load(context: Context): List<MapCountry> = cache ?: withContext(Dispatchers.Default) {
        context.assets.open("world_countries.tsv").bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.map { line ->
                val (iso, name, d) = line.split('\t', limit = 3)
                MapCountry(iso, name, PathParser().parsePathString(d).toPath())
            }.toList()
        }.also { cache = it }
    }

    /** Poids par code ISO, en rapprochant les libellés par nom normalisé (sans accents, casse). */
    fun weightsByIso(weights: Map<String, Double>, countries: List<MapCountry>): Map<String, Double> {
        val isoByName = countries.associate { normalize(it.name) to it.iso } + ALIASES
        val isos = countries.mapTo(HashSet()) { it.iso }
        return weights.entries.mapNotNull { (label, w) ->
            val key = normalize(label)
            (isoByName[key] ?: label.uppercase().takeIf { it in isos })?.let { it to w }
        }.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
    }

    private fun normalize(s: String) =
        Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace('’', '\'')
}
