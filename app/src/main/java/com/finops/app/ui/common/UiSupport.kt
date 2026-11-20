package com.finops.app.ui.common

import com.finops.app.domain.calculator.Performance
import com.finops.app.domain.model.MarketDataException
import com.finops.app.domain.model.PricePoint
import com.finops.app.domain.model.ValidationException
import com.finops.app.domain.model.ValuePoint
import com.finops.app.ui.designsystem.ChartPoint

/** Message affichable pour une erreur : messages métier tels quels, sinon message générique. */
fun Throwable.userMessage(): String = when (this) {
    is ValidationException, is MarketDataException -> message ?: GENERIC_ERROR
    else -> GENERIC_ERROR
}

const val GENERIC_ERROR = "Une erreur inattendue est survenue"

/** État d'un graphique chargé à la demande. */
data class ChartUiState(
    val loading: Boolean = true,
    val points: List<ChartPoint> = emptyList(),
    /** Courbe secondaire (capital investi), mêmes abscisses. */
    val secondary: List<ChartPoint>? = null,
    val performance: Performance? = null,
    val error: String? = null,
) {
    companion object {
        fun ofPrices(points: List<PricePoint>, performance: Performance?) = ChartUiState(
            loading = false,
            points = points.map { ChartPoint(it.epochMillis, it.price) },
            performance = performance,
        )

        fun ofValues(points: List<ValuePoint>, performance: Performance?) = ChartUiState(
            loading = false,
            points = points.map { ChartPoint(it.epochMillis, it.valueEur) },
            secondary = points.map { ChartPoint(it.epochMillis, it.investedEur) },
            performance = performance,
        )
    }
}
