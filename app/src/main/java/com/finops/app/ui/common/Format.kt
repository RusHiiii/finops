package com.finops.app.ui.common

import com.finops.app.domain.model.CurrencyCode
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

private val LOCALE: Locale = Locale.FRANCE
private val DATE = DateTimeFormatter.ofPattern("d MMM yyyy", LOCALE)
private val DATE_SHORT = DateTimeFormatter.ofPattern("dd/MM/yyyy", LOCALE)
private val DATE_TIME = DateTimeFormatter.ofPattern("d MMM HH:mm", LOCALE)
private val TIME = DateTimeFormatter.ofPattern("HH:mm", LOCALE)
private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", LOCALE)

fun Double.formatMoney(currency: CurrencyCode, decimals: Int = 2): String = formatMoney(currency.name, decimals)

/** Devise inconnue : nombre seul. */
fun Double.formatMoney(currencyCode: String?, decimals: Int = 2): String {
    val currency = currencyCode?.let { runCatching { Currency.getInstance(it) }.getOrNull() }
        ?: return NumberFormat.getNumberInstance(LOCALE).apply { maximumFractionDigits = 4 }.format(this)
    return NumberFormat.getCurrencyInstance(LOCALE).apply {
        this.currency = currency
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
        // Le format français écrit « $US » ; seul le dollar américain est géré, « $ » suffit.
        SHORT_SYMBOLS[currency.currencyCode]?.let { symbol ->
            (this as? java.text.DecimalFormat)?.decimalFormatSymbols =
                decimalFormatSymbols.apply { currencySymbol = symbol }
        }
    }.format(this)
}

private val SHORT_SYMBOLS = mapOf("USD" to "$")

fun Double.formatSignedMoney(currency: CurrencyCode): String = sign() + formatMoney(currency)

fun Double.formatSignedMoney(currencyCode: String?): String = sign() + formatMoney(currencyCode)

private fun Double.sign() = if (this > 0) "+" else ""

fun Double.formatSignedPercent(): String = String.format(LOCALE, "%+.2f %%", this)

fun Double.formatPercent(decimals: Int = 1): String = String.format(LOCALE, "%.${decimals}f %%", this)

/** 5,06 T / 12,3 Md / 450 M / 12 k. */
fun Double.formatCompact(): String {
    val (value, suffix) = when {
        this >= 1e12 -> this / 1e12 to " T"
        this >= 1e9 -> this / 1e9 to " Md"
        this >= 1e6 -> this / 1e6 to " M"
        this >= 1e3 -> this / 1e3 to " k"
        else -> this to ""
    }
    return NumberFormat.getNumberInstance(LOCALE).apply { maximumFractionDigits = 2 }.format(value) + suffix
}

fun Double.formatQuantity(): String =
    NumberFormat.getNumberInstance(LOCALE).apply { maximumFractionDigits = 6 }.format(this)

fun Double.formatDecimal(decimals: Int = 2): String = String.format(LOCALE, "%.${decimals}f", this)

fun LocalDate.formatLong(): String = format(DATE)
fun LocalDate.formatShort(): String = format(DATE_SHORT)
fun LocalDate.formatMonth(): String = format(MONTH).replaceFirstChar { it.titlecase(LOCALE) }

/** Date/heure locale d'un instant ; [intraday] = heure seule. */
fun Long.formatInstant(intraday: Boolean = false, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(this).atZone(zone).format(if (intraday) DATE_TIME else DATE)

/** « à l'instant », « il y a 5 min », « il y a 3 h », sinon date et heure. */
fun Long.formatRelative(now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val minutes = (now - this) / 60_000
    return when {
        minutes < 1 -> "à l'instant"
        minutes < 60 -> "il y a $minutes min"
        minutes < 24 * 60 -> "il y a ${minutes / 60} h"
        else -> Instant.ofEpochMilli(this).atZone(zone).format(DATE_TIME)
    }
}

fun Long.formatTime(zone: ZoneId = ZoneId.systemDefault(), seconds: Boolean = false): String =
    Instant.ofEpochMilli(this).atZone(zone).format(if (seconds) TIME_SECONDS else TIME)

private val TIME_SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss", LOCALE)
private val DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM", LOCALE)

/** Horodatage de mise à jour : « aujourd'hui à 14:32:05 », sinon « le 19/11 à 17:35:00 ». */
fun Long.formatUpdatedAt(now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
    val at = Instant.ofEpochMilli(this).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val day = if (at.toLocalDate() == today) "aujourd'hui" else "le " + at.format(DAY_MONTH)
    return "$day à " + at.format(TIME_SECONDS)
}

/** Accepte la virgule décimale et les espaces (« 1 234,5 »). */
fun String.toDecimalOrNull(): Double? =
    filterNot { it == ' ' || it == '\u00A0' || it == '\u202F' }.replace(',', '.').toDoubleOrNull()

fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
fun Long.utcMillisToLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Graduation d'axe : compacte au-delà de 10 000 (« 12,5 k »), sinon 0 à 2 décimales selon l'ordre de grandeur. */
fun Double.formatAxis(): String = when {
    kotlin.math.abs(this) >= 10_000 -> formatCompact()
    kotlin.math.abs(this) >= 100 -> String.format(LOCALE, "%.0f", this)
    else -> String.format(LOCALE, "%.2f", this)
}

private val DAY_HEADER = DateTimeFormatter.ofPattern("EEEE d MMMM", LOCALE)

/** « Lundi 24 novembre ». */
fun LocalDate.formatDayHeader(): String = format(DAY_HEADER).replaceFirstChar { it.titlecase(LOCALE) }
