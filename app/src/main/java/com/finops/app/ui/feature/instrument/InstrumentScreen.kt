package com.finops.app.ui.feature.instrument

import com.finops.app.ui.common.approxSigned
import com.finops.app.ui.common.stockLogoUrl
import com.finops.app.ui.common.LocalMoney
import com.finops.app.ui.common.MoneyDisplay
import com.finops.app.ui.common.approx
import androidx.compose.runtime.CompositionLocalProvider
import com.finops.app.ui.common.money
import com.finops.app.ui.common.signedMoney
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.finops.app.ui.designsystem.FinOpsTheme
import com.finops.app.ui.designsystem.HeroCard
import com.finops.app.ui.designsystem.HeroChangeBadge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import com.finops.app.domain.model.AssetType
import com.finops.app.domain.usecase.InstrumentInsights
import com.finops.app.ui.common.formatUpdatedAt
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finops.app.domain.calculator.Performance
import com.finops.app.domain.model.ChartRange
import com.finops.app.domain.model.Holding
import com.finops.app.domain.model.InstrumentSnapshot
import com.finops.app.ui.common.InstrumentHeroHeader
import com.finops.app.ui.common.PriceChartCard
import com.finops.app.ui.common.formatCompact
import com.finops.app.ui.common.formatDecimal
import com.finops.app.ui.common.formatMoney
import com.finops.app.ui.common.formatPercent
import com.finops.app.ui.common.formatQuantity
import com.finops.app.ui.common.formatSignedPercent
import com.finops.app.ui.common.label
import com.finops.app.ui.designsystem.AssetAvatar
import com.finops.app.ui.designsystem.Caption
import com.finops.app.ui.designsystem.HeroAmountStyle
import com.finops.app.ui.designsystem.LoadingBox
import com.finops.app.ui.designsystem.MessageBox
import com.finops.app.ui.designsystem.SectionCard
import com.finops.app.ui.designsystem.SignedAmount
import com.finops.app.ui.designsystem.StatGrid
import com.finops.app.ui.designsystem.gainLossColor

