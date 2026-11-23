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
    /** Devise dans laquelle tous les montants sont affichés (conversion au taux courant). */
    val displayCurrency: CurrencyCode = CurrencyCode.EUR,
    /** Notification la veille des publications de résultats des actions de la watchlist. */
    val notifyEarnings: Boolean = false,
    /** Déverrouillage par biométrie (ou code de l'appareil) à l'ouverture de l'app. */
    val biometricLock: Boolean = false,
)

/** Devise d'affichage et taux nécessaire à la conversion (1 USD = [usdToEur] EUR). */
data class DisplayCurrency(val currency: CurrencyCode, val usdToEur: Double?)

/** Contenu d'une sauvegarde exportée ou importée. */
data class BackupSummary(val portfolios: Int, val transactions: Int, val watchlist: Int)
