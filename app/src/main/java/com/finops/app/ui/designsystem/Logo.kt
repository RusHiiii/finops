package com.finops.app.ui.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Marque FinOps (même dessin que l'icône de lancement) : trois grains de riz de taille croissante,
 * inclinés, formant un histogramme montant, sur un carré bleu arrondi.
 */
@Composable
fun AppLogo(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val gradient = FinOpsTheme.finance.hero
    Canvas(modifier.size(size)) {
        // Repère 0..72 = zone utile de l'icône adaptative (18 à 90 sur 108).
        val k = this.size.width / 72f
        fun p(x: Float, y: Float) = Offset((x - 18) * k, (y - 18) * k)
        drawRoundRect(Brush.linearGradient(gradient), cornerRadius = CornerRadius(18 * k))
        // (centre x, centre y, largeur, hauteur, opacité)
        val grains = listOf(
            floatArrayOf(38f, 65f, 13f, 20f, 0.7f),
            floatArrayOf(54f, 58f, 15f, 32f, 0.85f),
            floatArrayOf(70f, 50f, 17f, 44f, 1f),
        )
        grains.forEach { (cx, cy, w, h, a) ->
            val c = p(cx, cy)
            rotate(18f, pivot = c) {
                drawOval(
                    Color.White.copy(alpha = a),
                    topLeft = Offset(c.x - w * k / 2, c.y - h * k / 2),
                    size = Size(w * k, h * k),
                )
            }
        }
    }
}

/** Logo + nom de l'app + slogan, pour la barre de titre de l'accueil. */
@Composable
fun AppWordmark(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AppLogo(size = 34.dp)
        androidx.compose.foundation.layout.Column {
            Text(
                androidx.compose.ui.res.stringResource(com.finops.app.R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                androidx.compose.ui.res.stringResource(com.finops.app.R.string.app_tagline),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
