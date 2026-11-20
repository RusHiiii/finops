package com.finops.app.ui.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.finops.app.domain.model.AccentColor

/** Couleurs propres à la finance (gain / perte), à la data-viz et au bandeau d'en-tête. */
@Immutable
data class FinanceColors(
    val gain: Color,
    val loss: Color,
    val gainContainer: Color,
    val lossContainer: Color,
    val chart: List<Color>,
    /** Dégradé du bandeau principal (patrimoine). */
    val hero: List<Color>,
    val onHero: Color,
    /** Fond d'écran : dégradé vertical bleuté, du haut vers le bas. */
    val backgroundGradient: List<Color>,
)

/** Palette de données : bleus d'abord, puis teintes complémentaires douces. */
private val ChartPalette = listOf(
    Color(0xFF2F6FEB), Color(0xFF38BDF8), Color(0xFF6366F1), Color(0xFF14B8A6),
    Color(0xFFF59E0B), Color(0xFFEC4899), Color(0xFF8B5CF6), Color(0xFF22C55E),
)

private val LightFinance = FinanceColors(
    gain = Color(0xFF0F9D6B),
    loss = Color(0xFFE0475B),
    gainContainer = Color(0x1F0F9D6B),
    lossContainer = Color(0x1FE0475B),
    chart = ChartPalette,
    hero = listOf(Color(0xFF5B9DFB), Color(0xFF2F6FEB), Color(0xFF2559D6)),
    onHero = Color.White,
    backgroundGradient = listOf(Color(0xFFD6E4FF), Color(0xFFE9F0FD), Color(0xFFF5F8FD)),
)

private val DarkFinance = FinanceColors(
    gain = Color(0xFF34D399),
    loss = Color(0xFFFB7185),
    gainContainer = Color(0x2634D399),
    lossContainer = Color(0x26FB7185),
    chart = ChartPalette,
    hero = listOf(Color(0xFF2F6FEB), Color(0xFF2149B8), Color(0xFF1A3A8F)),
    onHero = Color.White,
    backgroundGradient = listOf(Color(0xFF16284F), Color(0xFF0F1A31), Color(0xFF0B1220)),
)

/** Clair : fond bleuté très léger, cartes blanches, accent bleu. */
private val LightScheme = lightColorScheme(
    primary = Color(0xFF2F6FEB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = Color(0xFF0B2E73),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0C4A6E),
    background = Color(0xFFF5F8FD),
    onBackground = Color(0xFF0F1A2E),
    surface = Color(0xFFF5F8FD),
    onSurface = Color(0xFF0F1A2E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEDF2FB),
    surfaceContainerHighest = Color(0xFFE3EAF6),
    surfaceVariant = Color(0xFFEDF2FB),
    onSurfaceVariant = Color(0xFF5B6B85),
    outline = Color(0xFFC3CEE0),
    outlineVariant = Color(0xFFE6ECF5),
    error = Color(0xFFE0475B),
)

/** Sombre : bleu nuit plutôt que noir, même accent. */
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7AA7FF),
    onPrimary = Color(0xFF0A1A3F),
    primaryContainer = Color(0xFF1F3B7A),
    onPrimaryContainer = Color(0xFFDCE8FF),
    secondary = Color(0xFF7DD3FC),
    onSecondary = Color(0xFF082F49),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE6ECF7),
    surface = Color(0xFF0B1220),
    onSurface = Color(0xFFE6ECF7),
    surfaceContainerLowest = Color(0xFF070C16),
    surfaceContainerLow = Color(0xFF0F1828),
    surfaceContainer = Color(0xFF131E32),
    surfaceContainerHigh = Color(0xFF1A273F),
    surfaceContainerHighest = Color(0xFF22304B),
    surfaceVariant = Color(0xFF1A273F),
    onSurfaceVariant = Color(0xFF94A3BD),
    outline = Color(0xFF34435E),
    outlineVariant = Color(0xFF1F2B42),
    error = Color(0xFFFB7185),
)

/** Chiffres à chasse fixe : les montants restent alignés et ne « dansent » pas. */
private const val TABULAR = "tnum"

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR),
        bodyLarge = t.bodyLarge.copy(fontFeatureSettings = TABULAR),
        bodyMedium = t.bodyMedium.copy(fontFeatureSettings = TABULAR),
        bodySmall = t.bodySmall.copy(fontFeatureSettings = TABULAR),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR),
        labelMedium = t.labelMedium.copy(fontFeatureSettings = TABULAR),
    )
}

val LocalFinanceColors = staticCompositionLocalOf { LightFinance }

object FinOpsTheme {
    val finance: FinanceColors
        @Composable get() = LocalFinanceColors.current
}

/** Déclinaison d'une couleur d'accent en clair et en sombre. */
private class AccentPalette(
    val lightPrimary: Color, val lightContainer: Color, val lightOnContainer: Color,
    val darkPrimary: Color, val darkOnPrimary: Color, val darkContainer: Color, val darkOnContainer: Color,
    val lightHero: List<Color>, val darkHero: List<Color>,
    val lightBackground: List<Color>, val darkBackground: List<Color>,
)

