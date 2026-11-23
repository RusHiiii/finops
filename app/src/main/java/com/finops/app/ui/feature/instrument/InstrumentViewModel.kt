package com.finops.app.ui.feature.instrument

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finops.app.common.runSuspendCatching
import com.finops.app.domain.calculator.Performance
import com.finops.app.domain.model.ChartRange
import com.finops.app.domain.model.Holding
import com.finops.app.domain.model.InstrumentSnapshot
import com.finops.app.domain.model.Portfolio
import com.finops.app.domain.model.Symbol
import com.finops.app.domain.usecase.GetInstrumentInsightsUseCase
import com.finops.app.domain.usecase.GetInstrumentUseCase
import com.finops.app.domain.usecase.InstrumentInsights
import com.finops.app.domain.usecase.LinkIsinUseCase
import com.finops.app.domain.usecase.ObserveIsinUseCase
import com.finops.app.domain.usecase.ObserveSettingsUseCase
import com.finops.app.domain.model.CurrencyCode
import com.finops.app.domain.usecase.GetPerformancesUseCase
import com.finops.app.domain.usecase.GetPriceHistoryUseCase
import com.finops.app.domain.usecase.GetRateToEurUseCase
import com.finops.app.domain.usecase.ObserveDashboardUseCase
import com.finops.app.domain.usecase.ObserveIsWatchedUseCase
import com.finops.app.domain.usecase.ObservePortfoliosUseCase
import com.finops.app.domain.usecase.ToggleWatchlistUseCase
import com.finops.app.ui.common.ChartUiState
import com.finops.app.ui.common.priceChartFlow
import com.finops.app.ui.common.userMessage
import com.finops.app.ui.navigation.RouteArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

