package com.finops.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.finops.app.domain.model.CurrencyCode

/**
 * Affichage des montants dans la devise par défaut ([target]), au taux courant.
 * Un montant dont le taux est inconnu reste affiché dans sa devise d'origine.
 */
data class MoneyDisplay(
    val target: CurrencyCode = CurrencyCode.EUR,
    /** 1 USD = x EUR (dernier taux mémorisé). */
    val usdToEur: Double? = null,
    /** Taux vers l'euro d'autres devises (ex. devise de cotation d'une fiche : GBP, CHF…). */
    val extraRatesToEur: Map<String, Double> = emptyMap(),
    /** false : montants laissés dans leur devise d'origine (fiches, listes) ; seules les vues agrégées convertissent. */
    val enabled: Boolean = true,
) {
    private fun rateToEur(code: String): Double? = when (code) {
        "EUR" -> 1.0
        "USD" -> usdToEur
        else -> extraRatesToEur[code]
    }

    /** Multiplicateur devise [from] → devise par défaut ; null si un taux manque. */
    fun factor(from: String?): Double? {
        val code = from?.uppercase() ?: return null
        if (code == target.name) return 1.0
        if (!enabled) return null
        val toEur = rateToEur(code) ?: return null
        val eurToTarget = if (target == CurrencyCode.EUR) 1.0 else usdToEur?.takeIf { it > 0 }?.let { 1 / it } ?: return null
        return toEur * eurToTarget
    }

    /** Le montant sera-t-il affiché dans une autre devise que [from] ? */
    fun converts(from: String?): Boolean = from != null && from.uppercase() != target.name && factor(from) != null

    /** Même devise par défaut, mais montants affichés dans leur devise d'origine. */
    fun original(): MoneyDisplay = copy(enabled = false)

    fun withRate(code: String?, rateToEur: Double?): MoneyDisplay =
        if (code == null || rateToEur == null) this else copy(extraRatesToEur = extraRatesToEur + (code.uppercase() to rateToEur))

    fun format(amount: Double, from: String?, decimals: Int = 2): String =
        factor(from)?.let { (amount * it).formatMoney(target, decimals) } ?: amount.formatMoney(from, decimals)

    fun formatSigned(amount: Double, from: String?): String =
        factor(from)?.let { (amount * it).formatSignedMoney(target) } ?: amount.formatSignedMoney(from)
}

/** Devise d'affichage courante, fournie à la racine de l'UI. */
val LocalMoney = staticCompositionLocalOf { MoneyDisplay() }

@Composable
@ReadOnlyComposable
fun Double.money(currency: CurrencyCode, decimals: Int = 2): String = LocalMoney.current.format(this, currency.name, decimals)

@Composable
@ReadOnlyComposable
fun Double.money(currency: String?, decimals: Int = 2): String = LocalMoney.current.format(this, currency, decimals)

@Composable
@ReadOnlyComposable
fun Double.signedMoney(currency: CurrencyCode): String = LocalMoney.current.formatSigned(this, currency.name)

@Composable
@ReadOnlyComposable
fun Double.signedMoney(currency: String?): String = LocalMoney.current.formatSigned(this, currency)

/** Contenu affiché dans la devise d'origine des montants (pas de conversion). */
@Composable
fun OriginalCurrency(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMoney provides LocalMoney.current.original(), content = content)
}

/** « ≈ +12,30 € » : contre-valeur signée dans la devise par défaut, ou null si inutile ou taux inconnu. */
fun MoneyDisplay.approxSigned(amount: Double, from: String?): String? =
    if (converts(from)) "≈ " + formatSigned(amount, from) else null

/** « ≈ 39,84 € » : contre-valeur dans la devise par défaut, ou null si inutile ou taux inconnu. */
fun MoneyDisplay.approx(amount: Double, from: String?): String? =
    if (converts(from)) "≈ " + format(amount, from) else null

/**
 * « ≈ 39,84 € » : montant [eur] déjà converti en EUR (taux des transactions) affiché dans la devise par défaut ;
 * null si [from] est déjà la devise par défaut ou si le montant est inconnu.
 */
fun MoneyDisplay.approxEur(eur: Double?, from: String?): String? =
    if (eur != null && converts(from)) "≈ " + format(eur, CurrencyCode.EUR.name) else null

/** Variante signée de [approxEur]. */
fun MoneyDisplay.approxSignedEur(eur: Double?, from: String?): String? =
    if (eur != null && converts(from)) "≈ " + formatSigned(eur, CurrencyCode.EUR.name) else null