private fun palette(accent: AccentColor): AccentPalette = when (accent) {
    AccentColor.BLUE -> AccentPalette(
        Color(0xFF2F6FEB), Color(0xFFDCE8FF), Color(0xFF0B2E73),
        Color(0xFF7AA7FF), Color(0xFF0A1A3F), Color(0xFF1F3B7A), Color(0xFFDCE8FF),
        listOf(Color(0xFF5B9DFB), Color(0xFF2F6FEB), Color(0xFF2559D6)),
        listOf(Color(0xFF2F6FEB), Color(0xFF2149B8), Color(0xFF1A3A8F)),
        listOf(Color(0xFFD6E4FF), Color(0xFFE9F0FD), Color(0xFFF5F8FD)),
        listOf(Color(0xFF16284F), Color(0xFF0F1A31), Color(0xFF0B1220)),
    )
    AccentColor.EMERALD -> AccentPalette(
        Color(0xFF0D9488), Color(0xFFCCF2EC), Color(0xFF053B35),
        Color(0xFF5EEAD4), Color(0xFF042F2B), Color(0xFF134E48), Color(0xFFCCF2EC),
        listOf(Color(0xFF2DD4BF), Color(0xFF0D9488), Color(0xFF0B7A70)),
        listOf(Color(0xFF0D9488), Color(0xFF0B6F67), Color(0xFF08524C)),
        listOf(Color(0xFFCFF3EE), Color(0xFFE6F7F4), Color(0xFFF4FAF9)),
        listOf(Color(0xFF0F3532), Color(0xFF0C1F22), Color(0xFF0A1418)),
    )
    AccentColor.VIOLET -> AccentPalette(
        Color(0xFF7C3AED), Color(0xFFEDE4FF), Color(0xFF2E1065),
        Color(0xFFB79BFF), Color(0xFF1E0B46), Color(0xFF3B2370), Color(0xFFEDE4FF),
        listOf(Color(0xFFA78BFA), Color(0xFF7C3AED), Color(0xFF6527D3)),
        listOf(Color(0xFF7C3AED), Color(0xFF5B21B6), Color(0xFF431784)),
        listOf(Color(0xFFE6DCFF), Color(0xFFF1ECFD), Color(0xFFF7F5FD)),
        listOf(Color(0xFF2A1A4F), Color(0xFF181331), Color(0xFF100D1F)),
    )
    AccentColor.CORAL -> AccentPalette(
        Color(0xFFE8572F), Color(0xFFFFE3D9), Color(0xFF5A1A06),
        Color(0xFFFF9B7A), Color(0xFF3D1205), Color(0xFF6B2B17), Color(0xFFFFE3D9),
        listOf(Color(0xFFFF9A76), Color(0xFFF0643C), Color(0xFFD94E26)),
        listOf(Color(0xFFE8592E), Color(0xFFB8441F), Color(0xFF8A3215)),
        listOf(Color(0xFFFFE1D6), Color(0xFFFDEEE8), Color(0xFFFDF7F4)),
        listOf(Color(0xFF3A1E16), Color(0xFF22140F), Color(0xFF140E0B)),
    )
}

/** Couleur représentative d'un accent (pastilles du sélecteur). */
fun accentSwatch(accent: AccentColor): Color = palette(accent).lightPrimary

/** Couleur de fond de fenêtre (avant le premier rendu Compose). */
fun windowBackground(accent: AccentColor, dark: Boolean): Color =
    palette(accent).let { if (dark) it.darkBackground.last() else it.lightBackground[1] }

/**
 * Thème de l'app : mode clair / sombre et couleur d'accent ([accent]) ; les neutres, gains et pertes
 * sont communs à tous les accents (pas de couleurs dynamiques).
 */
@Composable
fun FinOpsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: AccentColor = AccentColor.BLUE,
    content: @Composable () -> Unit,
) {
    val p = palette(accent)
    val finance = if (darkTheme) {
        DarkFinance.copy(hero = p.darkHero, backgroundGradient = p.darkBackground, chart = listOf(p.lightPrimary) + ChartPalette.drop(1))
    } else {
        LightFinance.copy(hero = p.lightHero, backgroundGradient = p.lightBackground, chart = listOf(p.lightPrimary) + ChartPalette.drop(1))
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalFinanceColors provides finance) {
        val scheme = if (darkTheme) {
            DarkScheme.copy(
                primary = p.darkPrimary, onPrimary = p.darkOnPrimary, primaryContainer = p.darkContainer, onPrimaryContainer = p.darkOnContainer,
                secondaryContainer = p.darkContainer, onSecondaryContainer = p.darkOnContainer,
            )
        } else {
            LightScheme.copy(
                primary = p.lightPrimary, primaryContainer = p.lightContainer, onPrimaryContainer = p.lightOnContainer,
                secondaryContainer = p.lightContainer, onSecondaryContainer = p.lightOnContainer,
            )
        }
        MaterialTheme(colorScheme = scheme, typography = AppTypography) {
            // Les écrans ont un fond transparent (dégradé) : Material ne peut pas en déduire la couleur
            // du texte et retomberait sur le noir. On fixe donc la couleur de contenu par défaut.
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides scheme.onBackground,
                content = content,
            )
        }
    }
}

/** Style pour les très grands montants (patrimoine total). */
val HeroAmountStyle: TextStyle
    @Composable get() = MaterialTheme.typography.displaySmall

/** Fond dégradé de l'app, à peindre une fois sous tout le contenu. */
val AppBackground: androidx.compose.ui.graphics.Brush
    @Composable get() = androidx.compose.ui.graphics.Brush.verticalGradient(
        0f to FinOpsTheme.finance.backgroundGradient[0],
        0.35f to FinOpsTheme.finance.backgroundGradient[1],
        1f to FinOpsTheme.finance.backgroundGradient[2],
    )