data class InstrumentUiState(
    val symbol: Symbol,
    val loading: Boolean = true,
    val snapshot: InstrumentSnapshot? = null,
    val error: String? = null,
    val range: ChartRange = ChartRange.ONE_DAY,
    val chart: ChartUiState = ChartUiState(),
    /** null = en cours de chargement. */
    val performances: Map<ChartRange, Performance>? = null,
    /** Position consolidée de l'utilisateur sur cet instrument. */
    val holding: Holding? = null,
    val portfolios: List<Portfolio> = emptyList(),
    /** Présent dans la watchlist. */
    val watched: Boolean = false,
    /** Heure de récupération du cours. */
    val fetchedAt: Long? = null,
    /** Conversion devise de cotation → EUR (null si EUR ou indisponible). */
    val rateToEur: Double? = null,
    /** Labels, description, fiche fonds et risques ; chargés après la fiche. */
    val insightsLoading: Boolean = true,
    val insights: InstrumentInsights? = null,
    /** Message ponctuel (snackbar). */
    val message: String? = null,
    /** Devises suivies (paramètres). */
    val allowedCurrencies: Set<CurrencyCode> = CurrencyCode.entries.toSet(),
) {
    /** La devise de l'instrument est gérée et suivie : il peut entrer en portefeuille. */
    val currencyAllowed: Boolean get() = snapshot?.quote?.currencyCode?.let { it in allowedCurrencies } == true
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InstrumentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getInstrument: GetInstrumentUseCase,
    private val getHistory: GetPriceHistoryUseCase,
    private val getPerformances: GetPerformancesUseCase,
    observeDashboard: ObserveDashboardUseCase,
    observePortfolios: ObservePortfoliosUseCase,
    observeIsWatched: ObserveIsWatchedUseCase,
    private val toggleWatchlist: ToggleWatchlistUseCase,
    observeIsin: ObserveIsinUseCase,
    private val getInsights: GetInstrumentInsightsUseCase,
    private val linkIsin: LinkIsinUseCase,
    private val getRateToEur: GetRateToEurUseCase,
    private val clock: Clock,
    observeSettings: ObserveSettingsUseCase,
) : ViewModel() {
    val symbol = Symbol(
        checkNotNull(savedStateHandle[RouteArgs.TICKER]),
        checkNotNull(savedStateHandle[RouteArgs.EXCHANGE]),
    )

    private data class Load(
        val loading: Boolean = true,
        val snapshot: InstrumentSnapshot? = null,
        val error: String? = null,
        val performances: Map<ChartRange, Performance>? = null,
        val fetchedAt: Long? = null,
        val rateToEur: Double? = null,
    )

    private data class Insights(val loading: Boolean = true, val value: InstrumentInsights? = null)

    private data class Extra(
        val portfolios: List<Portfolio>,
        val watched: Boolean,
        val insights: Insights,
        val message: String?,
        val currencies: Set<CurrencyCode>,
    )

    private val load = MutableStateFlow(Load())
    private val range = MutableStateFlow(ChartRange.ONE_DAY)

    /** Chargé après la fiche : la variation 1J se calcule par rapport à la clôture précédente. */
    private val chart = load.map { it.snapshot }.filterNotNull().distinctUntilChanged()
        .flatMapLatest { s -> priceChartFlow(range, previousClose = { s.quote.previousClose }) { r -> getHistory(symbol, r) } }
        .onStart { emit(ChartUiState()) }

    private val holding = observeDashboard().map { d -> d.holdings.firstOrNull { it.asset.symbol == symbol } }
        .onStart { emit(null) }

    private val message = MutableStateFlow<String?>(null)

    /** Rechargé quand la fiche arrive ou quand un ISIN est associé. */
    private val insights = combine(
        load.map { it.snapshot }.filterNotNull().distinctUntilChanged(),
        observeIsin(symbol),
    ) { s, isin -> s to isin }
        .flatMapLatest { (s, isin) ->
            flow {
                emit(Insights(loading = true))
                emit(Insights(loading = false, value = runSuspendCatching { getInsights(s, isin) }.getOrNull()))
            }
        }
        .onStart { emit(Insights()) }

    private val extra = combine(
        observePortfolios().onStart { emit(emptyList()) },
        observeIsWatched(symbol).onStart { emit(false) },
        insights,
        message,
        observeSettings().map { it.currencies }.onStart { emit(CurrencyCode.entries.toSet()) },
    ) { p, w, i, m, c -> Extra(p, w, i, m, c) }

    val uiState: StateFlow<InstrumentUiState> = combine(load, range, chart, holding, extra) { l, r, c, h, e ->
        InstrumentUiState(
            symbol = symbol,
            loading = l.loading,
            snapshot = l.snapshot,
            error = l.error,
            range = r,
            chart = c,
            performances = l.performances,
            holding = h,
            portfolios = e.portfolios,
            watched = e.watched,
            fetchedAt = l.fetchedAt,
            rateToEur = l.rateToEur,
            insightsLoading = e.insights.loading,
            insights = e.insights.value,
            message = e.message,
            allowedCurrencies = e.currencies,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InstrumentUiState(symbol))

    init {
        reload()
    }

    fun reload() {
        load.value = Load()
        viewModelScope.launch {
            runSuspendCatching { getInstrument(symbol) }
                .onSuccess { s ->
                    load.update {
                        it.copy(
                            loading = false,
                            snapshot = s,
                            fetchedAt = clock.millis(),
                            error = if (s == null) "Instrument introuvable sur Google Finance" else null,
                        )
                    }
                    if (s != null) {
                        launch { getRateToEur(s.quote.currency)?.let { r -> load.update { it.copy(rateToEur = r) } } }
                        val perfs = getPerformances(symbol, s.quote.previousClose)
                        load.update { it.copy(performances = perfs) }
                    }
                }
                .onFailure { e -> load.update { it.copy(loading = false, error = e.userMessage()) } }
        }
    }

    /** Ajoute / retire de la watchlist (nécessite la fiche chargée). */
    fun toggleWatchlist() {
        val snapshot = load.value.snapshot ?: return
        viewModelScope.launch { runSuspendCatching { toggleWatchlist.invoke(snapshot) } }
    }

    /** Associe un ISIN saisi à l'instrument (débloque la fiche fonds justETF). */
    fun linkIsin(text: String) {
        viewModelScope.launch {
            runSuspendCatching { linkIsin.invoke(symbol, text) }.onFailure { e -> message.value = e.userMessage() }
        }
    }

    fun onMessageShown() {
        message.value = null
    }

    fun selectRange(r: ChartRange) {
        range.value = r
    }
}
