package com.finops.app.ui.feature.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finops.app.common.runSuspendCatching
import com.finops.app.domain.model.AssetType
import com.finops.app.domain.model.CurrencyCode
import com.finops.app.domain.model.Portfolio
import com.finops.app.domain.model.Symbol
import com.finops.app.domain.model.TransactionDraft
import com.finops.app.domain.model.TransactionSide
import com.finops.app.domain.usecase.AddTransactionUseCase
import com.finops.app.domain.usecase.GetAssetUseCase
import com.finops.app.domain.usecase.GetInstrumentUseCase
import com.finops.app.domain.usecase.ObservePortfoliosUseCase
import com.finops.app.domain.usecase.ObserveSettingsUseCase
import kotlinx.coroutines.flow.map
import com.finops.app.ui.common.toDecimalOrNull
import com.finops.app.ui.common.userMessage
import com.finops.app.ui.navigation.RouteArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class TransactionForm(
    val portfolioId: Long = RouteArgs.ALL,
    val side: TransactionSide = TransactionSide.BUY,
    val ticker: String = "",
    val exchange: String = "",
    val name: String = "",
    val type: AssetType = AssetType.ETF,
    val currency: CurrencyCode = CurrencyCode.EUR,
    val quantity: String = "",
    val price: String = "",
    val fees: String = "",
    val date: LocalDate,
    /** true si l'actif vient d'une position ou d'une fiche : champs de l'actif non modifiables. */
    val assetLocked: Boolean = false,
    val lookingUp: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
) {
    /** Montant total (frais inclus pour un achat, déduits pour une vente) ; null si saisie incomplète. */
    val total: Double?
        get() {
            val q = quantity.toDecimalOrNull() ?: return null
            val p = price.toDecimalOrNull() ?: return null
            val f = fees.ifBlank { "0" }.toDecimalOrNull() ?: return null
            return if (side == TransactionSide.BUY) q * p + f else q * p - f
        }
}

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observePortfolios: ObservePortfoliosUseCase,
    private val getAsset: GetAssetUseCase,
    private val getInstrument: GetInstrumentUseCase,
    private val addTransaction: AddTransactionUseCase,
    clock: Clock,
    observeSettings: ObserveSettingsUseCase,
) : ViewModel() {

    private val _form = MutableStateFlow(initialForm(savedStateHandle, LocalDate.now(clock)))
    val form: StateFlow<TransactionForm> = _form.asStateFlow()

    val portfolios: StateFlow<List<Portfolio>> =
        observePortfolios().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Devises proposées à la saisie (devises suivies). */
    val currencies: StateFlow<List<CurrencyCode>> = observeSettings()
        .map { s -> CurrencyCode.entries.filter { it in s.currencies } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CurrencyCode.entries)

    init {
        val assetId = savedStateHandle.get<Long>(RouteArgs.ASSET_ID) ?: RouteArgs.ALL
        if (assetId != RouteArgs.ALL) viewModelScope.launch {
            getAsset(assetId)?.let { a ->
                _form.update {
                    it.copy(
                        ticker = a.symbol.ticker,
                        exchange = a.symbol.exchange,
                        name = a.name,
                        type = a.type,
                        currency = a.currency,
                        price = it.price.ifBlank { a.lastPrice?.toString().orEmpty() },
                        assetLocked = true,
                    )
                }
            }
        }
        // Devise par défaut non suivie : bascule sur la première devise suivie (saisie libre uniquement).
        viewModelScope.launch {
            currencies.collect { allowed ->
                val f = _form.value
                if (!f.assetLocked && allowed.isNotEmpty() && f.currency !in allowed) _form.update { it.copy(currency = allowed.first()) }
            }
        }
        // Un seul portefeuille : présélection.
        viewModelScope.launch {
            portfolios.collect { list ->
                if (_form.value.portfolioId == RouteArgs.ALL && list.size == 1) _form.update { it.copy(portfolioId = list.first().id) }
            }
        }
    }

    fun edit(transform: TransactionForm.() -> TransactionForm) = _form.update { it.transform().copy(error = null) }

    /** Complète nom, type, devise et cours depuis Google Finance à partir du ticker et de la place. */
    fun lookup() {
        val f = _form.value
        val symbol = Symbol.parse("${f.ticker}:${f.exchange}") ?: run {
            _form.update { it.copy(error = "Ticker ou place invalide (ex. CW8 / EPA)") }
            return
        }
        _form.update { it.copy(lookingUp = true, error = null) }
        viewModelScope.launch {
            runSuspendCatching { getInstrument(symbol) }
                .onSuccess { s ->
                    val ccy = s?.quote?.currencyCode
                    _form.update {
                        when {
                            s == null -> it.copy(lookingUp = false, error = "${symbol.display} introuvable sur Google Finance")
                            ccy == null -> it.copy(lookingUp = false, error = "Devise ${s.quote.currency} non gérée (EUR / USD uniquement)")
                            else -> it.copy(
                                lookingUp = false,
                                ticker = symbol.ticker,
                                exchange = symbol.exchange,
                                name = s.quote.name,
                                type = s.quote.type ?: it.type,
                                currency = ccy,
                                price = s.quote.price.toString(),
                            )
                        }
                    }
                }
                .onFailure { e -> _form.update { it.copy(lookingUp = false, error = e.userMessage()) } }
        }
    }

    fun save() {
        val f = _form.value
        if (f.saving) return
        val symbol = Symbol.parse("${f.ticker}:${f.exchange}")
        val qty = f.quantity.toDecimalOrNull()
        val price = f.price.toDecimalOrNull()
        val fees = f.fees.ifBlank { "0" }.toDecimalOrNull()
        val error = when {
            symbol == null -> "Ticker ou place invalide (ex. CW8 / EPA)"
            qty == null -> "Quantité invalide"
            price == null -> "Prix invalide"
            fees == null -> "Frais invalides"
            else -> null
        }
        if (error != null) {
            _form.update { it.copy(error = error) }
            return
        }
        _form.update { it.copy(saving = true) }
        viewModelScope.launch {
            runSuspendCatching {
                addTransaction(
                    TransactionDraft(
                        portfolioId = f.portfolioId,
                        symbol = symbol!!,
                        name = f.name,
                        type = f.type,
                        currency = f.currency,
                        side = f.side,
                        quantity = qty!!,
                        price = price!!,
                        fees = fees!!,
                        date = f.date,
                    )
                )
            }.onSuccess {
                _form.update { it.copy(saving = false, saved = true) }
            }.onFailure { e ->
                _form.update { it.copy(saving = false, error = e.userMessage()) }
            }
        }
    }

    private companion object {
        fun initialForm(args: SavedStateHandle, today: LocalDate): TransactionForm {
            val ticker = args.get<String>(RouteArgs.TICKER)
            val exchange = args.get<String>(RouteArgs.EXCHANGE)
            return TransactionForm(
                portfolioId = args.get<Long>(RouteArgs.PORTFOLIO_ID) ?: RouteArgs.ALL,
                side = args.get<String>(RouteArgs.SIDE)?.let { s -> TransactionSide.entries.firstOrNull { it.name == s } } ?: TransactionSide.BUY,
                ticker = ticker.orEmpty(),
                exchange = exchange.orEmpty(),
                name = args.get<String>(RouteArgs.NAME).orEmpty(),
                type = args.get<String>(RouteArgs.TYPE)?.let { s -> AssetType.entries.firstOrNull { it.name == s } } ?: AssetType.ETF,
                currency = args.get<String>(RouteArgs.CURRENCY)?.let { s -> CurrencyCode.entries.firstOrNull { it.name == s } } ?: CurrencyCode.EUR,
                price = args.get<String>(RouteArgs.PRICE).orEmpty(),
                date = today,
                assetLocked = ticker != null && exchange != null,
            )
        }
    }
}
