package com.finops.app.ui.common

import com.finops.app.domain.calculator.PerformanceCalculator
import com.finops.app.domain.model.ChartRange
import com.finops.app.domain.model.ValuePoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart

/**
 * Courbe de valeur rechargée à chaque changement de période ou de positions ([positionsKey]).
 * Le chargement précédent est annulé si un nouveau démarre.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun historyChartFlow(
    positionsKey: Flow<Any>,
    range: Flow<ChartRange>,
    load: suspend (ChartRange) -> List<ValuePoint>,
): Flow<ChartUiState> =
    combine(positionsKey.distinctUntilChanged(), range) { _, r -> r }
        .flatMapLatest { r ->
            flow {
                emit(ChartUiState(loading = true))
                val state = try {
                    val points = load(r)
                    ChartUiState.ofValues(points, PerformanceCalculator.ofPortfolio(points))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ChartUiState(loading = false, error = e.userMessage())
                }
                emit(state)
            }
        }

/** Historique de cours d'un instrument, rechargé à chaque changement de période. */
@OptIn(ExperimentalCoroutinesApi::class)
fun priceChartFlow(
    range: Flow<ChartRange>,
    previousClose: () -> Double?,
    load: suspend (ChartRange) -> List<com.finops.app.domain.model.PricePoint>,
): Flow<ChartUiState> =
    range.flatMapLatest { r ->
        flow {
            emit(ChartUiState(loading = true))
            val state = try {
                val points = load(r)
                val reference = if (r == ChartRange.ONE_DAY) previousClose() else null
                if (points.isEmpty()) ChartUiState(loading = false, error = "Historique ${r.label} indisponible")
                else ChartUiState.ofPrices(points, PerformanceCalculator.ofPrices(points, reference))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ChartUiState(loading = false, error = e.userMessage())
            }
            emit(state)
        }
    }

/** Exposition secteur / pays, recalculée quand les positions changent ; échec = exposition absente. */
@OptIn(ExperimentalCoroutinesApi::class)
fun exposureFlow(
    positionsKey: Flow<Any>,
    load: suspend () -> com.finops.app.domain.usecase.Exposure,
): Flow<ExposureState> =
    positionsKey.distinctUntilChanged().flatMapLatest {
        flow {
            emit(ExposureState(loading = true))
            emit(ExposureState(loading = false, exposure = com.finops.app.common.runSuspendCatching { load() }.getOrNull()))
        }
    }.onStart { emit(ExposureState()) }