/** Données nécessaires pour pré-remplir une transaction depuis la fiche. */
data class AddFromInstrument(val portfolioId: Long, val snapshot: InstrumentSnapshot)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstrumentScreen(
    onBack: () -> Unit,
    onAddTransaction: (AddFromInstrument) -> Unit,
    onOpenHolding: (assetId: Long) -> Unit,
    vm: InstrumentViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var scrub by remember { mutableStateOf<Int?>(null) }
    var pickPortfolio by rememberSaveable { mutableStateOf(false) }
    val snapshot = state.snapshot
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.onMessageShown() }
    }
    val canAdd = snapshot != null && state.currencyAllowed && state.portfolios.isNotEmpty()

    Scaffold(

        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(snapshot?.quote?.name?.takeIf { it.isNotBlank() } ?: state.symbol.display, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                },
                actions = {
                    if (snapshot != null) {
                        IconButton(onClick = vm::toggleWatchlist) {
                            Icon(
                                if (state.watched) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = if (state.watched) "Ne plus suivre" else "Suivre dans la watchlist",
                                tint = if (state.watched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
            )
        },
        floatingActionButton = {
            if (canAdd) {
                ExtendedFloatingActionButton(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = {
                        if (state.portfolios.size == 1) onAddTransaction(AddFromInstrument(state.portfolios.first().id, snapshot))
                        else pickPortfolio = true
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Ajouter au portefeuille") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        // Fiche en devise d'origine ; la devise par défaut (taux de la devise de cotation inclus) ne sert qu'au header.
        val display = LocalMoney.current.withRate(snapshot?.quote?.currency, state.rateToEur)
        CompositionLocalProvider(LocalMoney provides display.original()) {
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingBox()
                snapshot == null -> MessageBox(
                    title = "Fiche indisponible",
                    message = state.error,
                    icon = Icons.Default.CloudOff,
                    actionLabel = "Réessayer",
                    onAction = vm::reload,
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { Header(snapshot, state.insights, state.fetchedAt, state.currencyAllowed, display) }
                    item {
                        PriceChartCard(
                            chart = state.chart,
                            range = state.range,
                            scrubIndex = scrub,
                            onScrub = { scrub = it },
                            onRangeSelected = vm::selectRange,
                            currency = snapshot.quote.currency,
                            baseline = snapshot.quote.previousClose.takeIf { state.range == ChartRange.ONE_DAY },
                            baselineLabel = "Clôture préc.",
                        )
                    }
                    item { PerformancesCard(state.performances) }
                    item { RiskCard(state.insights?.risk, state.insightsLoading) }
                    if (snapshot.quote.type != AssetType.STOCK) {
                        item { FundInfoCard(state.insights, state.insightsLoading, vm::linkIsin) }
                        state.insights?.fund?.let { f -> item { CompositionCard(f) } }
                    }
                    state.holding?.let { h -> item { MyPositionCard(h, display) { onOpenHolding(h.asset.id) } } }
                    item { CharacteristicsCard(snapshot) }
                    snapshot.stats?.description?.let { item { AboutCard(it) } }
                }
            }
        }
        }
    }

    if (pickPortfolio && snapshot != null) {
        AlertDialog(
            onDismissRequest = { pickPortfolio = false },
            title = { Text("Ajouter à…") },
            text = {
                Column {
                    state.portfolios.forEach { p ->
                        ListItem(
                            headlineContent = { Text(p.name) },
                            modifier = Modifier.clickable {
                                pickPortfolio = false
                                onAddTransaction(AddFromInstrument(p.id, snapshot))
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickPortfolio = false }) { Text("Annuler") } },
        )
    }
}

/** En-tête de fiche dans un bandeau bleu : identité, labels, cours, variation, heure. */
@Composable
private fun Header(s: InstrumentSnapshot, insights: InstrumentInsights?, fetchedAt: Long?, currencyAllowed: Boolean, display: MoneyDisplay) {
    val q = s.quote
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InstrumentHeroHeader(
            ticker = s.symbol.ticker,
            name = q.name,
            subtitle = listOfNotNull(s.symbol.display, q.type?.label, q.currency).joinToString("  ·  "),
            amount = q.price.money(q.currency),
            convertedAmount = display.approx(q.price, q.currency),
            labels = insights?.labels.orEmpty(),
            changePercent = q.changePercent,
            changeText = q.change?.let { it.signedMoney(q.currency) + " aujourd'hui" },
            footer = fetchedAt?.let { "Cours récupéré ${it.formatUpdatedAt()}" },
            logoUrl = stockLogoUrl(s.symbol, q.type),
        )
        if (!currencyAllowed) {
            Caption(
                if (q.currencyCode == null) "Devise ${q.currency ?: "inconnue"} non gérée (EUR / USD uniquement) : ajout au portefeuille impossible."
                else "Devise ${q.currency} non suivie : activez-la dans Paramètres → Devises suivies pour l'ajouter à un portefeuille.",
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Tableau des performances par période, façon fiche ETF. */
@Composable
private fun PerformancesCard(performances: Map<ChartRange, Performance>?) {
    SectionCard(title = "Performances") {
        if (performances == null) {
            LoadingBox(height = 96.dp)
            return@SectionCard
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ChartRange.entries.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { r ->
                        val p = performances[r]
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Caption(r.label)
                            Text(
                                p?.percent?.formatSignedPercent() ?: "–",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = p?.let { gainLossColor(it.absolute) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MyPositionCard(h: Holding, display: MoneyDisplay, onClick: () -> Unit) {
    val a = h.asset
    val ccy = a.currency.name
    SectionCard(title = "Ma position", modifier = Modifier.clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(h.marketValue.money(a.currency), style = MaterialTheme.typography.titleMedium)
                display.approx(h.marketValue, ccy)?.let { Caption(it) }
                Caption(
                    "${h.quantity.formatQuantity()} parts · PRU ${h.averagePrice.money(a.currency)}" +
                        (display.approx(h.averagePrice, ccy)?.let { " ($it)" } ?: ""),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                SignedAmount(h.unrealizedPnl, a.currency, percent = h.unrealizedPnlPercent)
                display.approxSigned(h.unrealizedPnl, ccy)?.let { Caption(it) }
            }
        }
    }
}

@Composable
private fun CharacteristicsCard(s: InstrumentSnapshot) {
    val q = s.quote
    val st = s.stats
    val ccy = q.currency
    SectionCard(title = "Données de marché") {
        StatGrid(
            listOf(
                "Clôture précédente" to q.previousClose?.money(ccy),
                "Ouverture" to st?.open?.money(ccy),
                "Plus bas / haut jour" to range(st?.dayLow, st?.dayHigh, ccy),
                "Plus bas / haut 52 sem." to range(st?.yearLow, st?.yearHigh, ccy),
                "Volume" to st?.volume?.formatCompact(),
                "Capitalisation" to st?.marketCap?.let { "${it.formatCompact()} ${ccy.orEmpty()}" },
                "PER" to st?.peRatio?.formatDecimal(),
                "Rendement dividende" to st?.dividendYieldPercent?.formatPercent(2),
            )
        )
    }
}

@Composable
private fun range(low: Double?, high: Double?, ccy: String?): String? =
    if (low == null || high == null) null else "${low.money(ccy)} – ${high.money(ccy)}"

@Composable
private fun AboutCard(description: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SectionCard(title = "À propos", modifier = Modifier.animateContentSize()) {
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
            Text(if (expanded) "Réduire" else "Lire la suite")
        }
    }
}
