package com.finops.app.ui.common

import com.finops.app.domain.model.AssetType
import com.finops.app.domain.model.ChartRange
import com.finops.app.domain.model.TransactionSide

val AssetType.label: String get() = when (this) {
    AssetType.ETF -> "ETF"
    AssetType.STOCK -> "Action"
}

val AssetType.pluralLabel: String get() = when (this) {
    AssetType.ETF -> "ETF"
    AssetType.STOCK -> "Actions"
}

val TransactionSide.label: String get() = when (this) {
    TransactionSide.BUY -> "Achat"
    TransactionSide.SELL -> "Vente"
}

/** Libellé long d'une période (tableau des performances). */
val ChartRange.longLabel: String get() = when (this) {
    ChartRange.ONE_DAY -> "1 jour"
    ChartRange.FIVE_DAYS -> "5 jours"
    ChartRange.ONE_MONTH -> "1 mois"
    ChartRange.SIX_MONTHS -> "6 mois"
    ChartRange.YEAR_TO_DATE -> "Depuis le 1er janv."
    ChartRange.ONE_YEAR -> "1 an"
    ChartRange.FIVE_YEARS -> "5 ans"
    ChartRange.MAX -> "Max"
}

/** Libellé d'une clé de répartition (nom d'enum de type ou de devise, ou symbole). */
fun allocationLabel(key: String): String =
    AssetType.entries.firstOrNull { it.name == key }?.pluralLabel ?: key

val ChartRange.isIntraday: Boolean get() = this == ChartRange.ONE_DAY || this == ChartRange.FIVE_DAYS
