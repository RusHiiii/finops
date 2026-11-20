package com.finops.app.domain.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Couleur d'accent de l'interface. */
enum class AccentColor { BLUE, EMERALD, VIOLET, CORAL }

/** Préférences de l'utilisateur. */
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentColor = AccentColor.BLUE,
    /** Période affichée à l'ouverture des graphiques de patrimoine et de portefeuille. */
    val defaultRange: ChartRange = ChartRange.ONE_YEAR,
    /** Actualiser les cours à l'ouverture de l'accueil. */
    val refreshOnLaunch: Boolean = true,
    /** Devises suivies : seuls ces instruments apparaissent en recherche et peuvent entrer en portefeuille. */
    val currencies: Set<CurrencyCode> = CurrencyCode.entries.toSet(),
)

/** Contenu d'une sauvegarde exportée ou importée. */
data class BackupSummary(val portfolios: Int, val transactions: Int, val watchlist: Int)
